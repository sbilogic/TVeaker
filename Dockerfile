FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim

ENV PYTHONUNBUFFERED=1 \
    UV_SYSTEM_PYTHON=1 \
    TVEAKER_ENV=production \
    TVEAKER_HOST=0.0.0.0 \
    PORT=8000

WORKDIR /app

# Copy dependency specifications first to leverage Docker layer caching
COPY pyproject.toml uv.lock ./

# Install production dependencies directly into system python
RUN uv sync --frozen --no-dev --no-install-project

# Copy application source, migrations, and assets
COPY src/ ./src/
COPY migrations/ ./migrations/
COPY alembic.ini ./
COPY docker-entrypoint.sh ./

# Install the application package in editable mode
RUN uv pip install --no-deps -e .

RUN chmod +x docker-entrypoint.sh

# Persistent storage directory for SQLite database and encrypted tokens
VOLUME /data

EXPOSE 8000

ENTRYPOINT ["./docker-entrypoint.sh"]
