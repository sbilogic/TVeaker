"""Initial schema migration for TVeaker

Revision ID: 0001_initial
Revises:
Create Date: 2026-08-29 10:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # accounts table
    op.create_table(
        "accounts",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("trakt_uuid", sa.String(), nullable=False),
        sa.Column("username", sa.String(), nullable=False),
        sa.Column("timezone", sa.String(), nullable=False, server_default="UTC"),
        sa.Column("connected_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_successful_sync_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("trakt_uuid"),
    )

    # media_items table
    op.create_table(
        "media_items",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("media_type", sa.String(), nullable=False),
        sa.Column("trakt_id", sa.Integer(), nullable=False),
        sa.Column("slug", sa.String(), nullable=True),
        sa.Column("imdb_id", sa.String(), nullable=True),
        sa.Column("tmdb_id", sa.Integer(), nullable=True),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("year", sa.Integer(), nullable=True),
        sa.Column("overview", sa.Text(), nullable=True),
        sa.Column("runtime_minutes", sa.Integer(), nullable=True),
        sa.Column("status", sa.String(), nullable=True),
        sa.Column("genres_json", sa.Text(), nullable=False, server_default="[]"),
        sa.Column("first_aired", sa.DateTime(timezone=True), nullable=True),
        sa.Column("remote_updated_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint("media_type IN ('movie', 'show')", name="ck_media_items_media_type"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("media_type", "trakt_id", name="uq_media_items_type_trakt_id"),
    )
    op.create_index("ix_media_items_trakt_id", "media_items", ["trakt_id"], unique=False)

    # episodes table
    op.create_table(
        "episodes",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("show_id", sa.Integer(), nullable=False),
        sa.Column("trakt_id", sa.Integer(), nullable=False),
        sa.Column("season_number", sa.Integer(), nullable=False),
        sa.Column("episode_number", sa.Integer(), nullable=False),
        sa.Column("title", sa.String(), nullable=True),
        sa.Column("overview", sa.Text(), nullable=True),
        sa.Column("runtime_minutes", sa.Integer(), nullable=True),
        sa.Column("first_aired", sa.DateTime(timezone=True), nullable=True),
        sa.Column("remote_updated_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["show_id"], ["media_items.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("trakt_id"),
        sa.UniqueConstraint(
            "show_id",
            "season_number",
            "episode_number",
            name="uq_episodes_show_season_episode",
        ),
    )
    op.create_index("ix_episodes_show_id", "episodes", ["show_id"], unique=False)
    op.create_index("ix_episodes_trakt_id", "episodes", ["trakt_id"], unique=False)

    # watch_events table
    op.create_table(
        "watch_events",
        sa.Column("history_id", sa.BigInteger(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("watched_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("action", sa.String(), nullable=False, server_default="watch"),
        sa.Column("movie_id", sa.Integer(), nullable=True),
        sa.Column("episode_id", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "((movie_id IS NOT NULL AND episode_id IS NULL) OR "
            "(movie_id IS NULL AND episode_id IS NOT NULL))",
            name="ck_watch_events_target",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["accounts.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["episode_id"], ["episodes.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["movie_id"], ["media_items.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("history_id"),
    )
    op.create_index(
        "ix_watch_events_account_watched",
        "watch_events",
        ["account_id", "watched_at"],
        unique=False,
    )

    # playback_states table
    op.create_table(
        "playback_states",
        sa.Column("playback_id", sa.BigInteger(), nullable=False),
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("progress_percent", sa.Float(), nullable=False),
        sa.Column("paused_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("movie_id", sa.Integer(), nullable=True),
        sa.Column("episode_id", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "((movie_id IS NOT NULL AND episode_id IS NULL) OR "
            "(movie_id IS NULL AND episode_id IS NOT NULL))",
            name="ck_playback_states_target",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["accounts.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["episode_id"], ["episodes.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["movie_id"], ["media_items.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("playback_id"),
    )

    # ratings table
    op.create_table(
        "ratings",
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("media_type", sa.String(), nullable=False),
        sa.Column("trakt_id", sa.Integer(), nullable=False),
        sa.Column("rating", sa.Integer(), nullable=False),
        sa.Column("rated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "media_type IN ('movie', 'show', 'episode')",
            name="ck_ratings_media_type",
        ),
        sa.CheckConstraint("rating >= 1 AND rating <= 10", name="ck_ratings_value_range"),
        sa.ForeignKeyConstraint(["account_id"], ["accounts.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("account_id", "media_type", "trakt_id", name="pk_ratings"),
    )

    # watchlist_items table
    op.create_table(
        "watchlist_items",
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("media_type", sa.String(), nullable=False),
        sa.Column("media_item_id", sa.Integer(), nullable=False),
        sa.Column("listed_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "media_type IN ('movie', 'show')",
            name="ck_watchlist_items_media_type",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["accounts.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["media_item_id"], ["media_items.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint(
            "account_id", "media_type", "media_item_id", name="pk_watchlist_items"
        ),
    )

    # tracked_shows table
    op.create_table(
        "tracked_shows",
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("show_id", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("status_source", sa.String(), nullable=False),
        sa.Column("include_specials", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("manual_episodes_per_week", sa.Float(), nullable=True),
        sa.Column("priority", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "status IN ('planned', 'watching', 'paused', 'completed', 'dropped')",
            name="ck_tracked_shows_status",
        ),
        sa.CheckConstraint(
            "status_source IN ('auto', 'manual')",
            name="ck_tracked_shows_status_source",
        ),
        sa.CheckConstraint(
            "manual_episodes_per_week IS NULL OR manual_episodes_per_week > 0",
            name="ck_tracked_shows_manual_pace_positive",
        ),
        sa.ForeignKeyConstraint(["account_id"], ["accounts.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["show_id"], ["media_items.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("account_id", "show_id", name="pk_tracked_shows"),
    )

    # sync_cursors table
    op.create_table(
        "sync_cursors",
        sa.Column("account_id", sa.Integer(), nullable=False),
        sa.Column("dataset", sa.String(), nullable=False),
        sa.Column("remote_activity_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_success_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["account_id"], ["accounts.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("account_id", "dataset", name="pk_sync_cursors"),
    )

    # sync_runs table
    op.create_table(
        "sync_runs",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("mode", sa.String(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("counts_json", sa.Text(), nullable=False, server_default="{}"),
        sa.Column("warnings_json", sa.Text(), nullable=False, server_default="[]"),
        sa.Column("error_summary", sa.Text(), nullable=True),
        sa.CheckConstraint(
            "status IN ('success', 'partial', 'failed', 'skipped')",
            name="ck_sync_runs_status",
        ),
        sa.PrimaryKeyConstraint("id"),
    )

    # recommendation_runs table
    op.create_table(
        "recommendation_runs",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("context_json", sa.Text(), nullable=False),
        sa.Column("model_version", sa.String(), nullable=False),
        sa.Column("ranked_candidates_json", sa.Text(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )

    # recommendation_feedback table
    op.create_table(
        "recommendation_feedback",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("run_id", sa.Integer(), nullable=False),
        sa.Column("candidate_id", sa.String(), nullable=False),
        sa.Column("action", sa.String(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "action IN ('accepted', 'not_now', 'not_interested')",
            name="ck_recommendation_feedback_action",
        ),
        sa.ForeignKeyConstraint(["run_id"], ["recommendation_runs.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_recommendation_feedback_candidate_id",
        "recommendation_feedback",
        ["candidate_id"],
        unique=False,
    )
    op.create_index(
        "ix_recommendation_feedback_run_id",
        "recommendation_feedback",
        ["run_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_recommendation_feedback_run_id", table_name="recommendation_feedback")
    op.drop_index(
        "ix_recommendation_feedback_candidate_id",
        table_name="recommendation_feedback",
    )
    op.drop_table("recommendation_feedback")
    op.drop_table("recommendation_runs")
    op.drop_table("sync_runs")
    op.drop_table("sync_cursors")
    op.drop_table("tracked_shows")
    op.drop_table("watchlist_items")
    op.drop_table("ratings")
    op.drop_table("playback_states")
    op.drop_index("ix_watch_events_account_watched", table_name="watch_events")
    op.drop_table("watch_events")
    op.drop_index("ix_episodes_trakt_id", table_name="episodes")
    op.drop_index("ix_episodes_show_id", table_name="episodes")
    op.drop_table("episodes")
    op.drop_index("ix_media_items_trakt_id", table_name="media_items")
    op.drop_table("media_items")
    op.drop_table("accounts")
