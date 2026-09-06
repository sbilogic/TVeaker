"""Tests for phone gateway setup."""

import json
from io import StringIO
from subprocess import CompletedProcess
from unittest.mock import MagicMock, patch
from urllib.error import URLError

import pytest

from tveaker.gateway import (
    CloudflareGateway,
    GatewayUnavailableError,
    PhoneGateway,
    TailscaleGateway,
)


class FakeTunnelProcess:
    def __init__(self, output: str, returncode: int = 0) -> None:
        self.stdout = StringIO(output)
        self.returncode = returncode
        self.terminated = False

    def poll(self) -> int | None:
        return self.returncode

    def wait(self, timeout: float | None = None) -> int:
        return self.returncode

    def terminate(self) -> None:
        self.terminated = True

    def kill(self) -> None:
        self.terminated = True


class RunningFakeTunnelProcess(FakeTunnelProcess):
    def __init__(self, output: str) -> None:
        super().__init__(output, returncode=0)
        self.running = True

    def poll(self) -> int | None:
        return None if self.running else self.returncode

    def terminate(self) -> None:
        self.terminated = True
        self.running = False


def test_tailscale_gateway_uses_stable_https_magicdns_url() -> None:
    commands: list[list[str]] = []

    def runner(command: list[str]) -> CompletedProcess[str]:
        commands.append(command)
        if command[1:] == ["status", "--json"]:
            return CompletedProcess(
                command,
                0,
                json.dumps(
                    {
                        "BackendState": "Running",
                        "Self": {"DNSName": "planetx.example.ts.net."},
                    }
                ),
                "",
            )
        return CompletedProcess(command, 0, "", "")

    gateway = TailscaleGateway(command="tailscale", runner=runner)

    assert gateway.provision(port=8000).url == "https://planetx.example.ts.net/"
    assert commands == [
        ["tailscale", "status", "--json"],
        ["tailscale", "serve", "--bg", "http://127.0.0.1:8000"],
    ]


def test_tailscale_gateway_requires_an_active_tailnet() -> None:
    def runner(command: list[str]) -> CompletedProcess[str]:
        return CompletedProcess(
            command,
            0,
            json.dumps({"BackendState": "NoState", "Self": {"DNSName": ""}}),
            "",
        )

    gateway = TailscaleGateway(command="tailscale", runner=runner)

    with pytest.raises(GatewayUnavailableError, match="not connected"):
        gateway.provision()


def test_cloudflare_gateway_uses_loopback_quick_tunnel_and_announces_its_url() -> None:
    commands: list[list[str]] = []
    announced: list[PhoneGateway] = []
    process = FakeTunnelProcess(
        "INFO Your quick Tunnel has been created! Visit it at "
        "https://seasons-example.trycloudflare.com\n"
    )

    def process_factory(command: list[str], **_: object) -> FakeTunnelProcess:
        commands.append(command)
        return process

    gateway = CloudflareGateway(
        command="cloudflared",
        process_factory=process_factory,
        health_check=lambda port: None,
    )

    gateway.serve(port=8000, on_ready=announced.append)

    assert commands == [
        [
            "cloudflared",
            "tunnel",
            "--no-autoupdate",
            "--url",
            "http://127.0.0.1:8000",
        ]
    ]
    assert announced == [PhoneGateway(url="https://seasons-example.trycloudflare.com/")]


def test_cloudflare_gateway_requires_a_public_url_for_named_tunnel() -> None:
    gateway = CloudflareGateway(health_check=lambda port: None)

    with pytest.raises(GatewayUnavailableError, match="--public-url"):
        gateway.serve(tunnel_token="not-a-real-token")


def test_cloudflare_gateway_uses_named_tunnel_without_printing_the_token() -> None:
    commands: list[list[str]] = []
    announced: list[PhoneGateway] = []
    process = FakeTunnelProcess("")

    def process_factory(command: list[str], **_: object) -> FakeTunnelProcess:
        commands.append(command)
        return process

    gateway = CloudflareGateway(
        process_factory=process_factory,
        health_check=lambda port: None,
    )

    gateway.serve(
        tunnel_token="not-a-real-token",
        public_url="https://tv.example.com",
        on_ready=announced.append,
    )

    assert commands == [
        ["cloudflared", "tunnel", "--no-autoupdate", "run", "--token", "not-a-real-token"]
    ]
    assert announced == [PhoneGateway(url="https://tv.example.com/")]


def test_cloudflare_gateway_checks_the_local_api_before_opening_a_public_route() -> None:
    response = MagicMock(status=200)
    with patch("tveaker.gateway.urlopen") as open_url:
        open_url.return_value.__enter__.return_value = response

        CloudflareGateway._verify_local_server(8123)

    open_url.assert_called_once_with("http://127.0.0.1:8123/api/v1/health", timeout=3)


def test_cloudflare_gateway_explains_when_the_local_api_is_not_running() -> None:
    with (
        patch("tveaker.gateway.urlopen", side_effect=URLError("connection refused")),
        pytest.raises(GatewayUnavailableError, match="Start `tveaker serve` first"),
    ):
        CloudflareGateway._verify_local_server(8123)


def test_cloudflare_gateway_rejects_an_invalid_named_public_url_before_starting() -> None:
    process_factory = MagicMock()
    gateway = CloudflareGateway(process_factory=process_factory, health_check=lambda port: None)

    with pytest.raises(GatewayUnavailableError, match="complete HTTPS"):
        gateway.serve(tunnel_token="not-a-real-token", public_url="http://tv.example.com")

    process_factory.assert_not_called()


def test_cloudflare_gateway_explains_when_cloudflared_is_not_installed() -> None:
    def missing_process(_: list[str], **__: object) -> FakeTunnelProcess:
        raise OSError("not found")

    gateway = CloudflareGateway(process_factory=missing_process, health_check=lambda port: None)

    with pytest.raises(GatewayUnavailableError, match="cloudflared is not available"):
        gateway.serve()


def test_cloudflare_gateway_stops_a_failed_tunnel_and_reports_missing_url() -> None:
    process = RunningFakeTunnelProcess("Cloudflare could not connect\n")
    gateway = CloudflareGateway(
        process_factory=lambda *args, **kwargs: process,
        health_check=lambda port: None,
    )

    with pytest.raises(GatewayUnavailableError, match="did not provide an HTTPS tunnel URL"):
        gateway.serve()

    assert process.terminated
