#!/usr/bin/env python3
"""Expose NVIDIA GPU usage per Podman container (exercises-* names) for Prometheus."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import socket
import subprocess
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from prometheus_client import CONTENT_TYPE_LATEST, REGISTRY, generate_latest
from prometheus_client.core import GaugeMetricFamily

LIBPOD_ID_RE = re.compile(r"libpod[-_]([a-f0-9]{64})", re.IGNORECASE)
EXERCISES_NAME_RE = re.compile(r"exercises", re.IGNORECASE)


class ExercisesGpuCollector:
    def __init__(self, podman_socket: str, proc_root: Path) -> None:
        self._podman_socket = podman_socket
        self._proc_root = proc_root
        self._container_names: dict[str, str] = {}

    def collect(self):
        nvidia_ok = GaugeMetricFamily(
            "exercises_gpu_exporter_nvidia_smi_available",
            "1 when nvidia-smi is available on this host",
        )
        if shutil.which("nvidia-smi") is None:
            nvidia_ok.add_metric([], 0)
            yield nvidia_ok
            return
        nvidia_ok.add_metric([], 1)
        yield nvidia_ok

        self._container_names = self._load_container_names()
        mem = GaugeMetricFamily(
            "exercises_container_gpu_memory_bytes",
            "GPU framebuffer memory attributed to a Podman container process",
            labels=["name", "gpu"],
        )
        util = GaugeMetricFamily(
            "exercises_container_gpu_utilization_percent",
            "GPU SM utilization percent attributed to a Podman container process",
            labels=["name", "gpu"],
        )
        procs = GaugeMetricFamily(
            "exercises_container_gpu_processes",
            "GPU compute processes counted for the container on this GPU",
            labels=["name", "gpu"],
        )

        usage: dict[tuple[str, str], dict[str, float]] = {}
        for gpu, pid, memory_mib in self._gpu_process_memory():
            name = self._container_name_for_pid(pid)
            if not name or not EXERCISES_NAME_RE.search(name):
                continue
            key = (name, gpu)
            bucket = usage.setdefault(key, {"memory_mib": 0.0, "util": 0.0, "count": 0})
            bucket["memory_mib"] += memory_mib
            bucket["count"] += 1

        for gpu, pid, sm_util in self._gpu_process_utilization():
            name = self._container_name_for_pid(pid)
            if not name or not EXERCISES_NAME_RE.search(name):
                continue
            key = (name, gpu)
            bucket = usage.setdefault(key, {"memory_mib": 0.0, "util": 0.0, "count": 0})
            bucket["util"] = max(bucket["util"], sm_util)

        for (name, gpu), values in sorted(usage.items()):
            mem.add_metric([name, gpu], values["memory_mib"] * 1024 * 1024)
            util.add_metric([name, gpu], values["util"])
            procs.add_metric([name, gpu], values["count"])

        yield mem
        yield util
        yield procs

    def _load_container_names(self) -> dict[str, str]:
        names: dict[str, str] = {}
        try:
            containers = self._podman_api("/v4.0.0/libpod/containers/json?all=true")
        except OSError:
            return names
        if not isinstance(containers, list):
            return names
        for item in containers:
            if not isinstance(item, dict):
                continue
            cid = str(item.get("Id", "")).strip()
            if not cid:
                continue
            raw_names = item.get("Names") or []
            if not raw_names:
                continue
            display = str(raw_names[0]).lstrip("/")
            names[cid] = display
            names[cid[:12]] = display
        return names

    def _container_name_for_pid(self, pid: str) -> str | None:
        try:
            pid_int = int(pid)
        except ValueError:
            return None
        cgroup_file = self._proc_root / str(pid_int) / "cgroup"
        try:
            text = cgroup_file.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return None
        for line in text.splitlines():
            match = LIBPOD_ID_RE.search(line)
            if not match:
                continue
            full_id = match.group(1).lower()
            name = self._container_names.get(full_id) or self._container_names.get(full_id[:12])
            if name:
                return name
        return self._container_name_via_podman_top(pid_int)

    def _container_name_via_podman_top(self, pid: int) -> str | None:
        if shutil.which("podman") is None:
            return None
        try:
            listed = subprocess.run(
                ["podman", "ps", "--format", "{{.ID}}"],
                capture_output=True,
                text=True,
                check=False,
                timeout=10,
            )
        except (OSError, subprocess.TimeoutExpired):
            return None
        for cid in listed.stdout.splitlines():
            cid = cid.strip()
            if not cid:
                continue
            try:
                top = subprocess.run(
                    ["podman", "top", cid, "-l", "pid"],
                    capture_output=True,
                    text=True,
                    check=False,
                    timeout=10,
                )
            except (OSError, subprocess.TimeoutExpired):
                continue
            pids = {line.strip() for line in top.stdout.splitlines()[1:] if line.strip().isdigit()}
            if str(pid) in pids:
                inspect = subprocess.run(
                    ["podman", "inspect", "-f", "{{.Name}}", cid],
                    capture_output=True,
                    text=True,
                    check=False,
                    timeout=10,
                )
                name = inspect.stdout.strip().lstrip("/")
                return name or None
        return None

    def _gpu_process_memory(self) -> list[tuple[str, str, float]]:
        try:
            completed = subprocess.run(
                [
                    "nvidia-smi",
                    "--query-compute-apps=gpu_bus_id,pid,used_gpu_memory",
                    "--format=csv,noheader,nounits",
                ],
                capture_output=True,
                text=True,
                check=False,
                timeout=15,
            )
        except (OSError, subprocess.TimeoutExpired):
            return []
        rows: list[tuple[str, str, float]] = []
        for line in completed.stdout.splitlines():
            parts = [part.strip() for part in line.split(",")]
            if len(parts) != 3:
                continue
            gpu, pid, memory = parts
            if not pid.isdigit():
                continue
            try:
                memory_mib = float(memory)
            except ValueError:
                continue
            rows.append((self._normalize_gpu_label(gpu), pid, memory_mib))
        return rows

    def _gpu_process_utilization(self) -> list[tuple[str, str, float]]:
        try:
            completed = subprocess.run(
                ["nvidia-smi", "pmon", "-c", "1", "-s", "u"],
                capture_output=True,
                text=True,
                check=False,
                timeout=15,
            )
        except (OSError, subprocess.TimeoutExpired):
            return []
        rows: list[tuple[str, str, float]] = []
        for line in completed.stdout.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 4:
                continue
            gpu, pid, proc_type, sm = parts[0], parts[1], parts[2], parts[3]
            if pid == "-" or not pid.isdigit() or proc_type != "C":
                continue
            try:
                util = float(sm)
            except ValueError:
                continue
            rows.append((gpu, pid, util))
        return rows

    @staticmethod
    def _normalize_gpu_label(gpu_bus_id: str) -> str:
        gpu_bus_id = gpu_bus_id.strip()
        if gpu_bus_id.upper().startswith("GPU-"):
            return gpu_bus_id.upper().replace("GPU-", "gpu")
        return gpu_bus_id or "gpu0"

    def _podman_api(self, path: str) -> Any:
        payload = _unix_http_get(self._podman_socket, path)
        return json.loads(payload)


def _unix_http_get(socket_path: str, path: str) -> str:
    request = (
        f"GET {path} HTTP/1.1\r\n"
        "Host: localhost\r\n"
        "Accept: application/json\r\n"
        "Connection: close\r\n\r\n"
    ).encode("utf-8")
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as sock:
        sock.connect(socket_path)
        sock.sendall(request)
        chunks: list[bytes] = []
        while True:
            data = sock.recv(65536)
            if not data:
                break
            chunks.append(data)
    raw = b"".join(chunks)
    _, _, body = raw.partition(b"\r\n\r\n")
    return body.decode("utf-8", errors="replace")


class MetricsHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if urlparse(self.path).path != "/metrics":
            self.send_error(404)
            return
        output = generate_latest(REGISTRY)
        self.send_response(200)
        self.send_header("Content-Type", CONTENT_TYPE_LATEST)
        self.send_header("Content-Length", str(len(output)))
        self.end_headers()
        self.wfile.write(output)

    def log_message(self, format: str, *args: Any) -> None:
        return


def main() -> int:
    parser = argparse.ArgumentParser(description="Exercises GPU exporter for Prometheus")
    parser.add_argument("--listen", default="0.0.0.0:9066", help="host:port to bind")
    parser.add_argument(
        "--podman-socket",
        default="/run/podman/podman.sock",
        help="Podman API socket (override for rootless)",
    )
    parser.add_argument(
        "--proc-root",
        default="/proc",
        help="Procfs root (use /host/proc when running in a container)",
    )
    args = parser.parse_args()

    if "://" in args.listen:
        print("Use host:port for --listen", file=sys.stderr)
        return 2
    host, port_str = args.listen.rsplit(":", 1)
    port = int(port_str)

    socket_path = args.podman_socket
    if not Path(socket_path).exists():
        fallback = Path("/run/user/1000/podman/podman.sock")
        if fallback.exists():
            socket_path = str(fallback)

    REGISTRY.register(ExercisesGpuCollector(socket_path, Path(args.proc_root)))
    server = HTTPServer((host, port), MetricsHandler)
    print(f"exercises-gpu-exporter listening on http://{host}:{port}/metrics", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
