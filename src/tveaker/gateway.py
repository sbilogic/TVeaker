"""HTTPS phone-gateway provisioning for a loopback-only TVeaker server."""

from __future__ import annotations

import json
import re
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import urlopen


class GatewayUnavailableError(RuntimeError):
    """Raised when the private Tailscale gateway cannot be provisioned."""


@dataclass(frozen=True)
class PhoneGateway:
    """The stable HTTPS endpoint a paired phone can use."""

    url: str


CommandRunner = Callable[[list[str]], subprocess.CompletedProcess[str]]
ProcessFactory = Callable[..., subprocess.Popen[str]]
HealthCheck = Callable[[int], None]
ReadyCallback = Callable[[PhoneGateway], None]


class TailscaleGateway:
    """Configures Tailscale Serve without exposing TVeaker to the public internet."""

    def __init__(self, command: str = "tailscale", runner: CommandRunner | None = None) -> None:
        self.command = command
        self._runner = runner or self._run_command

    @staticmethod
    def _run_command(command: list[str]) -> subprocess.CompletedProcess[str]:
        return subprocess.run(command, capture_output=True, check=False, text=True)

    def _execute(self, args: list[str]) -> subprocess.CompletedProcess[str]:
        command = [self.command, *args]
        try:
            result = self._runner(command)
        except OSError as exc:
            raise GatewayUnavailableError(
                "Tailscale is not available. Install it, sign in on this PC, then try again."
            ) from exc
        if result.returncode != 0:
            detail = result.stderr.strip() or result.stdout.strip() or "unknown Tailscale error"
            raise GatewayUnavailableError(f"Tailscale could not configure TVeaker: {detail}")
        return result

    def provision(self, port: int = 8000) -> PhoneGateway:
        """Expose a loopback-only TVeaker server to the private tailnet over HTTPS."""
        status_result = self._execute(["status", "--json"])
        try:
            status = json.loads(status_result.stdout)
        except json.JSONDecodeError as exc:
            raise GatewayUnavailableError(
                "Tailscale returned an unreadable status response."
            ) from exc

        dns_name = str(status.get("Self", {}).get("DNSName") or "").rstrip(".")
        if status.get("BackendState") != "Running" or not dns_name:
            raise GatewayUnavailableError(
                "Tailscale is not connected. Sign in on this PC and your phone first, then retry."
            )

        self._execute(["serve", "--bg", f"http://127.0.0.1:{port}"])
        return PhoneGateway(url=f"https://{dns_name}/")


class CloudflareGateway:
    """Runs a public Cloudflare HTTPS tunnel without opening a LAN listener."""

    _quick_tunnel_url = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com", re.IGNORECASE)

    def __init__(
        self,
        command: str = "cloudflared",
        process_factory: ProcessFactory | None = None,
        health_check: HealthCheck | None = None,
    ) -> None:
        self.command = command
        self._process_factory = process_factory or self._start_process
        self._health_check = health_check or self._verify_local_server

    @staticmethod
    def _start_process(command: list[str]) -> subprocess.Popen[str]:
        return subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

    @staticmethod
    def _verify_local_server(port: int) -> None:
        try:
            with urlopen(f"http://127.0.0.1:{port}/api/v1/health", timeout=3) as response:
                if response.status != 200:
                    raise GatewayUnavailableError(
                        f"TVeaker health check returned HTTP {response.status}."
                    )
        except (HTTPError, URLError, TimeoutError) as exc:
            raise GatewayUnavailableError(
                f"TVeaker is not running on 127.0.0.1:{port}. Start `tveaker serve` first."
            ) from exc

    @staticmethod
    def _normalize_public_url(public_url: str) -> PhoneGateway:
        parsed = urlparse(public_url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise GatewayUnavailableError(
                "A named Cloudflare tunnel needs its complete HTTPS public URL."
            )
        return PhoneGateway(url=f"{public_url.rstrip('/')}/")

    def command_for(self, port: int, tunnel_token: str | None = None) -> list[str]:
        """Return the cloudflared invocation without ever logging its token."""
        if tunnel_token:
            return [self.command, "tunnel", "--no-autoupdate", "run", "--token", tunnel_token]
        return [
            self.command,
            "tunnel",
            "--no-autoupdate",
            "--url",
            f"http://127.0.0.1:{port}",
        ]

    @classmethod
    def quick_url_from_output(cls, output: str) -> PhoneGateway | None:
        """Extract the temporary HTTPS address emitted by a Quick Tunnel."""
        match = cls._quick_tunnel_url.search(output)
        return PhoneGateway(url=f"{match.group(0)}/") if match else None

    def serve(
        self,
        port: int = 8000,
        on_ready: ReadyCallback | None = None,
        tunnel_token: str | None = None,
        public_url: str | None = None,
    ) -> None:
        """Keep a Cloudflare tunnel alive and announce its HTTPS address once ready."""
        self._health_check(port)
        if tunnel_token and not public_url:
            raise GatewayUnavailableError(
                "A named Cloudflare tunnel needs --public-url so TVeaker can show its hostname."
            )
        ready_gateway = self._normalize_public_url(public_url) if public_url else None

        try:
            process = self._process_factory(self.command_for(port, tunnel_token=tunnel_token))
        except OSError as exc:
            raise GatewayUnavailableError(
                "cloudflared is not available. Install Cloudflare Tunnel, then try again."
            ) from exc

        try:
            if ready_gateway is not None and on_ready is not None:
                on_ready(ready_gateway)

            if process.stdout is None:
                raise GatewayUnavailableError("cloudflared did not provide startup output.")

            for output_line in process.stdout:
                if ready_gateway is None:
                    ready_gateway = self.quick_url_from_output(output_line)
                    if ready_gateway is not None and on_ready is not None:
                        on_ready(ready_gateway)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

        if ready_gateway is None:
            raise GatewayUnavailableError(
                "Cloudflare did not provide an HTTPS tunnel URL. "
                "Check its connection and try again."
            )
