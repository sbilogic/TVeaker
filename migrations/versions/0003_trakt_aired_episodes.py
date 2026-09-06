"""Persist the Trakt export's standard-episode availability snapshot.

Revision ID: 0003_trakt_aired_episodes
Revises: 0002_now_watching
Create Date: 2026-09-04 23:15:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0003_trakt_aired_episodes"
down_revision: str | None = "0002_now_watching"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    bind = op.get_bind()
    insp = sa.inspect(bind)
    cols = [c["name"] for c in insp.get_columns("media_items")]
    if "trakt_aired_episodes" not in cols:
        with op.batch_alter_table("media_items") as batch_op:
            batch_op.add_column(sa.Column("trakt_aired_episodes", sa.Integer(), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table("media_items") as batch_op:
        batch_op.drop_column("trakt_aired_episodes")
