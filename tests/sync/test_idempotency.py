"""Tests for initial sync idempotency and deduplication."""

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
    Episode,
    MediaItem,
    Rating,
    WatchEvent,
)
from tveaker.sync.importer import AccountSync
from tveaker.trakt.client import TraktClient


@pytest.fixture
def fixtures_dir() -> Path:
    return Path(__file__).parent.parent / "fixtures" / "trakt"


@pytest.fixture
def sync_setup(fixtures_dir):
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    settings = Settings(
        trakt_client_id="test_client_id",
        trakt_client_secret="test_client_secret",
        trakt_api_base_url="https://api.trakt.tv",
    )
    token = TokenData("access_tok", "refresh_tok", 1000, 7200)
    store = MemoryTokenStore(token)
    oauth = TraktOAuth(settings=settings, token_store=store)
    client = TraktClient(settings=settings, token_store=store, oauth=oauth)
    clock = FrozenClock(datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC))

    sync = AccountSync(db_engine=engine, trakt_client=client, clock=clock)
    yield sync, engine, fixtures_dir
    Base.metadata.drop_all(bind=engine)


@respx.mock
def test_sync_idempotency(sync_setup):
    sync, engine, fixtures_dir = sync_setup

    # Mock endpoints
    settings_data = json.loads((fixtures_dir / "settings.json").read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/users/settings").mock(
        return_value=httpx.Response(200, json=settings_data)
    )

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

    respx.get("https://api.trakt.tv/users/me/ratings/movies").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "rated_at": "2026-08-28T20:00:00.000Z",
                    "rating": 9,
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
    respx.get("https://api.trakt.tv/users/me/watchlist/movies").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/users/me/watchlist/shows").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/sync/playback/movies").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/sync/playback/episodes").mock(
        return_value=httpx.Response(200, json=[])
    )

    seasons_data = json.loads((fixtures_dir / "seasons.json").read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/shows/1388/seasons").mock(
        return_value=httpx.Response(200, json=seasons_data)
    )

    respx.get("https://api.trakt.tv/recommendations/movies").mock(
        return_value=httpx.Response(200, json=[])
    )
    respx.get("https://api.trakt.tv/recommendations/shows").mock(
        return_value=httpx.Response(200, json=[])
    )
    activities_file = fixtures_dir / "last_activities.json"
    activities_data = json.loads(activities_file.read_text(encoding="utf-8"))
    respx.get("https://api.trakt.tv/sync/last_activities").mock(
        return_value=httpx.Response(200, json=activities_data)
    )

    # First sync run
    rep1 = sync.run(mode="initial")
    assert rep1.status == "success"

    with get_db_session(engine) as session:
        events_1 = len(session.execute(select(WatchEvent)).scalars().all())
        media_1 = len(session.execute(select(MediaItem)).scalars().all())
        episodes_1 = len(session.execute(select(Episode)).scalars().all())
        ratings_1 = len(session.execute(select(Rating)).scalars().all())

    # Second sync run with identical data
    rep2 = sync.run(mode="initial")
    assert rep2.status == "success"

    with get_db_session(engine) as session:
        events_2 = len(session.execute(select(WatchEvent)).scalars().all())
        media_2 = len(session.execute(select(MediaItem)).scalars().all())
        episodes_2 = len(session.execute(select(Episode)).scalars().all())
        ratings_2 = len(session.execute(select(Rating)).scalars().all())

    assert events_1 == events_2 == 2
    assert media_1 == media_2
    assert episodes_1 == episodes_2 == 4
    assert ratings_1 == ratings_2 == 1
