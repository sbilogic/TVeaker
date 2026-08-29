"""Comprehensive metadata, poster artwork, and episode title hydration engine."""

import logging
import threading
from typing import Any

import httpx
from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from tveaker.db import get_db_session
from tveaker.models import Episode, MediaItem

logger = logging.getLogger(__name__)

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

    def __init__(self, http_client: httpx.Client | None = None) -> None:
        self.client = http_client or httpx.Client(timeout=6.0, follow_redirects=True)

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
        """Hydrate poster art and real episode names for a single MediaItem.

        Returns: (poster_updated: bool, episodes_updated_count: int)
        """
        poster_updated = False
        episodes_updated = 0

        t_clean = media.title.lower().strip()

        # 1. Curated Artwork Check
        if not media.poster_url and t_clean in CURATED_ARTWORK:
            art = CURATED_ARTWORK[t_clean]
            media.poster_url = art["poster_url"]
            media.backdrop_url = art["backdrop_url"]
            poster_updated = True

        # 2. Movie or Show OMDb Poster Fetch if missing
        if not media.poster_url and media.imdb_id:
            omdb_poster = self.fetch_poster_from_omdb(media.imdb_id)
            if omdb_poster:
                media.poster_url = omdb_poster
                media.backdrop_url = omdb_poster
                poster_updated = True

        # 3. TVMaze Metadata & Episodes Fetch (for TV shows)
        if media.media_type == "show":
            tvmaze_data = self.fetch_show_data_from_tvmaze(media.title, media.imdb_id)
            if tvmaze_data:
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
                        else:
                            new_ep = Episode(
                                show_id=media.id,
                                trakt_id=-(media.id * 1000000 + season_num * 10000 + ep_num),
                                season_number=season_num,
                                episode_number=ep_num,
                                title=ep_name,
                                runtime_minutes=raw_ep.get("runtime")
                                or media.runtime_minutes
                                or 42,
                                overview=(raw_ep.get("summary") or "")
                                .replace("<p>", "")
                                .replace("</p>", "")
                                .strip(),
                            )
                            session.add(new_ep)
                            existing_ep_map[ep_key] = new_ep
                            episodes_updated += 1

            # 4. Fallback OMDb Season Search for remaining dummy episode titles
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


def hydrate_all_metadata(db_engine: Engine, limit: int = 1000) -> dict[str, int]:
    """Batch hydrate posters and episode titles for all media items in SQLite."""
    service = MetadataHydrationService()
    posters_updated_total = 0
    episodes_updated_total = 0

    with get_db_session(db_engine) as session:
        items = session.execute(select(MediaItem).limit(limit)).scalars().all()

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
        "posters_updated": posters_updated_total,
        "episodes_updated": episodes_updated_total,
    }


def start_background_metadata_hydration(db_engine: Engine) -> None:
    """Launch background thread to hydrate posters & episode titles on server startup."""

    def _worker() -> None:
        try:
            hydrate_all_metadata(db_engine, limit=1000)
        except Exception as e:
            logger.warning("Background metadata hydration warning: %s", e)

    thread = threading.Thread(target=_worker, daemon=True, name="tveaker-metadata-hydrator")
    thread.start()
