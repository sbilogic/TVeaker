"""Tests for HTML UI views rendered by FastAPI and Jinja2."""

import json
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool
from starlette.testclient import TestClient

from tveaker.auth.token_store import MemoryTokenStore, TokenData
from tveaker.clock import FrozenClock
from tveaker.config import Settings
from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.models import Account, Episode, MediaItem, TrackedShow
from tveaker.web.app import create_app


@pytest.fixture
def view_client():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
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
            last_successful_sync_at=now,
        )
        session.add(account)
        session.flush()

        show1 = MediaItem(
            id=1,
            media_type="show",
            trakt_id=101,
            title="Better Call Saul",
            genres_json=json.dumps(["drama", "crime"]),
        )
        session.add(show1)
        session.add(
            Episode(
                id=11,
                show_id=1,
                trakt_id=1001,
                season_number=1,
                episode_number=1,
                title="Pilot",
                first_aired=now - timedelta(days=1),
            )
        )
        session.flush()

        ts1 = TrackedShow(
            account_id=1,
            show_id=1,
            status="watching",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )
        session.add(ts1)

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
    yield client
    Base.metadata.drop_all(bind=engine)


def test_view_dashboard(view_client):
    res = view_client.get("/")
    assert res.status_code == 200
    assert "TONIGHT" in res.text
    assert "Better Call Saul" in res.text


def test_view_shows(view_client):
    res = view_client.get("/shows")
    assert res.status_code == 200
    assert "Tracked Shows" in res.text
    assert "Better Call Saul" in res.text
    assert "Specials: off" in res.text


def test_view_recommendations(view_client):
    res = view_client.get("/recommendations")
    assert res.status_code == 200
    assert "What to Watch Next" in res.text


def test_view_history(view_client):
    res = view_client.get("/history")
    assert res.status_code == 200
    assert "Watch History" in res.text


def test_view_settings(view_client):
    res = view_client.get("/settings")
    assert res.status_code == 200
    assert "CONTROL ROOM" in res.text
    assert "sahil" in res.text


def test_auth_login_redirect(view_client):
    res = view_client.get("/auth/login", follow_redirects=False)
    assert res.status_code == 307
    assert "trakt.tv/oauth/authorize" in res.headers["location"]
