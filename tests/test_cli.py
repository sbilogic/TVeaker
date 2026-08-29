"""Unit tests for TVeaker CLI."""

import pytest

from tveaker.cli import main, parse_args


def test_cli_parse_serve():
    args = parse_args(["serve", "--port", "9000"])
    assert args.command == "serve"
    assert args.port == 9000
    assert args.host == "127.0.0.1"


def test_cli_main_help_exits():
    with pytest.raises(SystemExit) as exc_info:
        main(["--help"])
    assert exc_info.value.code == 0


def test_cli_main_valid_command():
    res = main(["backup"])
    assert res == 0
