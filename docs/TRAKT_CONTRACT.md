# Trakt API v2 Contract Specification

This document defines the exact HTTP contract between TVeaker and the Trakt API v2 (`https://api.trakt.tv`).

## 1. Global Headers & Requirements

Every HTTP request to Trakt must include:
- `Content-Type: application/json`
- `User-Agent: TVeaker/1.0.0`
- `trakt-api-key: <client_id>`
- `trakt-api-version: 2`
- `Authorization: Bearer <access_token>` (for authenticated endpoints)

Rate Limiting: Trakt permits 500 authenticated GET requests per 5-minute rolling window. On HTTP 429, honor the `Retry-After` header.

## 2. Pagination Headers

Paginated endpoints return:
- `X-Pagination-Page`: Current 1-indexed page number.
- `X-Pagination-Page-Count`: Total number of pages.
- `X-Pagination-Item-Count`: Total number of items across all pages.
- `X-Pagination-Limit`: Number of items per page.

Pagination rule: An iteration is finished only when `X-Pagination-Page == X-Pagination-Page-Count` or `item_count == 0`. Never terminate early due to a short page if `page < page_count`.

## 3. Endpoints & Operations

### 3.1 User Settings
- **Path**: `GET /users/settings`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/userssettings
- **Returns**: User object containing `username`, `ids` (`slug`, `uuid`), and `account.timezone`.

### 3.2 Last Activities
- **Path**: `GET /sync/last_activities`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getsynclastactivities
- **Returns**: ISO-8601 timestamps for `all`, `movies.watched_at`, `movies.rated_at`, `movies.watchlisted_at`, `episodes.watched_at`, `episodes.rated_at`, `shows.rated_at`, `shows.watchlisted_at`, `playback`.

### 3.3 History
- **Path**: `GET /sync/history/{type}` (type = `movies`, `shows`, `seasons`, `episodes` or omit for all)
- **Params**: `page`, `limit`, `start_at`, `end_at`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getsynchistoryget
- **Returns**: Paginated list of watch items containing Trakt `id` (64-bit int), `watched_at`, `action`, `type`, and corresponding `movie` or `episode`+`show` media records.

### 3.4 Playback Progress
- **Path**: `GET /sync/playback/{movies,episodes}`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getsyncprogressplayback
- **Returns**: Active paused states containing `id`, `progress` (0-100 float), `paused_at`, and media reference.

### 3.5 Ratings
- **Path**: `GET /users/me/ratings/{movies,shows,episodes}`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getusersratings
- **Returns**: List containing `rated_at`, `rating` (1-10 int), and media reference.

### 3.6 Watchlist
- **Path**: `GET /users/me/watchlist/{movies,shows}`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getuserswatchlist
- **Returns**: List containing `listed_at`, `type`, and `movie` or `show` media reference.

### 3.7 Show Episode Catalog
- **Path**: `GET /shows/{trakt_id}/seasons?extended=episodes`
- **Auth**: Optional/Public (send client_id)
- **Docs**: https://docs.trakt.tv/reference/getshowsseasons
- **Returns**: Array of seasons with nested `episodes` array containing `season`, `number`, `title`, `ids`, `overview`, `runtime`, `first_aired`.

### 3.8 Watched Show Progress
- **Path**: `GET /shows/{trakt_id}/progress/watched`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getshowsprogresswatched
- **Returns**: Aggregated show completion metadata (`aired`, `completed`, `last_watched_at`, seasons breakdown).

### 3.9 Recommendations Seeds
- **Path**: `GET /recommendations/{movies,shows}?limit=100`
- **Auth**: Required
- **Docs**: https://docs.trakt.tv/reference/getrecommendationsmoviesrecommend
- **Returns**: List of recommended media items.
