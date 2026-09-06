#!/bin/sh
set -e

# Ensure database directory exists
DB_DIR=$(dirname "${TVEAKER_DATABASE_PATH:-data/tveaker.db}")
mkdir -p "$DB_DIR"

# Run database migrations
echo "Running database migrations..."
alembic upgrade head

# Start TVeaker server
echo "Starting TVeaker on port ${PORT:-8000}..."
exec python -m tveaker serve --host 0.0.0.0 --port "${PORT:-8000}"
