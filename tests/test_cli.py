"""Tests for CLI commands."""

from pathlib import Path
from unittest.mock import MagicMock, patch

from click.testing import CliRunner

from tveaker.cli import cli
from tveaker.config import Settings
from tveaker.gateway import PhoneGateway


def test_cli_help():
    runner = CliRunner()
    result = runner.invoke(cli, ["--help"])
    assert result.exit_code == 0
    assert "TVeaker - Local-first TV & Movie Intelligence" in result.output


def test_cli_doctor(tmp_path: Path):
    db_file = tmp_path / "test_doc.db"
    settings = Settings(database_url=f"sqlite:///{db_file}")

    with patch("tveaker.cli.get_settings", return_value=settings):
        runner = CliRunner()
        result = runner.invoke(cli, ["doctor"])
        assert result.exit_code == 0
        assert "TVeaker System Diagnostics" in result.output
        assert "SQLite Integrity" in result.output


def test_cli_backup(tmp_path: Path):
    dest = tmp_path / "test_backup.db"
    runner = CliRunner()
    result = runner.invoke(cli, ["backup", "--dest", str(dest)])
    assert result.exit_code == 0
    assert "Backup created successfully" in result.output
    assert dest.exists()


def test_cli_sync(tmp_path: Path):
    db_file = tmp_path / "test_sync.db"
    settings = Settings(
        database_url=f"sqlite:///{db_file}",
        trakt_client_id="cid",
        trakt_client_secret="csec",
    )

    with (
        patch("tveaker.cli.get_settings", return_value=settings),
        patch("tveaker.cli.AccountSync.run") as mock_run,
    ):
        mock_report = MagicMock()
        mock_report.status = "success"
        mock_report.run_id = 42
        mock_report.fetched = {"movies": 5}
        mock_report.inserted = {"movies": 5}
        mock_report.updated = {}
        mock_report.deleted = {}
        mock_run.return_value = mock_report

        runner = CliRunner()
        result = runner.invoke(cli, ["sync", "--mode", "incremental"])
        assert result.exit_code == 0
        assert "Starting incremental sync with Trakt..." in result.output
        assert "Run #42" in result.output


def test_cli_recommend(tmp_path: Path):
    db_file = tmp_path / "test_rec.db"
    settings = Settings(database_url=f"sqlite:///{db_file}")

    with (
        patch("tveaker.cli.get_settings", return_value=settings),
        patch("tveaker.cli.RecommendationEngine.recommend") as mock_rec,
    ):
        mock_res = MagicMock()
        mock_res.run_id = 99
        mock_item = MagicMock()
        mock_item.title = "Dune: Part Two"
        mock_item.media_type = "movie"
        mock_item.runtime_minutes = 166
        mock_item.score = 0.95
        mock_item.explanation = "Matches your high rating in Sci-Fi"
        mock_res.items = [mock_item]
        mock_rec.return_value = mock_res

        runner = CliRunner()
        result = runner.invoke(cli, ["recommend", "--budget", "180", "--intent", "movie"])
        assert result.exit_code == 0
        assert "Recommendations (Run #99):" in result.output
        assert "Dune: Part Two" in result.output


def test_cli_phone_gateway_runs_cloudflare_by_default():
    runner = CliRunner()
    with patch("tveaker.cli.CloudflareGateway.serve") as serve:
        def announce_url(**kwargs):
            kwargs["on_ready"](PhoneGateway(url="https://online.example.com/"))

        serve.side_effect = announce_url

        result = runner.invoke(cli, ["phone-gateway"])

    assert result.exit_code == 0
    assert "https://online.example.com/" in result.output
    assert "temporary public URL" in result.output


def test_cli_phone_gateway_keeps_tailscale_as_an_explicit_legacy_option():
    runner = CliRunner()
    with patch("tveaker.cli.TailscaleGateway.provision") as provision:
        provision.return_value = PhoneGateway(url="https://planetx.example.ts.net/")

        result = runner.invoke(cli, ["phone-gateway", "--provider", "tailscale"])

    assert result.exit_code == 0
    assert "https://planetx.example.ts.net/" in result.output
