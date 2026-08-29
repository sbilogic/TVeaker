"""Comprehensive tests for TVeaker data models and constraints."""

from datetime import UTC, datetime

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine
from sqlalchemy.exc import IntegrityError

from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    Rating,
    RecommendationFeedback,
    RecommendationRun,
    TrackedShow,
    WatchEvent,
)


@pytest.fixture
def db_engine():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)
    yield engine
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def base_account(db_engine):
    with get_db_session(db_engine) as session:
        account = Account(
            id=1,
            trakt_uuid="uuid-1234",
            username="testuser",
            timezone="UTC",
            connected_at=datetime.now(UTC),
        )
        session.add(account)
    return 1


def test_duplicate_trakt_uuid_fails(db_engine, base_account):
    def insert_dup():
        with get_db_session(db_engine) as session:
            dup = Account(
                id=2,
                trakt_uuid="uuid-1234",
                username="other",
                timezone="UTC",
                connected_at=datetime.now(UTC),
            )
            session.add(dup)

    with pytest.raises(IntegrityError):
        insert_dup()


def test_media_item_unique_type_and_trakt_id(db_engine):
    with get_db_session(db_engine) as session:
        m1 = MediaItem(
            media_type="show",
            trakt_id=100,
            title="Breaking Bad",
            year=2008,
            genres_json='["Crime", "Drama"]',
        )
        session.add(m1)

    def insert_duplicate_media():
        with get_db_session(db_engine) as session:
            m2 = MediaItem(
                media_type="show",
                trakt_id=100,
                title="Breaking Bad Duplicate",
            )
            session.add(m2)

    with pytest.raises(IntegrityError):
        insert_duplicate_media()


def test_media_item_invalid_media_type(db_engine):
    def insert_invalid():
        with get_db_session(db_engine) as session:
            item = MediaItem(
                media_type="invalid_type",
                trakt_id=200,
                title="Invalid",
            )
            session.add(item)

    with pytest.raises(IntegrityError):
        insert_invalid()


def test_watch_event_target_constraints(db_engine, base_account):
    with get_db_session(db_engine) as session:
        show = MediaItem(media_type="show", trakt_id=1, title="Test Show")
        movie = MediaItem(media_type="movie", trakt_id=2, title="Test Movie")
        session.add_all([show, movie])
        session.flush()

        ep = Episode(
            show_id=show.id,
            trakt_id=101,
            season_number=1,
            episode_number=1,
            title="Pilot",
        )
        session.add(ep)
        session.flush()

        # Valid movie watch event
        we_movie = WatchEvent(
            history_id=1,
            account_id=base_account,
            watched_at=datetime.now(UTC),
            action="watch",
            movie_id=movie.id,
        )
        # Valid episode watch event
        we_ep = WatchEvent(
            history_id=2,
            account_id=base_account,
            watched_at=datetime.now(UTC),
            action="watch",
            episode_id=ep.id,
        )
        session.add_all([we_movie, we_ep])

    # Zero targets should fail
    def insert_zero_target():
        with get_db_session(db_engine) as session:
            we = WatchEvent(
                history_id=3,
                account_id=base_account,
                watched_at=datetime.now(UTC),
                action="watch",
            )
            session.add(we)

    with pytest.raises(IntegrityError):
        insert_zero_target()

    # Two targets should fail
    def insert_two_targets():
        with get_db_session(db_engine) as session:
            we = WatchEvent(
                history_id=4,
                account_id=base_account,
                watched_at=datetime.now(UTC),
                action="watch",
                movie_id=1,
                episode_id=1,
            )
            session.add(we)

    with pytest.raises(IntegrityError):
        insert_two_targets()


def test_rating_range_and_media_type_constraints(db_engine, base_account):
    # Rating below 1 fails
    def insert_low_rating():
        with get_db_session(db_engine) as session:
            r = Rating(
                account_id=base_account,
                media_type="movie",
                trakt_id=50,
                rating=0,
                rated_at=datetime.now(UTC),
            )
            session.add(r)

    with pytest.raises(IntegrityError):
        insert_low_rating()

    # Rating above 10 fails
    def insert_high_rating():
        with get_db_session(db_engine) as session:
            r = Rating(
                account_id=base_account,
                media_type="movie",
                trakt_id=50,
                rating=11,
                rated_at=datetime.now(UTC),
            )
            session.add(r)

    with pytest.raises(IntegrityError):
        insert_high_rating()

    # Valid rating succeeds
    with get_db_session(db_engine) as session:
        r = Rating(
            account_id=base_account,
            media_type="movie",
            trakt_id=50,
            rating=9,
            rated_at=datetime.now(UTC),
        )
        session.add(r)


def test_tracked_show_constraints(db_engine, base_account):
    with get_db_session(db_engine) as session:
        show = MediaItem(media_type="show", trakt_id=500, title="Dark")
        session.add(show)
        session.flush()

        tracked = TrackedShow(
            account_id=base_account,
            show_id=show.id,
            status="watching",
            status_source="manual",
            include_specials=False,
            manual_episodes_per_week=3.5,
            priority=1,
            created_at=datetime.now(UTC),
            updated_at=datetime.now(UTC),
        )
        session.add(tracked)

    # Invalid status fails
    def insert_invalid_status():
        with get_db_session(db_engine) as session:
            t = TrackedShow(
                account_id=base_account,
                show_id=1,
                status="invalid_status",
                status_source="manual",
                created_at=datetime.now(UTC),
                updated_at=datetime.now(UTC),
            )
            session.add(t)

    with pytest.raises(IntegrityError):
        insert_invalid_status()

    # Negative manual pace fails
    def insert_negative_pace():
        with get_db_session(db_engine) as session:
            t = TrackedShow(
                account_id=base_account,
                show_id=1,
                status="watching",
                status_source="manual",
                manual_episodes_per_week=-2.0,
                created_at=datetime.now(UTC),
                updated_at=datetime.now(UTC),
            )
            session.add(t)

    with pytest.raises(IntegrityError):
        insert_negative_pace()


def test_recommendation_feedback_action_constraint(db_engine):
    with get_db_session(db_engine) as session:
        run = RecommendationRun(
            created_at=datetime.now(UTC),
            context_json="{}",
            model_version="content-v1",
            ranked_candidates_json="[]",
        )
        session.add(run)
        session.flush()

        fb = RecommendationFeedback(
            run_id=run.id,
            candidate_id="movie:123",
            action="accepted",
            created_at=datetime.now(UTC),
        )
        session.add(fb)

    def insert_invalid_action():
        with get_db_session(db_engine) as session:
            fb_bad = RecommendationFeedback(
                run_id=1,
                candidate_id="movie:123",
                action="disliked",
                created_at=datetime.now(UTC),
            )
            session.add(fb_bad)

    with pytest.raises(IntegrityError):
        insert_invalid_action()


def test_alembic_migration_upgrade_downgrade(tmp_path):
    db_file = tmp_path / "test_migration.db"
    alembic_cfg = Config("alembic.ini")
    alembic_cfg.set_main_option("sqlalchemy.url", f"sqlite:///{db_file.as_posix()}")

    # Upgrade to head
    command.upgrade(alembic_cfg, "head")

    # Downgrade to base
    command.downgrade(alembic_cfg, "base")

    # Re-upgrade to head
    command.upgrade(alembic_cfg, "head")
