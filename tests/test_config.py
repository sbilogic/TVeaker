"""Tests for Settings and configuration management."""

from tveaker.config import Settings, get_settings


def test_default_settings():
    settings = Settings()
    assert settings.env == "development"
    assert settings.host == "127.0.0.1"
    assert settings.port == 8000
    assert settings.sync_interval_minutes == 15
    assert not settings.is_trakt_configured


def test_settings_database_url_memory():
    settings = Settings(database_path=":memory:")
    assert settings.database_url == "sqlite:///:memory:"


def test_settings_trakt_configured():
    settings = Settings(
        trakt_client_id="dummy_client_id",
        trakt_client_secret="dummy_client_secret",
    )
    assert settings.is_trakt_configured
    assert settings.trakt_client_id == "dummy_client_id"


def test_get_settings_cached():
    s1 = get_settings()
    s2 = get_settings()
    assert s1 is s2
