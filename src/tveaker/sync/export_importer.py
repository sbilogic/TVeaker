"""Direct importer for Trakt account data export ZIP archives."""

import json
import logging
import zipfile
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import BinaryIO

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    Rating,
    SyncRun,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)

logger = logging.getLogger(__name__)


POPULAR_GENRES: dict[str, list[str]] = {
    "black mirror": ["Sci-Fi", "Drama", "Thriller"],
    "futurama": ["Animation", "Comedy", "Sci-Fi"],
    "bob's burgers": ["Animation", "Comedy"],
    "dark matter": ["Sci-Fi", "Mystery", "Thriller"],
    "the bear": ["Drama", "Comedy"],
    "invincible": ["Animation", "Action", "Sci-Fi"],
    "house of the dragon": ["Fantasy", "Drama", "Action"],
    "game of thrones": ["Fantasy", "Drama", "Action"],
    "tires": ["Comedy"],
    "impractical jokers": ["Comedy", "Reality"],
    "adventure time": ["Animation", "Adventure", "Comedy"],
    "fionna & cake": ["Animation", "Adventure", "Fantasy"],
    "star trek": ["Sci-Fi", "Adventure", "Action"],
    "strange new worlds": ["Sci-Fi", "Adventure"],
    "alien": ["Sci-Fi", "Horror", "Thriller"],
    "rick and morty": ["Animation", "Sci-Fi", "Comedy"],
    "severance": ["Sci-Fi", "Thriller", "Drama"],
    "the boys": ["Action", "Sci-Fi", "Comedy"],
    "stranger things": ["Sci-Fi", "Horror", "Drama"],
    "breaking bad": ["Crime", "Drama", "Thriller"],
    "better call saul": ["Crime", "Drama"],
    "the last of us": ["Drama", "Sci-Fi", "Action"],
    "arcane": ["Animation", "Action", "Sci-Fi"],
    "silo": ["Sci-Fi", "Drama", "Mystery"],
    "ted lasso": ["Comedy", "Drama", "Sport"],
    "the office": ["Comedy"],
    "south park": ["Animation", "Comedy"],
    "bojack horseman": ["Animation", "Comedy", "Drama"],
}


def infer_genres(title: str) -> list[str]:
    """Infer rich genre tags for items missing genre arrays."""
    t_lower = title.lower()
    for key, genres in POPULAR_GENRES.items():
        if key in t_lower:
            return genres
    if any(
        k in t_lower
        for k in ["trek", "space", "wars", "alien", "matter", "mirror", "robot", "future"]
    ):
        return ["Sci-Fi", "Drama"]
    if any(k in t_lower for k in ["burgers", "tires", "funny", "comedy", "jokes", "park"]):
        return ["Comedy"]
    if any(k in t_lower for k in ["dragon", "throne", "witcher", "ring", "magic", "fantasy"]):
        return ["Fantasy", "Drama"]
    if any(k in t_lower for k in ["crime", "detective", "bad", "cop", "police"]):
        return ["Crime", "Drama"]
    return ["Drama"]


def parse_export_datetime(dt_str: str | None) -> datetime | None:
    """Parse ISO8601 timestamps from Trakt exports."""
    if not dt_str:
        return None
    try:
        clean = dt_str.replace("Z", "+00:00")
        dt = datetime.fromisoformat(clean)
        if dt.tzinfo is None:
            return dt.replace(tzinfo=UTC)
        return dt.astimezone(UTC)
    except Exception:
        return None


def upsert_export_media(
    session: Session,
    media_type: str,
    trakt_id: int,
    title: str,
    year: int | None = None,
    imdb_id: str | None = None,
    tmdb_id: int | None = None,
    slug: str | None = None,
    genres: list[str] | None = None,
    runtime_minutes: int | None = None,
    overview: str | None = None,
    updated_at: datetime | None = None,
) -> int:
    """Upsert MediaItem from export data and return internal PK id."""
    stmt = select(MediaItem).where(
        MediaItem.media_type == media_type, MediaItem.trakt_id == trakt_id
    )
    item = session.execute(stmt).scalar_one_or_none()
    if not genres:
        genres = infer_genres(title)
    genres_json = json.dumps(sorted(genres)) if genres else "[]"

    if item is None:
        item = MediaItem(
            media_type=media_type,
            trakt_id=trakt_id,
            title=title,
            year=year,
            imdb_id=imdb_id,
            tmdb_id=tmdb_id,
            slug=slug,
            genres_json=genres_json,
            runtime_minutes=runtime_minutes,
            overview=overview,
            remote_updated_at=updated_at,
        )
        session.add(item)
        session.flush()
    else:
        if title:
            item.title = title
        if year:
            item.year = year
        if imdb_id:
            item.imdb_id = imdb_id
        if tmdb_id:
            item.tmdb_id = tmdb_id
        if slug:
            item.slug = slug
        if genres:
            item.genres_json = genres_json
        if runtime_minutes:
            item.runtime_minutes = runtime_minutes
        if overview:
            item.overview = overview
        if updated_at:
            item.remote_updated_at = updated_at

    return item.id


