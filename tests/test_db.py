"""Tests for SQLite database engine and session management."""

import pytest
from sqlalchemy import Column, ForeignKey, Integer, String, text
from sqlalchemy.exc import IntegrityError

from tveaker.db import (
    Base,
    check_db_health,
    create_db_engine,
    get_db_session,
)


class Parent(Base):
    __tablename__ = "test_parents"
    id = Column(Integer, primary_key=True)
    name = Column(String, nullable=False)


class Child(Base):
    __tablename__ = "test_children"
    id = Column(Integer, primary_key=True)
    parent_id = Column(Integer, ForeignKey("test_parents.id"), nullable=False)
    name = Column(String, nullable=False)


@pytest.fixture
def test_engine():
    engine = create_db_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    yield engine
    Base.metadata.drop_all(bind=engine)


def test_sqlite_pragmas_and_health(test_engine):
    with get_db_session(test_engine) as session:
        # Check foreign keys
        fk_enabled = session.execute(text("PRAGMA foreign_keys;")).scalar()
        assert fk_enabled == 1

        # Check busy timeout
        busy_timeout = session.execute(text("PRAGMA busy_timeout;")).scalar()
        assert busy_timeout == 5000

        # Health check
        assert check_db_health(session) is True


def test_foreign_key_enforcement(test_engine):
    def insert_orphan():
        with get_db_session(test_engine) as session:
            child = Child(id=1, parent_id=999, name="Orphan")
            session.add(child)

    with pytest.raises(IntegrityError):
        insert_orphan()


def test_transaction_rollback_on_error(test_engine):
    def cause_error():
        with get_db_session(test_engine) as session:
            parent = Parent(id=10, name="Alice")
            session.add(parent)
            session.flush()
            raise RuntimeError("Forced error to test rollback")

    with pytest.raises(RuntimeError, match="Forced error to test rollback"):
        cause_error()

    # Verify parent was not persisted due to rollback
    with get_db_session(test_engine) as session:
        found = session.get(Parent, 10)
        assert found is None


def test_transaction_commit_on_success(test_engine):
    with get_db_session(test_engine) as session:
        parent = Parent(id=20, name="Bob")
        session.add(parent)

    with get_db_session(test_engine) as session:
        found = session.get(Parent, 20)
        assert found is not None
        assert found.name == "Bob"
