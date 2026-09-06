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


def test_encrypted_file_token_store(tmp_path):
    from tveaker.auth.token_store import EncryptedFileTokenStore

    token_file = tmp_path / "tokens.enc"
    store = EncryptedFileTokenStore(secret_key="my-super-secret-key", file_path=token_file)

    assert store.get_token() is None

    token = TokenData(
        access_token="test_secret_token_123",
        refresh_token="test_refresh_token_456",
        created_at=1700000000,
        expires_in=7200,
    )
    store.save_token(token)

    # Verify the file is written and contents are encrypted (not plain text)
    raw_bytes = token_file.read_bytes()
    assert b"test_secret_token_123" not in raw_bytes
    assert b"test_refresh_token_456" not in raw_bytes

    # Verify retrieval decrypts cleanly
    restored = store.get_token()
    assert restored == token

    # Verify key mismatch fails to decrypt safely
    wrong_store = EncryptedFileTokenStore(secret_key="wrong-secret-key", file_path=token_file)
    assert wrong_store.get_token() is None

    # Clear token
    store.clear_token()
    assert store.get_token() is None
    assert not token_file.exists()


def test_create_token_store_fallback(tmp_path):
    from tveaker.auth.token_store import (
        EncryptedFileTokenStore,
        KeyringTokenStore,
        create_token_store,
    )

    # When keyring fails, returns EncryptedFileTokenStore
    token_file = tmp_path / "sub" / "tokens.enc"
    with patch("keyring.get_password", side_effect=KeyringError("Headless")):
        store = create_token_store(secret_key="secret", file_path=token_file)
        assert isinstance(store, EncryptedFileTokenStore)

    # When keyring succeeds, returns KeyringTokenStore
    with patch("keyring.get_password", return_value=None):
        store = create_token_store(secret_key="secret")
        assert isinstance(store, KeyringTokenStore)
