"""Exception hierarchy for Trakt API client errors."""


class TraktError(Exception):
    """Base exception for all Trakt API errors."""

    pass


class TraktAuthError(TraktError):
    """Raised on authentication/authorization failure (HTTP 401/403)."""

    pass


class TraktNotFoundError(TraktError):
    """Raised when a requested resource is not found (HTTP 404)."""

    pass


class TraktRateLimitError(TraktError):
    """Raised when rate limit is reached (HTTP 429)."""

    def __init__(self, message: str, retry_after: float = 1.0) -> None:
        super().__init__(message)
        self.retry_after = retry_after


class TraktServerError(TraktError):
    """Raised on remote server errors (HTTP 500/502/503/504)."""

    def __init__(self, message: str, status_code: int = 500) -> None:
        super().__init__(message)
        self.status_code = status_code


class TraktContractError(TraktError):
    """Raised when response data violates the expected API schema/contract."""

    pass
