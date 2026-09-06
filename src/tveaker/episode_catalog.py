"""One trusted episode-catalog seam for progress and local actions."""

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from tveaker.models import Episode, MediaItem, WatchEvent


@dataclass(frozen=True)
class ShowProgressCatalog:
    """Episodes eligible for progress, plus any count-only Trakt remainder."""

    episodes: tuple[Episode, ...]
    source_confirmed_episode_ids: frozenset[int]
    unresolved_episodes: int


def get_show_progress_catalog(
    session: Session,
    account_id: int,
    show_id: int,
    include_specials: bool = False,
) -> ShowProgressCatalog:
    """Return the shared local catalog bounded by Trakt's export snapshot.

    Positive Trakt IDs are trusted catalog rows. Legacy provider rows can only
    fill standard-episode gaps inside the latest exported ``aired_episodes``
    count; they never add future work beyond that Trakt-confirmed boundary.
    Season 0 remains separate and requires an explicit user opt-in.
    """
    show = session.get(MediaItem, show_id)
    if show is None or show.media_type != "show":
        return ShowProgressCatalog((), frozenset(), 0)

    episodes = session.execute(
        select(Episode)
        .where(Episode.show_id == show_id)
        .order_by(Episode.season_number.asc(), Episode.episode_number.asc())
    ).scalars().all()
    standard = [episode for episode in episodes if episode.season_number > 0]
    watched_ids = set(
        session.execute(
            select(WatchEvent.episode_id)
            .join(Episode, WatchEvent.episode_id == Episode.id)
            .where(
                WatchEvent.account_id == account_id,
                WatchEvent.episode_id.is_not(None),
                Episode.show_id == show_id,
            )
        ).scalars()
    )

    source_confirmed_ids: set[int] = set()
    unresolved_episodes = 0
    if show.trakt_aired_episodes is None:
        eligible_standard = [episode for episode in standard if episode.trakt_id > 0]
    else:
        reported_aired = max(0, show.trakt_aired_episodes)
        eligible_standard = standard[:reported_aired]
        source_confirmed_ids.update(
            episode.id for episode in eligible_standard if episode.trakt_id < 0
        )

        # History is authoritative even if a provider ordered an old episode
        # unexpectedly. Keep that evidence while retaining the Trakt cap.
        eligible_ids = {episode.id for episode in eligible_standard}
        eligible_standard.extend(
            episode
            for episode in standard
            if episode.id in watched_ids and episode.id not in eligible_ids
        )
        unresolved_episodes = max(0, reported_aired - len(eligible_standard))

    eligible = list(eligible_standard)
    if include_specials:
        eligible.extend(
            episode
            for episode in episodes
            if episode.season_number == 0 and episode.trakt_id > 0
        )

    eligible.sort(key=lambda episode: (episode.season_number, episode.episode_number))
    return ShowProgressCatalog(
        episodes=tuple(eligible),
        source_confirmed_episode_ids=frozenset(source_confirmed_ids),
        unresolved_episodes=unresolved_episodes,
    )
