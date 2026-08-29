"""Comprehensive tests for TraktClient HTTP behavior and endpoint parsing."""

import json
from pathlib import Path

import httpx
import pytest
import respx

from tveaker.auth.token_store import MemoryTokenStore, TokenData
from tveaker.auth.trakt_oauth import TraktOAuth
from tveaker.config import Settings
from tveaker.trakt.client import TraktClient
from tveaker.trakt.errors import (
    TraktAuthError,
    TraktContractError,
    TraktNotFoundError,
    TraktServerError,
)


@pytest.fixture
def fixtures_dir() -> Path:
    return Path(__file__).parent.parent / "fixtures" / "trakt"


@pytest.fixture
def client_setup():
    settings = Settings(
        trakt_client_id="client_abc",
        trakt_client_secret="client_secret_xyz",
        trakt_api_base_url="https://api.trakt.tv",
    )
    token = TokenData(
        access_token="valid_access_token",
        refresh_token="valid_refresh_token",
        created_at=1000,
        expires_in=7200,
    )
    store = MemoryTokenStore(token)
    oauth = TraktOAuth(settings=settings, token_store=store)
    sleeps: list[float] = []

    client = TraktClient(
        settings=settings,
        token_store=store,
        oauth=oauth,
        sleep_func=sleeps.append,
    )
    return client, store, sleeps


def test_auth_missing_token():
    settings = Settings(trakt_client_id="client_abc")
    store = MemoryTokenStore()
    client = TraktClient(settings=settings, token_store=store)

    with pytest.raises(TraktAuthError, match="No OAuth token available"):
        client.get_user_settings()


@respx.mock
def test_401_token_refresh_and_retry(client_setup):
    client, store, _ = client_setup

    route = respx.get("https://api.trakt.tv/users/settings").mock(
        side_effect=[
            httpx.Response(401, json={"error": "unauthorized"}),
            httpx.Response(
                200,
                json={
                    "user": {"username": "refreshed_user", "ids": {"slug": "u", "uuid": "uuid1"}},
                    "account": {"timezone": "UTC"},
                },
            ),
        ]
    )

    respx.post("https://api.trakt.tv/oauth/token").mock(
        return_value=httpx.Response(
            200,
            json={
                "access_token": "brand_new_token",
                "refresh_token": "brand_new_refresh",
                "created_at": 2000,
                "expires_in": 7200,
                "token_type": "bearer",
                "scope": "public",
            },
        )
    )

    settings_res = client.get_user_settings()
    assert route.call_count == 2
    assert settings_res.user.username == "refreshed_user"
    assert store.get_token() is not None
    assert store.get_token().access_token == "brand_new_token"


@respx.mock
def test_429_rate_limit_retry(client_setup):
    client, _, sleeps = client_setup

    route = respx.get("https://api.trakt.tv/sync/last_activities").mock(
        side_effect=[
            httpx.Response(429, headers={"retry-after": "2.5"}),
            httpx.Response(200, json={"all": "2026-08-29T10:00:00.000Z"}),
        ]
    )

    activities = client.get_last_activities()
    assert route.call_count == 2
    assert sleeps == [2.5]
    assert activities.all is not None


@respx.mock
def test_404_raises_not_found(client_setup):
    client, _, _ = client_setup
    respx.get("https://api.trakt.tv/shows/9999999/seasons").mock(
        return_value=httpx.Response(404, json={"error": "not found"})
    )

    with pytest.raises(TraktNotFoundError):
        client.get_show_seasons(9999999)


@respx.mock
def test_503_transient_retry_and_fail(client_setup):
    client, _, sleeps = client_setup

    respx.get("https://api.trakt.tv/users/settings").mock(
        return_value=httpx.Response(503, json={"error": "service unavailable"})
    )

    with pytest.raises(TraktServerError) as exc_info:
        client.get_user_settings()
    assert exc_info.value.status_code == 503
    assert len(sleeps) == 3


