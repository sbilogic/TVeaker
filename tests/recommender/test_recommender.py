"""Comprehensive tests for the recommendation engine, TF-IDF ranking, and explanations."""

import json
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import create_engine

from tveaker.clock import FrozenClock
from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    Rating,
    RecommendationRun,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)
from tveaker.recommender.engine import RecommendationEngine
from tveaker.recommender.ranker import RankingContext


@pytest.fixture
def recommender_env():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    now = datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC)

    with get_db_session(engine) as session:
        account = Account(
            id=1,
            trakt_uuid="u1",
            username="sahil",
            timezone="UTC",
            connected_at=now,
        )
        session.add(account)
        session.flush()

        # Watched and highly rated Sci-Fi Movie (creates Sci-Fi taste)
        m_sci = MediaItem(
            id=1,
            media_type="movie",
            trakt_id=10,
            title="Interstellar",
            genres_json=json.dumps(["sci-fi", "adventure"]),
            runtime_minutes=169,
            overview="A team of explorers travel through a wormhole in space.",
        )
        session.add(m_sci)
        session.flush()

        we1 = WatchEvent(
            history_id=101,
            account_id=1,
            movie_id=1,
            watched_at=now - timedelta(days=5),
            action="watch",
        )
        r1 = Rating(
            account_id=1,
            media_type="movie",
            trakt_id=10,
            rating=10,
            rated_at=now - timedelta(days=5),
        )
        session.add_all([we1, r1])

        # Candidate 1: Sci-Fi movie on watchlist (120 mins)
        m_cand1 = MediaItem(
            id=2,
            media_type="movie",
            trakt_id=20,
            title="Arrival",
            genres_json=json.dumps(["sci-fi", "drama"]),
            runtime_minutes=116,
            overview="Linguist works with the military to communicate with alien lifeforms.",
        )
        wl1 = WatchlistItem(account_id=1, media_type="movie", media_item_id=2, listed_at=now)

        # Candidate 2: Romance Comedy movie (90 mins)
        m_cand2 = MediaItem(
            id=3,
            media_type="movie",
            trakt_id=30,
            title="Love Comedy",
            genres_json=json.dumps(["romance", "comedy"]),
            runtime_minutes=90,
            overview="A couple navigates funny dating scenarios.",
        )

        # Candidate 3: Active in-progress show (45 min eps, 4 remaining)
        s_cand3 = MediaItem(
            id=4,
            media_type="show",
            trakt_id=40,
            title="Dark",
            genres_json=json.dumps(["sci-fi", "mystery"]),
            runtime_minutes=50,
            overview="A family saga with a supernatural twist set in a German town.",
        )
        ts3 = TrackedShow(
            account_id=1,
            show_id=4,
            status="watching",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )

        session.add_all([m_cand1, wl1, m_cand2, s_cand3, ts3])
        session.flush()

        # Seed episodes for Show Candidate 3 (6 episodes total, 2 watched)
        for ep_num in range(1, 7):
            ep = Episode(
                id=400 + ep_num,
                show_id=4,
                trakt_id=4000 + ep_num,
                season_number=1,
                episode_number=ep_num,
                title=f"S01E0{ep_num}",
                runtime_minutes=50,
                first_aired=now - timedelta(days=10 - ep_num),
            )
            session.add(ep)
            session.flush()
            if ep_num <= 2:
                session.add(
                    WatchEvent(
                        history_id=4000 + ep_num,
                        account_id=1,
                        episode_id=ep.id,
                        watched_at=now - timedelta(days=2),
                        action="watch",
                    )
                )

    clock = FrozenClock(now)
    engine_svc = RecommendationEngine(db_engine=engine, account_id=1, clock=clock)
    yield engine_svc, engine
    Base.metadata.drop_all(bind=engine)


def test_recommendation_taste_profile_and_ranking(recommender_env):
    rec_engine, _ = recommender_env

    res = rec_engine.recommend(context=RankingContext(intent="auto"), limit=5)
    assert len(res.items) >= 2

    # Sci-fi items should rank above Love Comedy
    top_candidate = res.items[0]
    assert top_candidate.title in ("Arrival", "Dark")
    assert "Sci-Fi" in top_candidate.explanation or "Watching" in top_candidate.explanation


def test_recommendation_time_budget_filter(recommender_env):
    rec_engine, _ = recommender_env

    # 95 minute budget should penalize 116 minute Arrival
    res_budget = rec_engine.recommend(
        context=RankingContext(time_budget_minutes=95),
        limit=5,
    )
    assert len(res_budget.items) >= 1
    # 50 min Dark or 90 min Love Comedy should get full budget score
    for it in res_budget.items:
        if it.title == "Love Comedy":
            assert it.breakdown.budget_score == 1.0
        elif it.title == "Arrival":
            assert it.breakdown.budget_score < 1.0


def test_recommendation_intent_finish_show(recommender_env):
    rec_engine, _ = recommender_env

    res = rec_engine.recommend(context=RankingContext(intent="finish_show"), limit=5)
    assert len(res.items) >= 1
    assert res.items[0].title == "Dark"
    assert res.items[0].media_type == "show"
    assert "Watching" in res.items[0].explanation


def test_recommendation_run_persisted_to_db(recommender_env):
    rec_engine, db_engine = recommender_env

    res = rec_engine.recommend(context=RankingContext(intent="movie"), limit=5)

    with get_db_session(db_engine) as session:
        run = session.get(RecommendationRun, res.run_id)
        assert run is not None
        assert run.model_version == "content-v1"
        data = json.loads(run.ranked_candidates_json)
        assert isinstance(data, list)
        assert len(data) >= 1
