"""Pytest configuration and shared fixtures for TVeaker tests."""

import pytest


@pytest.fixture
def sample_fixture() -> dict[str, str]:
    return {"status": "ok"}
