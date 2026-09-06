#!/bin/sh
set -e

TARGET_DB="${TVEAKER_DATABASE_PATH:-data/tveaker.db}"
DB_DIR=$(dirname "$TARGET_DB")
mkdir -p "$DB_DIR"

# Seed database if not present or empty
if [ ! -s "$TARGET_DB" ]; then
    if [ -f "data/seed.db.gz" ]; then
        echo "Initializing database from seed.db.gz..."
        python -c "import gzip, shutil; shutil.copyfileobj(gzip.open('data/seed.db.gz', 'rb'), open('$TARGET_DB', 'wb'))"
        echo "Database seeded successfully."
    fi
fi

# Run database migrations
echo "Running database migrations..."
python -m alembic upgrade head

# Start TVeaker server
echo "Starting TVeaker on port ${PORT:-8000}..."
exec python -m tveaker serve --host 0.0.0.0 --port "${PORT:-8000}"
