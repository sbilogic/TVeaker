"""Tests for recommendation candidate pool generation and exclusion rules."""

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
    RecommendationFeedback,
    RecommendationRun,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)
from tveaker.recommender.candidates import CandidatePoolGenerator


@pytest.fixture
def candidate_env():
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

        # Movie 1: Watchlisted (unwatched)
        m1 = MediaItem(
            id=1,
            media_type="movie",
            trakt_id=10,
            title="Dune 2",
            genres_json=json.dumps(["sci-fi"]),
        )
        # Movie 2: Watched
        m2 = MediaItem(
            id=2,
            media_type="movie",
            trakt_id=20,
            title="Inception",
            genres_json=json.dumps(["sci-fi"]),
        )
        # Movie 3: Deferred 'not_now' recently (within 30d)
        m3 = MediaItem(
            id=3,
            media_type="movie",
            trakt_id=30,
            title="Bad Movie",
            genres_json=json.dumps(["comedy"]),
        )
        # Movie 4: Deferred 'not_now' 40 days ago
        m4 = MediaItem(
            id=4,
            media_type="movie",
            trakt_id=40,
            title="Old Dismissed",
            genres_json=json.dumps(["drama"]),
        )
        # Movie 8: 'not_interested' permanently
        m8 = MediaItem(
            id=8,
            media_type="movie",
            trakt_id=80,
            title="Hated Movie",
            genres_json=json.dumps(["horror"]),
        )

        # Show 1: Active show (4 eps, 2 watched)
        s1 = MediaItem(
            id=5,
            media_type="show",
            trakt_id=50,
            title="Severance",
            genres_json=json.dumps(["drama"]),
        )
        # Show 2: Dropped show
        s2 = MediaItem(
            id=6,
            media_type="show",
            trakt_id=60,
            title="Dropped Show",
            genres_json=json.dumps(["comedy"]),
        )
        # Show 3: Completed show (2 eps, 2 watched)
        s3 = MediaItem(
            id=7,
            media_type="show",
            trakt_id=70,
            title="Completed Show",
            genres_json=json.dumps(["drama"]),
        )

        session.add_all([m1, m2, m3, m4, m8, s1, s2, s3])
        session.flush()

        # Watchlist
        wl1 = WatchlistItem(account_id=1, media_type="movie", media_item_id=1, listed_at=now)
        session.add(wl1)

        # Watched movie 2
        we2 = WatchEvent(history_id=1002, account_id=1, movie_id=2, watched_at=now, action="watch")
        session.add(we2)

        # Recommendation run & feedbacks
        run = RecommendationRun(
            created_at=now,
            context_json="{}",
            model_version="content-v1",
            ranked_candidates_json="[]",
        )
        session.add(run)
        session.flush()

        fb_recent = RecommendationFeedback(
            run_id=run.id,
            candidate_id="movie:3",
            action="not_now",
            created_at=now - timedelta(days=10),
        )
        fb_old = RecommendationFeedback(
            run_id=run.id,
            candidate_id="movie:4",
            action="not_now",
            created_at=now - timedelta(days=40),
        )
        fb_perm = RecommendationFeedback(
            run_id=run.id,
            candidate_id="movie:8",
            action="not_interested",
            created_at=now - timedelta(days=100),
        )
        session.add_all([fb_recent, fb_old, fb_perm])

        # Show 1: 4 episodes, 2 watched
        for ep_num in range(1, 5):
            ep = Episode(
                id=500 + ep_num,
                show_id=5,
                trakt_id=5000 + ep_num,
                season_number=1,
                episode_number=ep_num,
                title=f"S01E0{ep_num}",
            )
            session.add(ep)
            session.flush()
            if ep_num <= 2:
                session.add(
                    WatchEvent(
                        history_id=5000 + ep_num,
                        account_id=1,
                        episode_id=ep.id,
                        watched_at=now,
                        action="watch",
                    )
                )

        ts1 = TrackedShow(
            account_id=1,
            show_id=5,
            status="watching",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )

        # Show 2: Dropped show
        ts2 = TrackedShow(
            account_id=1,
            show_id=6,
            status="dropped",
            status_source="manual",
            created_at=now,
            updated_at=now,
        )

        # Show 3: 2 episodes, 2 watched (completed)
        for ep_num in range(1, 3):
            ep = Episode(
                id=700 + ep_num,
                show_id=7,
                trakt_id=7000 + ep_num,
                season_number=1,
                episode_number=ep_num,
                title=f"S01E0{ep_num}",
            )
            session.add(ep)
            session.flush()
            session.add(
                WatchEvent(
                    history_id=7000 + ep_num,
                    account_id=1,
                    episode_id=ep.id,
                    watched_at=now,
                    action="watch",
                )
            )

        ts3 = TrackedShow(
            account_id=1,
            show_id=7,
            status="completed",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )

        session.add_all([ts1, ts2, ts3])

    clock = FrozenClock(now)
    gen = CandidatePoolGenerator(db_engine=engine, account_id=1, clock=clock)
    yield gen, engine
    Base.metadata.drop_all(bind=engine)


def test_candidate_generation_and_exclusions(candidate_env):
    gen, _ = candidate_env

    candidates = gen.generate_candidates(media_type="all")
    candidate_ids = {c.candidate_id for c in candidates}

    # Movie 1 (Watchlisted) should be present
    assert "movie:1" in candidate_ids

    # Movie 2 (Watched) MUST be excluded
    assert "movie:2" not in candidate_ids

    # Movie 3 (not_now 10 days ago) MUST be excluded
    assert "movie:3" not in candidate_ids

    # Movie 4 (not_now 40 days ago) SHOULD be re-included
    assert "movie:4" in candidate_ids

    # Movie 8 (not_interested 100 days ago) MUST be permanently excluded
    assert "movie:8" not in candidate_ids

    # Show 1 (Severance, 2 remaining episodes) SHOULD be present
    assert "show:5" in candidate_ids
    sev_cand = next(c for c in candidates if c.candidate_id == "show:5")
    assert sev_cand.remaining_episodes == 2
    assert sev_cand.in_progress is True

    # Show 2 (Dropped) MUST be excluded
    assert "show:6" not in candidate_ids

    # Show 3 (Completed) MUST be excluded
    assert "show:7" not in candidate_ids


def test_candidate_generation_type_filters(candidate_env):
    gen, _ = candidate_env

    movie_candidates = gen.generate_candidates(media_type="movie")
    assert all(c.media_type == "movie" for c in movie_candidates)

    show_candidates = gen.generate_candidates(media_type="show")
    assert all(c.media_type == "show" for c in show_candidates)
