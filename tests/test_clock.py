"""Tests for SystemClock and FrozenClock."""

from datetime import UTC, datetime, timedelta

from tveaker.clock import FrozenClock, SystemClock


def test_system_clock():
    clock = SystemClock()
    now = clock.now()
    today = clock.today()
    assert now.tzinfo is not None
    assert now.tzinfo == UTC
    assert today == now.date()


def test_frozen_clock_deterministic():
    fixed = datetime(2026, 8, 29, 10, 0, 0, tzinfo=UTC)
    clock = FrozenClock(fixed)
    assert clock.now() == fixed
    assert clock.today() == fixed.date()

    clock.advance(timedelta(days=2, hours=3))
    assert clock.now() == datetime(2026, 8, 31, 13, 0, 0, tzinfo=UTC)
    assert clock.today() == datetime(2026, 8, 31, 13, 0, 0, tzinfo=UTC).date()

    new_time = datetime(2026, 12, 25, 0, 0, 0)
    clock.set_time(new_time)
    assert clock.now().year == 2026
    assert clock.now().month == 12
    assert clock.now().day == 25
    assert clock.now().tzinfo == UTC
