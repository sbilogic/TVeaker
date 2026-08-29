"""FastAPI routers for HTML UI views and REST API endpoints."""

import logging
from dataclasses import asdict
from pathlib import Path
from typing import Any, Literal

from fastapi import APIRouter, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, Field
from sqlalchemy import Engine, desc, select

from tveaker.auth.token_store import TokenStore
from tveaker.auth.trakt_oauth import TraktOAuth, TraktOAuthError
from tveaker.clock import Clock
from tveaker.config import Settings
from tveaker.db import get_db_session
from tveaker.estimator.estimator import ShowFinishEstimator
from tveaker.models import (
    Account,
    Episode,
    MediaItem,
    RecommendationFeedback,
    RecommendationRun,
    SyncCursor,
    SyncRun,
    WatchEvent,
)
from tveaker.recommender.engine import RecommendationEngine
from tveaker.recommender.ranker import IntentType, RankingContext
from tveaker.sync.export_importer import TraktExportImporter
from tveaker.sync.importer import AccountSync
from tveaker.tracking.manager import LocalShowTracker, TrackingStatus
from tveaker.trakt.client import TraktClient

logger = logging.getLogger(__name__)

templates_dir = Path(__file__).parent / "templates"
templates = Jinja2Templates(directory=str(templates_dir))

ui_router = APIRouter()
api_router = APIRouter(prefix="/api/v1")


# ---------------------------------------------------------
# Request / Response Schemas for API
# ---------------------------------------------------------
class SyncTriggerRequest(BaseModel):
    mode: Literal["initial", "incremental", "full"] = "incremental"


class UpdateShowRequest(BaseModel):
    status: TrackingStatus | None = None
    include_specials: bool | None = None
    manual_episodes_per_week: float | None = Field(default=None, gt=0)


class FeedbackRequest(BaseModel):
    run_id: int
    candidate_id: str
    action: Literal["accepted", "not_now", "not_interested"]


# ---------------------------------------------------------
# HTML UI Routes
# ---------------------------------------------------------
@ui_router.get("/", response_class=HTMLResponse)
def view_dashboard(request: Request) -> HTMLResponse:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock

    with get_db_session(db_engine) as session:
        account = session.get(Account, 1)

    estimator = ShowFinishEstimator(db_engine=db_engine, clock=clock)
    estimates = estimator.estimate_all(status="watching")

    recommender = RecommendationEngine(db_engine=db_engine, clock=clock)
    rec_result = recommender.recommend(context=RankingContext(intent="auto"), limit=6)

    return templates.TemplateResponse(
        request=request,
        name="dashboard.html",
        context={
            "active_page": "dashboard",
            "account": account,
            "estimates": estimates,
            "recommendations": rec_result.items,
            "run_id": rec_result.run_id,
        },
    )


@ui_router.get("/shows", response_class=HTMLResponse)
def view_shows(
    request: Request, status: TrackingStatus | None = Query(default=None)
) -> HTMLResponse:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock

    with get_db_session(db_engine) as session:
        account = session.get(Account, 1)

    estimator = ShowFinishEstimator(db_engine=db_engine, clock=clock)
    all_estimates = estimator.estimate_all(status=status)

    return templates.TemplateResponse(
        request=request,
        name="shows.html",
        context={
            "active_page": "shows",
            "account": account,
            "shows": all_estimates,
            "current_status": status,
        },
    )


@ui_router.get("/recommendations", response_class=HTMLResponse)
def view_recommendations(
    request: Request,
    time_budget_minutes: int | None = Query(default=None),
    intent: IntentType = Query(default="auto"),
) -> HTMLResponse:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock

    with get_db_session(db_engine) as session:
        account = session.get(Account, 1)

    recommender = RecommendationEngine(db_engine=db_engine, clock=clock)
    rec_result = recommender.recommend(
        context=RankingContext(time_budget_minutes=time_budget_minutes, intent=intent),
        limit=12,
    )

    return templates.TemplateResponse(
        request=request,
        name="recommendations.html",
        context={
            "active_page": "recommendations",
            "account": account,
            "recommendations": rec_result.items,
            "run_id": rec_result.run_id,
            "time_budget": time_budget_minutes,
            "intent": intent,
        },
    )


