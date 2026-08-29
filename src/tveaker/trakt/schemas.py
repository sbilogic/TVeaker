"""Pydantic schemas for Trakt API v2 payloads and responses."""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class TraktIds(BaseModel):
    model_config = ConfigDict(extra="ignore")

    trakt: int
    slug: str | None = None
    imdb: str | None = None
    tmdb: int | None = None
    tvdb: int | None = None


class TraktUserIds(BaseModel):
    model_config = ConfigDict(extra="ignore")

    slug: str
    uuid: str


class TraktUser(BaseModel):
    model_config = ConfigDict(extra="ignore")

    username: str
    ids: TraktUserIds
    name: str | None = None


class TraktUserAccount(BaseModel):
    model_config = ConfigDict(extra="ignore")

    timezone: str = "UTC"


class TraktUserSettings(BaseModel):
    model_config = ConfigDict(extra="ignore")

    user: TraktUser
    account: TraktUserAccount


class TraktMovie(BaseModel):
    model_config = ConfigDict(extra="ignore")

    title: str
    year: int | None = None
    ids: TraktIds
    overview: str | None = None
    runtime: int | None = None
    genres: list[str] = Field(default_factory=list)
    updated_at: datetime | None = None


class TraktShow(BaseModel):
    model_config = ConfigDict(extra="ignore")

    title: str
    year: int | None = None
    ids: TraktIds
    overview: str | None = None
    runtime: int | None = None
    genres: list[str] = Field(default_factory=list)
    status: str | None = None
    first_aired: datetime | None = None
    updated_at: datetime | None = None


class TraktEpisode(BaseModel):
    model_config = ConfigDict(extra="ignore")

    season: int
    number: int
    title: str | None = None
    ids: TraktIds
    overview: str | None = None
    runtime: int | None = None
    first_aired: datetime | None = None
    updated_at: datetime | None = None


class TraktSeason(BaseModel):
    model_config = ConfigDict(extra="ignore")

    number: int
    ids: TraktIds | None = None
    episodes: list[TraktEpisode] = Field(default_factory=list)


class TraktHistoryItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: int
    watched_at: datetime
    action: str = "watch"
    type: str  # movie, episode, show, season
    movie: TraktMovie | None = None
    show: TraktShow | None = None
    episode: TraktEpisode | None = None


class TraktPlaybackItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: int
    progress: float
    paused_at: datetime
    type: str
    movie: TraktMovie | None = None
    show: TraktShow | None = None
    episode: TraktEpisode | None = None


class TraktRatingItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    rated_at: datetime
    rating: int
    type: str  # movie, show, episode
    movie: TraktMovie | None = None
    show: TraktShow | None = None
    episode: TraktEpisode | None = None


class TraktWatchlistItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    listed_at: datetime
    type: str  # movie, show
    movie: TraktMovie | None = None
    show: TraktShow | None = None


class TraktProgressSeason(BaseModel):
    model_config = ConfigDict(extra="ignore")

    number: int
    aired: int = 0
    completed: int = 0


class TraktWatchedProgress(BaseModel):
    model_config = ConfigDict(extra="ignore")

    aired: int = 0
    completed: int = 0
    last_watched_at: datetime | None = None
    seasons: list[TraktProgressSeason] = Field(default_factory=list)


class TraktActivityItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    watched_at: datetime | None = None
    rated_at: datetime | None = None
    watchlisted_at: datetime | None = None
    paused_at: datetime | None = None


class TraktLastActivities(BaseModel):
    model_config = ConfigDict(extra="ignore")

    all: datetime | None = None
    movies: TraktActivityItem = Field(default_factory=TraktActivityItem)
    episodes: TraktActivityItem = Field(default_factory=TraktActivityItem)
    shows: TraktActivityItem = Field(default_factory=TraktActivityItem)
