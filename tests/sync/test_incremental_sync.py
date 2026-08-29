"""Tests for 15-minute incremental sync logic and cursor comparison."""

from datetime import UTC, datetime

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
    Episode,
    MediaItem,
    SyncCursor,
    WatchEvent,
)
from tveaker.sync.importer import AccountSync, _ensure_utc
from tveaker.trakt.client import TraktClient


@pytest.fixture
def inc_env():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    # Pre-seed account and sync cursors
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

        show = MediaItem(media_type="show", trakt_id=1388, title="Breaking Bad")
        session.add(show)
        session.flush()

        ep = Episode(
            show_id=show.id,
            trakt_id=73482,
            season_number=1,
            episode_number=1,
            title="Pilot",
        )
        session.add(ep)
        session.flush()

        we = WatchEvent(
            history_id=9001,
            account_id=1,
            watched_at=datetime(2026, 8, 28, 20, 0, 0, tzinfo=UTC),
            action="watch",
            episode_id=ep.id,
        )
        session.add(we)

        # Cursors
        c1 = SyncCursor(
            account_id=1,
            dataset="episodes:history",
            remote_activity_at=datetime(2026, 8, 28, 20, 0, 0, tzinfo=UTC),
            last_success_at=t0,
        )
        c2 = SyncCursor(
            account_id=1,
            dataset="movies:ratings",
            remote_activity_at=datetime(2026, 8, 28, 20, 0, 0, tzinfo=UTC),
            last_success_at=t0,
        )
        session.add_all([c1, c2])

    settings = Settings(
        trakt_client_id="client_1",
        trakt_client_secret="secret_1",
        trakt_api_base_url="https://api.trakt.tv",
    )
    token = TokenData("token_1", "ref_1", 1000, 7200)
    store = MemoryTokenStore(token)
    oauth = TraktOAuth(settings=settings, token_store=store)
    client = TraktClient(settings=settings, token_store=store, oauth=oauth)
    clock = FrozenClock(datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC))

    sync = AccountSync(db_engine=engine, trakt_client=client, clock=clock)
    yield sync, engine
    Base.metadata.drop_all(bind=engine)


@respx.mock
def test_incremental_sync_no_changes(inc_env):
    sync, _ = inc_env

    # Remote activities match local cursors
    respx.get("https://api.trakt.tv/sync/last_activities").mock(
        return_value=httpx.Response(
            200,
            json={
                "episodes": {"watched_at": "2026-08-28T20:00:00.000Z"},
                "movies": {"rated_at": "2026-08-28T20:00:00.000Z"},
                "shows": {},
            },
        )
    )

    history_route = respx.get("https://api.trakt.tv/sync/history")
    ratings_route = respx.get("https://api.trakt.tv/users/me/ratings/movies")
    respx.get("https://api.trakt.tv/shows/1388/seasons").mock(
        return_value=httpx.Response(200, json=[])
    )

    report = sync.run(mode="incremental")

    assert report.status == "success"
    assert not history_route.called
    assert not ratings_route.called


@respx.mock
def test_incremental_sync_with_new_watch_event(inc_env):
    sync, engine = inc_env

    # Remote activity has newer episode watch time
    respx.get("https://api.trakt.tv/sync/last_activities").mock(
        return_value=httpx.Response(
            200,
            json={
                "episodes": {"watched_at": "2026-08-29T10:00:00.000Z"},
                "movies": {"rated_at": "2026-08-28T20:00:00.000Z"},
                "shows": {},
            },
        )
    )

    # Mock history return with 1 new event
    new_event = {
        "id": 9002,
        "watched_at": "2026-08-29T10:00:00.000Z",
        "action": "watch",
        "type": "episode",
        "show": {"title": "Breaking Bad", "ids": {"trakt": 1388}},
        "episode": {
            "season": 1,
            "number": 2,
            "title": "Cat's in the Bag",
            "ids": {"trakt": 73483},
            "runtime": 48,
            "first_aired": "2008-01-27T00:00:00.000Z",
        },
    }

    respx.get("https://api.trakt.tv/sync/history").mock(
        return_value=httpx.Response(
            200,
            json=[new_event],
            headers={
                "x-pagination-page": "1",
                "x-pagination-page-count": "1",
                "x-pagination-item-count": "1",
                "x-pagination-limit": "100",
            },
        )
    )
    respx.get("https://api.trakt.tv/shows/1388/seasons").mock(
        return_value=httpx.Response(200, json=[])
    )

    report = sync.run(mode="incremental")

    assert report.status == "success"
    assert report.fetched.get("history") == 1
    assert report.inserted.get("history") == 1

    with get_db_session(engine) as session:
        events = session.execute(select(WatchEvent)).scalars().all()
        assert len(events) == 2  # 9001 and 9002

        cursor = session.get(SyncCursor, (1, "episodes:history"))
        assert cursor is not None
        assert _ensure_utc(cursor.remote_activity_at) == datetime(2026, 8, 29, 10, 0, 0, tzinfo=UTC)
