"""Tests for token storage and keyring adapters."""

from unittest.mock import patch

from keyring.errors import KeyringError

from tveaker.auth.token_store import KeyringTokenStore, MemoryTokenStore, TokenData


def test_token_data_serialization():
    token = TokenData(
        access_token="test_access_token",
        refresh_token="test_refresh_token",
        created_at=1700000000,
        expires_in=7200,
    )
    raw = token.to_json()
    restored = TokenData.from_json(raw)
    assert restored == token
    assert restored.access_token == "test_access_token"
    assert "test...oken" in repr(token)


def test_token_data_expiration():
    now_ts = 1700005000
    # expires at 1700000000 + 7200 = 1700007200. Buffer 300 means expires at 1700006900
    token_valid = TokenData("a", "r", 1700000000, 7200)
    assert not token_valid.is_expired(buffer_seconds=300, now_ts=now_ts)

    # expires at 1700000000 + 1000 = 1700001000 < 1700005000
    token_expired = TokenData("a", "r", 1700000000, 1000)
    assert token_expired.is_expired(buffer_seconds=300, now_ts=now_ts)


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


def test_keyring_token_store_error_handling():
    store = KeyringTokenStore(service_name="tveaker_test", username="test_user")

    with patch("keyring.get_password", side_effect=KeyringError("Locked")):
        assert store.get_token() is None

    with (
        patch("keyring.get_password", return_value="invalid-json"),
        patch.object(store, "clear_token") as mock_clear,
    ):
        assert store.get_token() is None
        mock_clear.assert_called_once()

    with patch("keyring.set_password") as mock_set:
        token = TokenData("acc", "ref", 100, 1000)
        store.save_token(token)
        mock_set.assert_called_once()

    with patch("keyring.delete_password") as mock_del:
        store.clear_token()
        mock_del.assert_called_once()
