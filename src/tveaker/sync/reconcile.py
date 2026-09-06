"""Reconciliation helpers for database upserts and snapshot replacement."""

import json
import logging
from datetime import UTC, datetime

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    PlaybackState,
    Rating,
    SyncCursor,
    WatchEvent,
    WatchlistItem,
)
from tveaker.trakt.schemas import (
    TraktEpisode,
    TraktHistoryItem,
    TraktMovie,
    TraktPlaybackItem,
    TraktRatingItem,
    TraktSeason,
    TraktShow,
    TraktUserSettings,
    TraktWatchlistItem,
)

logger = logging.getLogger(__name__)


def upsert_account(session: Session, settings: TraktUserSettings) -> Account:
    """Upsert the single local account row."""
    account = session.get(Account, 1)
    if account is None:
        account = Account(
            id=1,
            trakt_uuid=settings.user.ids.uuid,
            username=settings.user.username,
            timezone=settings.account.timezone or "UTC",
            connected_at=datetime.now(UTC),
        )
        session.add(account)
    else:
        account.username = settings.user.username
        account.timezone = settings.account.timezone or "UTC"
        account.trakt_uuid = settings.user.ids.uuid
    session.flush()
    return account


def upsert_media_movie(session: Session, movie: TraktMovie) -> MediaItem:
    """Upsert a movie MediaItem."""
    stmt = select(MediaItem).where(
        MediaItem.media_type == "movie",
        MediaItem.trakt_id == movie.ids.trakt,
    )
    item = session.execute(stmt).scalar_one_or_none()
    if item is None:
        item = MediaItem(
            media_type="movie",
            trakt_id=movie.ids.trakt,
            slug=movie.ids.slug,
            imdb_id=movie.ids.imdb,
            tmdb_id=movie.ids.tmdb,
            title=movie.title,
            year=movie.year,
            overview=movie.overview,
            runtime_minutes=movie.runtime,
            genres_json=json.dumps(sorted(movie.genres)),
            remote_updated_at=movie.updated_at,
        )
        session.add(item)
    else:
        item.title = movie.title
        item.year = movie.year or item.year
        item.overview = movie.overview or item.overview
        item.runtime_minutes = movie.runtime or item.runtime_minutes
        if movie.genres:
            item.genres_json = json.dumps(sorted(movie.genres))
        item.remote_updated_at = movie.updated_at or item.remote_updated_at
    session.flush()
    return item


def upsert_media_show(session: Session, show: TraktShow) -> MediaItem:
    """Upsert a show MediaItem."""
    stmt = select(MediaItem).where(
        MediaItem.media_type == "show",
        MediaItem.trakt_id == show.ids.trakt,
    )
    item = session.execute(stmt).scalar_one_or_none()
    if item is None:
        item = MediaItem(
            media_type="show",
            trakt_id=show.ids.trakt,
            slug=show.ids.slug,
            imdb_id=show.ids.imdb,
            tmdb_id=show.ids.tmdb,
            title=show.title,
            year=show.year,
            overview=show.overview,
            runtime_minutes=show.runtime,
            status=show.status,
            genres_json=json.dumps(sorted(show.genres)),
            first_aired=show.first_aired,
            remote_updated_at=show.updated_at,
        )
        session.add(item)
    else:
        item.title = show.title
        item.year = show.year or item.year
        item.overview = show.overview or item.overview
        item.runtime_minutes = show.runtime or item.runtime_minutes
        item.status = show.status or item.status
        if show.genres:
            item.genres_json = json.dumps(sorted(show.genres))
        item.first_aired = show.first_aired or item.first_aired
        item.remote_updated_at = show.updated_at or item.remote_updated_at
    session.flush()
    return item


def upsert_episode(session: Session, show_id: int, episode: TraktEpisode) -> Episode:
    """Upsert an Episode row."""
    stmt = select(Episode).where(Episode.trakt_id == episode.ids.trakt)
    ep = session.execute(stmt).scalar_one_or_none()
    if ep is None:
        legacy = session.execute(
            select(Episode).where(
                Episode.show_id == show_id,
                Episode.season_number == episode.season,
                Episode.episode_number == episode.number,
                Episode.trakt_id < 0,
            )
        ).scalar_one_or_none()
        if legacy is not None:
            # Retain any local watch event attached to an old metadata-only row
            # when Trakt later supplies the authoritative episode identifier.
            ep = legacy
            ep.trakt_id = episode.ids.trakt
            ep.title = episode.title or ep.title
            ep.overview = episode.overview or ep.overview
            ep.runtime_minutes = episode.runtime or ep.runtime_minutes
            ep.first_aired = episode.first_aired or ep.first_aired
            ep.remote_updated_at = episode.updated_at or ep.remote_updated_at
        else:
            ep = Episode(
                show_id=show_id,
                trakt_id=episode.ids.trakt,
                season_number=episode.season,
                episode_number=episode.number,
                title=episode.title,
                overview=episode.overview,
                runtime_minutes=episode.runtime,
                first_aired=episode.first_aired,
                remote_updated_at=episode.updated_at,
            )
            session.add(ep)
    else:
        ep.title = episode.title or ep.title
        ep.overview = episode.overview or ep.overview
        ep.runtime_minutes = episode.runtime or ep.runtime_minutes
        ep.first_aired = episode.first_aired or ep.first_aired
        ep.remote_updated_at = episode.updated_at or ep.remote_updated_at
    session.flush()
    return ep


