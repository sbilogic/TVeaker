"""Transparent natural language explanation generator for recommendations."""

from tveaker.recommender.candidates import Candidate
from tveaker.recommender.ranker import RankingBreakdown, RankingContext


class RecommendationExplainer:
    """Generates explainability text highlighting the primary drivers for a recommendation."""

    def explain(
        self,
        candidate: Candidate,
        breakdown: RankingBreakdown,
        context: RankingContext | None = None,
    ) -> str:
        """Generate a concise, user-facing rationale sentence."""
        reasons: list[str] = []

        # 1. In-progress status
        if candidate.in_progress:
            if candidate.remaining_episodes is not None:
                pl = "s" if candidate.remaining_episodes != 1 else ""
                reasons.append(f"Watching: {candidate.remaining_episodes} episode{pl} left")
            elif candidate.progress_percent is not None:
                reasons.append(f"You're paused at {int(candidate.progress_percent)}%")
            else:
                reasons.append("Currently in your active watching list")

        # 2. Watchlist origin
        if "watchlist" in candidate.source_reasons and not candidate.in_progress:
            reasons.append("Saved on your watchlist")

        # 3. Content / Genre match
        if breakdown.matched_genres:
            genre_str = ", ".join([g.title() for g in breakdown.matched_genres[:2]])
            reasons.append(f"Matches your high rating in {genre_str}")
        elif breakdown.content_score > 0.7:
            reasons.append("Highly aligned with your viewing history")

        # 4. Budget fit
        if (
            context
            and context.time_budget_minutes is not None
            and candidate.runtime_minutes is not None
            and candidate.runtime_minutes <= context.time_budget_minutes
        ):
            reasons.append(
                f"Fits your {context.time_budget_minutes}m budget ({candidate.runtime_minutes}m)"
            )

        if not reasons:
            reasons.append("Recommended based on your overall viewing preferences")

        return " • ".join(reasons)