@ui_router.get("/history", response_class=HTMLResponse)
def view_history(request: Request, limit: int = 100) -> HTMLResponse:
    db_engine: Engine = request.app.state.db_engine

    with get_db_session(db_engine) as session:
        account = session.get(Account, 1)

        stmt = (
            select(WatchEvent, MediaItem, Episode)
            .outerjoin(MediaItem, MediaItem.id == WatchEvent.movie_id)
            .outerjoin(Episode, Episode.id == WatchEvent.episode_id)
            .where(WatchEvent.account_id == 1)
            .order_by(desc(WatchEvent.watched_at))
            .limit(limit)
        )
        rows = session.execute(stmt).all()

        history_items = []
        for we, movie, ep in rows:
            if we.movie_id and movie:
                history_items.append(
                    {
                        "watched_at": we.watched_at,
                        "media_type": "movie",
                        "title": movie.title,
                        "action": we.action,
                        "season_number": None,
                        "episode_number": None,
                        "episode_title": None,
                    }
                )
            elif we.episode_id and ep:
                show = session.get(MediaItem, ep.show_id)
                history_items.append(
                    {
                        "watched_at": we.watched_at,
                        "media_type": "episode",
                        "title": show.title if show else "Show",
                        "action": we.action,
                        "season_number": ep.season_number,
                        "episode_number": ep.episode_number,
                        "episode_title": ep.title,
                    }
                )

    return templates.TemplateResponse(
        request=request,
        name="history.html",
        context={
            "active_page": "history",
            "account": account,
            "history": history_items,
        },
    )


@ui_router.get("/settings", response_class=HTMLResponse)
def view_settings(
    request: Request,
    error: str | None = Query(default=None),
    msg: str | None = Query(default=None),
) -> HTMLResponse:
    db_engine: Engine = request.app.state.db_engine
    settings: Settings = request.app.state.settings

    with get_db_session(db_engine) as session:
        account = session.get(Account, 1)
        sync_runs = (
            session.execute(select(SyncRun).order_by(desc(SyncRun.started_at)).limit(20))
            .scalars()
            .all()
        )

    return templates.TemplateResponse(
        request=request,
        name="settings.html",
        context={
            "active_page": "settings",
            "account": account,
            "sync_runs": sync_runs,
            "settings": settings,
            "is_trakt_configured": settings.is_trakt_configured,
            "error": error,
            "msg": msg,
        },
    )


@ui_router.post("/settings/import-export")
async def handle_import_export(
    request: Request,
    export_file: UploadFile | None = File(default=None),
) -> RedirectResponse:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock
    importer = TraktExportImporter(db_engine=db_engine, clock=clock)

    if export_file and export_file.filename:
        report = importer.import_zip(export_file.file)
        return RedirectResponse(
            url=f"/settings?msg=Export+imported+successfully!+({report.watch_events_count}+watches,+{report.shows_count}+shows)",
            status_code=303,
        )

    # Check default folder
    default_export = Path("exports from trakt/trakt-export-sahilbloch.zip")
    if default_export.exists():
        report = importer.import_zip(default_export)
        return RedirectResponse(
            url=f"/settings?msg=Export+imported+successfully!+({report.watch_events_count}+watches,+{report.shows_count}+shows)",
            status_code=303,
        )

    return RedirectResponse(url="/settings?error=no_file_uploaded", status_code=303)


