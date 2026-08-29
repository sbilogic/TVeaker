"""Multi-objective ranking engine combining content similarity and contextual filters."""

from dataclasses import dataclass
from typing import Literal

from tveaker.recommender.candidates import Candidate
from tveaker.recommender.features import ContentSimilarityResult

IntentType = Literal["auto", "movie", "show", "finish_show", "start_new"]


@dataclass(frozen=True)
class RankingBreakdown:
    candidate_id: str
    final_score: float
    content_score: float
    source_score: float
    progress_score: float
    budget_score: float
    intent_score: float
    matched_genres: list[str]


@dataclass(frozen=True)
class RankingContext:
    time_budget_minutes: int | None = None
    intent: IntentType = "auto"
    preferred_genres: list[str] | None = None


class ContextRanker:
    """Combines content similarity with contextual signals (budget, progress, intent, sources)."""

    def rank(
        self,
        candidates: list[Candidate],
        similarities: dict[str, ContentSimilarityResult],
        context: RankingContext | None = None,
    ) -> list[RankingBreakdown]:
        """Compute final multi-factor ranking scores for candidates."""
        ctx = context or RankingContext()
        scored_candidates: list[RankingBreakdown] = []

        for cand in candidates:
            # 1. Content score (0.40)
            sim_res = similarities.get(cand.candidate_id)
            content_sim = sim_res.similarity_score if sim_res else 0.5
            matched_genres = sim_res.matched_genres if sim_res else []

            # Optional boost if candidate matches explicit preferred_genres in context
            if ctx.preferred_genres:
                overlap = set(cand.genres).intersection(set(ctx.preferred_genres))
                if overlap:
                    content_sim = min(1.0, content_sim + 0.2)
                    matched_genres = list(set(matched_genres).union(overlap))

            # 2. Source score (0.20)
            source_score = 0.4
            if "watchlist" in cand.source_reasons:
                source_score = max(source_score, 1.0)
            if "in_progress_playback" in cand.source_reasons:
                source_score = max(source_score, 0.8)
            if "active_tracked_show" in cand.source_reasons:
                source_score = max(source_score, 0.9)
            if "trakt_recommendation" in cand.source_reasons:
                source_score = max(source_score, 0.6)

            # 3. Progress score (0.15)
            if cand.in_progress:
                if cand.progress_percent is not None:
                    progress_score = min(1.0, 0.5 + 0.5 * (cand.progress_percent / 100.0))
                elif cand.remaining_episodes is not None and cand.remaining_episodes > 0:
                    progress_score = 0.85
                else:
                    progress_score = 0.7
            else:
                progress_score = 0.3

            # 4. Time budget score (0.15)
            if ctx.time_budget_minutes is None:
                budget_score = 1.0
            elif cand.runtime_minutes is None:
                budget_score = 0.7
            elif cand.runtime_minutes <= ctx.time_budget_minutes:
                budget_score = 1.0
            else:
                overage = cand.runtime_minutes - ctx.time_budget_minutes
                budget_score = max(0.0, 1.0 - (overage / float(ctx.time_budget_minutes)))

            # 5. Intent score (0.10)
            intent = ctx.intent
            if intent == "finish_show":
                intent_score = 1.0 if cand.in_progress and cand.media_type == "show" else 0.2
            elif intent == "start_new":
                intent_score = 1.0 if not cand.in_progress else 0.2
            elif intent == "movie":
                intent_score = 1.0 if cand.media_type == "movie" else 0.2
            elif intent == "show":
                intent_score = 1.0 if cand.media_type == "show" else 0.2
            else:  # "auto"
                intent_score = 1.0

            # Composite score calculation
            final_score = (
                0.40 * content_sim
                + 0.20 * source_score
                + 0.15 * progress_score
                + 0.15 * budget_score
                + 0.10 * intent_score
            )

            scored_candidates.append(
                RankingBreakdown(
                    candidate_id=cand.candidate_id,
                    final_score=round(final_score, 4),
                    content_score=round(content_sim, 4),
                    source_score=round(source_score, 4),
                    progress_score=round(progress_score, 4),
                    budget_score=round(budget_score, 4),
                    intent_score=round(intent_score, 4),
                    matched_genres=matched_genres,
                )
            )

        scored_candidates.sort(key=lambda x: x.final_score, reverse=True)
        return scored_candidates
