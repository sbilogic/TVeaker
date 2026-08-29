"""Tests for episode catalog ingestion, specials, and watched progress handling."""

from datetime import UTC, datetime, timedelta
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
from tveaker.models import Episode, MediaItem, WatchEvent
from tveaker.sync.importer import AccountSync
from tveaker.trakt.client import TraktClient


@pytest.fixture
def fixtures_dir() -> Path:
    return Path(__file__).parent.parent / "fixtures" / "trakt"


@pytest.fixture
def catalog_env(fixtures_dir):
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    settings = Settings(
        trakt_client_id="test_client",
        trakt_client_secret="test_secret",
        trakt_api_base_url="https://api.trakt.tv",
    )
    token = TokenData("mock_token", "mock_ref", 1000, 7200)
    store = MemoryTokenStore(token)
    oauth = TraktOAuth(settings=settings, token_store=store)
    client = TraktClient(settings=settings, token_store=store, oauth=oauth)
    clock = FrozenClock(datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC))

    sync = AccountSync(db_engine=engine, trakt_client=client, clock=clock)
    yield sync, engine, fixtures_dir
    Base.metadata.drop_all(bind=engine)


@respx.mock
def test_specials_and_future_episodes_catalog(catalog_env):
    sync, engine, _ = catalog_env

    # Mock settings
    respx.get("https://api.trakt.tv/users/settings").mock(
        return_value=httpx.Response(
            200,
            json={
                "user": {"username": "test", "ids": {"slug": "t", "uuid": "u1"}},
                "account": {"timezone": "UTC"},
            },
        )
    )

    # History has 1 episode of show 500
    respx.get("https://api.trakt.tv/sync/history").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "id": 111,
                    "watched_at": "2026-08-20T10:00:00.000Z",
                    "action": "watch",
                    "type": "episode",
                    "show": {"title": "Sci-Fi Show", "ids": {"trakt": 500}},
                    "episode": {
                        "season": 1,
                        "number": 1,
                        "title": "Ep 1",
                        "ids": {"trakt": 5001},
                        "runtime": 45,
                        "first_aired": "2026-08-01T00:00:00.000Z",
                    },
                }
            ],
            headers={
                "x-pagination-page": "1",
                "x-pagination-page-count": "1",
                "x-pagination-item-count": "1",
                "x-pagination-limit": "100",
            },
        )
    )
    for path in [
        "/users/me/ratings/movies",
        "/users/me/ratings/shows",
        "/users/me/ratings/episodes",
        "/users/me/watchlist/movies",
        "/users/me/watchlist/shows",
        "/sync/playback/movies",
        "/sync/playback/episodes",
        "/recommendations/movies",
        "/recommendations/shows",
    ]:
        respx.get(f"https://api.trakt.tv{path}").mock(return_value=httpx.Response(200, json=[]))

    respx.get("https://api.trakt.tv/sync/last_activities").mock(
        return_value=httpx.Response(200, json={"all": "2026-08-29T10:00:00.000Z"})
    )

    # Seasons catalog has Season 0 (special), Season 1 Ep 1 (aired), Season 1 Ep 2 (future)
    future_air = (datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC) + timedelta(days=10)).isoformat()
    catalog_payload = [
        {
            "number": 0,
            "episodes": [
                {
                    "season": 0,
                    "number": 1,
                    "title": "Behind the Scenes",
                    "ids": {"trakt": 5000},
                    "runtime": None,  # Nullable runtime
                    "first_aired": "2026-07-01T00:00:00.000Z",
                }
            ],
        },
        {
            "number": 1,
            "episodes": [
                {
                    "season": 1,
                    "number": 1,
                    "title": "Ep 1",
                    "ids": {"trakt": 5001},
                    "runtime": 45,
                    "first_aired": "2026-08-01T00:00:00.000Z",
                },
                {
                    "season": 1,
                    "number": 2,
                    "title": "Ep 2 (Future)",
                    "ids": {"trakt": 5002},
                    "runtime": 50,
                    "first_aired": future_air,
                },
            ],
        },
    ]

    respx.get("https://api.trakt.tv/shows/500/seasons").mock(
        return_value=httpx.Response(200, json=catalog_payload)
    )

    report = sync.run(mode="initial")
    assert report.status == "success"

    with get_db_session(engine) as session:
        show = session.execute(select(MediaItem).where(MediaItem.trakt_id == 500)).scalar_one()

        episodes = (
            session.execute(select(Episode).where(Episode.show_id == show.id)).scalars().all()
        )

        assert len(episodes) == 3
        s0_ep = next(e for e in episodes if e.season_number == 0)
        assert s0_ep.runtime_minutes is None
        assert s0_ep.title == "Behind the Scenes"

        s1_ep2 = next(e for e in episodes if e.episode_number == 2)
        assert s1_ep2.runtime_minutes == 50

        # Verify historical watch event was preserved
        events = session.execute(select(WatchEvent)).scalars().all()
        assert len(events) == 1
        assert events[0].history_id == 111