@ui_router.post("/settings/credentials")
def update_credentials(
    request: Request,
    client_id: str = Form(...),
    client_secret: str = Form(...),
    redirect_uri: str = Form("http://127.0.0.1:8000/auth/trakt/callback"),
) -> RedirectResponse:
    settings: Settings = request.app.state.settings
    settings.trakt_client_id = client_id.strip()
    settings.trakt_client_secret = client_secret.strip()
    settings.trakt_redirect_uri = redirect_uri.strip()

    # Persist to .env file
    env_path = Path(".env")
    env_content = (
        f"TRAKT_CLIENT_ID={settings.trakt_client_id}\n"
        f"TRAKT_CLIENT_SECRET={settings.trakt_client_secret}\n"
        f"TRAKT_REDIRECT_URI={settings.trakt_redirect_uri}\n"
    )
    env_path.write_text(env_content, encoding="utf-8")

    # Refresh OAuth & Client instances
    token_store: TokenStore = request.app.state.token_store
    new_oauth = TraktOAuth(settings=settings, token_store=token_store)
    new_client = TraktClient(settings=settings, token_store=token_store, oauth=new_oauth)
    new_sync = AccountSync(
        db_engine=request.app.state.db_engine,
        trakt_client=new_client,
        clock=request.app.state.clock,
    )

    request.app.state.trakt_oauth = new_oauth
    request.app.state.trakt_client = new_client
    request.app.state.account_sync = new_sync

    return RedirectResponse(url="/auth/login", status_code=303)


# ---------------------------------------------------------
# Auth Routes
# ---------------------------------------------------------
@ui_router.get("/auth/login")
@ui_router.get("/auth/trakt/login")
def auth_login(request: Request) -> RedirectResponse:
    oauth: TraktOAuth = request.app.state.trakt_oauth
    if not oauth.settings.is_trakt_configured:
        return RedirectResponse(url="/settings?error=missing_credentials", status_code=303)

    try:
        auth_url, state = oauth.get_authorization_url()
        request.session["oauth_state"] = state
        return RedirectResponse(auth_url)
    except TraktOAuthError:
        return RedirectResponse(url="/settings?error=missing_credentials", status_code=303)


@ui_router.get("/auth/callback")
@ui_router.get("/auth/trakt/callback")
def auth_callback(
    request: Request, code: str = Query(default=""), state: str | None = Query(default=None)
) -> RedirectResponse:
    oauth: TraktOAuth = request.app.state.trakt_oauth
    expected_state = request.session.get("oauth_state")

    if not code:
        raise HTTPException(status_code=400, detail="Missing authorization code.")
    if not state or not expected_state:
        raise HTTPException(status_code=400, detail="Invalid OAuth state.")

    oauth.exchange_code_for_token(code=code, state=state, expected_state=str(expected_state))

    # Trigger initial sync in background / directly
    sync: AccountSync = request.app.state.account_sync
    try:
        sync.run("initial")
    except Exception as e:
        logger.error("Initial sync on login failed: %s", e)

    return RedirectResponse(url="/", status_code=303)


@ui_router.post("/auth/disconnect")
def auth_disconnect(request: Request) -> RedirectResponse:
    oauth: TraktOAuth = request.app.state.trakt_oauth
    oauth.disconnect()
    return RedirectResponse(url="/settings", status_code=303)


# ---------------------------------------------------------
# REST API Endpoints (/api/v1/*)
# ---------------------------------------------------------
@api_router.get("/health")
def api_health(request: Request) -> dict[str, Any]:
    db_engine: Engine = request.app.state.db_engine
    token_store: TokenStore = request.app.state.token_store
    clock: Clock = request.app.state.clock

    db_ok = False
    last_sync = None
    username = None

    try:
        with get_db_session(db_engine) as session:
            acc = session.get(Account, 1)
            if acc:
                last_sync = acc.last_successful_sync_at
                username = acc.username
            db_ok = True
    except Exception as e:
        logger.error("DB healthcheck error: %s", e)

    token = token_store.get_token()
    token_ok = token is not None and not token.is_expired(now_ts=int(clock.now().timestamp()))

    return {
        "status": "healthy" if db_ok else "degraded",
        "timestamp": clock.now().isoformat(),
        "database_connected": db_ok,
        "trakt_authenticated": token_ok,
        "username": username,
        "last_sync_at": last_sync.isoformat() if last_sync else None,
    }


