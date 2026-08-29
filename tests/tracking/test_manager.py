"""Tests for LocalShowTracker preference manager."""

from datetime import UTC, datetime

import pytest
from sqlalchemy import create_engine

from tveaker.clock import FrozenClock
from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.models import Account, MediaItem, TrackedShow
from tveaker.tracking.manager import LocalShowTracker


@pytest.fixture
def tracking_env():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    t0 = datetime(2026, 8, 29, 10, 0, 0, tzinfo=UTC)
    with get_db_session(engine) as session:
        account = Account(
            id=1,
            trakt_uuid="uuid-1",
            username="sahil",
            timezone="UTC",
            connected_at=t0,
        )
        session.add(account)
        session.flush()

        show1 = MediaItem(id=1, media_type="show", trakt_id=100, title="Show 1", year=2020)
        show2 = MediaItem(id=2, media_type="show", trakt_id=200, title="Show 2", year=2021)
        movie = MediaItem(id=3, media_type="movie", trakt_id=300, title="Movie 1", year=2022)
        session.add_all([show1, show2, movie])
        session.flush()

        ts1 = TrackedShow(
            account_id=1,
            show_id=1,
            status="watching",
            status_source="auto",
            created_at=t0,
            updated_at=t0,
        )
        ts2 = TrackedShow(
            account_id=1,
            show_id=2,
            status="planned",
            status_source="auto",
            created_at=t0,
            updated_at=t0,
        )
        session.add_all([ts1, ts2])

    clock = FrozenClock(datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC))
    tracker = LocalShowTracker(db_engine=engine, account_id=1, clock=clock)
    yield tracker, engine
    Base.metadata.drop_all(bind=engine)


def test_list_and_get_tracked_shows(tracking_env):
    tracker, _ = tracking_env

    all_shows = tracker.list()
    assert len(all_shows) == 2

    watching = tracker.list(status="watching")
    assert len(watching) == 1
    assert watching[0].title == "Show 1"

    planned = tracker.list(status="planned")
    assert len(planned) == 1
    assert planned[0].title == "Show 2"

    show1 = tracker.get(1)
    assert show1 is not None
    assert show1.trakt_id == 100
    assert show1.status == "watching"

    assert tracker.get(999) is None


def test_set_status_manual_override(tracking_env):
    tracker, _ = tracking_env

    updated = tracker.set_status(1, "paused")
    assert updated.status == "paused"
    assert updated.status_source == "manual"

    # Invalid status
    with pytest.raises(ValueError, match="Invalid tracking status"):
        tracker.set_status(1, "unknown_status")  # type: ignore

    # Non-existent show
    with pytest.raises(ValueError, match="not found"):
        tracker.set_status(999, "watching")


def test_set_manual_pace_and_specials(tracking_env):
    tracker, _ = tracking_env

    res = tracker.set_manual_pace(1, 4.5)
    assert res.manual_episodes_per_week == 4.5

    res_none = tracker.set_manual_pace(1, None)
    assert res_none.manual_episodes_per_week is None

    with pytest.raises(ValueError, match="strictly positive"):
        tracker.set_manual_pace(1, 0.0)

    with pytest.raises(ValueError, match="strictly positive"):
        tracker.set_manual_pace(1, -2.0)

    # Specials
    spec = tracker.set_include_specials(1, True)
    assert spec.include_specials is True
