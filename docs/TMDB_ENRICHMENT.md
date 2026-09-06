# TMDB metadata enrichment

TVeaker can enrich missing movie and series metadata with TMDB. TVMaze remains
the no-key fallback and is still used for episode schedules where available.

## Setup

1. Create a personal TMDB API credential from the [TMDB API setup page](https://developer.themoviedb.org/docs/getting-started).
2. Add its read-access bearer token to the local `.env` file:

   ```dotenv
   TMDB_ACCESS_TOKEN=your_read_access_token
   ```

   A legacy v3 key is also accepted as `TMDB_API_KEY`, but the bearer token is
   preferred. Never commit either value.
3. Restart the local server, then use **Fetch missing now** in Settings or wait
   for the daily refresh.

TVeaker searches TMDB by title, then retrieves the matched TV or movie record
to fill only fields that are absent locally: artwork, runtime, release date,
genres, and status. It uses the documented [TV search endpoint](https://developer.themoviedb.org/reference/search-tv) and equivalent movie lookup.

Without TMDB credentials, TVeaker continues to update TV data and episode
schedules through TVMaze, but it cannot reliably complete missing movie
runtimes and artwork from TMDB.
