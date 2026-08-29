"""Tests for the background sync scheduler."""

from datetime import UTC, datetime
from unittest.mock import MagicMock

from tveaker.clock import FrozenClock
from tveaker.scheduler import SyncScheduler
from tveaker.sync.importer import AccountSync, SyncReport


def test_scheduler_lifecycle_and_pending_jobs():
    mock_sync = MagicMock(spec=AccountSync)
    mock_sync.run.return_value = SyncReport(1, "incremental", "success", {}, {}, {}, {}, [])

    start_time = datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC)
    clock = FrozenClock(start_time)

    scheduler = SyncScheduler(
        account_sync=mock_sync,
        clock=clock,
        incremental_interval_seconds=900,  # 15 min
        full_reconcile_interval_seconds=604800,  # 7 days
    )

    # First run triggers full reconciliation
    scheduler.run_pending()
    mock_sync.run.assert_called_once_with("full")
    mock_sync.run.reset_mock()

    # Advancing 5 minutes: neither should run
    clock.advance(300)
    scheduler.run_pending()
    mock_sync.run.assert_not_called()

    # Advancing 11 more minutes (total 16m): incremental should run
    clock.advance(660)
    scheduler.run_pending()
    mock_sync.run.assert_called_once_with("incremental")
    mock_sync.run.reset_mock()

    # Advancing 7.5 days: full should run
    clock.advance(7 * 86400 + 3600)
    scheduler.run_pending()
    mock_sync.run.assert_called_once_with("full")


def test_scheduler_thread_start_stop():
    mock_sync = MagicMock(spec=AccountSync)
    scheduler = SyncScheduler(account_sync=mock_sync)

    scheduler.start()
    assert scheduler.is_running() is True

    scheduler.stop(timeout=1.0)
    assert scheduler.is_running() is False
