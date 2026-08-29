"""SQLAlchemy ORM models and SQLite constraints for TVeaker."""

import json
from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    PrimaryKeyConstraint,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from tveaker.db import Base


class Account(Base):
    __tablename__ = "accounts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, default=1)
    trakt_uuid: Mapped[str] = mapped_column(String, unique=True, nullable=False)
    username: Mapped[str] = mapped_column(String, nullable=False)
    timezone: Mapped[str] = mapped_column(String, nullable=False, default="UTC")
    connected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_successful_sync_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # Relationships
    watch_events: Mapped[list["WatchEvent"]] = relationship(
        "WatchEvent", back_populates="account", cascade="all, delete-orphan"
    )
    playback_states: Mapped[list["PlaybackState"]] = relationship(
        "PlaybackState", back_populates="account", cascade="all, delete-orphan"
    )
    ratings: Mapped[list["Rating"]] = relationship(
        "Rating", back_populates="account", cascade="all, delete-orphan"
    )
    watchlist_items: Mapped[list["WatchlistItem"]] = relationship(
        "WatchlistItem", back_populates="account", cascade="all, delete-orphan"
    )
    tracked_shows: Mapped[list["TrackedShow"]] = relationship(
        "TrackedShow", back_populates="account", cascade="all, delete-orphan"
    )
    sync_cursors: Mapped[list["SyncCursor"]] = relationship(
        "SyncCursor", back_populates="account", cascade="all, delete-orphan"
    )


class MediaItem(Base):
    __tablename__ = "media_items"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    media_type: Mapped[str] = mapped_column(String, nullable=False)
    trakt_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)
    slug: Mapped[str | None] = mapped_column(String, nullable=True)
    imdb_id: Mapped[str | None] = mapped_column(String, nullable=True)
    tmdb_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    title: Mapped[str] = mapped_column(String, nullable=False)
    year: Mapped[int | None] = mapped_column(Integer, nullable=True)
    overview: Mapped[str | None] = mapped_column(Text, nullable=True)
    runtime_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[str | None] = mapped_column(String, nullable=True)
    genres_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    poster_url: Mapped[str | None] = mapped_column(String, nullable=True)
    backdrop_url: Mapped[str | None] = mapped_column(String, nullable=True)
    first_aired: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    remote_updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # Relationships
    episodes: Mapped[list["Episode"]] = relationship(
        "Episode", back_populates="show", cascade="all, delete-orphan"
    )

    __table_args__ = (
        CheckConstraint(
            "media_type IN ('movie', 'show')",
            name="ck_media_items_media_type",
        ),
        UniqueConstraint("media_type", "trakt_id", name="uq_media_items_type_trakt_id"),
    )

    @property
    def genres(self) -> list[str]:
        try:
            return json.loads(self.genres_json)
        except Exception:
            return []

    @genres.setter
    def genres(self, value: list[str]) -> None:
        self.genres_json = json.dumps(sorted(value))


class Episode(Base):
    __tablename__ = "episodes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    show_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("media_items.id", ondelete="CASCADE"), nullable=False, index=True
    )
    trakt_id: Mapped[int] = mapped_column(Integer, unique=True, nullable=False, index=True)
    season_number: Mapped[int] = mapped_column(Integer, nullable=False)
    episode_number: Mapped[int] = mapped_column(Integer, nullable=False)
    title: Mapped[str | None] = mapped_column(String, nullable=True)
    overview: Mapped[str | None] = mapped_column(Text, nullable=True)
    runtime_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    first_aired: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    remote_updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # Relationships
    show: Mapped["MediaItem"] = relationship("MediaItem", back_populates="episodes")

    __table_args__ = (
        UniqueConstraint(
            "show_id",
            "season_number",
            "episode_number",
            name="uq_episodes_show_season_episode",
        ),
    )


class WatchEvent(Base):
    __tablename__ = "watch_events"

    history_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False
    )
    watched_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    action: Mapped[str] = mapped_column(String, nullable=False, default="watch")
    movie_id: Mapped[int | None] = mapped_column(
        Integer, ForeignKey("media_items.id", ondelete="CASCADE"), nullable=True
    )
    episode_id: Mapped[int | None] = mapped_column(
        Integer, ForeignKey("episodes.id", ondelete="CASCADE"), nullable=True
    )

    # Relationships
    account: Mapped["Account"] = relationship("Account", back_populates="watch_events")
    movie: Mapped["MediaItem | None"] = relationship("MediaItem")
    episode: Mapped["Episode | None"] = relationship("Episode")

    __table_args__ = (
        CheckConstraint(
            "((movie_id IS NOT NULL AND episode_id IS NULL) OR "
            "(movie_id IS NULL AND episode_id IS NOT NULL))",
            name="ck_watch_events_target",
        ),
        Index("ix_watch_events_account_watched", "account_id", "watched_at"),
    )


class PlaybackState(Base):
    __tablename__ = "playback_states"

    playback_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    account_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False
    )
    progress_percent: Mapped[float] = mapped_column(Float, nullable=False)
    paused_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    movie_id: Mapped[int | None] = mapped_column(
        Integer, ForeignKey("media_items.id", ondelete="CASCADE"), nullable=True
    )
    episode_id: Mapped[int | None] = mapped_column(
        Integer, ForeignKey("episodes.id", ondelete="CASCADE"), nullable=True
    )

    # Relationships
    account: Mapped["Account"] = relationship("Account", back_populates="playback_states")
    movie: Mapped["MediaItem | None"] = relationship("MediaItem")
    episode: Mapped["Episode | None"] = relationship("Episode")

    __table_args__ = (
        CheckConstraint(
            "((movie_id IS NOT NULL AND episode_id IS NULL) OR "
            "(movie_id IS NULL AND episode_id IS NOT NULL))",
            name="ck_playback_states_target",
        ),
    )