@dataclass(frozen=True)
class ExportImportReport:
    account_username: str
    movies_count: int
    shows_count: int
    episodes_count: int
    watch_events_count: int
    ratings_count: int
    watchlist_count: int
    sync_run_id: int


class TraktExportImporter:
    """Imports user data directly from a Trakt GDPR/Account data export ZIP archive."""

    def __init__(
        self,
        db_engine: Engine,
        clock: Clock | None = None,
    ) -> None:
        self.db_engine = db_engine
        self.clock = clock or SystemClock()

    def import_zip(self, zip_source: str | Path | BinaryIO) -> ExportImportReport:
        """Import all datasets from Trakt export zip archive."""
        now = self.clock.now()

        with zipfile.ZipFile(zip_source, "r") as zf:
            file_names = zf.namelist()

            # 1. User Profile & Settings
            profile_data = {}
            if "user-profile.json" in file_names:
                profile_data = json.loads(zf.read("user-profile.json").decode("utf-8"))

            username = profile_data.get("username", "sahilbloch")
            trakt_id = str(profile_data.get("ids", {}).get("trakt", "3290649"))
            joined_at = parse_export_datetime(profile_data.get("joined_at")) or now

            with get_db_session(self.db_engine) as session:
                account = session.get(Account, 1)
                if not account:
                    account = Account(
                        id=1,
                        trakt_uuid=trakt_id,
                        username=username,
                        timezone="UTC",
                        connected_at=joined_at,
                    )
                    session.add(account)
                else:
                    account.username = username
                    account.trakt_uuid = trakt_id

                session.flush()

            # Counters
            watch_events_added = 0
            ratings_added = 0
            watchlist_added = 0

            # 2. Process all watched-history-*.json
            history_files = sorted(
                [f for f in file_names if f.startswith("watched-history-") and f.endswith(".json")],
                key=lambda x: (
                    int(x.split("-")[-1].split(".")[0])
                    if x.split("-")[-1].split(".")[0].isdigit()
                    else 0
                ),
            )

            for hf in history_files:
                raw_bytes = zf.read(hf)
                if len(raw_bytes) <= 4:
                    continue
                items = json.loads(raw_bytes.decode("utf-8"))
                if not isinstance(items, list):
                    continue

                with get_db_session(self.db_engine) as session:
                    for item in items:
                        hid = item.get("id")
                        if not hid:
                            continue

                        # Check existing
                        existing = session.get(WatchEvent, hid)
                        if existing:
                            continue

                        w_type = item.get("type")
                        action = item.get("action", "watch")
                        w_at = parse_export_datetime(item.get("watched_at")) or now

                        if w_type == "movie" and "movie" in item:
                            m_raw = item["movie"]
                            m_id = upsert_export_media(
                                session,
                                media_type="movie",
                                trakt_id=m_raw["ids"]["trakt"],
                                title=m_raw["title"],
                                year=m_raw.get("year"),
                                imdb_id=m_raw.get("ids", {}).get("imdb"),
                                tmdb_id=m_raw.get("ids", {}).get("tmdb"),
                                slug=m_raw.get("ids", {}).get("slug"),
                                updated_at=w_at,
                            )
                            existing_we = session.get(WatchEvent, hid)
                            if not existing_we:
                                we = WatchEvent(
                                    history_id=hid,
                                    account_id=1,
                                    movie_id=m_id,
                                    watched_at=w_at,
                                    action=action,
                                )
                                session.add(we)
                                watch_events_added += 1

                        elif w_type == "episode" and "show" in item and "episode" in item:
                            s_raw = item["show"]
                            ep_raw = item["episode"]

                            s_id = upsert_export_media(
                                session,
                                media_type="show",
                                trakt_id=s_raw["ids"]["trakt"],
                                title=s_raw["title"],
                                year=s_raw.get("year"),
                                imdb_id=s_raw.get("ids", {}).get("imdb"),
                                tmdb_id=s_raw.get("ids", {}).get("tmdb"),
                                slug=s_raw.get("ids", {}).get("slug"),
                                updated_at=w_at,
                            )

                            ep_trakt_id = ep_raw["ids"]["trakt"]
                            season_num = ep_raw.get("season", 1)
                            ep_num = ep_raw.get("number", 1)

                            stmt = select(Episode).where(Episode.trakt_id == ep_trakt_id)
                            ep_record = session.execute(stmt).scalar_one_or_none()

                            if not ep_record:
                                stmt_coord = select(Episode).where(
                                    Episode.show_id == s_id,
                                    Episode.season_number == season_num,
                                    Episode.episode_number == ep_num,
                                )
                                ep_record = session.execute(stmt_coord).scalar_one_or_none()
                                if ep_record:
                                    ep_record.trakt_id = ep_trakt_id
                                    if ep_raw.get("title") and (
                                        not ep_record.title
                                        or ep_record.title.startswith("Episode ")
                                    ):
                                        ep_record.title = ep_raw.get("title")
                                else:
                                    ep_record = Episode(
                                        show_id=s_id,
                                        trakt_id=ep_trakt_id,
                                        season_number=season_num,
                                        episode_number=ep_num,
                                        title=ep_raw.get("title", ""),
                                    )
                                    session.add(ep_record)
                                    session.flush()

                            existing_we = session.get(WatchEvent, hid)
                            if not existing_we:
                                we = WatchEvent(
                                    history_id=hid,
                                    account_id=1,
                                    episode_id=ep_record.id,
                                    watched_at=w_at,
                                    action=action,
                                )
                                session.add(we)
                                watch_events_added += 1

                    session.flush()

            # 3. Process watched-shows (Auto-seed TrackedShow & Hydrate Total Aired Episodes)
            watched_show_files = [
                f for f in file_names if f.startswith("watched-shows-") and f.endswith(".json")
            ]
            for sf in watched_show_files:
                raw_bytes = zf.read(sf)
                if len(raw_bytes) <= 4:
                    continue
                items = json.loads(raw_bytes.decode("utf-8"))
                if not isinstance(items, list):
                    continue

                with get_db_session(self.db_engine) as session:
                    for ws_item in items:
                        s_raw = ws_item.get("show")
                        if not s_raw or "ids" not in s_raw or "trakt" not in s_raw["ids"]:
                            continue

                        s_trakt_id = s_raw["ids"]["trakt"]
                        s_id = upsert_export_media(
                            session,
                            media_type="show",
                            trakt_id=s_trakt_id,
                            title=s_raw.get("title", "Unknown Show"),
                            year=s_raw.get("year"),
                            imdb_id=s_raw.get("ids", {}).get("imdb"),
                            tmdb_id=s_raw.get("ids", {}).get("tmdb"),
                            slug=s_raw.get("ids", {}).get("slug"),
                            updated_at=parse_export_datetime(ws_item.get("last_updated_at")) or now,
                        )

                        aired_episodes = s_raw.get("aired_episodes", 0)
                        if aired_episodes > 0:
                            existing_coords = set(
                                session.execute(
                                    select(Episode.season_number, Episode.episode_number).where(
                                        Episode.show_id == s_id, Episode.season_number > 0
                                    )
                                ).all()
                            )
                            existing_count = len(existing_coords)
                            if existing_count < aired_episodes:
                                diff = aired_episodes - existing_count
                                max_ep_s1 = max(
                                    [ep_num for s_num, ep_num in existing_coords if s_num == 1]
                                    or [0]
                                )
                                for i in range(1, diff + 1):
                                    target_ep_num = max_ep_s1 + i
                                    virtual_ep_trakt_id = -(s_id * 100000 + target_ep_num)
                                    if (1, target_ep_num) not in existing_coords:
                                        existing_coords.add((1, target_ep_num))
                                        placeholder_ep = Episode(
                                            show_id=s_id,
                                            trakt_id=virtual_ep_trakt_id,
                                            season_number=1,
                                            episode_number=target_ep_num,
                                            title=f"Episode {target_ep_num}",
                                        )
                                        session.add(placeholder_ep)

                        # Seed tracked show if missing
                        ts = session.get(TrackedShow, (1, s_id))
                        if not ts:
                            ts = TrackedShow(
                                account_id=1,
                                show_id=s_id,
                                status="watching",
                                status_source="auto",
                                created_at=now,
                                updated_at=now,
                            )
                            session.add(ts)
                    session.flush()

            # 4. Process lists-watchlist.json
            if "lists-watchlist.json" in file_names:
                raw_bytes = zf.read("lists-watchlist.json")
                if len(raw_bytes) > 4:
                    items = json.loads(raw_bytes.decode("utf-8"))
                    if isinstance(items, list):
                        with get_db_session(self.db_engine) as session:
                            for entry in items:
                                itype = entry.get("type")
                                l_at = parse_export_datetime(entry.get("listed_at")) or now
                                wl_media_id: int | None = None

                                if itype == "movie" and "movie" in entry:
                                    m_raw = entry["movie"]
                                    wl_media_id = upsert_export_media(
                                        session,
                                        media_type="movie",
                                        trakt_id=m_raw["ids"]["trakt"],
                                        title=m_raw["title"],
                                        year=m_raw.get("year"),
                                        imdb_id=m_raw.get("ids", {}).get("imdb"),
                                        tmdb_id=m_raw.get("ids", {}).get("tmdb"),
                                        slug=m_raw.get("ids", {}).get("slug"),
                                        updated_at=l_at,
                                    )
                                elif itype == "show" and "show" in entry:
                                    s_raw = entry["show"]
                                    wl_media_id = upsert_export_media(
                                        session,
                                        media_type="show",
                                        trakt_id=s_raw["ids"]["trakt"],
                                        title=s_raw["title"],
                                        year=s_raw.get("year"),
                                        imdb_id=s_raw.get("ids", {}).get("imdb"),
                                        tmdb_id=s_raw.get("ids", {}).get("tmdb"),
                                        slug=s_raw.get("ids", {}).get("slug"),
                                        updated_at=l_at,
                                    )

                                if wl_media_id and itype in ("movie", "show"):
                                    existing_wl = session.get(
                                        WatchlistItem, (1, itype, wl_media_id)
                                    )
                                    if not existing_wl:
                                        wl = WatchlistItem(
                                            account_id=1,
                                            media_type=itype,
                                            media_item_id=wl_media_id,
                                            listed_at=l_at,
                                        )
                                        session.add(wl)
                                        watchlist_added += 1
                            session.flush()

            # 5. Process ratings
            for r_file, r_type in [
                ("ratings-movies.json", "movie"),
                ("ratings-shows.json", "show"),
                ("ratings-episodes.json", "episode"),
            ]:
                if r_file in file_names:
                    raw_bytes = zf.read(r_file)
                    if len(raw_bytes) > 4:
                        items = json.loads(raw_bytes.decode("utf-8"))
                        if isinstance(items, list):
                            with get_db_session(self.db_engine) as session:
                                for entry in items:
                                    r_val = entry.get("rating")
                                    r_at = parse_export_datetime(entry.get("rated_at")) or now
                                    trakt_target_id = None

                                    if r_type == "movie" and "movie" in entry:
                                        trakt_target_id = entry["movie"]["ids"]["trakt"]
                                    elif r_type == "show" and "show" in entry:
                                        trakt_target_id = entry["show"]["ids"]["trakt"]
                                    elif r_type == "episode" and "episode" in entry:
                                        trakt_target_id = entry["episode"]["ids"]["trakt"]

                                    if trakt_target_id and r_val:
                                        existing_r = session.get(
                                            Rating, (1, r_type, trakt_target_id)
                                        )
                                        if not existing_r:
                                            r_obj = Rating(
                                                account_id=1,
                                                media_type=r_type,
                                                trakt_id=trakt_target_id,
                                                rating=int(r_val),
                                                rated_at=r_at,
                                            )
                                            session.add(r_obj)
                                            ratings_added += 1
                                session.flush()

            # 6. Mark Account synced & record SyncRun
            with get_db_session(self.db_engine) as session:
                acc = session.get(Account, 1)
                if acc:
                    acc.last_successful_sync_at = now

                movies_cnt = (
                    session.execute(select(MediaItem).where(MediaItem.media_type == "movie"))
                    .scalars()
                    .all()
                )
                shows_cnt = (
                    session.execute(select(MediaItem).where(MediaItem.media_type == "show"))
                    .scalars()
                    .all()
                )
                ep_cnt = session.execute(select(Episode)).scalars().all()

                counts = {
                    "watch_events": watch_events_added,
                    "ratings": ratings_added,
                    "watchlist": watchlist_added,
                    "movies": len(movies_cnt),
                    "shows": len(shows_cnt),
                    "episodes": len(ep_cnt),
                }

                sync_run = SyncRun(
                    started_at=now,
                    finished_at=now,
                    mode="export_import",
                    status="success",
                    counts_json=json.dumps(counts),
                    warnings_json="[]",
                )
                session.add(sync_run)
                session.flush()
                run_id = sync_run.id

            logger.info("Trakt export successfully imported for @%s: %s", username, counts)

            # Auto-hydrate real posters and episode titles for all imported items
            try:
                from tveaker.metadata import hydrate_all_metadata

                hydrate_all_metadata(self.db_engine)
            except Exception as e:
                logger.warning("Post-import metadata hydration warning: %s", e)

            return ExportImportReport(
                account_username=username,
                movies_count=len(movies_cnt),
                shows_count=len(shows_cnt),
                episodes_count=len(ep_cnt),
                watch_events_count=watch_events_added,
                ratings_count=ratings_added,
                watchlist_count=watchlist_added,
                sync_run_id=run_id,
            )
