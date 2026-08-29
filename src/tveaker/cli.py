"""TVeaker Command-Line Interface (CLI)."""

import logging

import click
from sqlalchemy import text

from tveaker.auth.token_store import KeyringTokenStore
from tveaker.auth.trakt_oauth import TraktOAuth
from tveaker.backup import online_sqlite_backup
from tveaker.config import get_settings
from tveaker.db import create_db_engine, get_db_session
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    Rating,
    TrackedShow,
    WatchEvent,
    WatchlistItem,
)
from tveaker.recommender.engine import RecommendationEngine
from tveaker.recommender.ranker import RankingContext
from tveaker.scheduler import SyncScheduler
from tveaker.sync.export_importer import TraktExportImporter
from tveaker.sync.importer import AccountSync
from tveaker.trakt.client import TraktClient

logger = logging.getLogger(__name__)


@click.group()
def cli() -> None:
    """TVeaker - Local-first TV & Movie Intelligence."""


@cli.command()
def doctor() -> None:
    """Perform system diagnostic checks on database, tokens, and storage."""
    settings = get_settings()
    click.echo("========================================")
    click.echo(" TVeaker System Diagnostics")
    click.echo("========================================")

    engine = create_db_engine(settings.database_url)

    # 1. DB Integrity Check
    try:
        with engine.connect() as conn:
            integrity = conn.execute(text("PRAGMA integrity_check")).scalar()
            wal_mode = conn.execute(text("PRAGMA journal_mode")).scalar()
        click.echo(f"  [SQLite Integrity]    : {integrity}")
        click.echo(f"  [SQLite Journal Mode] : {wal_mode}")
    except Exception as e:
        click.echo(f"  [SQLite Integrity]    : ERROR: {e}")

    # 2. Token Store Check
    token_store = KeyringTokenStore()
    token = token_store.get_token()
    if token:
        expired = token.is_expired()
        status = "EXPIRED" if expired else "VALID"
        click.echo(f"  [Trakt OAuth Token]   : {status} (Expires in: {token.expires_in}s)")
    else:
        click.echo("  [Trakt OAuth Token]   : NOT FOUND (Run login or upload export)")

    # 3. Table Counts
    try:
        with get_db_session(engine) as session:
            acc = session.get(Account, 1)
            media_cnt = session.query(MediaItem).count()
            ep_cnt = session.query(Episode).count()
            watch_cnt = session.query(WatchEvent).count()
            track_cnt = session.query(TrackedShow).count()
            rating_cnt = session.query(Rating).count()
            wl_cnt = session.query(WatchlistItem).count()

            last_sync = (
                acc.last_successful_sync_at.isoformat()
                if acc and acc.last_successful_sync_at
                else "Never"
            )
            user_name = acc.username if acc else "None"
            click.echo(f"  [Account Status]      : User: {user_name} (Last Sync: {last_sync})")
            click.echo(
                f"  [Database Catalog]    : Media: {media_cnt} | Episodes: {ep_cnt} | "
                f"Watches: {watch_cnt} | Tracked: {track_cnt} | Ratings: {rating_cnt} | "
                f"Watchlist: {wl_cnt}"
            )
    except Exception as e:
        click.echo(f"  [Database Catalog]    : ERROR: {e}")

    click.echo("========================================")


@cli.command("import-export")
@click.argument("zip_path", type=click.Path(exists=True))
def import_export(zip_path: str) -> None:
    """Import data from a Trakt GDPR/Account export ZIP file."""
    settings = get_settings()
    engine = create_db_engine(settings.database_url)
    importer = TraktExportImporter(db_engine=engine)

    click.echo(f"Importing Trakt export from {zip_path}...")
    report = importer.import_zip(zip_path)

    click.echo("========================================")
    click.echo(f" Trakt Export Imported for @{report.account_username}")
    click.echo("========================================")
    click.echo(f"  [Shows]          : {report.shows_count}")
    click.echo(f"  [Movies]         : {report.movies_count}")
    click.echo(f"  [Episodes]       : {report.episodes_count}")
    click.echo(f"  [Watch Events]   : {report.watch_events_count}")
    click.echo(f"  [Watchlist Items]: {report.watchlist_count}")
    click.echo(f"  [Ratings]        : {report.ratings_count}")
    click.echo("========================================")


