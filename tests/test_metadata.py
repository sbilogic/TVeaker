"""Metadata hydration regression tests."""

from datetime import UTC, datetime

from sqlalchemy import create_engine, select

from tveaker.db import Base, _configure_sqlite_connection, get_db_session
from tveaker.metadata import MetadataHydrationService, prune_synthetic_episode_catalog
from tveaker.models import Account, Episode, MediaItem, NowWatching, PlaybackState, WatchEvent


def test_tvmaze_hydration_enriches_existing_episodes_without_creating_a_catalog(monkeypatch):
    engine = create_engine("sqlite:///:memory:")
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    with get_db_session(engine) as session:
        show = MediaItem(id=1, media_type="show", trakt_id=10, title="Schedule Test")
        session.add(show)
        session.add(
            Episode(
                id=11,
                show_id=1,
                trakt_id=101,
                season_number=1,
                episode_number=1,
                title="Pilot",
            )
        )

    payload = {
        "premiered": "2026-08-01",
        "averageRuntime": 48,
        "_embedded": {
            "episodes": [
                {
                    "season": 1,
                    "number": 1,
                    "name": "Pilot",
                    "runtime": 47,
                    "airstamp": "2026-08-01T20:00:00+00:00",
                },
                {
                    "season": 1,
                    "number": 2,
                    "name": "Next Week",
                    "runtime": 52,
                    "airdate": "2026-09-05",
                },
            ]
        },
    }
    service = MetadataHydrationService()
    monkeypatch.setattr(service, "fetch_show_data_from_tvmaze", lambda *_args: payload)

    with get_db_session(engine) as session:
        show = session.get(MediaItem, 1)
        assert show is not None
        service.hydrate_item(session, show)

    with get_db_session(engine) as session:
        show = session.get(MediaItem, 1)
        episodes = session.execute(select(Episode).order_by(Episode.episode_number)).scalars().all()

        assert show is not None
        assert show.runtime_minutes == 48
        assert show.first_aired is not None
        assert show.first_aired.replace(tzinfo=UTC) == datetime(2026, 8, 1, tzinfo=UTC)
        assert episodes[0].runtime_minutes == 47
        assert episodes[0].first_aired is not None
        assert episodes[0].first_aired.replace(tzinfo=UTC) == datetime(
            2026, 8, 1, 20, tzinfo=UTC
        )
        assert len(episodes) == 1


def test_prune_synthetic_episode_catalog_removes_only_unreferenced_hydration_rows():
    engine = create_engine("sqlite:///:memory:")
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)
    now = datetime(2026, 9, 4, tzinfo=UTC)

    with get_db_session(engine) as session:
        session.add(
            Account(
                id=1,
                trakt_uuid="account-1",
                username="test",
                timezone="UTC",
                connected_at=now,
            )
        )
        session.add(MediaItem(id=1, media_type="show", trakt_id=10, title="Catalog Test"))
        session.add_all(
            [
                Episode(
                    id=101,
                    show_id=1,
                    trakt_id=1001,
                    season_number=1,
                    episode_number=1,
                    title="Trusted",
                ),
                Episode(
                    id=102,
                    show_id=1,
                    trakt_id=-1002,
                    season_number=1,
                    episode_number=2,
                    title="Hydrated only",
                ),
                Episode(
                    id=103,
                    show_id=1,
                    trakt_id=-1003,
                    season_number=1,
                    episode_number=3,
                    title="Locally watched",
                ),
                Episode(
                    id=104,
                    show_id=1,
                    trakt_id=-1004,
                    season_number=1,
                    episode_number=4,
                    title="Playback in progress",
                ),
                Episode(
                    id=105,
                    show_id=1,
                    trakt_id=-1005,
                    season_number=1,
                    episode_number=5,
                    title="Selected now",
                ),
            ]
        )
        session.add(
            WatchEvent(
                history_id=1,
                account_id=1,
                episode_id=103,
                action="watch",
                watched_at=now,
            )
        )
        session.add(
            PlaybackState(
                playback_id=2,
                account_id=1,
                episode_id=104,
                progress_percent=50.0,
                paused_at=now,
            )
        )
        session.add(NowWatching(account_id=1, episode_id=105, selected_at=now))

    assert prune_synthetic_episode_catalog(engine) == 1

    with get_db_session(engine) as session:
        remaining_ids = set(session.scalars(select(Episode.id)).all())
        assert remaining_ids == {101, 103, 104, 105}
        assert session.get(WatchEvent, 1) is not None
        assert session.get(PlaybackState, 2) is not None
        assert session.get(NowWatching, 1) is not None


def test_tmdb_hydration_fills_missing_movie_metadata_without_overwriting_existing_data():
    engine = create_engine("sqlite:///:memory:")
    from sqlalchemy import event

    event.listen(engine, "connect", _configure_sqlite_connection)
    Base.metadata.create_all(bind=engine)

    class FakeTmdb:
        is_configured = True

        def fetch_media_data(self, _media):
            return {
                "poster_path": "/poster.jpg",
                "backdrop_path": "/backdrop.jpg",
                "runtime": 116,
                "release_date": "2020-07-01",
                "genres": [{"name": "Sci-Fi"}],
                "status": "Released",
            }

    service = MetadataHydrationService(tmdb_service=FakeTmdb())
    with get_db_session(engine) as session:
        movie = MediaItem(id=1, media_type="movie", trakt_id=1, title="A Movie")
        session.add(movie)
        service.hydrate_item(session, movie)

    with get_db_session(engine) as session:
        movie = session.get(MediaItem, 1)
        assert movie is not None
        assert movie.poster_url == "https://image.tmdb.org/t/p/original/poster.jpg"
        assert movie.backdrop_url == "https://image.tmdb.org/t/p/original/backdrop.jpg"
        assert movie.runtime_minutes == 116
        assert movie.first_aired is not None
        assert movie.genres == ["Sci-Fi"]
        assert movie.status == "Released"
