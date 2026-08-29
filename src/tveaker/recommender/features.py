"""Feature extraction and content-based similarity calculation."""

import json
import math
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sqlalchemy.orm import Session

from tveaker.models import Episode, MediaItem, Rating, WatchEvent, WatchlistItem
from tveaker.recommender.candidates import Candidate


def _ensure_utc(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def _build_item_corpus_entry(genres: Sequence[str], overview: str | None, title: str) -> str:
    """Build space-separated bag-of-words document for an item."""
    # Repeat genres 3x to give them higher structural weight over raw overview text
    genre_tokens = " ".join([f"genre_{g.lower().replace(' ', '_')}" for g in genres] * 3)
    overview_text = (overview or "").lower()
    return f"{title.lower()} {genre_tokens} {overview_text}"


@dataclass(frozen=True)
class ContentSimilarityResult:
    candidate_id: str
    similarity_score: float
    matched_genres: list[str]


class ContentFeatureModel:
    """Extracts TF-IDF features and computes user taste profile cosine similarities."""

    def __init__(self, session: Session, account_id: int, now: datetime) -> None:
        self.session = session
        self.account_id = account_id
        self.now_utc = _ensure_utc(now) or datetime.now(UTC)
        self.lambda_decay = math.log(2.0) / 180.0  # 180-day half life decay

    def calculate_similarities(
        self,
        candidates: list[Candidate],
    ) -> dict[str, ContentSimilarityResult]:
        """Compute cosine similarity between weighted user profile and candidates."""
        if not candidates:
            return {}

        # 1. Collect user interactions to build taste history
        # History watches with recency decay
        watch_events = (
            self.session.query(WatchEvent).filter(WatchEvent.account_id == self.account_id).all()
        )
        ratings = self.session.query(Rating).filter(Rating.account_id == self.account_id).all()
        watchlist_items = (
            self.session.query(WatchlistItem)
            .filter(WatchlistItem.account_id == self.account_id)
            .all()
        )

        user_item_weights: dict[int, float] = {}  # media_item_id -> weight

        for we in watch_events:
            w_time = _ensure_utc(we.watched_at)
            days_ago = (
                max(0.0, (self.now_utc - w_time).total_seconds() / 86400.0) if w_time else 0.0
            )
            recency_weight = math.exp(-self.lambda_decay * days_ago)

            media_id = None
            if we.movie_id:
                media_id = we.movie_id
            elif we.episode_id:
                ep = self.session.get(Episode, we.episode_id)
                if ep:
                    media_id = ep.show_id

            if media_id:
                user_item_weights[media_id] = user_item_weights.get(media_id, 0.0) + recency_weight

        for r in ratings:
            r_weight = r.rating / 10.0
            # Find media item id
            m_item = (
                self.session.query(MediaItem)
                .filter(MediaItem.media_type == r.media_type, MediaItem.trakt_id == r.trakt_id)
                .first()
            )
            if m_item:
                user_item_weights[m_item.id] = user_item_weights.get(m_item.id, 0.0) + (
                    r_weight * 1.5
                )

        for wl in watchlist_items:
            user_item_weights[wl.media_item_id] = user_item_weights.get(wl.media_item_id, 0.0) + 1.2

        # 2. Build corpus of user historical items + candidates
        all_media_ids = set(user_item_weights.keys()).union({c.media_item_id for c in candidates})
        media_records = self.session.query(MediaItem).filter(MediaItem.id.in_(all_media_ids)).all()
        media_map = {m.id: m for m in media_records}

        user_documents: list[str] = []
        user_weights_list: list[float] = []
        for m_id, w in user_item_weights.items():
            m = media_map.get(m_id)
            if m:
                genres = json.loads(m.genres_json) if m.genres_json else []
                doc = _build_item_corpus_entry(genres, m.overview, m.title)
                user_documents.append(doc)
                user_weights_list.append(w)

        candidate_documents = [
            _build_item_corpus_entry(c.genres, c.overview, c.title) for c in candidates
        ]

        if not user_documents:
            # Fallback if no user history: baseline similarity 0.5 for all candidates
            return {
                c.candidate_id: ContentSimilarityResult(
                    candidate_id=c.candidate_id,
                    similarity_score=0.5,
                    matched_genres=c.genres,
                )
                for c in candidates
            }

        # 3. Fit TF-IDF Vectorizer
        all_docs = user_documents + candidate_documents
        vectorizer = TfidfVectorizer(max_features=500, stop_words="english")
        tfidf_matrix = vectorizer.fit_transform(all_docs)

        n_user = len(user_documents)
        user_matrix = tfidf_matrix[:n_user]
        candidate_matrix = tfidf_matrix[n_user:]

        # 4. Compute weighted user taste vector
        weights_arr = np.array(user_weights_list).reshape(-1, 1)
        weights_arr = weights_arr / (np.sum(weights_arr) + 1e-9)

        # Weighted average profile
        user_profile = np.asarray((user_matrix.T.multiply(weights_arr.flatten())).sum(axis=1)).T

        # Cosine similarity between user profile and candidate items
        sims = cosine_similarity(user_profile, candidate_matrix).flatten()

        # Find top genres in user history for explainability
        user_genre_counts: dict[str, float] = {}
        for m_id, w in user_item_weights.items():
            m = media_map.get(m_id)
            if m and m.genres_json:
                for g in json.loads(m.genres_json):
                    user_genre_counts[g] = user_genre_counts.get(g, 0.0) + w

        top_user_genres = {
            g for g, _ in sorted(user_genre_counts.items(), key=lambda x: x[1], reverse=True)[:5]
        }

        results: dict[str, ContentSimilarityResult] = {}
        for idx, cand in enumerate(candidates):
            sim_score = float(np.clip(sims[idx], 0.0, 1.0))
            matched = [g for g in cand.genres if g in top_user_genres]
            results[cand.candidate_id] = ContentSimilarityResult(
                candidate_id=cand.candidate_id,
                similarity_score=round(sim_score, 4),
                matched_genres=matched,
            )

        return results
