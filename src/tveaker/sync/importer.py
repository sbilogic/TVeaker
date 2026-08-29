"""Account synchronization engine and snapshot importer."""

import logging
from dataclasses import dataclass
from datetime import timedelta
from typing import Literal

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    SyncRun,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)
from tveaker.sync.reconcile import (
    replace_playback_snapshot,
    replace_ratings_snapshot,
    replace_watchlist_snapshot,
    update_sync_cursor,
    upsert_account,
    upsert_media_movie,
    upsert_media_show,
    upsert_season_episodes,
    upsert_watch_events,
)
from tveaker.trakt.client import TraktClient
from tveaker.trakt.schemas import TraktMovie, TraktShow

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class SyncReport:
    run_id: int
    mode: str
    status: Literal["success", "partial", "failed", "skipped"]
    fetched: dict[str, int]
    inserted: dict[str, int]
    updated: dict[str, int]
    deleted: dict[str, int]
    warnings: tuple[str, ...]


class AccountSync:
    """Manages full, initial, and incremental synchronization with Trakt."""

    def __init__(
        self,
        db_engine: Engine,
        trakt_client: TraktClient,
        clock: Clock | None = None,
    ) -> None:
        self.db_engine = db_engine
        self.client = trakt_client
        self.clock = clock or SystemClock()

    def run(self, mode: Literal["initial", "incremental", "full"] = "initial") -> SyncReport:
        """Execute a synchronization run."""
        started_at = self.clock.now()
        warnings: list[str] = []
        fetched_counts: dict[str, int] = {}
        inserted_counts: dict[str, int] = {}
        updated_counts: dict[str, int] = {}
        deleted_counts: dict[str, int] = {}

        # 1. Create sync run entry
        with get_db_session(self.db_engine) as session:
            sync_run = SyncRun(
                mode=mode,
                status="partial",
                started_at=started_at,
                counts_json="{}",
                warnings_json="[]",
            )
            session.add(sync_run)
            session.flush()
            run_id = sync_run.id

        status: Literal["success", "partial", "failed", "skipped"] = "success"

        try:
            # 2. Authenticate & fetch user settings
            settings = self.client.get_user_settings()
            with get_db_session(self.db_engine) as session:
                account = upsert_account(session, settings)
                account_id = account.id

            # 3. History ingestion
            history_items = list(self.client.drain_history())
            fetched_counts["history"] = len(history_items)
            with get_db_session(self.db_engine) as session:
                ins, upd = upsert_watch_events(session, account_id, history_items)
                inserted_counts["history"] = ins
                updated_counts["history"] = upd

            # 4. Ratings snapshot
            movie_ratings = self.client.get_ratings("movies")
            show_ratings = self.client.get_ratings("shows")
            ep_ratings = self.client.get_ratings("episodes")
            all_ratings = movie_ratings + show_ratings + ep_ratings
            fetched_counts["ratings"] = len(all_ratings)
            with get_db_session(self.db_engine) as session:
                ins = replace_ratings_snapshot(session, account_id, all_ratings)
                inserted_counts["ratings"] = ins

            # 5. Watchlist snapshot
            wl_movies = self.client.get_watchlist("movies")
            wl_shows = self.client.get_watchlist("shows")
            fetched_counts["watchlist"] = len(wl_movies) + len(wl_shows)
            with get_db_session(self.db_engine) as session:
                ins = replace_watchlist_snapshot(session, account_id, wl_movies, wl_shows)
                inserted_counts["watchlist"] = ins

            # 6. Playback snapshot
            pb_movies = self.client.get_playback("movies")
            pb_episodes = self.client.get_playback("episodes")
            fetched_counts["playback"] = len(pb_movies) + len(pb_episodes)
            with get_db_session(self.db_engine) as session:
                ins = replace_playback_snapshot(session, account_id, pb_movies, pb_episodes)
                inserted_counts["playback"] = ins

            # 7. Discover and hydrate relevant shows (seasons and episode catalogs)
            show_trakt_ids: set[int] = set()
            with get_db_session(self.db_engine) as session:
                shows = (
                    session.execute(
                        select(MediaItem.trakt_id).where(MediaItem.media_type == "show")
                    )
                    .scalars()
                    .all()
                )
                show_trakt_ids.update(shows)

            catalog_episodes_ins = 0
            catalog_episodes_upd = 0
            for trakt_id in show_trakt_ids:
                try:
                    seasons = self.client.get_show_seasons(trakt_id)
                    with get_db_session(self.db_engine) as session:
                        show_item = session.execute(
                            select(MediaItem).where(
                                MediaItem.media_type == "show",
                                MediaItem.trakt_id == trakt_id,
                            )
                        ).scalar_one_or_none()
                        if show_item:
                            ins, upd = upsert_season_episodes(session, show_item.id, seasons)
                            catalog_episodes_ins += ins
                            catalog_episodes_upd += upd
                except Exception as e:
                    logger.warning("Failed to fetch season catalog for show %d: %s", trakt_id, e)
                    warnings.append(f"Catalog error for show {trakt_id}: {type(e).__name__}")
                    status = "partial"

            inserted_counts["catalog_episodes"] = catalog_episodes_ins
            updated_counts["catalog_episodes"] = catalog_episodes_upd

            # 8. Seed local show tracking
            with get_db_session(self.db_engine) as session:
                self._seed_tracked_shows(session, account_id)

            # 9. Optional recommendation seeds
            try:
                rec_movies = self.client.get_recommendations("movies", limit=100)
                rec_shows = self.client.get_recommendations("shows", limit=100)
                with get_db_session(self.db_engine) as session:
                    for m in rec_movies:
                        if isinstance(m, TraktMovie):
                            upsert_media_movie(session, m)
                    for s in rec_shows:
                        if isinstance(s, TraktShow):
                            upsert_media_show(session, s)
            except Exception as e:
                logger.warning("Failed to fetch recommendation seeds: %s", e)
                warnings.append(f"Recommendation seeds fetch failed: {type(e).__name__}")
                status = "partial"

            # 10. Update sync cursors from last_activities
            try:
                activities = self.client.get_last_activities()
                now_utc = self.clock.now()
                with get_db_session(self.db_engine) as session:
                    if activities.movies.watched_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "movies:history",
                            activities.movies.watched_at,
                            now_utc,
                        )
                    if activities.episodes.watched_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "episodes:history",
                            activities.episodes.watched_at,
                            now_utc,
                        )
                    if activities.movies.rated_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "movies:ratings",
                            activities.movies.rated_at,
                            now_utc,
                        )
                    if activities.episodes.rated_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "episodes:ratings",
                            activities.episodes.rated_at,
                            now_utc,
                        )
                    if activities.shows.rated_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "shows:ratings",
                            activities.shows.rated_at,
                            now_utc,
                        )
                    if activities.movies.watchlisted_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "movies:watchlist",
                            activities.movies.watchlisted_at,
                            now_utc,
                        )
                    if activities.shows.watchlisted_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "shows:watchlist",
                            activities.shows.watchlisted_at,
                            now_utc,
                        )
                    if activities.episodes.paused_at or activities.movies.paused_at:
                        p_time = (
                            activities.episodes.paused_at or activities.movies.paused_at or now_utc
                        )
                        update_sync_cursor(session, account_id, "playback", p_time, now_utc)

                    acc = session.get(Account, account_id)
                    if acc:
                        acc.last_successful_sync_at = now_utc
            except Exception as e:
                logger.warning("Failed to update sync cursors: %s", e)
                warnings.append(f"Activity cursor update failed: {type(e).__name__}")
                status = "partial"

        except Exception as e:
            logger.error("Sync run %d failed: %s", run_id, e, exc_info=True)
            status = "failed"
            warnings.append(f"Fatal sync error: {str(e)}")

        finished_at = self.clock.now()

        # Update SyncRun record
        with get_db_session(self.db_engine) as session:
            sync_entry = session.get(SyncRun, run_id)
            if sync_entry:
                sync_entry.status = status
                sync_entry.finished_at = finished_at
                sync_entry.counts = {
                    "fetched": fetched_counts,
                    "inserted": inserted_counts,
                    "updated": updated_counts,
                    "deleted": deleted_counts,
                }
                sync_entry.warnings = warnings

        return SyncReport(
            run_id=run_id,
            mode=mode,
            status=status,
            fetched=fetched_counts,
            inserted=inserted_counts,
            updated=updated_counts,
            deleted=deleted_counts,
            warnings=tuple(warnings),
        )

    def _seed_tracked_shows(self, session: Session, account_id: int) -> None:
        """Seed automatic local show tracking based on watch history and watchlist."""
        now = self.clock.now()
        threshold_180d = now - timedelta(days=180)

        # 1. Shows with watch events in last 180 days
        recent_show_ids = (
            session.execute(
                select(Episode.show_id)
                .join(WatchEvent, WatchEvent.episode_id == Episode.id)
                .where(
                    WatchEvent.account_id == account_id,
                    WatchEvent.watched_at >= threshold_180d,
                )
                .distinct()
            )
            .scalars()
            .all()
        )

        for show_id in recent_show_ids:
            existing = session.get(TrackedShow, (account_id, show_id))
            if existing is None:
                tracked = TrackedShow(
                    account_id=account_id,
                    show_id=show_id,
                    status="watching",
                    status_source="auto",
                    created_at=now,
                    updated_at=now,
                )
                session.add(tracked)

        # 2. Shows in watchlist that have not been started
        watchlisted_show_ids = (
            session.execute(
                select(WatchlistItem.media_item_id).where(
                    WatchlistItem.account_id == account_id,
                    WatchlistItem.media_type == "show",
                )
            )
            .scalars()
            .all()
        )

        for show_id in watchlisted_show_ids:
            existing = session.get(TrackedShow, (account_id, show_id))
            if existing is None:
                # Check if started
                watched_count = (
                    session.execute(
                        select(WatchEvent.history_id)
                        .join(Episode, WatchEvent.episode_id == Episode.id)
                        .where(
                            WatchEvent.account_id == account_id,
                            Episode.show_id == show_id,
                        )
                    )
                    .scalars()
                    .first()
                )

                if not watched_count:
                    tracked = TrackedShow(
                        account_id=account_id,
                        show_id=show_id,
                        status="planned",
                        status_source="auto",
                        created_at=now,
                        updated_at=now,
                    )
                    session.add(tracked)

        session.flush()
