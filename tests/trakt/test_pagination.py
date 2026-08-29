"""Tests for pagination parsing and models."""

import httpx

from tveaker.trakt.pagination import PaginatedResponse, parse_pagination_headers


def test_parse_pagination_headers_standard():
    resp = httpx.Response(
        200,
        headers={
            "x-pagination-page": "2",
            "x-pagination-page-count": "5",
            "x-pagination-item-count": "450",
            "x-pagination-limit": "100",
        },
    )
    page, page_count, item_count, limit = parse_pagination_headers(resp)
    assert page == 2
    assert page_count == 5
    assert item_count == 450
    assert limit == 100

    paginated = PaginatedResponse(
        items=["item1", "item2"],
        page=page,
        page_count=page_count,
        item_count=item_count,
        limit=limit,
    )
    assert paginated.has_next_page is True


def test_parse_pagination_headers_missing():
    resp = httpx.Response(200)
    page, page_count, item_count, limit = parse_pagination_headers(
        resp, default_page=1, default_limit=50
    )
    assert page == 1
    assert page_count == 1
    assert item_count == 0
    assert limit == 50

    paginated = PaginatedResponse(
        items=[],
        page=page,
        page_count=page_count,
        item_count=item_count,
        limit=limit,
    )
    assert paginated.has_next_page is False
