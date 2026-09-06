"""Database queries and pace calculations for show progress and finish estimates."""

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Literal

from sqlalchemy import select
from sqlalchemy.orm import Session

from tveaker.episode_catalog import get_show_progress_catalog
from tveaker.models import Episode, MediaItem, TrackedShow, WatchEvent


def _ensure_utc(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def is_episode_released(first_aired: datetime | None, now: datetime) -> bool:
    """Return whether an episode has a known air date that has passed."""
    first_aired_utc = _ensure_utc(first_aired)
    now_utc = _ensure_utc(now) or datetime.now(UTC)
    return first_aired_utc is not None and first_aired_utc <= now_utc


def format_runtime_duration(minutes: int) -> str:
    """Format total runtime minutes into a clean human-readable duration string."""
    if minutes <= 0:
        return "0m"
    if minutes < 60:
        return f"{minutes}m"
    if minutes < 1440:  # < 24 hours
        hours = minutes // 60
        mins = minutes % 60
        return f"{hours}h {mins}m" if mins > 0 else f"{hours}h"
    days = minutes // 1440
    remaining_mins = minutes % 1440
    hours = remaining_mins // 60
    return f"{days}d {hours}h" if hours > 0 else f"{days}d"


@dataclass(frozen=True)
class ShowCatalogCounts:
    total_episodes: int
    aired_episodes: int
    watched_episodes: int
    remaining_episodes: int
    unwatched_minutes: int
    unaired_episodes: int
    unresolved_episodes: int
    next_air_date: datetime | None
    avg_runtime_minutes: int
    remaining_runtime_display: str


def get_show_episode_counts(
    session: Session,
    account_id: int,
    show_id: int,
    now: datetime,
    include_specials: bool = False,
) -> ShowCatalogCounts:
    """Compute watched, available, remaining, and unwatched runtime metrics.

    A local/Trakt watch event is authoritative evidence that its episode has
    aired. Catalog providers can legitimately omit an air date, so metadata
    gaps must never hide imported watch history from progress.
    """
    now_utc = _ensure_utc(now) or datetime.now(UTC)

    catalog = get_show_progress_catalog(
        session=session,
        account_id=account_id,
        show_id=show_id,
        include_specials=include_specials,
    )
    episodes = list(catalog.episodes)

    # Get set of watched episode IDs for this account
    watched_ep_ids = set(
        session.execute(
            select(WatchEvent.episode_id)
            .join(Episode, WatchEvent.episode_id == Episode.id)
            .where(
                WatchEvent.account_id == account_id,
                Episode.show_id == show_id,
            )
        )
        .scalars()
        .all()
    )

    total = len(episodes)
    aired = 0
    watched = 0
    remaining = 0
    unwatched_mins = 0
    unaired = 0
    future_air_dates: list[datetime] = []

    # Runtime estimates must only learn from released episodes. Future catalog
    # entries can contain placeholders or runtimes that later change.
    show = session.get(MediaItem, show_id)
    known_runtimes = [
        e.runtime_minutes
        for e in episodes
        if (
            is_episode_released(e.first_aired, now_utc)
            or e.id in catalog.source_confirmed_episode_ids
        )
        and e.runtime_minutes is not None
        and e.runtime_minutes > 0
    ]

    if known_runtimes:
        avg_runtime = int(sum(known_runtimes) / len(known_runtimes))
    elif show and show.runtime_minutes and show.runtime_minutes > 0:
        avg_runtime = show.runtime_minutes
    elif show and show.genres:
        g = set(show.genres)
        if "Animation" in g or "Comedy" in g:
            avg_runtime = 24
        elif "Sci-Fi" in g or "Drama" in g or "Action" in g or "Crime" in g:
            avg_runtime = 50
        elif "Documentary" in g or "Reality" in g:
            avg_runtime = 44
        else:
            avg_runtime = 42
    else:
        avg_runtime = 42

    for ep in episodes:
        is_watched = ep.id in watched_ep_ids

        ep_runtime = (
            ep.runtime_minutes
            if (ep.runtime_minutes is not None and ep.runtime_minutes > 0)
            else avg_runtime
        )

        # A watch event is stronger evidence than incomplete catalog metadata.
        # Unwatched episodes with an unknown date remain outside the queue so
        # we never invite the user to watch an episode whose availability is
        # genuinely unknown.
        first_aired_utc = _ensure_utc(ep.first_aired)
        is_released = (
            is_episode_released(ep.first_aired, now_utc)
            or ep.id in catalog.source_confirmed_episode_ids
        )
        if is_released or is_watched:
            aired += 1
            if is_watched:
                watched += 1
            elif is_released:
                remaining += 1
                unwatched_mins += ep_runtime
        else:
            unaired += 1
            if first_aired_utc is not None:
                future_air_dates.append(first_aired_utc)

    if catalog.unresolved_episodes:
        total += catalog.unresolved_episodes
        aired += catalog.unresolved_episodes
        remaining += catalog.unresolved_episodes
        unwatched_mins += catalog.unresolved_episodes * avg_runtime

    next_air = min(future_air_dates) if future_air_dates else None
    runtime_display = format_runtime_duration(unwatched_mins)

    return ShowCatalogCounts(
        total_episodes=total,
        aired_episodes=aired,
        watched_episodes=watched,
        remaining_episodes=remaining,
        unwatched_minutes=unwatched_mins,
        unaired_episodes=unaired,
        unresolved_episodes=catalog.unresolved_episodes,
        next_air_date=next_air,
        avg_runtime_minutes=avg_runtime,
        remaining_runtime_display=runtime_display,
    )


PaceSource = Literal["manual", "show_blended", "account_blended", "default_fallback"]


@dataclass(frozen=True)
class PaceResult:
    episodes_per_week: float
    source: PaceSource


def get_effective_pace(
    session: Session,
    account_id: int,
    show_id: int,
    now: datetime,
) -> PaceResult:
    """Calculate effective velocity in episodes/week using blended 30d/90d windows."""
    now_utc = _ensure_utc(now) or datetime.now(UTC)

    # 1. Manual override takes highest priority
    tracked = session.get(TrackedShow, (account_id, show_id))
    if tracked and tracked.manual_episodes_per_week is not None:
        return PaceResult(
            episodes_per_week=float(tracked.manual_episodes_per_week),
            source="manual",
        )

    # 2. Show-specific blended pace
    show_pace = _compute_blended_pace_for_scope(
        session=session,
        account_id=account_id,
        now=now_utc,
        show_id=show_id,
    )
    if show_pace is not None and show_pace > 0:
        return PaceResult(episodes_per_week=show_pace, source="show_blended")

    # 3. Account-wide blended pace
    account_pace = _compute_blended_pace_for_scope(
        session=session,
        account_id=account_id,
        now=now_utc,
        show_id=None,
    )
    if account_pace is not None and account_pace > 0:
        return PaceResult(episodes_per_week=account_pace, source="account_blended")

    # 4. Default fallback: 1.0 ep/week
    return PaceResult(episodes_per_week=1.0, source="default_fallback")


def _compute_blended_pace_for_scope(
    session: Session,
    account_id: int,
    now: datetime,
    show_id: int | None = None,
) -> float | None:
    """Compute 70% 30d + 30% 90d blended pace for a show or whole account."""
    cutoff_30d = now - timedelta(days=30)
    cutoff_90d = now - timedelta(days=90)

    # Base query for episode watch events
    base_stmt = (
        select(WatchEvent.watched_at)
        .join(Episode, WatchEvent.episode_id == Episode.id)
        .where(
            WatchEvent.account_id == account_id,
            WatchEvent.episode_id.isnot(None),
            WatchEvent.watched_at >= cutoff_90d,
            WatchEvent.watched_at <= now,
        )
    )
    if show_id is not None:
        base_stmt = base_stmt.where(Episode.show_id == show_id)

    watched_timestamps = session.execute(base_stmt).scalars().all()

    if not watched_timestamps:
        return None

    count_30d = sum(1 for ts in watched_timestamps if (_ensure_utc(ts) or now) >= cutoff_30d)
    count_90d = len(watched_timestamps)

    pace_30d = (count_30d / 30.0) * 7.0
    pace_90d = (count_90d / 90.0) * 7.0

    # 70% weight to 30d, 30% weight to 90d
    blended = (0.7 * pace_30d) + (0.3 * pace_90d)
    return round(blended, 2)
