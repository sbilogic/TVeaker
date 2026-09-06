"""Comprehensive metadata, poster artwork, and episode title hydration engine."""

import logging
import threading
from datetime import UTC, datetime
from typing import Any, cast

import httpx
from sqlalchemy import Engine, delete, or_, select
from sqlalchemy.engine import CursorResult
from sqlalchemy.orm import Session

from tveaker.config import Settings, get_settings
from tveaker.db import get_db_session
from tveaker.episode_catalog import get_show_progress_catalog
from tveaker.models import Episode, MediaItem, NowWatching, PlaybackState, WatchEvent

logger = logging.getLogger(__name__)

TMDB_API_BASE_URL = "https://api.themoviedb.org/3"
TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"


class TMDBMetadataService:
    """Optional TMDB resolver for richer movie and series metadata."""

    def __init__(
        self,
        settings: Settings | None = None,
        http_client: httpx.Client | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.client = http_client or httpx.Client(timeout=8.0, follow_redirects=True)

    @property
    def is_configured(self) -> bool:
        return self.settings.is_tmdb_configured

    def _get(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any] | None:
        if not self.is_configured:
            return None
        query = dict(params or {})
        headers = {"accept": "application/json"}
        if self.settings.tmdb_access_token:
            headers["Authorization"] = f"Bearer {self.settings.tmdb_access_token}"
        else:
            query["api_key"] = self.settings.tmdb_api_key
        try:
            response = self.client.get(f"{TMDB_API_BASE_URL}{path}", params=query, headers=headers)
            if response.status_code == 200:
                return response.json()
        except httpx.HTTPError as exc:
            logger.debug("TMDB request %s failed: %s", path, exc)
        return None

    def fetch_media_data(self, media: MediaItem) -> dict[str, Any] | None:
        """Resolve a title to TMDB details without persisting remote IDs."""
        resource = "tv" if media.media_type == "show" else "movie"
        params: dict[str, Any] = {
            "query": media.title,
            "include_adult": "false",
            "language": "en-US",
        }
        if media.year:
            params["first_air_date_year" if resource == "tv" else "year"] = media.year
        search = self._get(f"/search/{resource}", params)
        results = (search or {}).get("results") or []
        if not results:
            return None
        title_key = "name" if resource == "tv" else "title"
        exact = next(
            (
                item
                for item in results
                if str(item.get(title_key, "")).casefold() == media.title.casefold()
            ),
            None,
        )
        candidate = exact or results[0]
        remote_id = candidate.get("id")
        if not remote_id:
            return None
        return self._get(f"/{resource}/{remote_id}", {"language": "en-US"})


def _parse_air_datetime(raw_episode: dict[str, Any]) -> datetime | None:
    """Parse TVMaze's precise airstamp, falling back to its calendar air date."""
    raw_value = raw_episode.get("airstamp") or raw_episode.get("airdate")
    if not raw_value:
        return None
    try:
        parsed = datetime.fromisoformat(str(raw_value).replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)

# Curated fallback poster & backdrop URLs for popular series & movies
CURATED_ARTWORK: dict[str, dict[str, str]] = {
    "black mirror": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/564/1411764.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/564/1411764.jpg",
    },
    "futurama": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/633/1584696.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/633/1584696.jpg",
    },
    "bob's burgers": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/589/1474468.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/589/1474468.jpg",
    },
    "dark matter": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/633/1584721.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/633/1584721.jpg",
    },
    "the bear": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/629/1574642.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/629/1574642.jpg",
    },
    "house of the dragon": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/627/1568449.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/627/1568449.jpg",
    },
    "invincible": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/618/1545777.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/618/1545777.jpg",
    },
    "game of thrones": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/498/1245275.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/498/1245275.jpg",
    },
    "tires": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/520/1300772.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/520/1300772.jpg",
    },
    "impractical jokers": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/397/993700.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/397/993700.jpg",
    },
    "severance": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/441/1104886.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/441/1104886.jpg",
    },
    "rick and morty": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/481/1204065.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/481/1204065.jpg",
    },
    "the boys": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/516/1291880.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/516/1291880.jpg",
    },
    "stranger things": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/396/991288.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/396/991288.jpg",
    },
    "arcane": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/373/933816.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/373/933816.jpg",
    },
    "the last of us": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/444/1110594.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/444/1110594.jpg",
    },
}


