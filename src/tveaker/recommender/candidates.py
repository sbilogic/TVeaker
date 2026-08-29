"""Candidate generation and exclusion filters for recommendation engine."""

import json
import logging
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Literal

from sqlalchemy import Engine, select

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.models import (
    Episode,
    MediaItem,
    PlaybackState,
    RecommendationFeedback,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)

logger = logging.getLogger(__name__)


def _ensure_utc(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


@dataclass(frozen=True)
class Candidate:
    candidate_id: str
    media_type: Literal["movie", "show"]
    media_item_id: int
    trakt_id: int
    title: str
    year: int | None
    overview: str | None
    genres: list[str]
    runtime_minutes: int | None
    source_reasons: list[str]
    in_progress: bool
    remaining_episodes: int | None
    progress_percent: float | None
    poster_url: str | None = None
    backdrop_url: str | None = None


class CandidatePoolGenerator:
    """Extracts candidate pool for ranking while enforcing deduplication and exclusion rules."""

    def __init__(
        self,
        db_engine: Engine,
        account_id: int = 1,
        clock: Clock | None = None,
    ) -> None:
        self.db_engine = db_engine
        self.account_id = account_id
        self.clock = clock or SystemClock()

    def generate_candidates(
        self,
        media_type: Literal["all", "movie", "show"] = "all",
        max_candidates: int = 200,
    ) -> list[Candidate]:
        """Generate candidate pool applying all exclusion criteria."""
        now = self.clock.now()
        now_utc = _ensure_utc(now) or datetime.now(UTC)
        cutoff_30d = now_utc - timedelta(days=30)

        with get_db_session(self.db_engine) as session:
            # 1. Gather exclusions
            # A. Watched movies
            watched_movie_ids = set(
                session.execute(
                    select(WatchEvent.movie_id).where(
                        WatchEvent.account_id == self.account_id,
                        WatchEvent.movie_id.isnot(None),
                    )
                )
                .scalars()
                .all()
            )

            # B. Dropped or completed shows in tracking
            excluded_show_ids = set(
                session.execute(
                    select(TrackedShow.show_id).where(
                        TrackedShow.account_id == self.account_id,
                        TrackedShow.status.in_(["dropped", "completed"]),
                    )
                )
                .scalars()
                .all()
            )

            # C. Dismissed recommendations:
            # 'not_interested' -> permanent exclusion
            # 'not_now' -> excluded for 30 days
            raw_feedback = session.execute(
                select(
                    RecommendationFeedback.candidate_id,
                    RecommendationFeedback.action,
                    RecommendationFeedback.created_at,
                )
            ).all()

            excluded_candidate_keys = set()
            for cand_id, action, created_at in raw_feedback:
                if action == "not_interested":
                    excluded_candidate_keys.add(cand_id)
                elif action == "not_now":
                    c_utc = _ensure_utc(created_at)
                    if c_utc and c_utc >= cutoff_30d:
                        excluded_candidate_keys.add(cand_id)

            # Candidate collector: media_item_id -> dict
            candidates_map: dict[int, dict] = {}

            # Source 1: Watchlist items
            wl_stmt = (
                select(WatchlistItem, MediaItem)
                .join(MediaItem, MediaItem.id == WatchlistItem.media_item_id)
                .where(WatchlistItem.account_id == self.account_id)
            )
            if media_type != "all":
                wl_stmt = wl_stmt.where(WatchlistItem.media_type == media_type)

            for _wl, media in session.execute(wl_stmt).all():
                if media.media_type == "movie" and media.id in watched_movie_ids:
                    continue
                if media.media_type == "show" and media.id in excluded_show_ids:
                    continue
                cand_key = f"{media.media_type}:{media.id}"
                if cand_key in excluded_candidate_keys:
                    continue

                if media.id not in candidates_map:
                    candidates_map[media.id] = {
                        "media": media,
                        "sources": ["watchlist"],
                        "in_progress": False,
                        "progress_percent": None,
                        "remaining_episodes": None,
                    }
                else:
                    candidates_map[media.id]["sources"].append("watchlist")

            # Source 2: In-progress playback state (movies and shows)
            pb_stmt = select(PlaybackState).where(PlaybackState.account_id == self.account_id)
            for pb in session.execute(pb_stmt).scalars().all():
                media = None
                if pb.movie_id:
                    media = session.get(MediaItem, pb.movie_id)
                elif pb.episode_id:
                    ep = session.get(Episode, pb.episode_id)
                    if ep:
                        media = session.get(MediaItem, ep.show_id)

                if media is None:
                    continue
                if media_type != "all" and media.media_type != media_type:
                    continue
                if media.media_type == "movie" and media.id in watched_movie_ids:
                    continue
                if media.media_type == "show" and media.id in excluded_show_ids:
                    continue
                cand_key = f"{media.media_type}:{media.id}"
                if cand_key in excluded_candidate_keys:
                    continue

                if media.id not in candidates_map:
                    candidates_map[media.id] = {
                        "media": media,
                        "sources": ["in_progress_playback"],
                        "in_progress": True,
                        "progress_percent": pb.progress_percent,
                        "remaining_episodes": None,
                    }
                else:
                    candidates_map[media.id]["sources"].append("in_progress_playback")
                    candidates_map[media.id]["in_progress"] = True
                    candidates_map[media.id]["progress_percent"] = pb.progress_percent

            # Source 3: Active watching shows in TrackedShow
            active_shows = session.execute(
                select(TrackedShow, MediaItem)
                .join(MediaItem, MediaItem.id == TrackedShow.show_id)
                .where(
                    TrackedShow.account_id == self.account_id,
                    TrackedShow.status == "watching",
                )
            ).all()

            for _ts, media in active_shows:
                if media_type not in ("all", "show"):
                    continue
                if media.id in excluded_show_ids:
                    continue
                cand_key = f"show:{media.id}"
                if cand_key in excluded_candidate_keys:
                    continue

                if media.id not in candidates_map:
                    candidates_map[media.id] = {
                        "media": media,
                        "sources": ["active_tracked_show"],
                        "in_progress": True,
                        "progress_percent": None,
                        "remaining_episodes": None,
                    }
                else:
                    candidates_map[media.id]["sources"].append("active_tracked_show")
                    candidates_map[media.id]["in_progress"] = True

            # Source 4: Cached recommendation seeds & other catalog items
            cat_stmt = select(MediaItem)
            if media_type != "all":
                cat_stmt = cat_stmt.where(MediaItem.media_type == media_type)

            for media in session.execute(cat_stmt).scalars().all():
                if len(candidates_map) >= max_candidates:
                    break
                if media.media_type == "movie" and media.id in watched_movie_ids:
                    continue
                if media.media_type == "show" and media.id in excluded_show_ids:
                    continue
                cand_key = f"{media.media_type}:{media.id}"
                if cand_key in excluded_candidate_keys:
                    continue

                if media.id not in candidates_map:
                    candidates_map[media.id] = {
                        "media": media,
                        "sources": ["catalog_discovery"],
                        "in_progress": False,
                        "progress_percent": None,
                        "remaining_episodes": None,
                    }

            # Convert to Candidate objects
            result: list[Candidate] = []
            for item_data in candidates_map.values():
                media = item_data["media"]
                genres = json.loads(media.genres_json) if media.genres_json else []

                # Compute remaining episodes for shows
                remaining_eps = None
                if media.media_type == "show":
                    all_eps = (
                        session.execute(
                            select(Episode.id).where(
                                Episode.show_id == media.id,
                                Episode.season_number > 0,
                            )
                        )
                        .scalars()
                        .all()
                    )
                    watched_eps = set(
                        session.execute(
                            select(WatchEvent.episode_id)
                            .join(Episode, WatchEvent.episode_id == Episode.id)
                            .where(
                                WatchEvent.account_id == self.account_id,
                                Episode.show_id == media.id,
                            )
                        )
                        .scalars()
                        .all()
                    )
                    remaining_eps = len([e for e in all_eps if e not in watched_eps])
                    if remaining_eps == 0 and len(all_eps) > 0:
                        # Fully completed show, exclude
                        continue

                m_type: Literal["movie", "show"] = (
                    "movie" if media.media_type == "movie" else "show"
                )
                result.append(
                    Candidate(
                        candidate_id=f"{media.media_type}:{media.id}",
                        media_type=m_type,
                        media_item_id=media.id,
                        trakt_id=media.trakt_id,
                        title=media.title,
                        year=media.year,
                        overview=media.overview,
                        genres=genres,
                        runtime_minutes=media.runtime_minutes,
                        source_reasons=list(set(item_data["sources"])),
                        in_progress=item_data["in_progress"],
                        remaining_episodes=remaining_eps,
                        progress_percent=item_data["progress_percent"],
                        poster_url=media.poster_url,
                        backdrop_url=media.backdrop_url,
                    )
                )

            return result[:max_candidates]
