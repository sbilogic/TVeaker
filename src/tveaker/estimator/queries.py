"""Database queries and pace calculations for show progress and finish estimates."""

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Literal

from sqlalchemy import select
from sqlalchemy.orm import Session

from tveaker.models import Episode, TrackedShow, WatchEvent


def _ensure_utc(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


@dataclass(frozen=True)
class ShowCatalogCounts:
    total_episodes: int
    aired_episodes: int
    watched_episodes: int
    remaining_episodes: int
    unwatched_minutes: int
    unaired_episodes: int
    next_air_date: datetime | None


def get_show_episode_counts(
    session: Session,
    account_id: int,
    show_id: int,
    now: datetime,
    include_specials: bool = False,
) -> ShowCatalogCounts:
    """Compute watched, aired, remaining, and unaired episode metrics."""
    now_utc = _ensure_utc(now) or datetime.now(UTC)

    # Base episode select
    ep_stmt = select(Episode).where(Episode.show_id == show_id)
    if not include_specials:
        ep_stmt = ep_stmt.where(Episode.season_number > 0)

    episodes = session.execute(ep_stmt).scalars().all()

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

    # Get average runtime for fallback
    known_runtimes = [e.runtime_minutes for e in episodes if e.runtime_minutes is not None]
    avg_runtime = int(sum(known_runtimes) / len(known_runtimes)) if known_runtimes else 45

    for ep in episodes:
        is_watched = ep.id in watched_ep_ids
        if is_watched:
            watched += 1

        # Check aired status with timezone normalization
        first_aired_utc = _ensure_utc(ep.first_aired)
        if first_aired_utc is None or first_aired_utc <= now_utc:
            aired += 1
            if not is_watched:
                remaining += 1
                unwatched_mins += (
                    ep.runtime_minutes if ep.runtime_minutes is not None else avg_runtime
                )
        else:
            unaired += 1
            future_air_dates.append(first_aired_utc)
            if not is_watched:
                remaining += 1
                unwatched_mins += (
                    ep.runtime_minutes if ep.runtime_minutes is not None else avg_runtime
                )

    next_air = min(future_air_dates) if future_air_dates else None

    return ShowCatalogCounts(
        total_episodes=total,
        aired_episodes=aired,
        watched_episodes=watched,
        remaining_episodes=remaining,
        unwatched_minutes=unwatched_mins,
        unaired_episodes=unaired,
        next_air_date=next_air,
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
    """Calculate viewing pace using manual override or blended historical velocity."""
    # 1. Check manual pace override
    tracked = session.get(TrackedShow, (account_id, show_id))
    if (
        tracked
        and tracked.manual_episodes_per_week is not None
        and tracked.manual_episodes_per_week > 0
    ):
        return PaceResult(
            episodes_per_week=float(tracked.manual_episodes_per_week),
            source="manual",
        )

    # 2. Show-specific historical pace
    show_pace = _calculate_blended_pace(session, account_id, now, show_id=show_id)
    if show_pace is not None and show_pace > 0:
        return PaceResult(
            episodes_per_week=show_pace,
            source="show_blended",
        )

    # 3. Account-level historical pace across all shows
    account_pace = _calculate_blended_pace(session, account_id, now, show_id=None)
    if account_pace is not None and account_pace > 0:
        return PaceResult(
            episodes_per_week=account_pace,
            source="account_blended",
        )

    # 4. Default fallback
    return PaceResult(
        episodes_per_week=2.0,
        source="default_fallback",
    )


def _calculate_blended_pace(
    session: Session,
    account_id: int,
    now: datetime,
    show_id: int | None = None,
) -> float | None:
    """Calculate blended velocity over 30d, 90d, and all-time windows."""
    now_utc = _ensure_utc(now) or datetime.now(UTC)

    base_stmt = (
        select(WatchEvent.watched_at)
        .join(Episode, WatchEvent.episode_id == Episode.id)
        .where(WatchEvent.account_id == account_id)
    )
    if show_id is not None:
        base_stmt = base_stmt.where(Episode.show_id == show_id)

    raw_watch_times = session.execute(base_stmt).scalars().all()
    watch_times: list[datetime] = []
    for raw in raw_watch_times:
        normalized = _ensure_utc(raw)
        if normalized is not None:
            watch_times.append(normalized)

    if not watch_times:
        return None

    # Filter by windows
    cutoff_30d = now_utc - timedelta(days=30)
    cutoff_90d = now_utc - timedelta(days=90)

    events_30d = [t for t in watch_times if t >= cutoff_30d]
    events_90d = [t for t in watch_times if t >= cutoff_90d]
    events_all = watch_times

    pace_30d = len(events_30d) / (30.0 / 7.0)
    pace_90d = len(events_90d) / (90.0 / 7.0)

    earliest = min(watch_times)
    span_days = max((now_utc - earliest).days, 7)
    pace_all = len(events_all) / (span_days / 7.0)

    if events_30d:
        blended = (0.60 * pace_30d) + (0.30 * pace_90d) + (0.10 * pace_all)
    elif events_90d:
        blended = (0.70 * pace_90d) + (0.30 * pace_all)
    else:
        blended = pace_all

    return round(blended, 2)
