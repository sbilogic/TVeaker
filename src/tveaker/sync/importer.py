"""Account synchronization engine and snapshot importer."""

import logging
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Literal

from sqlalchemy import Engine, func, select
from sqlalchemy.orm import Session

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    SyncCursor,
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


def _ensure_utc(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def _is_newer(remote_dt: datetime | None, local_dt: datetime | None) -> bool:
    if remote_dt is None:
        return False
    if local_dt is None:
        return True
    rem_utc = _ensure_utc(remote_dt)
    loc_utc = _ensure_utc(local_dt)
    return rem_utc is not None and loc_utc is not None and rem_utc > loc_utc


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
        """Execute a synchronization run according to the specified mode."""
        if mode == "incremental":
            return self._run_incremental()
        elif mode == "full":
            return self._run_full()
        else:
            return self._run_initial()

    def _run_initial(self) -> SyncReport:
        """Execute full initial import."""
        started_at = self.clock.now()
        warnings: list[str] = []
        fetched_counts: dict[str, int] = {}
        inserted_counts: dict[str, int] = {}
        updated_counts: dict[str, int] = {}
        deleted_counts: dict[str, int] = {}

        with get_db_session(self.db_engine) as session:
            sync_run = SyncRun(
                mode="initial",
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
            settings = self.client.get_user_settings()
            with get_db_session(self.db_engine) as session:
                account = upsert_account(session, settings)
                account_id = account.id

            # History
            history_items = list(self.client.drain_history())
            fetched_counts["history"] = len(history_items)
            with get_db_session(self.db_engine) as session:
                ins, upd = upsert_watch_events(session, account_id, history_items)
                inserted_counts["history"] = ins
                updated_counts["history"] = upd

            # Ratings
            movie_ratings = self.client.get_ratings("movies")
            show_ratings = self.client.get_ratings("shows")
            ep_ratings = self.client.get_ratings("episodes")
            all_ratings = movie_ratings + show_ratings + ep_ratings
            fetched_counts["ratings"] = len(all_ratings)
            with get_db_session(self.db_engine) as session:
                ins = replace_ratings_snapshot(session, account_id, all_ratings)
                inserted_counts["ratings"] = ins

            # Watchlist
            wl_movies = self.client.get_watchlist("movies")
            wl_shows = self.client.get_watchlist("shows")
            fetched_counts["watchlist"] = len(wl_movies) + len(wl_shows)
            with get_db_session(self.db_engine) as session:
                ins = replace_watchlist_snapshot(session, account_id, wl_movies, wl_shows)
                inserted_counts["watchlist"] = ins

            # Playback
            pb_movies = self.client.get_playback("movies")
            pb_episodes = self.client.get_playback("episodes")
            fetched_counts["playback"] = len(pb_movies) + len(pb_episodes)
            with get_db_session(self.db_engine) as session:
                ins = replace_playback_snapshot(session, account_id, pb_movies, pb_episodes)
                inserted_counts["playback"] = ins

            # Catalogs
            self._hydrate_show_catalogs(account_id, inserted_counts, updated_counts, warnings)

            # Seed tracked shows
            with get_db_session(self.db_engine) as session:
                self._seed_tracked_shows(session, account_id)

            # Recommendation seeds
            self._fetch_recommendation_seeds(warnings)

            # Update cursors
            self._update_all_cursors(account_id, warnings)

        except Exception as e:
            logger.error("Initial sync run %d failed: %s", run_id, e, exc_info=True)
            status = "failed"
            warnings.append(f"Fatal sync error: {str(e)}")

        finished_at = self.clock.now()
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
            mode="initial",
            status=status,
            fetched=fetched_counts,
            inserted=inserted_counts,
            updated=updated_counts,
            deleted=deleted_counts,
            warnings=tuple(warnings),
        )

    def _run_incremental(self) -> SyncReport:
        """Execute 15-minute incremental sync checking activity timestamps."""
        started_at = self.clock.now()
        warnings: list[str] = []
        fetched_counts: dict[str, int] = {}
        inserted_counts: dict[str, int] = {}
        updated_counts: dict[str, int] = {}
        deleted_counts: dict[str, int] = {}

        with get_db_session(self.db_engine) as session:
            account = session.get(Account, 1)
            if account is None:
                return self._run_initial()
            account_id = account.id

            sync_run = SyncRun(
                mode="incremental",
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
            activities = self.client.get_last_activities()
            now_utc = self.clock.now()

            # Load existing cursors
            with get_db_session(self.db_engine) as session:
                cursor_rows = (
                    session.execute(select(SyncCursor).where(SyncCursor.account_id == account_id))
                    .scalars()
                    .all()
                )
                cursors = {c.dataset: c.remote_activity_at for c in cursor_rows}

            # 1. History changes (Movies or Episodes)
            history_changed = _is_newer(
                activities.movies.watched_at, cursors.get("movies:history")
            ) or _is_newer(activities.episodes.watched_at, cursors.get("episodes:history"))

            if history_changed:
                with get_db_session(self.db_engine) as session:
                    latest_watch = session.execute(
                        select(func.max(WatchEvent.watched_at)).where(
                            WatchEvent.account_id == account_id
                        )
                    ).scalar()

                if latest_watch:
                    latest_watch_utc = _ensure_utc(latest_watch)
                    start_at = latest_watch_utc - timedelta(days=7) if latest_watch_utc else None
                else:
                    start_at = None

                items = list(self.client.drain_history(start_at=start_at))
                fetched_counts["history"] = len(items)
                with get_db_session(self.db_engine) as session:
                    ins, upd = upsert_watch_events(session, account_id, items)
                    inserted_counts["history"] = ins
                    updated_counts["history"] = upd
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

            # 2. Ratings changes
            ratings_changed = (
                _is_newer(activities.movies.rated_at, cursors.get("movies:ratings"))
                or _is_newer(activities.shows.rated_at, cursors.get("shows:ratings"))
                or _is_newer(activities.episodes.rated_at, cursors.get("episodes:ratings"))
            )

            if ratings_changed:
                movie_ratings = self.client.get_ratings("movies")
                show_ratings = self.client.get_ratings("shows")
                ep_ratings = self.client.get_ratings("episodes")
                all_ratings = movie_ratings + show_ratings + ep_ratings
                fetched_counts["ratings"] = len(all_ratings)
                with get_db_session(self.db_engine) as session:
                    ins = replace_ratings_snapshot(session, account_id, all_ratings)
                    inserted_counts["ratings"] = ins
                    if activities.movies.rated_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "movies:ratings",
                            activities.movies.rated_at,
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
                    if activities.episodes.rated_at:
                        update_sync_cursor(
                            session,
                            account_id,
                            "episodes:ratings",
                            activities.episodes.rated_at,
                            now_utc,
                        )

            # 3. Watchlist changes
            wl_changed = _is_newer(
                activities.movies.watchlisted_at, cursors.get("movies:watchlist")
            ) or _is_newer(activities.shows.watchlisted_at, cursors.get("shows:watchlist"))

            if wl_changed:
                wl_movies = self.client.get_watchlist("movies")
                wl_shows = self.client.get_watchlist("shows")
                fetched_counts["watchlist"] = len(wl_movies) + len(wl_shows)
                with get_db_session(self.db_engine) as session:
                    ins = replace_watchlist_snapshot(session, account_id, wl_movies, wl_shows)
                    inserted_counts["watchlist"] = ins
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

            # 4. Playback changes
            pb_time = activities.episodes.paused_at or activities.movies.paused_at
            if pb_time and _is_newer(pb_time, cursors.get("playback")):
                pb_movies = self.client.get_playback("movies")
                pb_episodes = self.client.get_playback("episodes")
                fetched_counts["playback"] = len(pb_movies) + len(pb_episodes)
                with get_db_session(self.db_engine) as session:
                    ins = replace_playback_snapshot(session, account_id, pb_movies, pb_episodes)
                    inserted_counts["playback"] = ins
                    update_sync_cursor(session, account_id, "playback", pb_time, now_utc)

            # 5. Hydrate any new shows & seed local tracking
            self._hydrate_show_catalogs(account_id, inserted_counts, updated_counts, warnings)
            with get_db_session(self.db_engine) as session:
                self._seed_tracked_shows(session, account_id)
                acc = session.get(Account, account_id)
                if acc:
                    acc.last_successful_sync_at = now_utc

        except Exception as e:
            logger.error("Incremental sync run %d failed: %s", run_id, e, exc_info=True)
            status = "failed"
            warnings.append(f"Fatal incremental sync error: {str(e)}")

        finished_at = self.clock.now()
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
            mode="incremental",
            status=status,
            fetched=fetched_counts,
            inserted=inserted_counts,
            updated=updated_counts,
            deleted=deleted_counts,
            warnings=tuple(warnings),
        )

    def _run_full(self) -> SyncReport:
        """Execute 7-day full reconciliation: drains all remote data and reconciles deletions."""
        started_at = self.clock.now()
        warnings: list[str] = []
        fetched_counts: dict[str, int] = {}
        inserted_counts: dict[str, int] = {}
        updated_counts: dict[str, int] = {}
        deleted_counts: dict[str, int] = {}

        with get_db_session(self.db_engine) as session:
            account = session.get(Account, 1)
            if account is None:
                return self._run_initial()
            account_id = account.id

            sync_run = SyncRun(
                mode="full",
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
            # Full history drain & deletion reconciliation
            remote_history = list(self.client.drain_history())
            remote_history_ids = {h.id for h in remote_history}
            fetched_counts["history"] = len(remote_history)

            with get_db_session(self.db_engine) as session:
                ins, upd = upsert_watch_events(session, account_id, remote_history)
                inserted_counts["history"] = ins
                updated_counts["history"] = upd

                # Delete local history rows no longer present on Trakt
                local_events = (
                    session.execute(select(WatchEvent).where(WatchEvent.account_id == account_id))
                    .scalars()
                    .all()
                )
                deleted_events = 0
                for ev in local_events:
                    if ev.history_id not in remote_history_ids:
                        session.delete(ev)
                        deleted_events += 1
                deleted_counts["history"] = deleted_events

            # Full snapshots for ratings, watchlist, playback
            movie_ratings = self.client.get_ratings("movies")
            show_ratings = self.client.get_ratings("shows")
            ep_ratings = self.client.get_ratings("episodes")
            all_ratings = movie_ratings + show_ratings + ep_ratings
            fetched_counts["ratings"] = len(all_ratings)
            with get_db_session(self.db_engine) as session:
                ins = replace_ratings_snapshot(session, account_id, all_ratings)
                inserted_counts["ratings"] = ins

            wl_movies = self.client.get_watchlist("movies")
            wl_shows = self.client.get_watchlist("shows")
            fetched_counts["watchlist"] = len(wl_movies) + len(wl_shows)
            with get_db_session(self.db_engine) as session:
                ins = replace_watchlist_snapshot(session, account_id, wl_movies, wl_shows)
                inserted_counts["watchlist"] = ins

            pb_movies = self.client.get_playback("movies")
            pb_episodes = self.client.get_playback("episodes")
            fetched_counts["playback"] = len(pb_movies) + len(pb_episodes)
            with get_db_session(self.db_engine) as session:
                ins = replace_playback_snapshot(session, account_id, pb_movies, pb_episodes)
                inserted_counts["playback"] = ins

            # Hydrate catalogs
            self._hydrate_show_catalogs(account_id, inserted_counts, updated_counts, warnings)

            # Seed / reconcile tracked shows (preserves manual statuses and custom pace)
            with get_db_session(self.db_engine) as session:
                self._seed_tracked_shows(session, account_id)

            # Update all cursors
            self._update_all_cursors(account_id, warnings)

        except Exception as e:
            logger.error("Full reconciliation run %d failed: %s", run_id, e, exc_info=True)
            status = "failed"
            warnings.append(f"Fatal full reconciliation error: {str(e)}")

        finished_at = self.clock.now()
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
            mode="full",
            status=status,
            fetched=fetched_counts,
            inserted=inserted_counts,
            updated=updated_counts,
            deleted=deleted_counts,
            warnings=tuple(warnings),
        )

    def _hydrate_show_catalogs(
        self,
        account_id: int,
        inserted_counts: dict[str, int],
        updated_counts: dict[str, int],
        warnings: list[str],
    ) -> None:
        """Hydrate seasons and episodes for shows present in the database."""
        show_trakt_ids: set[int] = set()
        with get_db_session(self.db_engine) as session:
            shows = (
                session.execute(select(MediaItem.trakt_id).where(MediaItem.media_type == "show"))
                .scalars()
                .all()
            )
            show_trakt_ids.update(shows)

        cat_ins = 0
        cat_upd = 0
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
                        cat_ins += ins
                        cat_upd += upd
            except Exception as e:
                logger.warning("Failed to fetch season catalog for show %d: %s", trakt_id, e)
                warnings.append(f"Catalog error for show {trakt_id}: {type(e).__name__}")

        inserted_counts["catalog_episodes"] = inserted_counts.get("catalog_episodes", 0) + cat_ins
        updated_counts["catalog_episodes"] = updated_counts.get("catalog_episodes", 0) + cat_upd

    def _fetch_recommendation_seeds(self, warnings: list[str]) -> None:
        """Fetch optional movie and show recommendation seeds."""
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

    def _update_all_cursors(self, account_id: int, warnings: list[str]) -> None:
        """Fetch latest activities and advance all cursors upon committed success."""
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
                    p_time = activities.episodes.paused_at or activities.movies.paused_at or now_utc
                    update_sync_cursor(session, account_id, "playback", p_time, now_utc)

                acc = session.get(Account, account_id)
                if acc:
                    acc.last_successful_sync_at = now_utc
        except Exception as e:
            logger.warning("Failed to update sync cursors: %s", e)
            warnings.append(f"Activity cursor update failed: {type(e).__name__}")

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
