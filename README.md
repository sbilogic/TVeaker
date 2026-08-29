# TVeaker

Private, local-first media companion, finish-time estimator, and instant recommendation engine for Trakt. Built for **Web** and **Android**.

## Features

- **Trakt 1-Way Sync**: Safely import history, ratings, watchlists, playback states, and full season/episode catalogs into a local SQLite WAL database.
- **Finish & Catch-up Estimator**: Calculate remaining screen time, personal viewing pace (episodes/minutes per day), and calendar finish/catch-up dates with confidence metrics.
- **Local Show Tracking**: Manage statuses (`watching`, `planned`, `paused`, `completed`, `dropped`) and custom viewing paces offline.
- **Instant Recommender (<200ms)**: Fast, explainable content recommendations powered by TF-IDF taste profiles, momentum, runtime fit, and user feedback.
- **Web Dashboard**: Responsive dark UI for desktop and mobile browsers.
- **Native Android App**: Jetpack Compose + Material 3 client for your Android phone or tablet.
- **System Doctor & Backups**: Automated SQLite integrity checks, backups, and diagnostic tooling.

## Quick Start

```powershell
# Install dependencies
uv sync

# Configure environment
cp .env.example .env

# Run database migrations
uv run alembic upgrade head

# Run system doctor
uv run python -m tveaker doctor

# Start the web server
uv run python -m tveaker serve
```

## Running Tests

```powershell
uv run ruff check .
uv run mypy src
uv run pytest --cov=src/tveaker --cov-report=term-missing
```
