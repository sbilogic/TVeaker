"""Tests for the Trakt GDPR / Account export ZIP importer."""

import io
import json
import zipfile
from datetime import UTC, datetime

from sqlalchemy import create_engine, select
from sqlalchemy.pool import StaticPool

from tveaker.clock import FrozenClock
from tveaker.db import Base, get_db_session
from tveaker.models import Account, Episode, TrackedShow, WatchlistItem
from tveaker.sync.export_importer import TraktExportImporter


def _build_test_export_zip() -> io.BytesIO:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        profile = {
            "username": "testuser",
            "ids": {"trakt": 12345},
            "joined_at": "2020-01-01T00:00:00.000Z",
        }
        zf.writestr("user-profile.json", json.dumps(profile))

        watched_shows = [
            {
                "plays": 5,
                "last_watched_at": "2026-08-01T12:00:00.000Z",
                "show": {
                    "ids": {"trakt": 999, "slug": "test-show"},
                    "title": "Test Show",
                    "year": 2024,
                    "aired_episodes": 10,
                },
            }
        ]
        zf.writestr("watched-shows-1.json", json.dumps(watched_shows))

        history = [
            {
                "id": 1001,
                "watched_at": "2026-08-01T12:00:00.000Z",
                "action": "watch",
                "type": "episode",
                "episode": {"ids": {"trakt": 5001}, "title": "Pilot", "season": 1, "number": 1},
                "show": {
                    "ids": {"trakt": 999, "slug": "test-show"},
                    "title": "Test Show",
                    "year": 2024,
                    "aired_episodes": 10,
                },
            },
            {
                "id": 1002,
                "watched_at": "2026-08-02T14:00:00.000Z",
                "action": "watch",
                "type": "movie",
                "movie": {
                    "ids": {"trakt": 888, "slug": "test-movie"},
                    "title": "Test Movie",
                    "year": 2023,
                },
            },
        ]
        zf.writestr("watched-history-1.json", json.dumps(history))

        watchlist = [
            {
                "type": "movie",
                "movie": {
                    "ids": {"trakt": 777, "slug": "watchlist-movie"},
                    "title": "Watchlist Movie",
                    "year": 2025,
                },
                "listed_at": "2026-08-10T10:00:00.000Z",
            }
        ]
        zf.writestr("lists-watchlist.json", json.dumps(watchlist))

    buf.seek(0)
    return buf


def test_export_importer_basic() -> None:
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    clock = FrozenClock(datetime(2026, 8, 29, 10, 0, 0, tzinfo=UTC))

    importer = TraktExportImporter(db_engine=engine, clock=clock)
    zip_data = _build_test_export_zip()

    report = importer.import_zip(zip_data)

    assert report.account_username == "testuser"
    assert report.movies_count == 2
    assert report.shows_count == 1
    assert report.episodes_count == 1  # 1 real episode from history
    assert report.watch_events_count == 2
    assert report.watchlist_count == 1

    with get_db_session(engine) as session:
        acc = session.get(Account, 1)
        assert acc is not None
        assert acc.username == "testuser"
        assert acc.last_successful_sync_at is not None

        tracked = session.execute(select(TrackedShow)).scalars().all()
        assert len(tracked) == 1
        assert tracked[0].status == "watching"

        wl = session.execute(select(WatchlistItem)).scalars().all()
        assert len(wl) == 1

        eps = session.execute(select(Episode)).scalars().all()
        assert len(eps) >= 1