@cli.command()
@click.option(
    "--mode",
    type=click.Choice(["incremental", "initial", "full"]),
    default="incremental",
    help="Sync mode to execute.",
)
def sync(mode: str) -> None:
    """Execute a manual synchronization against Trakt API."""
    settings = get_settings()
    engine = create_db_engine(settings.database_url)
    token_store = KeyringTokenStore()
    oauth = TraktOAuth(settings=settings, token_store=token_store)
    client = TraktClient(settings=settings, token_store=token_store, oauth=oauth)
    syncer = AccountSync(db_engine=engine, trakt_client=client)

    click.echo(f"Starting {mode} sync with Trakt...")
    report = syncer.run(mode=mode)  # type: ignore

    click.echo(f"Sync finished: status={report.status} (Run #{report.run_id})")
    click.echo(f"Fetched: {report.fetched}")
    click.echo(f"Inserted: {report.inserted}")
    click.echo(f"Updated: {report.updated}")
    click.echo(f"Deleted: {report.deleted}")


@cli.command()
@click.option("--budget", type=int, default=None, help="Available time budget in minutes.")
@click.option(
    "--intent",
    type=click.Choice(["auto", "finish_show", "start_new", "movie", "show"]),
    default="auto",
    help="Recommendation intent.",
)
@click.option("--limit", type=int, default=5, help="Maximum number of recommendations.")
def recommend(budget: int | None, intent: str, limit: int) -> None:
    """Generate recommendations based on local taste profile and time budget."""
    settings = get_settings()
    engine = create_db_engine(settings.database_url)
    recommender = RecommendationEngine(db_engine=engine)

    ctx = RankingContext(time_budget_minutes=budget, intent=intent)  # type: ignore
    result = recommender.recommend(context=ctx, limit=limit)

    click.echo(f"\nRecommendations (Run #{result.run_id}):")
    click.echo("-" * 70)
    for idx, item in enumerate(result.items, 1):
        rt = f"{item.runtime_minutes}m" if item.runtime_minutes else "n/a"
        pct = int(item.score * 100)
        click.echo(f"{idx}. {item.title} [{item.media_type.upper()}] ({rt}) - {pct}% match")
        click.echo(f"   Reason: {item.explanation}")
    click.echo("-" * 70)


@cli.command()
@click.option(
    "--dest",
    type=click.Path(),
    default="data/backup_tveaker.db",
    help="Output destination path for SQLite hot backup.",
)
def backup(dest: str) -> None:
    """Create an online hot backup of the SQLite database."""
    settings = get_settings()
    click.echo(f"Creating online hot backup to {dest}...")
    engine = create_db_engine(settings.database_url)
    res = online_sqlite_backup(engine, dest)
    click.echo(f"Backup created successfully: {res.destination_path} ({res.bytes_written} bytes)")


@cli.command()
@click.option("--host", type=str, default="127.0.0.1", help="Host address to bind to.")
@click.option("--port", type=int, default=8000, help="Port to listen on.")
@click.option("--scheduler/--no-scheduler", default=True, help="Enable background sync scheduler.")
def serve(host: str, port: int, scheduler: bool) -> None:
    """Start the TVeaker web server and REST API."""
    import uvicorn

    from tveaker.web.app import create_app

    settings = get_settings()
    engine = create_db_engine(settings.database_url)
    token_store = KeyringTokenStore()
    oauth = TraktOAuth(settings=settings, token_store=token_store)
    client = TraktClient(settings=settings, token_store=token_store, oauth=oauth)
    syncer = AccountSync(db_engine=engine, trakt_client=client)

    sched = None
    if scheduler:
        sched = SyncScheduler(account_sync=syncer)
        sched.start()

    try:
        app = create_app(settings=settings, db_engine=engine, token_store=token_store)
        uvicorn.run(app, host=host, port=port)
    finally:
        if sched is not None:
            sched.stop()


def main() -> None:
    cli()


if __name__ == "__main__":
    main()
