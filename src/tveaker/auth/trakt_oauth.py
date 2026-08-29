"""Trakt OAuth 2.0 implementation with state verification and atomic refresh."""

import logging
import secrets
import threading
from urllib.parse import urlencode

import httpx

from tveaker.auth.token_store import KeyringTokenStore, TokenData, TokenStore
from tveaker.config import Settings, get_settings

logger = logging.getLogger(__name__)


class TraktOAuthError(Exception):
    """Base exception for Trakt OAuth failures."""

    pass


class TraktOAuth:
    """Manages the Trakt OAuth 2.0 flow, token exchange, and refresh lifecycle."""

    def __init__(
        self,
        settings: Settings | None = None,
        token_store: TokenStore | None = None,
        http_client: httpx.Client | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.token_store = token_store or KeyringTokenStore()
        self._http_client = http_client
        self._refresh_lock = threading.Lock()

    def _get_client(self) -> httpx.Client:
        if self._http_client is not None:
            return self._http_client
        return httpx.Client(timeout=30.0)

    def get_authorization_url(self, state: str | None = None) -> tuple[str, str]:
        """Generate the Trakt authorization URL and CSRF state parameter."""
        if not self.settings.is_trakt_configured:
            raise TraktOAuthError("Trakt client ID and client secret must be configured.")

        csrf_state = state or secrets.token_urlsafe(32)
        params = {
            "response_type": "code",
            "client_id": self.settings.trakt_client_id,
            "redirect_uri": self.settings.trakt_redirect_uri,
            "state": csrf_state,
        }
        url = f"https://trakt.tv/oauth/authorize?{urlencode(params)}"
        return url, csrf_state

    def exchange_code_for_token(self, code: str, state: str, expected_state: str) -> TokenData:
        """Exchange the authorization code for access and refresh tokens."""
        if not secrets.compare_digest(state, expected_state):
            raise TraktOAuthError("OAuth state verification failed. Possible CSRF attempt.")

        if not self.settings.is_trakt_configured:
            raise TraktOAuthError("Trakt client ID and client secret must be configured.")

        payload = {
            "code": code,
            "client_id": self.settings.trakt_client_id,
            "client_secret": self.settings.trakt_client_secret,
            "redirect_uri": self.settings.trakt_redirect_uri,
            "grant_type": "authorization_code",
        }

        url = f"{self.settings.trakt_api_base_url}/oauth/token"
        headers = {
            "Content-Type": "application/json",
            "User-Agent": "TVeaker/1.0.0",
        }

        try:
            client = self._get_client()
            resp = client.post(url, json=payload, headers=headers)
            if resp.status_code != 200:
                logger.error("OAuth token exchange failed with status %d", resp.status_code)
                raise TraktOAuthError(f"Token exchange failed: HTTP {resp.status_code}")

            data = resp.json()
            token = TokenData(
                access_token=data["access_token"],
                refresh_token=data["refresh_token"],
                created_at=int(data["created_at"]),
                expires_in=int(data["expires_in"]),
                token_type=data.get("token_type", "bearer"),
                scope=data.get("scope", "public"),
            )
            self.token_store.save_token(token)
            logger.info("Successfully obtained and saved Trakt OAuth token.")
            return token
        except httpx.HTTPError as e:
            logger.error("HTTP error during OAuth token exchange: %s", type(e).__name__)
            msg = f"Network error during token exchange: {type(e).__name__}"
            raise TraktOAuthError(msg) from e

    def refresh_token(self, force: bool = False) -> TokenData:
        """Atomically refresh the OAuth access token if expired or forced."""
        with self._refresh_lock:
            current = self.token_store.get_token()
            if current is None:
                raise TraktOAuthError("No existing token found to refresh.")

            if not force and not current.is_expired():
                return current

            if not self.settings.is_trakt_configured:
                raise TraktOAuthError("Trakt credentials not configured.")

            payload = {
                "refresh_token": current.refresh_token,
                "client_id": self.settings.trakt_client_id,
                "client_secret": self.settings.trakt_client_secret,
                "redirect_uri": self.settings.trakt_redirect_uri,
                "grant_type": "refresh_token",
            }

            url = f"{self.settings.trakt_api_base_url}/oauth/token"
            headers = {
                "Content-Type": "application/json",
                "User-Agent": "TVeaker/1.0.0",
            }

            try:
                client = self._get_client()
                resp = client.post(url, json=payload, headers=headers)
                if resp.status_code != 200:
                    logger.error("OAuth refresh failed with status %d", resp.status_code)
                    raise TraktOAuthError(f"Token refresh failed: HTTP {resp.status_code}")

                data = resp.json()
                new_token = TokenData(
                    access_token=data["access_token"],
                    refresh_token=data["refresh_token"],
                    created_at=int(data["created_at"]),
                    expires_in=int(data["expires_in"]),
                    token_type=data.get("token_type", "bearer"),
                    scope=data.get("scope", "public"),
                )
                self.token_store.save_token(new_token)
                logger.info("Successfully refreshed and saved new Trakt OAuth token.")
                return new_token
            except httpx.HTTPError as e:
                logger.error("HTTP error during OAuth token refresh: %s", type(e).__name__)
                msg = f"Network error during token refresh: {type(e).__name__}"
                raise TraktOAuthError(msg) from e

    def disconnect(self) -> None:
        """Disconnect the current Trakt account and remove stored tokens."""
        self.token_store.delete_token()
        logger.info("Trakt account disconnected and tokens removed.")