def upsert_season_episodes(
    session: Session, show_id: int, seasons: list[TraktSeason]
) -> tuple[int, int]:
    """Upsert all episodes in a show's season catalog."""
    inserted = 0
    updated = 0
    for season in seasons:
        for ep_schema in season.episodes:
            stmt = select(Episode).where(Episode.trakt_id == ep_schema.ids.trakt)
            existing = session.execute(stmt).scalar_one_or_none()
            if existing is None:
                upsert_episode(session, show_id, ep_schema)
                inserted += 1
            else:
                upsert_episode(session, show_id, ep_schema)
                updated += 1
    return inserted, updated


def upsert_watch_events(
    session: Session, account_id: int, items: list[TraktHistoryItem]
) -> tuple[int, int]:
    """Upsert watch history events and dependent media items."""
    inserted = 0
    updated = 0

    for item in items:
        movie_id = None
        episode_id = None

        if item.type == "movie" and item.movie:
            media = upsert_media_movie(session, item.movie)
            movie_id = media.id
        elif item.type == "episode" and item.episode and item.show:
            show = upsert_media_show(session, item.show)
            ep = upsert_episode(session, show.id, item.episode)
            episode_id = ep.id
        else:
            continue

        existing = session.get(WatchEvent, item.id)
        if existing is None:
            we = WatchEvent(
                history_id=item.id,
                account_id=account_id,
                watched_at=item.watched_at,
                action=item.action,
                movie_id=movie_id,
                episode_id=episode_id,
            )
            session.add(we)
            inserted += 1
        else:
            existing.watched_at = item.watched_at
            existing.action = item.action
            existing.movie_id = movie_id
            existing.episode_id = episode_id
            updated += 1

    session.flush()
    return inserted, updated


def replace_playback_snapshot(
    session: Session,
    account_id: int,
    movies: list[TraktPlaybackItem],
    episodes: list[TraktPlaybackItem],
) -> int:
    """Replace all playback state rows for the account."""
    session.execute(delete(PlaybackState).where(PlaybackState.account_id == account_id))

    count = 0
    for item in movies:
        if item.movie:
            movie = upsert_media_movie(session, item.movie)
            pb = PlaybackState(
                playback_id=item.id,
                account_id=account_id,
                progress_percent=item.progress,
                paused_at=item.paused_at,
                movie_id=movie.id,
                episode_id=None,
            )
            session.add(pb)
            count += 1

    for item in episodes:
        if item.episode and item.show:
            show = upsert_media_show(session, item.show)
            ep = upsert_episode(session, show.id, item.episode)
            pb = PlaybackState(
                playback_id=item.id,
                account_id=account_id,
                progress_percent=item.progress,
                paused_at=item.paused_at,
                movie_id=None,
                episode_id=ep.id,
            )
            session.add(pb)
            count += 1

    session.flush()
    return count


def replace_ratings_snapshot(
    session: Session,
    account_id: int,
    ratings: list[TraktRatingItem],
) -> int:
    """Replace ratings snapshot for the account."""
    session.execute(delete(Rating).where(Rating.account_id == account_id))

    count = 0
    for r in ratings:
        if r.type == "movie" and r.movie:
            upsert_media_movie(session, r.movie)
            trakt_id = r.movie.ids.trakt
        elif r.type == "show" and r.show:
            upsert_media_show(session, r.show)
            trakt_id = r.show.ids.trakt
        elif r.type == "episode" and r.episode:
            if r.show:
                show = upsert_media_show(session, r.show)
                upsert_episode(session, show.id, r.episode)
            trakt_id = r.episode.ids.trakt
        else:
            continue

        rating_row = Rating(
            account_id=account_id,
            media_type=r.type,
            trakt_id=trakt_id,
            rating=r.rating,
            rated_at=r.rated_at,
        )
        session.add(rating_row)
        count += 1

    session.flush()
    return count


def replace_watchlist_snapshot(
    session: Session,
    account_id: int,
    movies: list[TraktWatchlistItem],
    shows: list[TraktWatchlistItem],
) -> int:
    """Replace watchlist snapshot for the account."""
    session.execute(delete(WatchlistItem).where(WatchlistItem.account_id == account_id))

    count = 0
    for item in movies:
        if item.movie:
            m = upsert_media_movie(session, item.movie)
            wl = WatchlistItem(
                account_id=account_id,
                media_type="movie",
                media_item_id=m.id,
                listed_at=item.listed_at,
            )
            session.add(wl)
            count += 1

    for item in shows:
        if item.show:
            s = upsert_media_show(session, item.show)
            wl = WatchlistItem(
                account_id=account_id,
                media_type="show",
                media_item_id=s.id,
                listed_at=item.listed_at,
            )
            session.add(wl)
            count += 1

    session.flush()
    return count


def update_sync_cursor(
    session: Session,
    account_id: int,
    dataset: str,
    remote_activity_at: datetime,
    success_at: datetime,
) -> None:
    """Update or insert a sync cursor for a dataset."""
    cursor = session.get(SyncCursor, (account_id, dataset))
    if cursor is None:
        cursor = SyncCursor(
            account_id=account_id,
            dataset=dataset,
            remote_activity_at=remote_activity_at,
            last_success_at=success_at,
        )
        session.add(cursor)
    else:
        cursor.remote_activity_at = remote_activity_at
        cursor.last_success_at = success_at
    session.flush()