@api_router.get("/sync/status")
def api_sync_status(request: Request) -> dict[str, Any]:
    db_engine: Engine = request.app.state.db_engine

    with get_db_session(db_engine) as session:
        cursors = (
            session.execute(select(SyncCursor).where(SyncCursor.account_id == 1)).scalars().all()
        )
        cursor_data = {
            c.dataset: {
                "remote_activity_at": c.remote_activity_at.isoformat(),
                "last_success_at": c.last_success_at.isoformat(),
            }
            for c in cursors
        }

        recent_runs = (
            session.execute(select(SyncRun).order_by(desc(SyncRun.started_at)).limit(10))
            .scalars()
            .all()
        )
        runs_data = [
            {
                "id": r.id,
                "mode": r.mode,
                "status": r.status,
                "started_at": r.started_at.isoformat() if r.started_at else None,
                "finished_at": r.finished_at.isoformat() if r.finished_at else None,
                "counts": r.counts,
                "warnings": r.warnings,
            }
            for r in recent_runs
        ]

    return {
        "cursors": cursor_data,
        "recent_runs": runs_data,
    }


@api_router.post("/sync/trigger")
def api_sync_trigger(request: Request, payload: SyncTriggerRequest) -> dict[str, Any]:
    sync: AccountSync = request.app.state.account_sync
    report = sync.run(payload.mode)
    return asdict(report)


@api_router.get("/shows")
def api_list_shows(
    request: Request, status: TrackingStatus | None = Query(default=None)
) -> list[dict[str, Any]]:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock

    estimator = ShowFinishEstimator(db_engine=db_engine, clock=clock)
    estimates = estimator.estimate_all(status=status)
    return [asdict(e) for e in estimates]


@api_router.patch("/shows/{show_id}")
def api_update_show(request: Request, show_id: int, payload: UpdateShowRequest) -> dict[str, Any]:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock
    tracker = LocalShowTracker(db_engine=db_engine, clock=clock)

    show = tracker.get(show_id)
    if show is None:
        raise HTTPException(status_code=404, detail=f"Show {show_id} not found.")

    if payload.status is not None:
        show = tracker.set_status(show_id, payload.status)
    if payload.include_specials is not None:
        show = tracker.set_include_specials(show_id, payload.include_specials)
    if payload.manual_episodes_per_week is not None:
        show = tracker.set_manual_pace(show_id, payload.manual_episodes_per_week)

    return asdict(show)


@api_router.post("/shows/{show_id}/quick-scrobble")
def api_quick_scrobble(request: Request, show_id: int) -> dict[str, Any]:
    """Mark the next unwatched episode of a show as watched locally."""
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock
    now = clock.now()

    with get_db_session(db_engine) as session:
        watched_ep_ids = set(
            session.execute(
                select(WatchEvent.episode_id)
                .join(Episode, WatchEvent.episode_id == Episode.id)
                .where(
                    WatchEvent.account_id == 1,
                    Episode.show_id == show_id,
                )
            )
            .scalars()
            .all()
        )

        unwatched_ep = (
            session.execute(
                select(Episode)
                .where(
                    Episode.show_id == show_id,
                    Episode.season_number > 0,
                    Episode.id.not_in(watched_ep_ids),
                )
                .order_by(Episode.season_number.asc(), Episode.episode_number.asc())
            )
            .scalars()
            .first()
        )

        if unwatched_ep is None:
            raise HTTPException(
                status_code=400, detail="No unwatched episodes remaining for this show."
            )

        event = WatchEvent(
            account_id=1,
            episode_id=unwatched_ep.id,
            action="watch",
            watched_at=now,
        )
        session.add(event)
        session.flush()

        ep_info = {
            "id": unwatched_ep.id,
            "season_number": unwatched_ep.season_number,
            "episode_number": unwatched_ep.episode_number,
            "title": unwatched_ep.title,
        }

    estimator = ShowFinishEstimator(db_engine=db_engine, clock=clock)
    updated_est = estimator.estimate_show(show_id)

    return {
        "success": True,
        "scrobbled_episode": ep_info,
        "updated_estimate": asdict(updated_est) if updated_est else None,
    }


