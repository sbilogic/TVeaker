"""Tests for TVeaker REST API endpoints."""

import json
from datetime import UTC, datetime
from unittest.mock import patch

import pytest
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool
from starlette.testclient import TestClient

from tveaker.auth.token_store import MemoryTokenStore, TokenData
from tveaker.clock import FrozenClock
from tveaker.config import Settings
from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)
from tveaker.sync.importer import SyncReport
from tveaker.web.app import create_app


@pytest.fixture
def app_client():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    now = datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC)

    # Seed basic account, show, movie
    with get_db_session(engine) as session:
        account = Account(
            id=1,
            trakt_uuid="u1",
            username="sahil",
            timezone="UTC",
            connected_at=now,
            last_successful_sync_at=now,
        )
        session.add(account)
        session.flush()

        show1 = MediaItem(
            id=1,
            media_type="show",
            trakt_id=101,
            title="Breaking Bad",
            genres_json=json.dumps(["drama", "crime"]),
        )
        movie1 = MediaItem(
            id=2,
            media_type="movie",
            trakt_id=202,
            title="Dune 2",
            genres_json=json.dumps(["sci-fi"]),
            runtime_minutes=166,
        )
        session.add_all([show1, movie1])
        session.flush()

        # Ep
        ep1 = Episode(
            id=11,
            show_id=1,
            trakt_id=1001,
            season_number=1,
            episode_number=1,
            title="Pilot",
            runtime_minutes=58,
        )
        session.add(ep1)
        session.flush()

        # Watch history
        we1 = WatchEvent(
            history_id=9001,
            account_id=1,
            episode_id=11,
            watched_at=now,
            action="watch",
        )
        session.add(we1)

        # Tracked show
        ts1 = TrackedShow(
            account_id=1,
            show_id=1,
            status="watching",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )
        # Watchlist
        wl1 = WatchlistItem(
            account_id=1,
            media_type="movie",
            media_item_id=2,
            listed_at=now,
        )
        session.add_all([ts1, wl1])

    settings = Settings(
        trakt_client_id="cid",
        trakt_client_secret="csec",
        trakt_api_base_url="https://api.trakt.tv",
    )
    token = TokenData("tok1", "ref1", int(now.timestamp()), 7200)
    store = MemoryTokenStore(token)
    clock = FrozenClock(now)

    app = create_app(settings=settings, db_engine=engine, token_store=store, clock=clock)
    client = TestClient(app)
    yield client, engine, now
    Base.metadata.drop_all(bind=engine)


def test_api_health(app_client):
    client, _, _ = app_client
    res = client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "healthy"
    assert data["database_connected"] is True
    assert data["trakt_authenticated"] is True
    assert data["username"] == "sahil"


def test_api_sync_status(app_client):
    client, _, _ = app_client
    res = client.get("/api/v1/sync/status")
    assert res.status_code == 200
    data = res.json()
    assert "cursors" in data
    assert "recent_runs" in data


def test_api_sync_trigger(app_client):
    client, _, _ = app_client
    with patch("tveaker.sync.importer.AccountSync.run") as mock_run:
        mock_run.return_value = SyncReport(1, "incremental", "success", {}, {}, {}, {}, [])
        res = client.post("/api/v1/sync/trigger", json={"mode": "incremental"})
        assert res.status_code == 200
        assert res.json()["status"] == "success"


def test_api_shows_list_and_patch(app_client):
    client, _, _ = app_client

    # List shows
    res = client.get("/api/v1/shows")
    assert res.status_code == 200
    shows = res.json()
    assert len(shows) == 1
    assert shows[0]["title"] == "Breaking Bad"

    # Patch show
    res_patch = client.patch(
        "/api/v1/shows/1",
        json={"status": "paused", "manual_episodes_per_week": 4.5, "include_specials": True},
    )
    assert res_patch.status_code == 200
    patched = res_patch.json()
    assert patched["status"] == "paused"
    assert patched["manual_episodes_per_week"] == 4.5
    assert patched["include_specials"] is True

    # 404 for nonexistent show
    res_404 = client.patch("/api/v1/shows/999", json={"status": "watching"})
    assert res_404.status_code == 404


def test_api_recommendations_and_feedback(app_client):
    client, _, _ = app_client

    # Get recommendations
    res = client.get("/api/v1/recommendations?intent=auto&limit=5")
    assert res.status_code == 200
    data = res.json()
    assert "run_id" in data
    assert "items" in data
    assert len(data["items"]) >= 1

    run_id = data["run_id"]
    candidate_id = data["items"][0]["candidate_id"]

    # Submit feedback
    fb_res = client.post(
        "/api/v1/recommendations/feedback",
        json={"run_id": run_id, "candidate_id": candidate_id, "action": "not_now"},
    )
    assert fb_res.status_code == 200
    fb_data = fb_res.json()
    assert fb_data["action"] == "not_now"
    assert fb_data["candidate_id"] == candidate_id

    # 404 for nonexistent run feedback
    fb_404 = client.post(
        "/api/v1/recommendations/feedback",
        json={"run_id": 9999, "candidate_id": "movie:1", "action": "accepted"},
    )
    assert fb_404.status_code == 404


def test_api_history(app_client):
    client, _, _ = app_client
    res = client.get("/api/v1/history?limit=10")
    assert res.status_code == 200
    history = res.json()
    assert len(history) == 1
    assert history[0]["media_type"] == "episode"
    assert history[0]["title"] == "Breaking Bad"