class MetadataHydrationService:
    """Hydrates real episode titles, runtimes, overviews, and poster artwork."""

    def __init__(
        self,
        http_client: httpx.Client | None = None,
        tmdb_service: TMDBMetadataService | None = None,
    ) -> None:
        self.client = http_client or httpx.Client(timeout=6.0, follow_redirects=True)
        self.tmdb = tmdb_service or TMDBMetadataService()

    def fetch_show_data_from_tvmaze(
        self, title: str, imdb_id: str | None = None
    ) -> dict[str, Any] | None:
        """Fetch show metadata and embedded episodes from TVMaze API."""
        try:
            if imdb_id and imdb_id.startswith("tt"):
                res = self.client.get(f"https://api.tvmaze.com/lookup/shows?imdb={imdb_id}")
                if res.status_code == 200:
                    show_obj = res.json()
                    tvm_id = show_obj.get("id")
                    if tvm_id:
                        res_eps = self.client.get(
                            f"https://api.tvmaze.com/shows/{tvm_id}?embed=episodes"
                        )
                        if res_eps.status_code == 200:
                            return res_eps.json()
                    return show_obj

            res = self.client.get(
                f"https://api.tvmaze.com/singlesearch/shows?q={title}&embed=episodes"
            )
            if res.status_code == 200:
                return res.json()
        except Exception as e:
            logger.debug("TVMaze lookup failed for %s: %s", title, e)

        return None

    def fetch_poster_from_omdb(self, imdb_id: str) -> str | None:
        """Fetch high-resolution movie/show poster from OMDb API."""
        if not imdb_id or not imdb_id.startswith("tt"):
            return None
        try:
            res = self.client.get(f"http://www.omdbapi.com/?i={imdb_id}&apikey=trilogy")
            if res.status_code == 200:
                p = res.json().get("Poster")
                if p and p != "N/A":
                    return p
        except Exception as e:
            logger.debug("OMDb poster fetch failed for %s: %s", imdb_id, e)
        return None

    def fetch_omdb_season_episodes(self, imdb_id: str, season_number: int) -> dict[int, str]:
        """Fetch official episode names for a season from OMDb API."""
        if not imdb_id or not imdb_id.startswith("tt"):
            return {}
        try:
            res = self.client.get(
                f"http://www.omdbapi.com/?i={imdb_id}&Season={season_number}&apikey=trilogy"
            )
            if res.status_code == 200:
                raw_eps = res.json().get("Episodes", [])
                return {
                    int(e["Episode"]): e["Title"]
                    for e in raw_eps
                    if "Episode" in e and "Title" in e
                }
        except Exception as e:
            logger.debug(
                "OMDb season episode fetch failed for %s S%d: %s", imdb_id, season_number, e
            )
        return {}

    def hydrate_item(self, session: Session, media: MediaItem) -> tuple[bool, int]:
        """Hydrate poster art and existing Trakt episode metadata for one MediaItem.

        Returns: (poster_updated: bool, episodes_updated_count: int)
        """
        poster_updated = False
        episodes_updated = 0

        t_clean = media.title.lower().strip()

        # 1. Use TMDB whenever the owner has configured it. It is stronger for
        # movies and media-level artwork/runtime; TVMaze remains the schedule
        # authority for episode-level air dates and runtimes.
        tmdb_data = self.tmdb.fetch_media_data(media)
        if tmdb_data:
            poster_path = tmdb_data.get("poster_path")
            backdrop_path = tmdb_data.get("backdrop_path")
            if not media.poster_url and poster_path:
                media.poster_url = f"{TMDB_IMAGE_BASE_URL}{poster_path}"
                poster_updated = True
            if not media.backdrop_url and backdrop_path:
                media.backdrop_url = f"{TMDB_IMAGE_BASE_URL}{backdrop_path}"
            runtime = tmdb_data.get("runtime")
            if not runtime:
                runtimes = tmdb_data.get("episode_run_time") or []
                runtime = next(
                    (value for value in runtimes if isinstance(value, int) and value > 0),
                    None,
                )
            if not media.runtime_minutes and isinstance(runtime, int) and runtime > 0:
                media.runtime_minutes = runtime
            release_date = tmdb_data.get("first_air_date") or tmdb_data.get("release_date")
            if media.first_aired is None and release_date:
                media.first_aired = _parse_air_datetime({"airdate": release_date})
            if not media.genres and tmdb_data.get("genres"):
                media.genres = [genre["name"] for genre in tmdb_data["genres"] if genre.get("name")]
            if not media.status and tmdb_data.get("status"):
                media.status = str(tmdb_data["status"])

        # 2. Curated artwork remains an offline-safe fallback.
        if not media.poster_url and t_clean in CURATED_ARTWORK:
            art = CURATED_ARTWORK[t_clean]
            media.poster_url = art["poster_url"]
            media.backdrop_url = art["backdrop_url"]
            poster_updated = True

        # 3. Movie or show OMDb poster fallback.
        if not media.poster_url and media.imdb_id:
            omdb_poster = self.fetch_poster_from_omdb(media.imdb_id)
            if omdb_poster:
                media.poster_url = omdb_poster
                media.backdrop_url = omdb_poster
                poster_updated = True

        # 4. TVMaze is the episode schedule fallback and no-key provider.
        if media.media_type == "show":
            tvmaze_data = self.fetch_show_data_from_tvmaze(media.title, media.imdb_id)
            if tvmaze_data:
                show_runtime = tvmaze_data.get("averageRuntime") or tvmaze_data.get("runtime")
                if not media.runtime_minutes and show_runtime:
                    media.runtime_minutes = int(show_runtime)

                if media.first_aired is None and tvmaze_data.get("premiered"):
                    media.first_aired = _parse_air_datetime(
                        {"airdate": tvmaze_data["premiered"]}
                    )

                img = tvmaze_data.get("image") or {}
                if not media.poster_url and img.get("medium"):
                    media.poster_url = img.get("medium")
                    media.backdrop_url = img.get("original") or img.get("medium")
                    poster_updated = True

                # Process embedded episodes
                raw_episodes = tvmaze_data.get("_embedded", {}).get("episodes", [])
                if raw_episodes:
                    stmt = select(Episode).where(Episode.show_id == media.id)
                    existing_ep_map = {
                        (ep.season_number, ep.episode_number): ep
                        for ep in session.execute(stmt).scalars().all()
                    }

                    for raw_ep in raw_episodes:
                        season_num = raw_ep.get("season")
                        ep_num = raw_ep.get("number")
                        ep_name = raw_ep.get("name")
                        first_aired = _parse_air_datetime(raw_ep)

                        if season_num is None or ep_num is None or not ep_name:
                            continue

                        ep_key = (season_num, ep_num)
                        ep_record = existing_ep_map.get(ep_key)

                        if ep_record:
                            is_dummy = (
                                not ep_record.title
                                or ep_record.title == f"Episode {ep_num}"
                                or ep_record.title.startswith("Episode ")
                            )
                            if is_dummy and ep_name:
                                ep_record.title = ep_name
                                episodes_updated += 1
                            if not ep_record.runtime_minutes and raw_ep.get("runtime"):
                                ep_record.runtime_minutes = raw_ep.get("runtime")
                            if ep_record.first_aired is None and first_aired is not None:
                                ep_record.first_aired = first_aired
                            if not ep_record.overview and raw_ep.get("summary"):
                                clean_summary = (
                                    (raw_ep.get("summary") or "")
                                    .replace("<p>", "")
                                    .replace("</p>", "")
                                    .replace("<b>", "")
                                    .replace("</b>", "")
                                    .strip()
                                )
                                ep_record.overview = clean_summary
            # 5. Fallback OMDb Season Search for remaining dummy episode titles
            if media.imdb_id:
                stmt = select(Episode).where(Episode.show_id == media.id)
                show_eps = session.execute(stmt).scalars().all()
                dummy_eps = [
                    ep
                    for ep in show_eps
                    if not ep.title
                    or ep.title == f"Episode {ep.episode_number}"
                    or ep.title.startswith("Episode ")
                ]
                if dummy_eps:
                    seasons = {ep.season_number for ep in dummy_eps if ep.season_number > 0}
                    for s_num in seasons:
                        omdb_ep_map = self.fetch_omdb_season_episodes(media.imdb_id, s_num)
                        if omdb_ep_map:
                            for ep in dummy_eps:
                                if ep.season_number == s_num and ep.episode_number in omdb_ep_map:
                                    ep.title = omdb_ep_map[ep.episode_number]
                                    episodes_updated += 1

        return poster_updated, episodes_updated


