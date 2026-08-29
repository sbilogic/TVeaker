"""FastAPI application factory and middleware configuration."""

from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from sqlalchemy import Engine
from starlette.middleware.sessions import SessionMiddleware

from tveaker.auth.token_store import KeyringTokenStore, TokenStore
from tveaker.auth.trakt_oauth import TraktOAuth
from tveaker.clock import Clock, SystemClock
from tveaker.config import Settings, get_settings
from tveaker.db import create_db_engine
from tveaker.sync.importer import AccountSync
from tveaker.trakt.client import TraktClient
from tveaker.web.routes import api_router, ui_router


def create_app(
    settings: Settings | None = None,
    db_engine: Engine | None = None,
    token_store: TokenStore | None = None,
    clock: Clock | None = None,
) -> FastAPI:
    """Create and configure the TVeaker FastAPI application."""
    app_settings = settings or get_settings()
    app_engine = db_engine or create_db_engine(app_settings.database_url)
    app_clock = clock or SystemClock()
    app_token_store = token_store or KeyringTokenStore()

    app_oauth = TraktOAuth(settings=app_settings, token_store=app_token_store)
    app_client = TraktClient(settings=app_settings, token_store=app_token_store, oauth=app_oauth)
    app_sync = AccountSync(db_engine=app_engine, trakt_client=app_client, clock=app_clock)

    app = FastAPI(
        title="TVeaker",
        description="Local-first TV and movie intelligence tracker and recommender",
        version="1.0.0",
    )

    # Session middleware for OAuth state
    app.add_middleware(
        SessionMiddleware,
        secret_key=app_settings.secret_key or "tveaker-development-secret-key-32b",
    )

    # Mount static assets
    static_path = Path(__file__).parent / "static"
    if static_path.exists():
        app.mount("/static", StaticFiles(directory=str(static_path)), name="static")

    # Store state
    app.state.settings = app_settings
    app.state.db_engine = app_engine
    app.state.clock = app_clock
    app.state.token_store = app_token_store
    app.state.trakt_oauth = app_oauth
    app.state.trakt_client = app_client
    app.state.account_sync = app_sync

    # Include routers
    app.include_router(ui_router)
    app.include_router(api_router)

    return app