@api_router.get("/shows/{show_id}/unwatched")
def api_get_unwatched_episodes(request: Request, show_id: int) -> dict[str, Any]:
    """Get the exact list of remaining unwatched episodes for a show."""
    db_engine: Engine = request.app.state.db_engine
    with get_db_session(db_engine) as session:
        media = session.get(MediaItem, show_id)
        if media is None:
            raise HTTPException(status_code=404, detail=f"Show {show_id} not found.")

        # Get watched episode IDs
        watched_ep_ids = set(
            session.execute(
                select(WatchEvent.episode_id)
                .join(Episode, WatchEvent.episode_id == Episode.id)
                .where(
                    WatchEvent.account_id == 1,
                    Episode.show_id == show_id,
                )
            )
            .scalars()
            .all()
        )

        all_eps = (
            session.execute(
                select(Episode)
                .where(
                    Episode.show_id == show_id,
                    Episode.season_number > 0,
                )
                .order_by(Episode.season_number.asc(), Episode.episode_number.asc())
            )
            .scalars()
            .all()
        )

        unwatched = [
            {
                "id": ep.id,
                "season_number": ep.season_number,
                "episode_number": ep.episode_number,
                "title": ep.title,
                "overview": ep.overview,
                "runtime_minutes": ep.runtime_minutes or media.runtime_minutes or 42,
                "first_aired": ep.first_aired.isoformat() if ep.first_aired else None,
            }
            for ep in all_eps
            if ep.id not in watched_ep_ids
        ]

        total_unwatched_mins = sum(
            int(ep["runtime_minutes"])
            for ep in unwatched
            if isinstance(ep["runtime_minutes"], (int, float))
        )

        return {
            "show_id": show_id,
            "title": media.title,
            "year": media.year,
            "poster_url": media.poster_url,
            "backdrop_url": media.backdrop_url,
            "genres": media.genres,
            "total_episodes": len(all_eps),
            "watched_episodes": len(all_eps) - len(unwatched),
            "remaining_episodes": len(unwatched),
            "unwatched_minutes": total_unwatched_mins,
            "unwatched_episodes": unwatched,
        }


@api_router.post("/shows/{show_id}/episodes/{episode_id}/watch")
def api_watch_episode(request: Request, show_id: int, episode_id: int) -> dict[str, Any]:
    """Mark a specific episode of a show as watched locally."""
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock
    now = clock.now()

    with get_db_session(db_engine) as session:
        ep = session.get(Episode, episode_id)
        if ep is None or ep.show_id != show_id:
            raise HTTPException(status_code=404, detail="Episode not found for this show.")

        event = WatchEvent(
            account_id=1,
            episode_id=ep.id,
            action="watch",
            watched_at=now,
        )
        session.add(event)
        session.flush()

    estimator = ShowFinishEstimator(db_engine=db_engine, clock=clock)
    updated_est = estimator.estimate_show(show_id)

    return {
        "success": True,
        "watched_episode_id": episode_id,
        "updated_estimate": asdict(updated_est) if updated_est else None,
    }


@api_router.get("/recommendations")
def api_get_recommendations(
    request: Request,
    time_budget_minutes: int | None = Query(default=None),
    intent: IntentType = Query(default="auto"),
    limit: int = Query(default=10, le=50),
) -> dict[str, Any]:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock

    recommender = RecommendationEngine(db_engine=db_engine, clock=clock)
    ctx = RankingContext(time_budget_minutes=time_budget_minutes, intent=intent)
    result = recommender.recommend(context=ctx, limit=limit)

    return {
        "run_id": result.run_id,
        "context": asdict(result.context),
        "items": [asdict(it) for it in result.items],
    }


