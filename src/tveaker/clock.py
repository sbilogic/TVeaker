"""Clock abstraction layer for deterministic time management."""

from abc import ABC, abstractmethod
from datetime import UTC, date, datetime, timedelta


class Clock(ABC):
    """Abstract clock interface providing current time in UTC."""

    @abstractmethod
    def now(self) -> datetime:
        """Return current datetime in UTC with timezone awareness."""
        raise NotImplementedError

    def today(self) -> date:
        """Return current date in UTC."""
        return self.now().date()


class SystemClock(Clock):
    """Production clock backed by system time."""

    def now(self) -> datetime:
        return datetime.now(UTC)


class FrozenClock(Clock):
    """Test clock that returns a fixed datetime and allows manual advancing."""

    def __init__(self, initial_time: datetime | None = None) -> None:
        if initial_time is None:
            self._current_time = datetime.now(UTC)
        elif initial_time.tzinfo is None:
            self._current_time = initial_time.replace(tzinfo=UTC)
        else:
            self._current_time = initial_time.astimezone(UTC)

    def now(self) -> datetime:
        return self._current_time

    def set_time(self, new_time: datetime) -> None:
        if new_time.tzinfo is None:
            self._current_time = new_time.replace(tzinfo=UTC)
        else:
            self._current_time = new_time.astimezone(UTC)

    def advance(self, delta: timedelta | int | float) -> None:
        """Advance time by timedelta or numeric seconds."""
        if isinstance(delta, (int, float)):
            self._current_time += timedelta(seconds=delta)
        else:
            self._current_time += delta
