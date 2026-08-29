"""Tests for TokenData and TokenStore implementations."""

import time

from tveaker.auth.token_store import MemoryTokenStore, TokenData


def test_token_data_serialization_and_masking():
    token = TokenData(
        access_token="secret_access_token_12345",
        refresh_token="secret_refresh_token_67890",
        created_at=1000,
        expires_in=7200,
    )
    raw = token.to_json()
    loaded = TokenData.from_json(raw)
    assert loaded == token
    assert "secret_access_token_12345" not in repr(token)
    assert "secr...2345" in repr(token)


def test_token_expiration_buffer():
    now = int(time.time())
    valid_token = TokenData(
        access_token="valid",
        refresh_token="valid_refresh",
        created_at=now,
        expires_in=3600,
    )
    assert not valid_token.is_expired(buffer_seconds=300)

    expiring_soon_token = TokenData(
        access_token="expiring",
        refresh_token="expiring_refresh",
        created_at=now - 3400,
        expires_in=3600,
    )
    # 3600 - 3400 = 200s left, with buffer 300s it should be expired
    assert expiring_soon_token.is_expired(buffer_seconds=300)


def test_memory_token_store():
    store = MemoryTokenStore()
    assert store.get_token() is None

    token = TokenData(
        access_token="acc",
        refresh_token="ref",
        created_at=100,
        expires_in=1000,
    )
    store.save_token(token)
    assert store.get_token() == token

    store.delete_token()
    assert store.get_token() is None
