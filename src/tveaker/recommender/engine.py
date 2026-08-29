"""Recommendation engine pipeline and persistence service."""

import json
import logging
from dataclasses import asdict, dataclass
from typing import Literal

from sqlalchemy import Engine

from tveaker.clock import Clock, SystemClock
from tveaker.db import get_db_session
from tveaker.models import RecommendationRun
from tveaker.recommender.candidates import CandidatePoolGenerator
from tveaker.recommender.explainer import RecommendationExplainer
from tveaker.recommender.features import ContentFeatureModel
from tveaker.recommender.ranker import (
    ContextRanker,
    RankingBreakdown,
    RankingContext,
)

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class RecommendationItem:
    candidate_id: str
    media_type: Literal["movie", "show"]
    media_item_id: int
    trakt_id: int
    title: str
    year: int | None
    overview: str | None
    genres: list[str]
    runtime_minutes: int | None
    score: float
    explanation: str
    breakdown: RankingBreakdown


@dataclass(frozen=True)
class RecommendationResult:
    run_id: int
    items: list[RecommendationItem]
    context: RankingContext


class RecommendationEngine:
    """End-to-end recommendation workflow pipeline."""

    def __init__(
        self,
        db_engine: Engine,
        account_id: int = 1,
        clock: Clock | None = None,
    ) -> None:
        self.db_engine = db_engine
        self.account_id = account_id
        self.clock = clock or SystemClock()
        self.candidate_generator = CandidatePoolGenerator(
            db_engine=db_engine, account_id=account_id, clock=self.clock
        )
        self.ranker = ContextRanker()
        self.explainer = RecommendationExplainer()

    def recommend(
        self,
        context: RankingContext | None = None,
        limit: int = 10,
    ) -> RecommendationResult:
        """Generate ranked recommendations and persist run."""
        ctx = context or RankingContext()
        now = self.clock.now()

        # Determine media filter from intent
        media_filter: Literal["all", "movie", "show"] = "all"
        if ctx.intent == "movie":
            media_filter = "movie"
        elif ctx.intent in ("show", "finish_show"):
            media_filter = "show"

        # 1. Candidate Pool Generation
        candidates = self.candidate_generator.generate_candidates(media_type=media_filter)
        if not candidates:
            # Fallback if no candidates
            with get_db_session(self.db_engine) as session:
                rec_run = RecommendationRun(
                    created_at=now,
                    context_json=json.dumps(asdict(ctx)),
                    model_version="content-v1",
                    ranked_candidates_json="[]",
                )
                session.add(rec_run)
                session.flush()
                run_id = rec_run.id
            return RecommendationResult(run_id=run_id, items=[], context=ctx)

        # 2. Content-based feature similarity
        with get_db_session(self.db_engine) as session:
            feature_model = ContentFeatureModel(
                session=session, account_id=self.account_id, now=now
            )
            similarities = feature_model.calculate_similarities(candidates)

        # 3. Contextual Multi-factor Ranking
        ranked_breakdowns = self.ranker.rank(candidates, similarities, context=ctx)

        # 4. Explanation generation
        cand_map = {c.candidate_id: c for c in candidates}
        final_items: list[RecommendationItem] = []
        serializable_ranks: list[dict] = []

        for b in ranked_breakdowns[:limit]:
            cand = cand_map[b.candidate_id]
            explanation = self.explainer.explain(cand, b, context=ctx)

            item = RecommendationItem(
                candidate_id=cand.candidate_id,
                media_type=cand.media_type,
                media_item_id=cand.media_item_id,
                trakt_id=cand.trakt_id,
                title=cand.title,
                year=cand.year,
                overview=cand.overview,
                genres=cand.genres,
                runtime_minutes=cand.runtime_minutes,
                score=b.final_score,
                explanation=explanation,
                breakdown=b,
            )
            final_items.append(item)
            serializable_ranks.append(
                {
                    "candidate_id": cand.candidate_id,
                    "title": cand.title,
                    "score": b.final_score,
                    "breakdown": asdict(b),
                    "explanation": explanation,
                }
            )

        # 5. Persist RecommendationRun
        with get_db_session(self.db_engine) as session:
            rec_run = RecommendationRun(
                created_at=now,
                context_json=json.dumps(asdict(ctx)),
                model_version="content-v1",
                ranked_candidates_json=json.dumps(serializable_ranks),
            )
            session.add(rec_run)
            session.flush()
            run_id = rec_run.id

        return RecommendationResult(run_id=run_id, items=final_items, context=ctx)