class Rating(Base):
    __tablename__ = "ratings"

    account_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False
    )
    media_type: Mapped[str] = mapped_column(String, nullable=False)
    trakt_id: Mapped[int] = mapped_column(Integer, nullable=False)
    rating: Mapped[int] = mapped_column(Integer, nullable=False)
    rated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    # Relationships
    account: Mapped["Account"] = relationship("Account", back_populates="ratings")

    __table_args__ = (
        PrimaryKeyConstraint("account_id", "media_type", "trakt_id", name="pk_ratings"),
        CheckConstraint(
            "media_type IN ('movie', 'show', 'episode')",
            name="ck_ratings_media_type",
        ),
        CheckConstraint("rating >= 1 AND rating <= 10", name="ck_ratings_value_range"),
    )


class WatchlistItem(Base):
    __tablename__ = "watchlist_items"

    account_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False
    )
    media_type: Mapped[str] = mapped_column(String, nullable=False)
    media_item_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("media_items.id", ondelete="CASCADE"), nullable=False
    )
    listed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    # Relationships
    account: Mapped["Account"] = relationship("Account", back_populates="watchlist_items")
    media_item: Mapped["MediaItem"] = relationship("MediaItem")

    __table_args__ = (
        PrimaryKeyConstraint(
            "account_id", "media_type", "media_item_id", name="pk_watchlist_items"
        ),
        CheckConstraint(
            "media_type IN ('movie', 'show')",
            name="ck_watchlist_items_media_type",
        ),
    )


class TrackedShow(Base):
    __tablename__ = "tracked_shows"

    account_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False
    )
    show_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("media_items.id", ondelete="CASCADE"), nullable=False
    )
    status: Mapped[str] = mapped_column(String, nullable=False)
    status_source: Mapped[str] = mapped_column(String, nullable=False)
    include_specials: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    manual_episodes_per_week: Mapped[float | None] = mapped_column(Float, nullable=True)
    priority: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    # Relationships
    account: Mapped["Account"] = relationship("Account", back_populates="tracked_shows")
    show: Mapped["MediaItem"] = relationship("MediaItem")

    __table_args__ = (
        PrimaryKeyConstraint("account_id", "show_id", name="pk_tracked_shows"),
        CheckConstraint(
            "status IN ('planned', 'watching', 'paused', 'completed', 'dropped')",
            name="ck_tracked_shows_status",
        ),
        CheckConstraint(
            "status_source IN ('auto', 'manual')",
            name="ck_tracked_shows_status_source",
        ),
        CheckConstraint(
            "manual_episodes_per_week IS NULL OR manual_episodes_per_week > 0",
            name="ck_tracked_shows_manual_pace_positive",
        ),
    )


class SyncCursor(Base):
    __tablename__ = "sync_cursors"

    account_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False
    )
    dataset: Mapped[str] = mapped_column(String, nullable=False)
    remote_activity_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_success_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    # Relationships
    account: Mapped["Account"] = relationship("Account", back_populates="sync_cursors")

    __table_args__ = (PrimaryKeyConstraint("account_id", "dataset", name="pk_sync_cursors"),)


class SyncRun(Base):
    __tablename__ = "sync_runs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    mode: Mapped[str] = mapped_column(String, nullable=False)
    status: Mapped[str] = mapped_column(String, nullable=False)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    counts_json: Mapped[str] = mapped_column(Text, nullable=False, default="{}")
    warnings_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    error_summary: Mapped[str | None] = mapped_column(Text, nullable=True)

    __table_args__ = (
        CheckConstraint(
            "status IN ('success', 'partial', 'failed', 'skipped')",
            name="ck_sync_runs_status",
        ),
    )

    @property
    def counts(self) -> dict[str, Any]:
        try:
            return json.loads(self.counts_json)
        except Exception:
            return {}

    @counts.setter
    def counts(self, val: dict[str, Any]) -> None:
        self.counts_json = json.dumps(val)

    @property
    def warnings(self) -> list[str]:
        try:
            return json.loads(self.warnings_json)
        except Exception:
            return []

    @warnings.setter
    def warnings(self, val: list[str]) -> None:
        self.warnings_json = json.dumps(val)


class RecommendationRun(Base):
    __tablename__ = "recommendation_runs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    context_json: Mapped[str] = mapped_column(Text, nullable=False)
    model_version: Mapped[str] = mapped_column(String, nullable=False)
    ranked_candidates_json: Mapped[str] = mapped_column(Text, nullable=False)

    # Relationships
    feedback: Mapped[list["RecommendationFeedback"]] = relationship(
        "RecommendationFeedback", back_populates="run", cascade="all, delete-orphan"
    )


class RecommendationFeedback(Base):
    __tablename__ = "recommendation_feedback"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    run_id: Mapped[int] = mapped_column(
        Integer,
        ForeignKey("recommendation_runs.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    candidate_id: Mapped[str] = mapped_column(String, nullable=False, index=True)
    action: Mapped[str] = mapped_column(String, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    # Relationships
    run: Mapped["RecommendationRun"] = relationship("RecommendationRun", back_populates="feedback")

    __table_args__ = (
        CheckConstraint(
            "action IN ('accepted', 'not_now', 'not_interested')",
            name="ck_recommendation_feedback_action",
        ),
    )
