"""Pytest configuration and shared fixtures for TVeaker tests."""

from pathlib import Path

import pytest

from tveaker.config import get_settings


@pytest.fixture(autouse=True)
def isolate_test_database(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    """Ensure all tests use an isolated test database and do not touch data/tveaker.db."""
    test_db = tmp_path / "test_env.db"
    monkeypatch.setenv("TVEAKER_DATABASE_PATH", str(test_db))
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()
