"""Tests for Trakt OAuth authorization flow, token exchange, and refresh."""

import logging

import httpx
import pytest
import respx

from tveaker.auth.token_store import MemoryTokenStore, TokenData
from tveaker.auth.trakt_oauth import TraktOAuth, TraktOAuthError
from tveaker.config import Settings


@pytest.fixture
def mock_settings():
    return Settings(
        trakt_client_id="test_client_id_123",
        trakt_client_secret="test_client_secret_456",
        trakt_redirect_uri="http://127.0.0.1:8000/auth/trakt/callback",
        trakt_api_base_url="https://api.trakt.tv",
    )


@pytest.fixture
def unconfigured_settings():
    return Settings(
        trakt_client_id="",
        trakt_client_secret="",
    )


def test_authorization_url_unconfigured(unconfigured_settings):
    oauth = TraktOAuth(settings=unconfigured_settings)
    with pytest.raises(TraktOAuthError, match="must be configured"):
        oauth.get_authorization_url()


def test_authorization_url_generation(mock_settings):
    oauth = TraktOAuth(settings=mock_settings)
    url, state = oauth.get_authorization_url()
    assert "https://trakt.tv/oauth/authorize" in url
    assert "client_id=test_client_id_123" in url
    assert f"state={state}" in url
    assert "response_type=code" in url


def test_exchange_code_state_mismatch(mock_settings):
    store = MemoryTokenStore()
    oauth = TraktOAuth(settings=mock_settings, token_store=store)
    with pytest.raises(TraktOAuthError, match="OAuth state verification failed"):
        oauth.exchange_code_for_token("code123", "stateA", "stateB")
    assert store.get_token() is None


@respx.mock
def test_exchange_code_success(mock_settings, caplog):
    store = MemoryTokenStore()
    client = httpx.Client()
    oauth = TraktOAuth(settings=mock_settings, token_store=store, http_client=client)

    token_route = respx.post("https://api.trakt.tv/oauth/token").mock(
        return_value=httpx.Response(
            200,
            json={
                "access_token": "mock_access_111",
                "refresh_token": "mock_refresh_222",
                "created_at": 1700000000,
                "expires_in": 7200,
                "token_type": "bearer",
                "scope": "public",
            },
        )
    )

    with caplog.at_level(logging.INFO):
        token = oauth.exchange_code_for_token("valid_code", "state_match", "state_match")

    assert token_route.called
    assert token.access_token == "mock_access_111"
    assert token.refresh_token == "mock_refresh_222"
    assert store.get_token() == token

    # Verify no raw secrets leaked in logs
    log_text = caplog.text
    assert "mock_access_111" not in log_text
    assert "mock_refresh_222" not in log_text


@respx.mock
def test_refresh_token_lifecycle(mock_settings):
    store = MemoryTokenStore()
    client = httpx.Client()
    oauth = TraktOAuth(settings=mock_settings, token_store=store, http_client=client)

    # Initial expired token
    old_token = TokenData(
        access_token="old_access",
        refresh_token="old_refresh",
        created_at=1000,
        expires_in=3600,
    )
    store.save_token(old_token)

    refresh_route = respx.post("https://api.trakt.tv/oauth/token").mock(
        return_value=httpx.Response(
            200,
            json={
                "access_token": "new_access_333",
                "refresh_token": "new_refresh_444",
                "created_at": 1800000000,
                "expires_in": 7200,
                "token_type": "bearer",
                "scope": "public",
            },
        )
    )

    refreshed = oauth.refresh_token()
    assert refresh_route.called
    assert refreshed.access_token == "new_access_333"
    assert refreshed.refresh_token == "new_refresh_444"
    assert store.get_token() == refreshed

    # Refreshing again without force shouldn't call network since it's fresh
    refresh_route.reset()
    same_token = oauth.refresh_token(force=False)
    assert not refresh_route.called
    assert same_token == refreshed


def test_disconnect(mock_settings):
    store = MemoryTokenStore()
    token = TokenData("acc", "ref", 100, 1000)
    store.save_token(token)
    oauth = TraktOAuth(settings=mock_settings, token_store=store)

    oauth.disconnect()
    assert store.get_token() is None
