"""Database engine, session management, and SQLite optimizations."""

from collections.abc import Generator
from contextlib import contextmanager
from typing import Any

from sqlalchemy import Engine, create_engine, event, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from tveaker.config import get_settings


class Base(DeclarativeBase):
    """Base class for all SQLAlchemy declarative models."""

    pass


def _configure_sqlite_connection(dbapi_connection: Any, connection_record: Any) -> None:
    """Apply performance and integrity PRAGMAs to SQLite connections."""
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA foreign_keys = ON;")
    cursor.execute("PRAGMA journal_mode = WAL;")
    cursor.execute("PRAGMA busy_timeout = 5000;")
    cursor.execute("PRAGMA synchronous = NORMAL;")
    cursor.close()


def create_db_engine(database_url: str | None = None, echo: bool = False) -> Engine:
    """Create and configure a SQLite SQLAlchemy engine with WAL mode and foreign keys."""
    if database_url is None:
        settings = get_settings()
        database_url = settings.database_url

    engine = create_engine(
        database_url,
        echo=echo,
        connect_args={"check_same_thread": False},
    )

    event.listen(engine, "connect", _configure_sqlite_connection)
    return engine


_engine: Engine | None = None
_SessionFactory: sessionmaker[Session] | None = None


def get_engine() -> Engine:
    global _engine
    if _engine is None:
        _engine = create_db_engine()
    return _engine


def get_session_factory(engine: Engine | None = None) -> sessionmaker[Session]:
    global _SessionFactory
    if _SessionFactory is None or engine is not None:
        target_engine = engine or get_engine()
        _SessionFactory = sessionmaker(
            bind=target_engine,
            autoflush=False,
            expire_on_commit=False,
        )
    return _SessionFactory


@contextmanager
def get_db_session(engine: Engine | None = None) -> Generator[Session, None, None]:
    """Context manager providing a transactional database session."""
    factory = get_session_factory(engine)
    session = factory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def init_db(engine: Engine | None = None) -> None:
    """Initialize database tables using metadata."""
    target_engine = engine or get_engine()
    Base.metadata.create_all(bind=target_engine)


def check_db_health(session: Session) -> bool:
    """Execute a simple query and integrity check to verify database health."""
    try:
        result = session.execute(text("PRAGMA integrity_check;")).scalar()
        return result == "ok"
    except Exception:
        return False
