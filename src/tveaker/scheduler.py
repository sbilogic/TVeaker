"""Background sync scheduler for 15-minute incremental polling and 7-day full reconciliation."""

import logging
import threading
from datetime import datetime

from tveaker.clock import Clock, SystemClock
from tveaker.sync.importer import AccountSync

logger = logging.getLogger(__name__)


class SyncScheduler:
    """Background thread scheduler managing recurring sync cycles."""

    def __init__(
        self,
        account_sync: AccountSync,
        clock: Clock | None = None,
        incremental_interval_seconds: int = 900,  # 15 minutes
        full_reconcile_interval_seconds: int = 604800,  # 7 days
    ) -> None:
        self.account_sync = account_sync
        self.clock = clock or SystemClock()
        self.incremental_interval = incremental_interval_seconds
        self.full_reconcile_interval = full_reconcile_interval_seconds

        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None
        self._last_incremental_run: datetime | None = None
        self._last_full_run: datetime | None = None

    def start(self) -> None:
        """Start scheduler in a daemon background thread."""
        if self._thread is not None and self._thread.is_alive():
            logger.warning("SyncScheduler is already running.")
            return

        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run_loop, name="tveaker-scheduler", daemon=True
        )
        self._thread.start()
        logger.info("SyncScheduler background daemon started.")

    def stop(self, timeout: float = 5.0) -> None:
        """Signal scheduler to stop and join worker thread."""
        self._stop_event.set()
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=timeout)
            logger.info("SyncScheduler stopped.")

    def is_running(self) -> bool:
        return (
            self._thread is not None and self._thread.is_alive() and not self._stop_event.is_set()
        )

    def run_pending(self) -> None:
        """Check intervals and run pending sync jobs (can be invoked directly in tests)."""
        now = self.clock.now()

        # Check full reconciliation (7 days)
        if (
            self._last_full_run is None
            or (now - self._last_full_run).total_seconds() >= self.full_reconcile_interval
        ):
            try:
                logger.info("Executing scheduled 7-day full reconciliation...")
                self.account_sync.run("full")
                self._last_full_run = now
                self._last_incremental_run = now
                return
            except Exception as e:
                logger.error("Scheduled full reconciliation failed: %s", e)

        # Check incremental sync (15 minutes)
        if (
            self._last_incremental_run is None
            or (now - self._last_incremental_run).total_seconds() >= self.incremental_interval
        ):
            try:
                logger.info("Executing scheduled 15-minute incremental sync...")
                self.account_sync.run("incremental")
                self._last_incremental_run = now
            except Exception as e:
                logger.error("Scheduled incremental sync failed: %s", e)

    def _run_loop(self) -> None:
        while not self._stop_event.is_set():
            self.run_pending()
            # Wait with interruptibility
            self._stop_event.wait(timeout=10.0)
