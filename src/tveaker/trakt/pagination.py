"""Pagination models and response header parsers for Trakt."""

from collections.abc import Sequence
from dataclasses import dataclass

import httpx


@dataclass(frozen=True)
class PaginatedResponse[T]:
    items: Sequence[T]
    page: int
    page_count: int
    item_count: int
    limit: int

    @property
    def has_next_page(self) -> bool:
        return self.page < self.page_count


def parse_pagination_headers(
    response: httpx.Response,
    default_page: int = 1,
    default_limit: int = 100,
) -> tuple[int, int, int, int]:
    """Extract page, page_count, item_count, and limit from Trakt headers."""
    try:
        page = int(response.headers.get("x-pagination-page", default_page))
    except (ValueError, TypeError):
        page = default_page

    try:
        page_count = int(response.headers.get("x-pagination-page-count", 1))
    except (ValueError, TypeError):
        page_count = 1

    try:
        item_count = int(response.headers.get("x-pagination-item-count", 0))
    except (ValueError, TypeError):
        item_count = 0

    try:
        limit = int(response.headers.get("x-pagination-limit", default_limit))
    except (ValueError, TypeError):
        limit = default_limit

    return page, page_count, item_count, limit
