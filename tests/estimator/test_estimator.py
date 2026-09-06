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
    assert est_no_specials.include_specials is False
    # Season 1 has 4 episodes (3 aired, 1 future)
    assert est_no_specials.total_episodes == 4
    assert est_no_specials.aired_episodes == 3
    assert est_no_specials.unaired_episodes == 1
    assert est_no_specials.remaining_episodes == 3
    assert est_no_specials.unwatched_minutes == 3 * 50
    assert est_no_specials.next_air_date == now + timedelta(days=7)

    # Enable specials
    with get_db_session(engine) as session:
        ts = session.get(TrackedShow, (1, 3))
        assert ts is not None
        ts.include_specials = True

    est_with_specials = estimator.estimate_show(3)
    assert est_with_specials is not None
    assert est_with_specials.include_specials is True
    # 4 season 1 episodes + 1 season 0 special = 5
    assert est_with_specials.total_episodes == 5
    assert est_with_specials.aired_episodes == 4
    assert est_with_specials.remaining_episodes == 4


def test_unknown_air_dates_are_not_to_watch_and_show_runtime_is_the_fallback(estimator_env):
    estimator, engine, now = estimator_env

    with get_db_session(engine) as session:
        show = MediaItem(
            id=4,
            media_type="show",
            trakt_id=104,
            title="Release Date Test",
            runtime_minutes=44,
        )
        session.add(show)
        session.add_all(
            [
                Episode(
                    id=401,
                    show_id=4,
                    trakt_id=4001,
                    season_number=1,
                    episode_number=1,
                    title="Aired, runtime missing",
                    runtime_minutes=None,
                    first_aired=now - timedelta(days=1),
                ),
                Episode(
                    id=402,
                    show_id=4,
                    trakt_id=4002,
                    season_number=1,
                    episode_number=2,
                    title="Future",
                    runtime_minutes=51,
                    first_aired=now + timedelta(days=7),
                ),
                Episode(
                    id=403,
                    show_id=4,
                    trakt_id=4003,
                    season_number=1,
                    episode_number=3,
                    title="Schedule unknown",
                    runtime_minutes=55,
                    first_aired=None,
                ),
            ]
        )
        session.add(
            TrackedShow(
                account_id=1,
                show_id=4,
                status="watching",
                status_source="auto",
                created_at=now,
                updated_at=now,
            )
        )

    estimate = estimator.estimate_show(4)

    assert estimate is not None
    assert estimate.total_episodes == 3
    assert estimate.aired_episodes == 1
    assert estimate.unaired_episodes == 2
    assert estimate.remaining_episodes == 1
    assert estimate.unwatched_minutes == 44
    assert estimate.avg_runtime_minutes == 44
    assert estimate.next_air_date == now + timedelta(days=7)


def test_watched_episode_without_an_air_date_is_evidence_that_it_has_aired(estimator_env):
    """Imported Trakt history must never disappear just because catalog metadata is incomplete."""
    estimator, engine, now = estimator_env

    with get_db_session(engine) as session:
        show = MediaItem(id=5, media_type="show", trakt_id=105, title="History Is Evidence")
        session.add(show)
        session.add_all(
            [
                Episode(
                    id=501,
                    show_id=5,
                    trakt_id=5001,
                    season_number=1,
                    episode_number=1,
                    title="Known release",
                    runtime_minutes=30,
                    first_aired=now - timedelta(days=1),
                ),
                Episode(
                    id=502,
                    show_id=5,
                    trakt_id=5002,
                    season_number=1,
                    episode_number=2,
                    title="Imported history, date missing",
                    runtime_minutes=30,
                    first_aired=None,
                ),
                Episode(
                    id=503,
                    show_id=5,
                    trakt_id=5003,
                    season_number=1,
                    episode_number=3,
                    title="Future",
                    runtime_minutes=30,
                    first_aired=now + timedelta(days=7),
                ),
            ]
        )
        session.add_all(
            [
                WatchEvent(
                    history_id=50001,
                    account_id=1,
                    watched_at=now,
                    action="watch",
                    episode_id=502,
                ),
                TrackedShow(
                    account_id=1,
                    show_id=5,
                    status="watching",
                    status_source="auto",
                    created_at=now,
                    updated_at=now,
                ),
            ]
        )

    estimate = estimator.estimate_show(5)

    assert estimate is not None
    assert estimate.total_episodes == 3
    assert estimate.aired_episodes == 2
    assert estimate.unaired_episodes == 1
    assert estimate.watched_episodes == 1
    assert estimate.remaining_episodes == 1
    assert estimate.completion_percent == 50.0


