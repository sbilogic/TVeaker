"""Local show tracking manager and preference state service."""

import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Literal, Protocol

from sqlalchemy import Engine, select

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.models import MediaItem, TrackedShow

logger = logging.getLogger(__name__)

TrackingStatus = Literal["planned", "watching", "paused", "completed", "dropped"]
VALID_STATUSES: set[str] = {"planned", "watching", "paused", "completed", "dropped"}


@dataclass(frozen=True)
class TrackedShowView:
    show_id: int
    trakt_id: int
    title: str
    year: int | None
    overview: str | None
    status: str
    status_source: str
    include_specials: bool
    manual_episodes_per_week: float | None
    priority: int
    updated_at: datetime


class ShowTracking(Protocol):
    """Protocol for local show tracking operations."""

    def list(self, status: TrackingStatus | None = None) -> list[TrackedShowView]: ...
    def get(self, show_id: int) -> TrackedShowView | None: ...
    def set_status(self, show_id: int, status: TrackingStatus) -> TrackedShowView: ...
    def set_include_specials(self, show_id: int, include: bool) -> TrackedShowView: ...
    def set_manual_pace(self, show_id: int, episodes_per_week: float | None) -> TrackedShowView: ...


class LocalShowTracker:
    """Manages local show tracking preferences and statuses."""

    def __init__(
        self,
        db_engine: Engine,
        account_id: int = 1,
        clock: Clock | None = None,
    ) -> None:
        self.db_engine = db_engine
        self.account_id = account_id
        self.clock = clock or SystemClock()

    def list(self, status: TrackingStatus | None = None) -> list[TrackedShowView]:
        """List tracked shows, optionally filtered by tracking status."""
        with get_db_session(self.db_engine) as session:
            stmt = (
                select(TrackedShow, MediaItem)
                .join(MediaItem, MediaItem.id == TrackedShow.show_id)
                .where(TrackedShow.account_id == self.account_id)
            )
            if status is not None:
                stmt = stmt.where(TrackedShow.status == status)

            stmt = stmt.order_by(TrackedShow.priority.desc(), MediaItem.title.asc())
            rows = session.execute(stmt).all()

            results: list[TrackedShowView] = []
            for tracked, media in rows:
                results.append(
                    TrackedShowView(
                        show_id=tracked.show_id,
                        trakt_id=media.trakt_id,
                        title=media.title,
                        year=media.year,
                        overview=media.overview,
                        status=tracked.status,
                        status_source=tracked.status_source,
                        include_specials=tracked.include_specials,
                        manual_episodes_per_week=tracked.manual_episodes_per_week,
                        priority=tracked.priority,
                        updated_at=tracked.updated_at,
                    )
                )
            return results

    def get(self, show_id: int) -> TrackedShowView | None:
        """Get tracking details for a specific show."""
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
            return TrackedShowView(
                show_id=tracked.show_id,
                trakt_id=media.trakt_id,
                title=media.title,
                year=media.year,
                overview=media.overview,
                status=tracked.status,
                status_source=tracked.status_source,
                include_specials=tracked.include_specials,
                manual_episodes_per_week=tracked.manual_episodes_per_week,
                priority=tracked.priority,
                updated_at=tracked.updated_at,
            )

    def set_status(self, show_id: int, status: TrackingStatus) -> TrackedShowView:
        """Update tracking status manually."""
        if status not in VALID_STATUSES:
            raise ValueError(f"Invalid tracking status: '{status}'")

        now = self.clock.now()
        with get_db_session(self.db_engine) as session:
            media = session.get(MediaItem, show_id)
            if media is None or media.media_type != "show":
                raise ValueError(f"Show with id {show_id} not found.")

            tracked = session.get(TrackedShow, (self.account_id, show_id))
            if tracked is None:
                tracked = TrackedShow(
                    account_id=self.account_id,
                    show_id=show_id,
                    status=status,
                    status_source="manual",
                    created_at=now,
                    updated_at=now,
                )
                session.add(tracked)
            else:
                tracked.status = status
                tracked.status_source = "manual"
                tracked.updated_at = now

            session.flush()

        res = self.get(show_id)
        assert res is not None
        return res

    def set_include_specials(self, show_id: int, include: bool) -> TrackedShowView:
        """Update whether specials (Season 0) count towards progress/completion."""
        now = self.clock.now()
        with get_db_session(self.db_engine) as session:
            media = session.get(MediaItem, show_id)
            if media is None or media.media_type != "show":
                raise ValueError(f"Show with id {show_id} not found.")

            tracked = session.get(TrackedShow, (self.account_id, show_id))
            if tracked is None:
                tracked = TrackedShow(
                    account_id=self.account_id,
                    show_id=show_id,
                    status="watching",
                    status_source="manual",
                    include_specials=include,
                    created_at=now,
                    updated_at=now,
                )
                session.add(tracked)
            else:
                tracked.include_specials = include
                tracked.updated_at = now

            session.flush()

        res = self.get(show_id)
        assert res is not None
        return res

    def set_manual_pace(self, show_id: int, episodes_per_week: float | None) -> TrackedShowView:
        """Set a manual viewing pace override (must be > 0 or None)."""
        if episodes_per_week is not None and episodes_per_week <= 0:
            raise ValueError("Manual pace episodes per week must be strictly positive.")

        now = self.clock.now()
        with get_db_session(self.db_engine) as session:
            media = session.get(MediaItem, show_id)
            if media is None or media.media_type != "show":
                raise ValueError(f"Show with id {show_id} not found.")

            tracked = session.get(TrackedShow, (self.account_id, show_id))
            if tracked is None:
                tracked = TrackedShow(
                    account_id=self.account_id,
                    show_id=show_id,
                    status="watching",
                    status_source="manual",
                    manual_episodes_per_week=episodes_per_week,
                    created_at=now,
                    updated_at=now,
                )
                session.add(tracked)
            else:
                tracked.manual_episodes_per_week = episodes_per_week
                tracked.updated_at = now

            session.flush()

        res = self.get(show_id)
        assert res is not None
        return res
