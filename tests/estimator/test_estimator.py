"""Comprehensive unit tests for ShowFinishEstimator and velocity calculation."""

from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import create_engine

from tveaker.clock import FrozenClock
from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.estimator.estimator import ShowFinishEstimator
from tveaker.models import Account, Episode, MediaItem, TrackedShow, WatchEvent


@pytest.fixture
def estimator_env():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    now = datetime(2026, 8, 29, 12, 0, 0, tzinfo=UTC)

    with get_db_session(engine) as session:
        # Account
        account = Account(id=1, trakt_uuid="u1", username="sahil", timezone="UTC", connected_at=now)
        session.add(account)
        session.flush()

        # Show 1: 10 episodes aired, 6 watched, manual pace 2 eps/week
        show1 = MediaItem(id=1, media_type="show", trakt_id=101, title="Breaking Bad", year=2008)
        session.add(show1)
        session.flush()

        for ep_num in range(1, 11):
            ep = Episode(
                id=100 + ep_num,
                show_id=1,
                trakt_id=1000 + ep_num,
                season_number=1,
                episode_number=ep_num,
                title=f"S01E{ep_num:02d}",
                runtime_minutes=45,
                first_aired=now - timedelta(days=100 - ep_num),
            )
            session.add(ep)
            session.flush()
            if ep_num <= 6:
                we = WatchEvent(
                    history_id=2000 + ep_num,
                    account_id=1,
                    watched_at=now - timedelta(days=20 - ep_num),
                    action="watch",
                    episode_id=ep.id,
                )
                session.add(we)

        ts1 = TrackedShow(
            account_id=1,
            show_id=1,
            status="watching",
            status_source="manual",
            manual_episodes_per_week=2.0,
            created_at=now,
            updated_at=now,
        )
        session.add(ts1)

        # Show 2: Caught up show (all 5 episodes watched)
        show2 = MediaItem(id=2, media_type="show", trakt_id=102, title="Chernobyl", year=2019)
        session.add(show2)
        session.flush()

        for ep_num in range(1, 6):
            ep = Episode(
                id=200 + ep_num,
                show_id=2,
                trakt_id=2000 + ep_num,
                season_number=1,
                episode_number=ep_num,
                title=f"Ep {ep_num}",
                runtime_minutes=60,
                first_aired=now - timedelta(days=500),
            )
            session.add(ep)
            session.flush()
            we = WatchEvent(
                history_id=3000 + ep_num,
                account_id=1,
                watched_at=now - timedelta(days=400),
                action="watch",
                episode_id=ep.id,
            )
            session.add(we)

        ts2 = TrackedShow(
            account_id=1,
            show_id=2,
            status="completed",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )
        session.add(ts2)

        # Show 3: Active show with future episodes + Season 0 special
        show3 = MediaItem(id=3, media_type="show", trakt_id=103, title="Severance", year=2022)
        session.add(show3)
        session.flush()

        # Season 0 special
        s0_ep = Episode(
            id=300,
            show_id=3,
            trakt_id=3000,
            season_number=0,
            episode_number=1,
            title="Behind the Scenes",
            runtime_minutes=30,
            first_aired=now - timedelta(days=30),
        )
        session.add(s0_ep)

        # Season 1: 3 aired, 1 future
        for ep_num in range(1, 4):
            ep = Episode(
                id=300 + ep_num,
                show_id=3,
                trakt_id=3000 + ep_num,
                season_number=1,
                episode_number=ep_num,
                title=f"S01E{ep_num:02d}",
                runtime_minutes=50,
                first_aired=now - timedelta(days=30 - ep_num),
            )
            session.add(ep)

        # Future episode
        future_ep = Episode(
            id=304,
            show_id=3,
            trakt_id=3004,
            season_number=1,
            episode_number=4,
            title="S01E04 (Future)",
            runtime_minutes=50,
            first_aired=now + timedelta(days=7),
        )
        session.add(future_ep)

        ts3 = TrackedShow(
            account_id=1,
            show_id=3,
            status="watching",
            status_source="auto",
            created_at=now,
            updated_at=now,
        )
        session.add(ts3)

    clock = FrozenClock(now)
    estimator = ShowFinishEstimator(db_engine=engine, account_id=1, clock=clock)
    yield estimator, engine, now
    Base.metadata.drop_all(bind=engine)


def test_estimate_with_manual_pace(estimator_env):
    estimator, _, now = estimator_env

    est = estimator.estimate_show(1)
    assert est is not None
    assert est.title == "Breaking Bad"
    assert est.total_episodes == 10
    assert est.aired_episodes == 10
    assert est.watched_episodes == 6
    assert est.remaining_episodes == 4
    assert est.unwatched_minutes == 4 * 45
    assert est.completion_percent == 60.0
    assert est.episodes_per_week == 2.0
    assert est.pace_source == "manual"
    assert est.is_caught_up is False

    # 4 remaining / 2 per week = 2 weeks = 14 days
    assert est.days_to_finish == 14
    assert est.estimated_finish_date == now + timedelta(days=14)


def test_estimate_caught_up_show(estimator_env):
    estimator, _, _ = estimator_env

    est = estimator.estimate_show(2)
    assert est is not None
    assert est.title == "Chernobyl"
    assert est.total_episodes == 5
    assert est.watched_episodes == 5
    assert est.remaining_episodes == 0
    assert est.completion_percent == 100.0
    assert est.is_caught_up is True
    assert est.days_to_finish == 0
    assert est.estimated_finish_date is None


def test_estimate_with_specials_and_future_airing(estimator_env):
    estimator, engine, now = estimator_env

    est_no_specials = estimator.estimate_show(3)
    assert est_no_specials is not None
    # Season 1 has 4 episodes (3 aired, 1 future)
    assert est_no_specials.total_episodes == 4
    assert est_no_specials.aired_episodes == 3
    assert est_no_specials.next_air_date == now + timedelta(days=7)

    # Enable specials
    with get_db_session(engine) as session:
        ts = session.get(TrackedShow, (1, 3))
        assert ts is not None
        ts.include_specials = True

    est_with_specials = estimator.estimate_show(3)
    assert est_with_specials is not None
    # 4 season 1 episodes + 1 season 0 special = 5
    assert est_with_specials.total_episodes == 5


def test_estimate_all(estimator_env):
    estimator, _, _ = estimator_env

    estimates = estimator.estimate_all()
    assert len(estimates) == 3

    watching_estimates = estimator.estimate_all(status="watching")
    assert len(watching_estimates) == 2
