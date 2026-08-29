"""Online SQLite hot backup utility."""

import logging
import sqlite3
from pathlib import Path
from typing import NamedTuple

from sqlalchemy import Engine

logger = logging.getLogger(__name__)


class BackupResult(NamedTuple):
    destination_path: Path
    bytes_written: int


def online_sqlite_backup(
    db_path_or_engine: str | Path | Engine,
    destination_path: str | Path,
) -> BackupResult:
    """Safely perform an online hot backup using SQLite native backup API."""
    dest_path = Path(destination_path).resolve()
    dest_path.parent.mkdir(parents=True, exist_ok=True)

    if isinstance(db_path_or_engine, Engine):
        raw_url = str(db_path_or_engine.url)
        if raw_url.startswith("sqlite:///"):
            source_file = raw_url.replace("sqlite:///", "")
            source_conn = sqlite3.connect(source_file)
        else:
            raise ValueError(f"Unsupported engine url for file backup: {raw_url}")
    elif isinstance(db_path_or_engine, (str, Path)):
        source_conn = sqlite3.connect(str(db_path_or_engine))
    else:
        raise TypeError(f"Invalid source type: {type(db_path_or_engine)}")

    dest_conn = sqlite3.connect(str(dest_path))

    try:
        with dest_conn:
            source_conn.backup(dest_conn, pages=100)
        file_size = dest_path.stat().st_size
        logger.info("Online backup successfully written to %s (%d bytes)", dest_path, file_size)
        return BackupResult(destination_path=dest_path, bytes_written=file_size)
    finally:
        dest_conn.close()
        source_conn.close()
