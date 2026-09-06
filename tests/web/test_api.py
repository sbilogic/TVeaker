"""Tests for TVeaker REST API endpoints."""

import json
from datetime import UTC, datetime, timedelta
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
from tveaker.web import routes as web_routes
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
            first_aired=now - timedelta(days=30),
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
    assert shows[0]["include_specials"] is False

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


def test_dashboard_does_not_offer_a_caught_up_show_as_tonight(app_client):
    client, _, _ = app_client

    response = client.get("/")

    assert response.status_code == 200
    assert "01 / CONTINUE" not in response.text
    assert "0 EPISODES LEFT" not in response.text


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


def test_api_unwatched_only_returns_released_episodes_with_runtime(app_client):
    client, engine, now = app_client

    with get_db_session(engine) as session:
        show = session.get(MediaItem, 1)
        assert show is not None
        show.runtime_minutes = 44
        session.add_all(
            [
                Episode(
                    id=12,
                    show_id=1,
                    trakt_id=1002,
                    season_number=1,
                    episode_number=2,
                    title="Released",
                    runtime_minutes=47,
                    first_aired=now - timedelta(days=1),
                ),
                Episode(
                    id=13,
                    show_id=1,
                    trakt_id=1003,
                    season_number=1,
                    episode_number=3,
                    title="Future",
                    runtime_minutes=52,
                    first_aired=now + timedelta(days=7),
                ),
                Episode(
                    id=14,
                    show_id=1,
                    trakt_id=1004,
                    season_number=1,
                    episode_number=4,
                    title="Schedule unknown",
                    runtime_minutes=None,
                    first_aired=None,
                ),
            ]
        )

    response = client.get("/api/v1/shows/1/unwatched")

    assert response.status_code == 200
    data = response.json()
    assert data["total_episodes"] == 4
    assert data["aired_episodes"] == 2
    assert data["unaired_episodes"] == 2
    assert data["watched_episodes"] == 1
    assert data["remaining_episodes"] == 1
    assert data["unwatched_minutes"] == 47
    assert [episode["title"] for episode in data["unwatched_episodes"]] == ["Released"]

    scrobble = client.post("/api/v1/shows/1/quick-scrobble")
    assert scrobble.status_code == 200
    assert scrobble.json()["scrobbled_episode"]["title"] == "Released"

    no_released_episode = client.post("/api/v1/shows/1/quick-scrobble")
    assert no_released_episode.status_code == 400

    future_episode = client.post("/api/v1/shows/1/episodes/13/watch")
    assert future_episode.status_code == 400


def test_api_unwatched_respects_the_explicit_specials_setting(app_client):
    client, engine, now = app_client

    with get_db_session(engine) as session:
        session.add(
            Episode(
                id=12,
                show_id=1,
                trakt_id=1002,
                season_number=0,
                episode_number=1,
                title="Making of the Pilot",
                runtime_minutes=20,
                first_aired=now - timedelta(days=1),
            )
        )

    default_response = client.get("/api/v1/shows/1/unwatched")
    assert default_response.status_code == 200
    assert default_response.json()["total_episodes"] == 1
    assert default_response.json()["unwatched_episodes"] == []

    update = client.patch("/api/v1/shows/1", json={"include_specials": True})
    assert update.status_code == 200
    assert update.json()["include_specials"] is True

    with_specials = client.get("/api/v1/shows/1/unwatched")
    assert with_specials.status_code == 200
    assert with_specials.json()["total_episodes"] == 2
    assert with_specials.json()["aired_episodes"] == 2
    assert with_specials.json()["remaining_episodes"] == 1
    assert [episode["title"] for episode in with_specials.json()["unwatched_episodes"]] == [
        "Making of the Pilot"
    ]

    quick_scrobble = client.post("/api/v1/shows/1/quick-scrobble")
    assert quick_scrobble.status_code == 200
    assert quick_scrobble.json()["scrobbled_episode"]["title"] == "Making of the Pilot"


def test_api_excludes_legacy_synthetic_episodes_from_actionable_catalog(app_client):
    client, engine, now = app_client

    with get_db_session(engine) as session:
        session.add(
            Episode(
                id=12,
                show_id=1,
                trakt_id=-1002,
                season_number=1,
                episode_number=2,
                title="Provider-only row",
                runtime_minutes=47,
                first_aired=now - timedelta(days=1),
            )
        )

    unwatched = client.get("/api/v1/shows/1/unwatched")
    assert unwatched.status_code == 200
    assert unwatched.json()["total_episodes"] == 1
    assert unwatched.json()["remaining_episodes"] == 0
    assert unwatched.json()["unwatched_episodes"] == []

    assert client.post("/api/v1/shows/1/quick-scrobble").status_code == 400
    assert client.put("/api/v1/now-watching", json={"episode_id": 12}).status_code == 409
    assert client.post("/api/v1/shows/1/episodes/12/watch").status_code == 409


