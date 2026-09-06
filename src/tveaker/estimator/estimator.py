"""Show finish date and catch-up estimation engine."""

import math
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Protocol

from sqlalchemy import Engine, select

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.estimator.queries import (
    PaceSource,
    get_effective_pace,
    get_show_episode_counts,
)
from tveaker.models import MediaItem, TrackedShow


@dataclass(frozen=True)
class ShowEstimate:
    show_id: int
    trakt_id: int
    title: str
    year: int | None
    status: str
    status_source: str
    include_specials: bool
    total_episodes: int
    aired_episodes: int
    unaired_episodes: int
    unresolved_episodes: int
    watched_episodes: int
    remaining_episodes: int
    unwatched_minutes: int
    avg_runtime_minutes: int
    remaining_runtime_display: str
    completion_percent: float
    episodes_per_week: float
    pace_source: PaceSource
    estimated_finish_date: datetime | None
    days_to_finish: int | None
    is_caught_up: bool
    next_air_date: datetime | None
    genres: list[str] = field(default_factory=list)
    poster_url: str | None = None
    backdrop_url: str | None = None


class FinishEstimator(Protocol):
    """Protocol for finish estimation service."""

    def estimate_show(self, show_id: int) -> ShowEstimate | None: ...
    def estimate_all(self, status: str | None = None) -> list[ShowEstimate]: ...


class ShowFinishEstimator:
    """Calculates show completion percentage, effective velocity, and target finish dates."""

    def __init__(
        self,
        db_engine: Engine,
        account_id: int = 1,
        clock: Clock | None = None,
    ) -> None:
        self.db_engine = db_engine
        self.account_id = account_id
        self.clock = clock or SystemClock()

    def estimate_show(self, show_id: int) -> ShowEstimate | None:
        """Generate a finish and catch-up estimate for a single tracked show."""
        now = self.clock.now()
        with get_db_session(self.db_engine) as session:
            stmt = (
                select(TrackedShow, MediaItem)
                .join(MediaItem, MediaItem.id == TrackedShow.show_id)
                .where(
                    TrackedShow.account_id == self.account_id,
                    TrackedShow.show_id == show_id,
                )
            )
            row = session.execute(stmt).first()
            if row is None:
                return None
            tracked, media = row

            counts = get_show_episode_counts(
                session=session,
                account_id=self.account_id,
                show_id=show_id,
                now=now,
                include_specials=tracked.include_specials,
            )

            pace = get_effective_pace(
                session=session,
                account_id=self.account_id,
                show_id=show_id,
                now=now,
            )

            # Completion is progress through the episodes currently available.
            if counts.aired_episodes > 0:
                completion_pct = round(
                    min(100.0, (counts.watched_episodes / float(counts.aired_episodes)) * 100.0),
                    1,
                )
            else:
                completion_pct = 0.0

            is_caught_up = counts.watched_episodes >= counts.aired_episodes

            # Finish date calculation
            if counts.remaining_episodes == 0:
                finish_date = None
                days_to_finish = 0
            elif pace.episodes_per_week > 0:
                weeks_needed = counts.remaining_episodes / pace.episodes_per_week
                days_needed = math.ceil(weeks_needed * 7.0)
                finish_date = now + timedelta(days=days_needed)
                days_to_finish = days_needed
            else:
                finish_date = None
                days_to_finish = None

            return ShowEstimate(
                show_id=show_id,
                trakt_id=media.trakt_id,
                title=media.title,
                year=media.year,
                status=tracked.status,
                status_source=tracked.status_source,
                include_specials=tracked.include_specials,
                total_episodes=counts.total_episodes,
                aired_episodes=counts.aired_episodes,
                unaired_episodes=counts.unaired_episodes,
                unresolved_episodes=counts.unresolved_episodes,
                watched_episodes=counts.watched_episodes,
                remaining_episodes=counts.remaining_episodes,
                unwatched_minutes=counts.unwatched_minutes,
                avg_runtime_minutes=counts.avg_runtime_minutes,
                remaining_runtime_display=counts.remaining_runtime_display,
                completion_percent=completion_pct,
                episodes_per_week=pace.episodes_per_week,
                pace_source=pace.source,
                estimated_finish_date=finish_date,
                days_to_finish=days_to_finish,
                is_caught_up=is_caught_up,
                next_air_date=counts.next_air_date,
                genres=media.genres,
                poster_url=media.poster_url,
                backdrop_url=media.backdrop_url,
            )

    def estimate_all(self, status: str | None = None) -> list[ShowEstimate]:
        """Generate estimates for all tracked shows, optionally filtered by status."""
        with get_db_session(self.db_engine) as session:
            stmt = select(TrackedShow.show_id).where(TrackedShow.account_id == self.account_id)
            if status is not None:
                stmt = stmt.where(TrackedShow.status == status)
            show_ids = session.execute(stmt).scalars().all()

        results: list[ShowEstimate] = []
        for sid in show_ids:
            est = self.estimate_show(sid)
            if est is not None:
                results.append(est)

        # Sort by days_to_finish ascending (None at the end)
        results.sort(
            key=lambda x: (
                x.days_to_finish is None or x.days_to_finish == 0,
                x.days_to_finish or 999999,
            )
        )
        return results
