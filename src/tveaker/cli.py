"""Command line interface for TVeaker."""

import argparse
import sys
from collections.abc import Sequence


def parse_args(args: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="tveaker",
        description="TVeaker: Private, local-first media tracker and recommendation engine.",
    )
    subparsers = parser.add_subparsers(dest="command", help="Available subcommands")

    # serve command
    serve_parser = subparsers.add_parser("serve", help="Run the TVeaker web and API server")
    serve_parser.add_argument(
        "--host",
        default="127.0.0.1",
        help="Host address to bind (default: 127.0.0.1)",
    )
    serve_parser.add_argument(
        "--port",
        type=int,
        default=8000,
        help="Port to bind (default: 8000)",
    )
    serve_parser.add_argument(
        "--reload",
        action="store_true",
        help="Enable auto-reload for development",
    )

    # sync command
    sync_parser = subparsers.add_parser("sync", help="Run an incremental or full sync from Trakt")
    sync_parser.add_argument(
        "--mode",
        choices=["incremental", "initial", "full"],
        default="incremental",
        help="Sync mode (default: incremental)",
    )

    # backup command
    subparsers.add_parser("backup", help="Create an online SQLite backup of the local database")

    # doctor command
    subparsers.add_parser("doctor", help="Run system diagnostics and verify environment health")

    return parser.parse_args(args)


def main(args: Sequence[str] | None = None) -> int:
    if args is None:
        args = sys.argv[1:]
    if not args:
        parse_args(["--help"])
        return 0
    parsed = parse_args(args)
    if not parsed.command:
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