@api_router.post("/recommendations/feedback")
def api_submit_feedback(request: Request, payload: FeedbackRequest) -> dict[str, Any]:
    db_engine: Engine = request.app.state.db_engine
    clock: Clock = request.app.state.clock
    now = clock.now()

    with get_db_session(db_engine) as session:
        run = session.get(RecommendationRun, payload.run_id)
        if run is None:
            raise HTTPException(status_code=404, detail=f"Run {payload.run_id} not found.")

        fb = RecommendationFeedback(
            run_id=payload.run_id,
            candidate_id=payload.candidate_id,
            action=payload.action,
            created_at=now,
        )
        session.add(fb)
        session.flush()
        fb_id = fb.id

    return {
        "id": fb_id,
        "run_id": payload.run_id,
        "candidate_id": payload.candidate_id,
        "action": payload.action,
        "created_at": now.isoformat(),
    }


@api_router.get("/history")
def api_get_history(
    request: Request, limit: int = Query(default=50, le=200)
) -> list[dict[str, Any]]:
    db_engine: Engine = request.app.state.db_engine

    with get_db_session(db_engine) as session:
        stmt = (
            select(WatchEvent, MediaItem, Episode)
            .outerjoin(MediaItem, MediaItem.id == WatchEvent.movie_id)
            .outerjoin(Episode, Episode.id == WatchEvent.episode_id)
            .where(WatchEvent.account_id == 1)
            .order_by(desc(WatchEvent.watched_at))
            .limit(limit)
        )
        rows = session.execute(stmt).all()

        history_items = []
        for we, movie, ep in rows:
            if we.movie_id and movie:
                history_items.append(
                    {
                        "history_id": we.history_id,
                        "watched_at": we.watched_at.isoformat() if we.watched_at else None,
                        "media_type": "movie",
                        "media_id": movie.id,
                        "trakt_id": movie.trakt_id,
                        "title": movie.title,
                        "action": we.action,
                    }
                )
            elif we.episode_id and ep:
                show = session.get(MediaItem, ep.show_id)
                history_items.append(
                    {
                        "history_id": we.history_id,
                        "watched_at": we.watched_at.isoformat() if we.watched_at else None,
                        "media_type": "episode",
                        "media_id": show.id if show else None,
                        "trakt_id": show.trakt_id if show else None,
                        "title": show.title if show else "Show",
                        "season_number": ep.season_number,
                        "episode_number": ep.episode_number,
                        "episode_title": ep.title,
                        "action": we.action,
                    }
                )

    return history_items


# ---------------------------------------------------------
# OTA App Update Endpoints
# ---------------------------------------------------------
@api_router.get("/app/version")
@ui_router.get("/apks/latest.json")
def api_get_app_version() -> dict[str, Any]:
    """Return the latest available Android APK build version and release notes."""
    apk_path = Path("android/app/build/outputs/apk/debug/app-debug.apk")
    size_bytes = apk_path.stat().st_size if apk_path.exists() else None

    return {
        "version_code": 6,
        "version_name": "1.4.0",
        "apk_url": "/api/v1/app/download-apk",
        "changelog": (
            "Stripe Design System Overhaul: Signature Iris & Cyan luminous gradients, "
            "hairline specular borders, tactile floating glass dock, and Aurora Spotlight cards."
        ),
        "release_date": "2026-08-29",
        "apk_size_bytes": size_bytes,
    }


@api_router.get("/app/download-apk")
@ui_router.get("/apks/app-debug.apk")
def api_download_apk() -> Any:
    """Download the latest TVeaker Android APK for OTA installation."""
    from fastapi.responses import FileResponse

    apk_path = Path("android/app/build/outputs/apk/debug/app-debug.apk")
    if not apk_path.exists():
        raise HTTPException(status_code=404, detail="APK build not found on server.")

    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename="tveaker-v1.4.0.apk",
    )
