"""Tests for online SQLite hot backup."""

import sqlite3
from pathlib import Path

from tveaker.backup import online_sqlite_backup


def test_online_sqlite_backup(tmp_path: Path):
    source_db = tmp_path / "source.db"
    dest_db = tmp_path / "backup" / "dest.db"

    # Create source DB with some data
    conn = sqlite3.connect(str(source_db))
    conn.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)")
    conn.execute("INSERT INTO users (name) VALUES ('Sahil')")
    conn.commit()
    conn.close()

    # Perform online backup
    res = online_sqlite_backup(source_db, dest_db)
    assert res.destination_path.exists()
    assert res.bytes_written > 0

    # Verify backup contents
    backup_conn = sqlite3.connect(str(dest_db))
    row = backup_conn.execute("SELECT name FROM users WHERE id = 1").fetchone()
    backup_conn.close()

    assert row is not None
    assert row[0] == "Sahil"
