"""Tests for initial Trakt sync process and database ingestion."""

import json
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
    SyncCursor,
    SyncRun,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)
from tveaker.sync.importer import AccountSync
from tveaker.trakt.client import TraktClient


@pytest.fixture
def fixtures_dir() -> Path:
    return Path(__file__).parent.parent / "fixtures" / "trakt"


@pytest.fixture
def sync_env(fixtures_dir):
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    settings = Settings(
        trakt_client_id="test_client_1",
        trakt_client_secret="test_secret_1",
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
def test_initial_sync_flow(sync_env):
    sync, engine, fixtures_dir = sync_env

    # 1. Mock settings
    settings_data = json.loads((fixtures_dir / "settings.json").read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/users/settings").mock(
        return_value=httpx.Response(200, json=settings_data)
    )

    # 2. Mock history
    history_p1 = json.loads((fixtures_dir / "history_page1.json").read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/sync/history").mock(
        return_value=httpx.Response(
            200,
            json=history_p1,
            headers={
                "x-pagination-page": "1",
                "x-pagination-page-count": "1",
                "x-pagination-item-count": "2",
                "x-pagination-limit": "100",
            },
        )
    )

    # 3. Mock ratings
    respx.get("https://api.trakt.tv/users/me/ratings/movies").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "rated_at": "2026-08-28T20:00:00.000Z",
                    "rating": 10,
                    "type": "movie",
                    "movie": {"title": "Inception", "ids": {"trakt": 16}},
                }
            ],
        )
    )
    respx.get("https://api.trakt.tv/users/me/ratings/shows").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/users/me/ratings/episodes").mock(
        return_value=httpx.Response(200, json=[])
    )

    # 4. Mock watchlist
    respx.get("https://api.trakt.tv/users/me/watchlist/movies").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/users/me/watchlist/shows").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "listed_at": "2026-08-27T10:00:00.000Z",
                    "type": "show",
                    "show": {
                        "title": "Severance",
                        "ids": {"trakt": 9999},
                        "genres": ["sci-fi", "thriller"],
                    },
                }
            ],
        )
    )

    # 5. Mock playback
    respx.get("https://api.trakt.tv/sync/playback/movies").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/sync/playback/episodes").mock(
        return_value=httpx.Response(200, json=[])
    )

    # 6. Mock seasons for discovered shows
    seasons_data = json.loads((fixtures_dir / "seasons.json").read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/shows/1388/seasons").mock(
        return_value=httpx.Response(200, json=seasons_data)
    )
    respx.get("https://api.trakt.tv/shows/9999/seasons").mock(
        return_value=httpx.Response(200, json=[])
    )

    # 7. Mock recommendations
    respx.get("https://api.trakt.tv/recommendations/movies").mock(
        return_value=httpx.Response(
            200,
            json=[{"title": "Arrival", "ids": {"trakt": 300}, "year": 2016}],
        )
    )
    respx.get("https://api.trakt.tv/recommendations/shows").mock(
        return_value=httpx.Response(200, json=[])
    )

    # 8. Mock last activities
    activities_file = fixtures_dir / "last_activities.json"
    activities_data = json.loads(activities_file.read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/sync/last_activities").mock(
        return_value=httpx.Response(200, json=activities_data)
    )

    # Run initial sync
    report = sync.run(mode="initial")

    assert report.status == "success"
    assert report.fetched["history"] == 2
    assert report.inserted["history"] == 2
    assert report.inserted["ratings"] == 1
    assert report.inserted["watchlist"] == 1

    # Verify DB state
    with get_db_session(engine) as session:
        account = session.get(Account, 1)
        assert account is not None
        assert account.username == "sahil"

        # Check media items
        media_count = len(session.execute(select(MediaItem)).scalars().all())
        assert media_count >= 3  # Inception, Breaking Bad, Severance, Arrival

        # Check watch events
        events = session.execute(select(WatchEvent)).scalars().all()
        assert len(events) == 2

        # Check ratings
        ratings = session.execute(select(Rating)).scalars().all()
        assert len(ratings) == 1
        assert ratings[0].rating == 10

        # Check watchlist
        wl = session.execute(select(WatchlistItem)).scalars().all()
        assert len(wl) == 1

        # Check tracked shows auto-seeded
        tracked = session.execute(select(TrackedShow)).scalars().all()
        assert len(tracked) == 2
        statuses = {t.show.title: t.status for t in tracked}
        assert statuses.get("Breaking Bad") == "watching"
        assert statuses.get("Severance") == "planned"

        # Check sync cursors
        cursors = session.execute(select(SyncCursor)).scalars().all()
        assert len(cursors) >= 1

        # Check sync run
        run_record = session.get(SyncRun, report.run_id)
        assert run_record is not None
        assert run_record.status == "success"
