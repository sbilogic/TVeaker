"""Tests for 7-day full reconciliation and remote deletion cleanup."""

from datetime import UTC, datetime
from pathlib import Path

import httpx
import pytest
import respx
from sqlalchemy import create_engine, select

from tveaker.auth.token_store import MemoryTokenStore, TokenData
from tveaker.auth.trakt_oauth import TraktOAuth
from tveaker.clock import FrozenClock
from tveaker.config import Settings
from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.models import (
    Account,
    MediaItem,
    Rating,
    RecommendationFeedback,
    RecommendationRun,
    TrackedShow,
    WatchEvent,
)
from tveaker.sync.importer import AccountSync
from tveaker.trakt.client import TraktClient


@pytest.fixture
def fixtures_dir() -> Path:
    return Path(__file__).parent.parent / "fixtures" / "trakt"


@pytest.fixture
def recon_env(fixtures_dir):
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    t0 = datetime(2026, 8, 29, 8, 0, 0, tzinfo=UTC)
    with get_db_session(engine) as session:
        account = Account(
            id=1,
            trakt_uuid="uuid-1",
            username="sahil",
            timezone="UTC",
            connected_at=t0,
        )
        session.add(account)
        session.flush()

        movie1 = MediaItem(media_type="movie", trakt_id=10, title="Movie 1")
        movie2 = MediaItem(media_type="movie", trakt_id=20, title="Movie 2")
        session.add_all([movie1, movie2])
        session.flush()

        # Two local watch events
        we1 = WatchEvent(
            history_id=1001,
            account_id=1,
            watched_at=t0,
            action="watch",
            movie_id=movie1.id,
        )
        we2 = WatchEvent(
            history_id=1002,
            account_id=1,
            watched_at=t0,
            action="watch",
            movie_id=movie2.id,
        )
        session.add_all([we1, we2])

        # Two local ratings
        r1 = Rating(account_id=1, media_type="movie", trakt_id=10, rating=8, rated_at=t0)
        r2 = Rating(account_id=1, media_type="movie", trakt_id=20, rating=5, rated_at=t0)
        session.add_all([r1, r2])

        # Local manual tracked show
        ts = TrackedShow(
            account_id=1,
            show_id=movie1.id,  # using item 1
            status="watching",
            status_source="manual",
            manual_episodes_per_week=5.0,
            created_at=t0,
            updated_at=t0,
        )
        session.add(ts)

        # Local recommendation run & feedback
        run = RecommendationRun(
            created_at=t0,
            context_json="{}",
            model_version="content-v1",
            ranked_candidates_json="[]",
        )
        session.add(run)
        session.flush()

        fb = RecommendationFeedback(
            run_id=run.id,
            candidate_id="movie:10",
            action="accepted",
            created_at=t0,
        )
        session.add(fb)

    settings = Settings(
        trakt_client_id="c1",
        trakt_client_secret="s1",
        trakt_api_base_url="https://api.trakt.tv",
    )
    token = TokenData("token_1", "ref_1", 1000, 7200)
    store = MemoryTokenStore(token)
    oauth = TraktOAuth(settings=settings, token_store=store)
    client = TraktClient(settings=settings, token_store=store, oauth=oauth)
    clock = FrozenClock(datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC))

    sync = AccountSync(db_engine=engine, trakt_client=client, clock=clock)
    yield sync, engine, fixtures_dir
    Base.metadata.drop_all(bind=engine)


@respx.mock
def test_full_reconciliation_deletes_remote_removed_items(recon_env):
    sync, engine, _ = recon_env

    # Remote only has history_id 1001 (1002 was deleted remotely)
    remote_history = [
        {
            "id": 1001,
            "watched_at": "2026-08-29T08:00:00.000Z",
            "action": "watch",
            "type": "movie",
            "movie": {"title": "Movie 1", "ids": {"trakt": 10}},
        }
    ]
    respx.get("https://api.trakt.tv/sync/history").mock(
        return_value=httpx.Response(
            200,
            json=remote_history,
            headers={
                "x-pagination-page": "1",
                "x-pagination-page-count": "1",
                "x-pagination-item-count": "1",
                "x-pagination-limit": "100",
            },
        )
    )

    # Remote only has rating for movie 10
    respx.get("https://api.trakt.tv/users/me/ratings/movies").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "rated_at": "2026-08-29T08:00:00.000Z",
                    "rating": 8,
                    "type": "movie",
                    "movie": {"title": "Movie 1", "ids": {"trakt": 10}},
                }
            ],
        )
    )
    for path in [
        "/users/me/ratings/shows",
        "/users/me/ratings/episodes",
        "/users/me/watchlist/movies",
        "/users/me/watchlist/shows",
        "/sync/playback/movies",
        "/sync/playback/episodes",
    ]:
        respx.get(f"https://api.trakt.tv{path}").mock(return_value=httpx.Response(200, json=[]))

    respx.get("https://api.trakt.tv/sync/last_activities").mock(
        return_value=httpx.Response(200, json={"all": "2026-08-29T10:00:00.000Z"})
    )

    report = sync.run(mode="full")
    assert report.status == "success"
    assert report.deleted.get("history") == 1

    with get_db_session(engine) as session:
        # History event 1002 was deleted, 1001 remains
        events = session.execute(select(WatchEvent)).scalars().all()
        assert len(events) == 1
        assert events[0].history_id == 1001

        # Rating 20 was deleted, rating 10 remains
        ratings = session.execute(select(Rating)).scalars().all()
        assert len(ratings) == 1
        assert ratings[0].trakt_id == 10

        # Local manual tracking row was preserved!
        tracked = session.execute(select(TrackedShow)).scalars().all()
        assert len(tracked) == 1
        assert tracked[0].status == "watching"
        assert tracked[0].status_source == "manual"
        assert tracked[0].manual_episodes_per_week == 5.0

        # Local feedback was preserved!
        feedbacks = session.execute(select(RecommendationFeedback)).scalars().all()
        assert len(feedbacks) == 1
        assert feedbacks[0].action == "accepted"
