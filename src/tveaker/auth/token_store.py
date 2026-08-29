"""Secure OAuth token storage using OS Keyring with local test adapters."""

import json
import logging
import time
from contextlib import suppress
from dataclasses import asdict, dataclass
from typing import Protocol

import keyring
from keyring.errors import KeyringError

logger = logging.getLogger(__name__)

SERVICE_NAME = "tveaker"
USERNAME = "trakt_oauth"


@dataclass(frozen=True)
class TokenData:
    access_token: str
    refresh_token: str
    created_at: int
    expires_in: int
    token_type: str = "bearer"
    scope: str = "public"

    def is_expired(self, buffer_seconds: int = 300, now_ts: int | None = None) -> bool:
        """Return True if token expires within the buffer window."""
        now = now_ts if now_ts is not None else int(time.time())
        return (self.created_at + self.expires_in - buffer_seconds) <= now

    def to_json(self) -> str:
        return json.dumps(asdict(self))

    @classmethod
    def from_json(cls, raw: str) -> "TokenData":
        d = json.loads(raw)
        return cls(
            access_token=d["access_token"],
            refresh_token=d["refresh_token"],
            created_at=int(d["created_at"]),
            expires_in=int(d["expires_in"]),
            token_type=d.get("token_type", "bearer"),
            scope=d.get("scope", "public"),
        )

    def __repr__(self) -> str:
        if len(self.access_token) >= 8:
            masked_access = f"{self.access_token[:4]}...{self.access_token[-4:]}"
        else:
            masked_access = "***"
        if len(self.refresh_token) >= 8:
            masked_refresh = f"{self.refresh_token[:4]}...{self.refresh_token[-4:]}"
        else:
            masked_refresh = "***"
        return (
            f"TokenData(access_token='{masked_access}', refresh_token='{masked_refresh}', "
            f"created_at={self.created_at}, expires_in={self.expires_in})"
        )


class TokenStore(Protocol):
    """Protocol for secure token storage backends."""

    def get_token(self) -> TokenData | None: ...
    def save_token(self, token: TokenData) -> None: ...
    def clear_token(self) -> None: ...
    def delete_token(self) -> None: ...


class KeyringTokenStore:
    """Store tokens securely using the OS Credential Manager / Keyring."""

    def __init__(self, service_name: str = SERVICE_NAME, username: str = USERNAME) -> None:
        self.service_name = service_name
        self.username = username

    def get_token(self) -> TokenData | None:
        try:
            raw = keyring.get_password(self.service_name, self.username)
            if not raw:
                return None
            return TokenData.from_json(raw)
        except KeyringError as e:
            logger.error("Failed to read token from keyring: %s", e)
            return None
        except (json.JSONDecodeError, KeyError, ValueError) as e:
            logger.warning("Corrupt token found in keyring, clearing: %s", e)
            self.clear_token()
            return None

    def save_token(self, token: TokenData) -> None:
        try:
            keyring.set_password(self.service_name, self.username, token.to_json())
        except KeyringError as e:
            logger.error("Failed to save token to keyring: %s", e)
            raise

    def clear_token(self) -> None:
        with suppress(KeyringError):
            keyring.delete_password(self.service_name, self.username)

    def delete_token(self) -> None:
        self.clear_token()


class MemoryTokenStore:
    """In-memory token store for deterministic unit and integration testing."""

    def __init__(self, initial_token: TokenData | None = None) -> None:
        self._token = initial_token

    def get_token(self) -> TokenData | None:
        return self._token

    def save_token(self, token: TokenData) -> None:
        self._token = token

    def clear_token(self) -> None:
        self._token = None

    def delete_token(self) -> None:
        self.clear_token()
