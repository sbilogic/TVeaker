# TVeaker Domain Context & Glossary

This document defines the core domain concepts used across TVeaker.

- **Watch event**: An immutable Trakt history row representing one completed watch. Identified by Trakt `history_id`.
- **Playback state**: The latest paused/in-progress percentage for a movie or episode. It is a replaceable snapshot, not a permanent watch event.
- **Media item**: A movie or TV show that can be rated, tracked, or recommended.
- **Episode**: A numbered segment of a show, identified by a Trakt `episode_id`.
- **Available episode**: An episode with a non-future `first_aired` timestamp, or one with a local watch event. A watch event is authoritative evidence that the episode was available even when the imported catalog lacks an air date.
- **Tracked show**: A local preference to follow a show with a custom local status (`planned`, `watching`, `paused`, `completed`, `dropped`). It is independent from the Trakt watchlist.
- **Progress**: The unique set of aired episodes watched at least once. Rewatches do not increase progress count.
- **Remaining estimate**: Remaining screen minutes across eligible unwatched episodes, with confidence metrics and assumptions.
- **Catch-up estimate**: Remaining estimate for an ongoing show using aired episodes only.
- **Finish estimate**: Remaining estimate for an ended show.
- **Candidate**: An episode, unstarted show, or movie eligible for recommendation.
- **Recommendation run**: A stored ranking result including context, component score breakdown, and selected items.
- **Feedback**: A local `accepted`, `not_now` (7-day suppression), or `not_interested` (permanent exclusion) response modifying future candidate scoring.
- **Sync run**: One attempt to reconcile selected Trakt datasets into the local SQLite database.
- **Activity cursor**: The last successfully imported Trakt activity timestamps for each dataset.