def test_api_uses_trakt_aired_snapshot_for_provider_episode_candidates(app_client):
    client, engine, now = app_client

    with get_db_session(engine) as session:
        show = session.get(MediaItem, 1)
        assert show is not None
        show.trakt_aired_episodes = 2
        session.add_all(
            [
                Episode(
                    id=12,
                    show_id=1,
                    trakt_id=-1002,
                    season_number=1,
                    episode_number=2,
                    title="Snapshot-backed episode",
                    runtime_minutes=47,
                    first_aired=now - timedelta(days=1),
                ),
                Episode(
                    id=13,
                    show_id=1,
                    trakt_id=-1003,
                    season_number=2,
                    episode_number=1,
                    title="Provider-only overreach",
                    runtime_minutes=47,
                    first_aired=now - timedelta(days=1),
                ),
            ]
        )

    unwatched = client.get("/api/v1/shows/1/unwatched")
    assert unwatched.status_code == 200
    assert unwatched.json()["total_episodes"] == 2
    assert unwatched.json()["remaining_episodes"] == 1
    assert [episode["id"] for episode in unwatched.json()["unwatched_episodes"]] == [12]

    assert client.put("/api/v1/now-watching", json={"episode_id": 12}).status_code == 200


def test_api_selects_one_exact_episode_as_now_watching(app_client):
    """A user can explicitly choose the next episode before marking it watched."""
    client, engine, now = app_client

    with get_db_session(engine) as session:
        session.add(
            Episode(
                id=12,
                show_id=1,
                trakt_id=1002,
                season_number=1,
                episode_number=2,
                title="Cat's in the Bag...",
                runtime_minutes=48,
                first_aired=now - timedelta(days=1),
            )
        )

    initial = client.get("/api/v1/now-watching")
    assert initial.status_code == 200
    assert initial.json() is None

    select_episode = client.put("/api/v1/now-watching", json={"episode_id": 12})
    assert select_episode.status_code == 200
    assert select_episode.json() == {
        "show_id": 1,
        "show_title": "Breaking Bad",
        "episode_id": 12,
        "season_number": 1,
        "episode_number": 2,
        "episode_title": "Cat's in the Bag...",
        "runtime_minutes": 48,
    }

    current = client.get("/api/v1/now-watching")
    assert current.status_code == 200
    assert current.json()["episode_id"] == 12

    watched = client.post("/api/v1/shows/1/episodes/12/watch")
    assert watched.status_code == 200

    cleared = client.get("/api/v1/now-watching")
    assert cleared.status_code == 200
    assert cleared.json() is None


def test_dashboard_prioritizes_the_explicit_now_watching_episode(app_client):
    client, engine, now = app_client

    with get_db_session(engine) as session:
        session.add(
            Episode(
                id=12,
                show_id=1,
                trakt_id=1002,
                season_number=1,
                episode_number=2,
                title="Cat's in the Bag...",
                runtime_minutes=48,
                first_aired=now - timedelta(days=1),
            )
        )

    assert client.put("/api/v1/now-watching", json={"episode_id": 12}).status_code == 200

    dashboard = client.get("/")

    assert dashboard.status_code == 200
    assert "NOW WATCHING" in dashboard.text
    assert "Cat&#39;s in the Bag..." in dashboard.text


def test_ota_version_comes_from_the_apk_artifact(app_client, monkeypatch, tmp_path):
    client, _, _ = app_client
    apk_path = tmp_path / "app-debug.apk"
    metadata_path = tmp_path / "output-metadata.json"
    apk_path.write_bytes(b"test-apk")
    metadata_path.write_text(
        json.dumps(
            {
                "elements": [
                    {
                        "versionCode": 88,
                        "versionName": "8.8.0",
                        "outputFile": "app-debug.apk",
                    }
                ]
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(web_routes, "ANDROID_APK_PATH", apk_path)
    monkeypatch.setattr(web_routes, "ANDROID_APK_METADATA_PATH", metadata_path)

    response = client.get("/api/v1/app/version")

    assert response.status_code == 200
    assert response.json()["version_code"] == 88
    assert response.json()["version_name"] == "8.8.0"
    assert response.json()["apk_size_bytes"] == 8


def test_artwork_proxy_rejects_untrusted_hosts(app_client):
    client, _, _ = app_client

    response = client.get(
        "/api/v1/artwork", params={"url": "https://example.com/not-artwork.jpg"}
    )

    assert response.status_code == 400
