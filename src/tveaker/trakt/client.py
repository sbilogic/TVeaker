"""Resilient Trakt API v2 HTTP client with pagination, retry, and token refresh."""

import logging
import time
from collections.abc import Callable, Generator
from datetime import datetime
from typing import Any, Literal

import httpx

from tveaker.auth.token_store import TokenData, TokenStore
from tveaker.auth.trakt_oauth import TraktOAuth
from tveaker.config import Settings, get_settings
from tveaker.trakt.errors import (
    TraktAuthError,
    TraktContractError,
    TraktError,
    TraktNotFoundError,
    TraktRateLimitError,
    TraktServerError,
)
from tveaker.trakt.pagination import PaginatedResponse, parse_pagination_headers
from tveaker.trakt.schemas import (
    TraktEpisode,
    TraktHistoryItem,
    TraktLastActivities,
    TraktMovie,
    TraktPlaybackItem,
    TraktRatingItem,
    TraktSeason,
    TraktShow,
    TraktUserSettings,
    TraktWatchedProgress,
    TraktWatchlistItem,
)

logger = logging.getLogger(__name__)


class TraktClient:
    """HTTP client communicating with Trakt.tv API v2."""

    def __init__(
        self,
        settings: Settings | None = None,
        token_store: TokenStore | None = None,
        oauth: TraktOAuth | None = None,
        http_client: httpx.Client | None = None,
        sleep_func: Callable[[float], None] = time.sleep,
    ) -> None:
        self.settings = settings or get_settings()
        self.token_store = token_store
        self.oauth = oauth or (TraktOAuth(self.settings, token_store) if token_store else None)
        self._http_client = http_client or httpx.Client(timeout=45.0)
        self._sleep = sleep_func

    def _get_token(self) -> TokenData | None:
        if self.token_store:
            return self.token_store.get_token()
        return None

    def _build_headers(self, requires_auth: bool = True) -> dict[str, str]:
        headers = {
            "Content-Type": "application/json",
            "User-Agent": "TVeaker/1.0.0",
            "trakt-api-key": self.settings.trakt_client_id,
            "trakt-api-version": "2",
        }
        if requires_auth:
            token = self._get_token()
            if not token:
                raise TraktAuthError("No OAuth token available for authenticated Trakt request.")
            headers["Authorization"] = f"Bearer {token.access_token}"
        return headers

    def request(
        self,
        method: str,
        path: str,
        params: dict[str, Any] | None = None,
        json_body: Any = None,
        requires_auth: bool = True,
        max_retries: int = 3,
    ) -> httpx.Response:
        """Send a request to Trakt with 401 refresh, 429 backoff, and 5xx retries."""
        url = f"{self.settings.trakt_api_base_url.rstrip('/')}/{path.lstrip('/')}"
        attempt = 0
        refreshed_auth = False

        while attempt <= max_retries:
            headers = self._build_headers(requires_auth=requires_auth)
            try:
                resp = self._http_client.request(
                    method=method,
                    url=url,
                    params=params,
                    json=json_body,
                    headers=headers,
                )

                # Handle 401 Unauthorized
                if resp.status_code == 401:
                    if requires_auth and self.oauth and not refreshed_auth:
                        logger.info("Received 401 from Trakt, refreshing OAuth token...")
                        try:
                            self.oauth.refresh_token(force=True)
                            refreshed_auth = True
                            attempt += 1
                            continue
                        except Exception as e:
                            logger.error("OAuth refresh failed during request retry")
                            raise TraktAuthError("Token refresh failed on 401") from e
                    raise TraktAuthError("Unauthorized response from Trakt.")

                # Handle 404 Not Found
                if resp.status_code == 404:
                    raise TraktNotFoundError(f"Resource not found at {path}")

                # Handle 429 Rate Limit
                if resp.status_code == 429:
                    attempt += 1
                    if attempt > max_retries:
                        raise TraktRateLimitError("Trakt rate limit exceeded.")
                    retry_after_str = resp.headers.get("retry-after", "1.0")
                    try:
                        retry_after = float(retry_after_str)
                    except ValueError:
                        retry_after = 1.0
                    logger.warning("Trakt 429 Rate Limit: sleeping for %.2fs", retry_after)
                    self._sleep(retry_after)
                    continue

                # Handle 5xx Transient Server Errors
                if resp.status_code in (500, 502, 503, 504):
                    attempt += 1
                    if attempt > max_retries:
                        raise TraktServerError(
                            f"Trakt server error {resp.status_code}", status_code=resp.status_code
                        )
                    backoff = min(1.0 * (2 ** (attempt - 1)), 10.0)
                    logger.warning("Trakt %d error: retrying in %.2fs", resp.status_code, backoff)
                    self._sleep(backoff)
                    continue

                # Other 4xx Client Errors
                if resp.status_code >= 400:
                    raise TraktError(f"Trakt API error HTTP {resp.status_code}: {resp.text}")

                return resp

            except httpx.RequestError as e:
                attempt += 1
                if attempt > max_retries:
                    raise TraktServerError(f"Network transport error: {type(e).__name__}") from e
                backoff = min(1.0 * (2 ** (attempt - 1)), 10.0)
                logger.warning("Network error %s: retrying in %.2fs", type(e).__name__, backoff)
                self._sleep(backoff)

        raise TraktError("Exceeded maximum request attempts.")

    def get_user_settings(self) -> TraktUserSettings:
        """Fetch current authenticated user profile and account settings."""
        resp = self.request("GET", "/users/settings", requires_auth=True)
        try:
            return TraktUserSettings.model_validate(resp.json())
        except Exception as e:
            raise TraktContractError(f"Failed to parse user settings: {e}") from e

    def get_last_activities(self) -> TraktLastActivities:
        """Fetch last activity timestamps for incremental sync cursor checks."""
        resp = self.request("GET", "/sync/last_activities", requires_auth=True)
        try:
            return TraktLastActivities.model_validate(resp.json())
        except Exception as e:
            raise TraktContractError(f"Failed to parse last activities: {e}") from e

    def get_history(
        self,
        media_type: str | None = None,
        start_at: datetime | None = None,
        end_at: datetime | None = None,
        page: int = 1,
        limit: int = 100,
    ) -> PaginatedResponse[TraktHistoryItem]:
        """Fetch one page of watch history."""
        path = f"/sync/history/{media_type}" if media_type else "/sync/history"
        params: dict[str, Any] = {
            "page": page,
            "limit": limit,
            "extended": "full",
        }
        if start_at:
            params["start_at"] = start_at.isoformat()
        if end_at:
            params["end_at"] = end_at.isoformat()

        resp = self.request("GET", path, params=params, requires_auth=True)
        cur_page, page_count, item_count, item_limit = parse_pagination_headers(resp, page, limit)

        try:
            raw_items = resp.json()
            items = [TraktHistoryItem.model_validate(item) for item in raw_items]
            return PaginatedResponse(
                items=items,
                page=cur_page,
                page_count=page_count,
                item_count=item_count,
                limit=item_limit,
            )
        except Exception as e:
            raise TraktContractError(f"Failed to parse history response: {e}") from e

    def drain_history(
        self,
        media_type: str | None = None,
        start_at: datetime | None = None,
        end_at: datetime | None = None,
        limit: int = 100,
    ) -> Generator[TraktHistoryItem, None, None]:
        """Generator that drains all pages of history."""
        current_page = 1
        while True:
            page_resp = self.get_history(
                media_type=media_type,
                start_at=start_at,
                end_at=end_at,
                page=current_page,
                limit=limit,
            )
            yield from page_resp.items

            if not page_resp.has_next_page:
                break
            current_page += 1

    def get_playback(self, media_type: Literal["movies", "episodes"]) -> list[TraktPlaybackItem]:
        """Fetch active paused playback progress snapshots."""
        path = f"/sync/playback/{media_type}"
        params = {"extended": "full"}
        resp = self.request("GET", path, params=params, requires_auth=True)
        try:
            return [TraktPlaybackItem.model_validate(i) for i in resp.json()]
        except Exception as e:
            raise TraktContractError(f"Failed to parse playback items: {e}") from e

    def get_ratings(
        self, media_type: Literal["movies", "shows", "episodes"]
    ) -> list[TraktRatingItem]:
        """Fetch all user ratings for a specific media type."""
        path = f"/users/me/ratings/{media_type}"
        params = {"extended": "full"}
        resp = self.request("GET", path, params=params, requires_auth=True)
        try:
            return [TraktRatingItem.model_validate(i) for i in resp.json()]
        except Exception as e:
            raise TraktContractError(f"Failed to parse ratings items: {e}") from e

    def get_watchlist(self, media_type: Literal["movies", "shows"]) -> list[TraktWatchlistItem]:
        """Fetch current user watchlist items for a specific media type."""
        path = f"/users/me/watchlist/{media_type}"
        params = {"extended": "full"}
        resp = self.request("GET", path, params=params, requires_auth=True)
        try:
            return [TraktWatchlistItem.model_validate(i) for i in resp.json()]
        except Exception as e:
            raise TraktContractError(f"Failed to parse watchlist items: {e}") from e

    def get_show_seasons(self, trakt_id: int) -> list[TraktSeason]:
        """Fetch full season and episode catalog for a show."""
        path = f"/shows/{trakt_id}/seasons"
        params = {"extended": "episodes,full"}
        resp = self.request("GET", path, params=params, requires_auth=False)
        try:
            raw_seasons = resp.json()
            seasons: list[TraktSeason] = []
            for s in raw_seasons:
                episodes = [TraktEpisode.model_validate(ep) for ep in s.get("episodes", [])]
                season = TraktSeason(
                    number=s["number"],
                    ids=s.get("ids"),
                    episodes=episodes,
                )
                seasons.append(season)
            return seasons
        except Exception as e:
            raise TraktContractError(f"Failed to parse seasons for show {trakt_id}: {e}") from e

    def get_show_progress_watched(self, trakt_id: int) -> TraktWatchedProgress:
        """Fetch watched progress metadata for a specific show."""
        path = f"/shows/{trakt_id}/progress/watched"
        params = {"specials": "true", "count_specials": "true"}
        resp = self.request("GET", path, params=params, requires_auth=True)
        try:
            return TraktWatchedProgress.model_validate(resp.json())
        except Exception as e:
            raise TraktContractError(f"Failed to parse show progress for {trakt_id}: {e}") from e

    def get_recommendations(
        self, media_type: Literal["movies", "shows"], limit: int = 100
    ) -> list[TraktMovie | TraktShow]:
        """Fetch recommended seeds from Trakt."""
        path = f"/recommendations/{media_type}"
        params = {"limit": limit, "extended": "full"}
        resp = self.request("GET", path, params=params, requires_auth=True)
        try:
            raw_items = resp.json()
            if media_type == "movies":
                return [TraktMovie.model_validate(i) for i in raw_items]
            else:
                return [TraktShow.model_validate(i) for i in raw_items]
        except Exception as e:
            raise TraktContractError(f"Failed to parse recommendations: {e}") from e
