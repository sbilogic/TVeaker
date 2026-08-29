# TVeaker Agent & Engineering Rules

## 1. Golden Rules
1. **Trakt is Read-Only**: TVeaker NEVER writes history, scrobbles, ratings, or watchlists back to Trakt in v1. All user modifications are stored locally.
2. **Deterministic Recommendation**: Recommendations must be generated locally from SQLite and TF-IDF profiles in $<200\text{ms}$. No remote network or LLM calls in the recommendation request path.
3. **Strict Credential Safety**: OAuth tokens and API secrets must never be logged, printed, committed to Git, or stored in SQLite unmasked. Use OS `keyring` or encrypted storage.
4. **Resilient Pagination**: All Trakt paginated responses must be drained using `X-Pagination-Page` and `X-Pagination-Page-Count` response headers.
5. **Atomic Sync & Transactions**: Each sync dataset writes in its own transaction; activity cursors advance only upon committed success.

## 2. Testing & Quality
- Every task must include comprehensive unit and integration tests.
- Maintain $\ge 90\%$ test coverage across `src/tveaker`.
- Run static checks (`ruff check`, `mypy`) and `pytest` before marking tasks complete.