def prune_synthetic_episode_catalog(db_engine: Engine) -> int:
    """Remove provider rows outside Trakt's progress cap without losing history."""
    protected_episode_ids = select(WatchEvent.episode_id).where(
        WatchEvent.episode_id.is_not(None)
    ).union(
        select(PlaybackState.episode_id).where(PlaybackState.episode_id.is_not(None)),
        select(NowWatching.episode_id),
    )

    with get_db_session(db_engine) as session:
        eligible_candidate_ids: set[int] = set()
        show_ids = session.execute(
            select(MediaItem.id).where(MediaItem.media_type == "show")
        ).scalars().all()
        for show_id in show_ids:
            catalog = get_show_progress_catalog(
                session=session,
                account_id=1,
                show_id=show_id,
                include_specials=False,
            )
            eligible_candidate_ids.update(
                episode.id for episode in catalog.episodes if episode.trakt_id < 0
            )

        result = cast(
            CursorResult[Any],
            session.execute(
                delete(Episode).where(
                    Episode.trakt_id < 0,
                    Episode.id.not_in(protected_episode_ids),
                    Episode.id.not_in(eligible_candidate_ids),
                )
            ),
        )
        return int(result.rowcount or 0)


def hydrate_all_metadata(db_engine: Engine, limit: int = 75) -> dict[str, int | str]:
    """Hydrate the items that still lack dependable metadata, in bounded batches."""
    pruned_synthetic_episodes = prune_synthetic_episode_catalog(db_engine)
    service = MetadataHydrationService()
    posters_updated_total = 0
    episodes_updated_total = 0

    with get_db_session(db_engine) as session:
        # Only spend network work where it can improve the experience. Shows
        # with a missing episode schedule are included even when their card art
        # is already present, so forecasts stay release-aware.
        incomplete_episode = (
            select(Episode.id)
            .where(
                Episode.show_id == MediaItem.id,
                or_(
                    Episode.runtime_minutes.is_(None),
                    Episode.first_aired.is_(None),
                    Episode.title.is_(None),
                ),
            )
            .exists()
        )
        pending = or_(
            MediaItem.poster_url.is_(None),
            MediaItem.backdrop_url.is_(None),
            MediaItem.runtime_minutes.is_(None),
            MediaItem.first_aired.is_(None),
            incomplete_episode,
        )
        items = session.execute(
            select(MediaItem)
            .where(pending)
            .order_by(
                MediaItem.poster_url.is_(None).desc(),
                MediaItem.runtime_minutes.is_(None).desc(),
                MediaItem.id.desc(),
            )
            .limit(limit)
        ).scalars().all()

        for media in items:
            p_up, ep_up = service.hydrate_item(session, media)
            if p_up:
                posters_updated_total += 1
            episodes_updated_total += ep_up

        session.flush()

    logger.info(
        "Metadata hydration complete: %d posters updated, %d episode titles hydrated.",
        posters_updated_total,
        episodes_updated_total,
    )

    return {
        "items_checked": len(items),
        "posters_updated": posters_updated_total,
        "episodes_updated": episodes_updated_total,
        "synthetic_episodes_pruned": pruned_synthetic_episodes,
        "provider": "tmdb" if service.tmdb.is_configured else "tvmaze_fallback",
    }


def start_background_metadata_hydration(db_engine: Engine) -> None:
    """Launch background thread to hydrate posters & episode titles on server startup."""

    def _worker() -> None:
        try:
            hydrate_all_metadata(db_engine)
        except Exception as e:
            logger.warning("Background metadata hydration warning: %s", e)

    thread = threading.Thread(target=_worker, daemon=True, name="tveaker-metadata-hydrator")
    thread.start()
