"""Configuration and settings management using Pydantic Settings."""

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="TVEAKER_",
        extra="ignore",
    )

    env: Literal["development", "production", "test"] = "development"
    host: str = "127.0.0.1"
    port: int = 8000
    secret_key: str = "dev-insecure-secret-key-please-change-in-production-32bytes"
    database_path: str = "data/tveaker.db"
    sync_interval_minutes: int = 15
    log_level: str = "INFO"
    user_timezone: str = "UTC"

    # Trakt API Configuration
    trakt_client_id: str = Field(default="", alias="TRAKT_CLIENT_ID")
    trakt_client_secret: str = Field(default="", alias="TRAKT_CLIENT_SECRET")
    trakt_redirect_uri: str = Field(
        default="http://127.0.0.1:8000/auth/trakt/callback",
        alias="TRAKT_REDIRECT_URI",
    )
    trakt_api_base_url: str = "https://api.trakt.tv"

    @property
    def database_url(self) -> str:
        db_path = Path(self.database_path)
        if self.database_path == ":memory:":
            return "sqlite:///:memory:"
        db_path.parent.mkdir(parents=True, exist_ok=True)
        return f"sqlite:///{db_path.resolve().as_posix()}"

    @property
    def is_trakt_configured(self) -> bool:
        return bool(self.trakt_client_id.strip() and self.trakt_client_secret.strip())


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