@respx.mock
def test_drain_history_multi_page(client_setup, fixtures_dir):
    client, _, _ = client_setup

    p1_data = json.loads((fixtures_dir / "history_page1.json").read_text(encoding="utf-8"))
    p2_data = json.loads((fixtures_dir / "history_page2.json").read_text(encoding="utf-8"))

    respx.get("https://api.trakt.tv/sync/history").mock(
        side_effect=[
            httpx.Response(
                200,
                json=p1_data,
                headers={
                    "x-pagination-page": "1",
                    "x-pagination-page-count": "2",
                    "x-pagination-item-count": "3",
                    "x-pagination-limit": "2",
                },
            ),
            httpx.Response(
                200,
                json=p2_data,
                headers={
                    "x-pagination-page": "2",
                    "x-pagination-page-count": "2",
                    "x-pagination-item-count": "3",
                    "x-pagination-limit": "2",
                },
            ),
        ]
    )

    items = list(client.drain_history())
    assert len(items) == 3
    assert items[0].id == 9001
    assert items[1].id == 9002
    assert items[2].id == 9003


@respx.mock
def test_get_show_seasons_catalog(client_setup, fixtures_dir):
    client, _, _ = client_setup
    seasons_data = json.loads((fixtures_dir / "seasons.json").read_text(encoding="utf-8"))

    respx.get("https://api.trakt.tv/shows/1388/seasons").mock(
        return_value=httpx.Response(200, json=seasons_data)
    )

    seasons = client.get_show_seasons(1388)
    assert len(seasons) == 2
    assert seasons[0].number == 0  # specials
    assert seasons[1].number == 1
    assert len(seasons[1].episodes) == 3
    assert seasons[1].episodes[0].title == "Pilot"


@respx.mock
def test_playback_and_ratings_and_watchlist(client_setup):
    client, _, _ = client_setup

    respx.get("https://api.trakt.tv/sync/playback/movies").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "id": 101,
                    "progress": 45.5,
                    "paused_at": "2026-08-29T08:00:00.000Z",
                    "type": "movie",
                    "movie": {"title": "Dune", "ids": {"trakt": 99}},
                }
            ],
        )
    )
    playback = client.get_playback("movies")
    assert len(playback) == 1
    assert playback[0].progress == 45.5

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
    ratings = client.get_ratings("movies")
    assert len(ratings) == 1
    assert ratings[0].rating == 9

    respx.get("https://api.trakt.tv/users/me/watchlist/shows").mock(
        return_value=httpx.Response(
            200,
            json=[
                {
                    "listed_at": "2026-08-27T10:00:00.000Z",
                    "type": "show",
                    "show": {"title": "Succession", "ids": {"trakt": 200}},
                }
            ],
        )
    )
    watchlist = client.get_watchlist("shows")
    assert len(watchlist) == 1
    assert watchlist[0].show is not None
    assert watchlist[0].show.title == "Succession"


@respx.mock
def test_progress_watched_and_recommendations(client_setup):
    client, _, _ = client_setup

    respx.get("https://api.trakt.tv/shows/1388/progress/watched").mock(
        return_value=httpx.Response(
            200,
            json={
                "aired": 62,
                "completed": 60,
                "last_watched_at": "2026-08-29T08:00:00.000Z",
                "seasons": [{"number": 1, "aired": 7, "completed": 7}],
            },
        )
    )
    progress = client.get_show_progress_watched(1388)
    assert progress.aired == 62
    assert progress.completed == 60

    respx.get("https://api.trakt.tv/recommendations/movies").mock(
        return_value=httpx.Response(
            200,
            json=[{"title": "Arrival", "ids": {"trakt": 300}, "year": 2016}],
        )
    )
    recs = client.get_recommendations("movies")
    assert len(recs) == 1
    assert recs[0].title == "Arrival"


@respx.mock
def test_contract_error_on_malformed_json(client_setup):
    client, _, _ = client_setup

    respx.get("https://api.trakt.tv/users/settings").mock(
        return_value=httpx.Response(200, json={"unexpected_format": 123})
    )

    with pytest.raises(TraktContractError):
        client.get_user_settings()
