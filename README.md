# TVeaker

Private, local-first media companion, finish-time estimator, and instant recommendation engine for Trakt. Built for **Web** and **Android**.

## Features

- **Trakt 1-Way Sync**: Safely import history, ratings, watchlists, playback states, and full season/episode catalogs into a local SQLite WAL database.
- **Finish & Catch-up Estimator**: Calculate remaining screen time, personal viewing pace (episodes/minutes per day), and calendar finish/catch-up dates with confidence metrics.
- **Local Show Tracking**: Manage statuses (`watching`, `planned`, `paused`, `completed`, `dropped`) and custom viewing paces offline.
- **Instant Recommender (<200ms)**: Fast, explainable content recommendations powered by TF-IDF taste profiles, momentum, runtime fit, and user feedback.
- **Web Dashboard**: Responsive dark UI for desktop and mobile browsers.
- **Native Android App**: Jetpack Compose + Material 3 client for your Android phone or tablet.
- **Proactive Metadata Enrichment**: Missing posters, runtimes, release dates, and episode details refresh daily with TVMaze by default; optionally add TMDB credentials for richer movie and series metadata.
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

## Free Cloud Deployment (Always-On 24/7)

Deploy TVeaker to the cloud for free so your Android companion connects permanently without running servers or keeping your PC on:

### Option A: Render (Easiest — 1-Click / Blueprint)
1. Push this repository to your GitHub account.
2. Log into [Render.com](https://render.com) (free account).
3. Click **New +** → **Blueprint**, select your repo (it automatically detects `render.yaml`).
4. Set your `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`, and optional `TMDB_ACCESS_TOKEN`.
5. Click **Apply**. Render will build and deploy your container, giving you a permanent HTTPS URL like `https://tveaker.onrender.com/`.
6. Enter that URL into Android **Settings → Online phone gateway**.

### Option B: Fly.io (With Free Persistent Volume)
1. Install the [Fly CLI](https://fly.io/docs/hands-on/install-flyctl/) (`winget install flyctl` or `brew install flyctl`).
2. Run `fly launch` and create the persistent volume:
   ```bash
   fly volumes create tveaker_data --size 1
   fly deploy
   ```
3. Set your secrets:
   ```bash
   fly secrets set TVEAKER_SECRET_KEY="your-secret-key-32bytes" TRAKT_CLIENT_ID="id" TRAKT_CLIENT_SECRET="secret"
   ```
4. Enter your permanent `https://tveaker.fly.dev/` URL into Android **Settings → Online phone gateway**.

## Phone access (local tunnel alternative)

TVeaker stays bound to `127.0.0.1`; the phone reaches it through a Cloudflare HTTPS tunnel instead of your Wi-Fi IP or Tailscale:

```powershell
# Keep TVeaker running, then create its public HTTPS route.
uv run python -m tveaker phone-gateway
```

Paste the printed HTTPS URL into Android **Settings → Online phone gateway**. It works on mobile data and is not tied to a Wi-Fi address. The temporary Quick Tunnel URL changes whenever this command stops; treat it as private and do not share it.

For a permanent hostname, configure a named Cloudflare Tunnel and run:

```powershell
$env:TVEAKER_CLOUDFLARE_TUNNEL_TOKEN = "your-named-tunnel-token"
uv run python -m tveaker phone-gateway --public-url https://tv.example.com/
```

The tunnel token is read from the environment and is never printed by TVeaker. The Android companion rejects the known stale LAN addresses rather than spending 15 seconds trying them.

Use **Watch now** from an episode list to choose the exact episode you intend to watch. It is stored locally and only becomes a watch event when you tap **Mark watched**.

## Running Tests

```powershell
uv run ruff check .
uv run mypy src
uv run pytest --cov=src/tveaker --cov-report=term-missing
```
