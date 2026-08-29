"""Media poster and backdrop image resolution service."""

import logging

import httpx
from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from tveaker.db import get_db_session
from tveaker.models import MediaItem

logger = logging.getLogger(__name__)

# Curated fallback poster URLs for popular TV series and movies
CURATED_POSTERS: dict[str, dict[str, str | None]] = {
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
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/499/1247656.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/499/1247656.jpg",
    },
    "game of thrones": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/498/1245275.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/498/1245275.jpg",
    },
    "tires": {
        "poster_url": "https://static.tvmaze.com/uploads/images/medium_portrait/512/1281813.jpg",
        "backdrop_url": "https://static.tvmaze.com/uploads/images/original_untouched/512/1281813.jpg",
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


class MediaImageService:
    """Fetches, resolves, and caches poster and backdrop art for media items."""

    def __init__(self, http_client: httpx.Client | None = None) -> None:
        self.client = http_client or httpx.Client(timeout=6.0)

    def resolve_image(
        self, title: str, imdb_id: str | None = None, media_type: str = "show"
    ) -> dict[str, str | None]:
        """Resolve poster_url and backdrop_url for a media item."""
        t_clean = title.lower().strip()

        # 1. Check curated high-resolution index
        if t_clean in CURATED_POSTERS:
            return CURATED_POSTERS[t_clean]

        for key, urls in CURATED_POSTERS.items():
            if key in t_clean:
                return urls

        # 2. Query TVMaze by IMDB ID or Title (100% Free API, No Key Needed)
        try:
            if imdb_id and imdb_id.startswith("tt"):
                res = self.client.get(f"https://api.tvmaze.com/lookup/shows?imdb={imdb_id}")
                if res.status_code == 200:
                    data = res.json()
                    img = data.get("image") or {}
                    if img.get("medium"):
                        return {
                            "poster_url": img.get("medium"),
                            "backdrop_url": img.get("original") or img.get("medium"),
                        }

            res = self.client.get(f"https://api.tvmaze.com/singlesearch/shows?q={title}")
            if res.status_code == 200:
                data = res.json()
                img = data.get("image") or {}
                if img.get("medium"):
                    return {
                        "poster_url": img.get("medium"),
                        "backdrop_url": img.get("original") or img.get("medium"),
                    }
        except Exception as e:
            logger.debug("Failed to resolve image from TVMaze for %s: %s", title, e)

        return {"poster_url": None, "backdrop_url": None}

    def enrich_item(self, session: Session, item: MediaItem) -> bool:
        """Enrich a single media item with poster images if missing."""
        if item.poster_url:
            return False

        urls = self.resolve_image(item.title, imdb_id=item.imdb_id, media_type=item.media_type)
        if urls.get("poster_url"):
            item.poster_url = urls["poster_url"]
            item.backdrop_url = urls["backdrop_url"]
            return True
        return False


def enrich_all_media_images(db_engine: Engine, limit: int = 50) -> int:
    """Batch enrich media items in the database with poster art."""
    service = MediaImageService()
    enriched_count = 0

    with get_db_session(db_engine) as session:
        items = (
            session.execute(select(MediaItem).where(MediaItem.poster_url.is_(None)).limit(limit))
            .scalars()
            .all()
        )

        for it in items:
            if service.enrich_item(session, it):
                enriched_count += 1

        session.flush()

    return enriched_count
