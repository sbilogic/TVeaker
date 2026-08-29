"""Deterministic clock abstractions for TVeaker."""

from datetime import UTC, date, datetime, timedelta
from typing import Protocol


class Clock(Protocol):
    """Protocol for time sources."""

    def now(self) -> datetime:
        """Return the current timezone-aware UTC datetime."""
        ...

    def today(self) -> date:
        """Return the current UTC date."""
        ...


class SystemClock:
    """Real system clock returning UTC datetime and date."""

    def now(self) -> datetime:
        return datetime.now(UTC)

    def today(self) -> date:
        return datetime.now(UTC).date()


class FrozenClock:
    """Deterministic, mutable clock for testing time-dependent logic."""

    def __init__(self, current_time: datetime | None = None) -> None:
        if current_time is None:
            self._current_time = datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC)
        else:
            if current_time.tzinfo is None:
                self._current_time = current_time.replace(tzinfo=UTC)
            else:
                self._current_time = current_time.astimezone(UTC)

    def now(self) -> datetime:
        return self._current_time

    def today(self) -> date:
        return self._current_time.date()

    def set_time(self, new_time: datetime) -> None:
        if new_time.tzinfo is None:
            self._current_time = new_time.replace(tzinfo=UTC)
        else:
            self._current_time = new_time.astimezone(UTC)

    def advance(self, delta: timedelta) -> None:
        self._current_time += delta