def test_synthetic_metadata_episodes_do_not_create_false_remaining_work(estimator_env):
    """TVMaze enrichment must not turn unverified provider rows into a viewing backlog."""
    estimator, engine, now = estimator_env

    with get_db_session(engine) as session:
        show = MediaItem(id=6, media_type="show", trakt_id=106, title="Authoritative Catalog")
        session.add(show)
        session.add_all(
            [
                Episode(
                    id=601,
                    show_id=6,
                    trakt_id=6001,
                    season_number=1,
                    episode_number=1,
                    title="Imported watch",
                    runtime_minutes=30,
                    first_aired=None,
                ),
                Episode(
                    id=602,
                    show_id=6,
                    trakt_id=-6002,
                    season_number=2,
                    episode_number=1,
                    title="Hydrated-only episode",
                    runtime_minutes=30,
                    first_aired=now - timedelta(days=1),
                ),
            ]
        )
        session.add_all(
            [
                WatchEvent(
                    history_id=60001,
                    account_id=1,
                    watched_at=now,
                    action="watch",
                    episode_id=601,
                ),
                TrackedShow(
                    account_id=1,
                    show_id=6,
                    status="watching",
                    status_source="auto",
                    created_at=now,
                    updated_at=now,
                ),
            ]
        )

    estimate = estimator.estimate_show(6)

    assert estimate is not None
    assert estimate.total_episodes == 1
    assert estimate.watched_episodes == 1
    assert estimate.remaining_episodes == 0
    assert estimate.is_caught_up is True


def test_trakt_aired_snapshot_caps_provider_candidates_without_hiding_real_remaining_work(
    estimator_env,
):
    """Provider rows may fill a Trakt-confirmed gap, never expand beyond it."""
    estimator, engine, now = estimator_env

    with get_db_session(engine) as session:
        session.add(
            MediaItem(
                id=7,
                media_type="show",
                trakt_id=107,
                title="Snapshot-backed Show",
                trakt_aired_episodes=2,
                runtime_minutes=30,
            )
        )
        session.add_all(
            [
                Episode(
                    id=701,
                    show_id=7,
                    trakt_id=7001,
                    season_number=1,
                    episode_number=1,
                    title="Imported watch",
                    runtime_minutes=30,
                    first_aired=now - timedelta(days=2),
                ),
                Episode(
                    id=702,
                    show_id=7,
                    trakt_id=-7002,
                    season_number=1,
                    episode_number=2,
                    title="Confirmed-gap candidate",
                    runtime_minutes=30,
                    first_aired=now - timedelta(days=1),
                ),
                Episode(
                    id=703,
                    show_id=7,
                    trakt_id=-7003,
                    season_number=2,
                    episode_number=1,
                    title="Provider future overreach",
                    runtime_minutes=30,
                    first_aired=now - timedelta(days=1),
                ),
                WatchEvent(
                    history_id=70001,
                    account_id=1,
                    watched_at=now,
                    action="watch",
                    episode_id=701,
                ),
                TrackedShow(
                    account_id=1,
                    show_id=7,
                    status="watching",
                    status_source="auto",
                    created_at=now,
                    updated_at=now,
                ),
            ]
        )

    estimate = estimator.estimate_show(7)

    assert estimate is not None
    assert estimate.total_episodes == 2
    assert estimate.aired_episodes == 2
    assert estimate.watched_episodes == 1
    assert estimate.remaining_episodes == 1
    assert estimate.unwatched_minutes == 30


def test_estimate_all(estimator_env):
    estimator, _, _ = estimator_env

    estimates = estimator.estimate_all()
    assert len(estimates) == 3

    watching_estimates = estimator.estimate_all(status="watching")
    assert len(watching_estimates) == 2
