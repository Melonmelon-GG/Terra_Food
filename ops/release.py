#!/usr/bin/env python3
"""Build every app component, apply explicitly, mark deployed only after real health checks.

Run on the mini-host. This program never fetches/pulls the live checkout, migrates data
itself, installs timers, or automatically rolls back a database schema.
"""
import argparse
import fcntl
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import urllib.request

SERVICES = ("backend", "web", "agent-api", "agent-mcp")
DOCKER = ["/snap/docker/current/bin/docker", "--host", "unix:///var/run/docker.sock"] if Path("/snap/docker/current/bin/docker").exists() else ["docker"]


def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, timeout=kwargs.pop("timeout", 900 if "build" in args else 30), **kwargs)


def capture(args):
    return run(args, capture_output=True).stdout.strip()


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2)+"\n")
    temporary.replace(path)


def prepare(repo, revision, output):
    sha = capture(["git", "-C", str(repo), "rev-parse", revision+"^{commit}"])
    images = {}
    with tempfile.TemporaryDirectory(prefix="terra-release-") as name:
        root = Path(name)
        archive = root / "source.tar"
        with archive.open("wb") as stream:
            subprocess.run(["git", "-C", str(repo), "archive", sha], check=True, stdout=stream)
        with tarfile.open(archive) as source:
            source.extractall(root / "source", filter="data")
        for component, dockerfile in (("backend", "ops/Dockerfile.backend"), ("web", "performance/Dockerfile.web"), ("agent", "agent-service/Dockerfile")):
            tag = "terrafood-"+component+":"+sha
            context = root / "source" / "agent-service" if component == "agent" else root / "source"
            run(DOCKER+["build", "--label", "org.opencontainers.image.revision="+sha,
                        "--build-arg", "SOURCE_REVISION="+sha, "-t", tag,
                        "-f", str(root / "source" / dockerfile), str(context)])
            images[component] = capture(DOCKER+["image", "inspect", tag, "--format", "{{.Id}}"])
    images["agent-api"] = images["agent-mcp"] = images.pop("agent")
    save(output, {"schema": 1, "revision": sha, "images": images, "prepared_at": time.time()})


def healthy(base):
    for path in ("/", "/api/foods/catalog?page=1&pageSize=1"):
        with urllib.request.urlopen(base+path, timeout=5) as response:
            body = response.read(262144)
            if response.status != 200:
                return False
            if path == "/" and b"<html" not in body.lower():
                return False
            if path != "/" and not isinstance(json.loads(body).get("items"), list):
                return False
    return True


def apply(args):
    manifest = json.loads(args.manifest.read_text())
    if not re.fullmatch(r"[0-9a-f]{40}",manifest.get("revision", "")):
        raise ValueError("release revision must be a full commit hash")
    if set(manifest["images"]) != set(SERVICES):
        raise ValueError("release must contain backend, web and both Agent components")
    for identity in manifest["images"].values():
        if not identity.startswith("sha256:") or len(identity) != 71:
            raise ValueError("unresolved image identity")
        capture(DOCKER+["image", "inspect", identity, "--format", "{{.Id}}"])
    if args.env_file.stat().st_mode & 0o077:
        raise ValueError("runtime env file must be private (0600)")
    required = {"AGENT_INTERNAL_TOKEN", "MCP_INTERNAL_TOKEN", "MCP_BACKEND_TOKEN", "AGENT_CONTEXT_SECRET"}
    variables = dict(line.split("=", 1) for line in args.env_file.read_text().splitlines() if "=" in line and not line.lstrip().startswith("#"))
    if any(len(variables.get(key, "")) < 32 for key in required) or len({variables[key] for key in required}) != 4:
        raise ValueError("four distinct service credentials of at least 32 characters are required")
    override = args.manifest.with_suffix(".compose.json")
    service_credentials = {
        "backend": ("AGENT_INTERNAL_TOKEN", "MCP_BACKEND_TOKEN", "AGENT_CONTEXT_SECRET"),
        "agent-api": ("AGENT_INTERNAL_TOKEN", "MCP_INTERNAL_TOKEN"),
        "agent-mcp": ("MCP_INTERNAL_TOKEN", "MCP_BACKEND_TOKEN"),
        "web": (),
    }
    # Interpolation references, never credential values, enter the manifest directory.
    # Explicitly propagate the new token names even with an older base Compose file.
    save(override, {"services": {name: {"image": identity, "build": None,
        "environment": {key: "${"+key+":?required service credential}" for key in service_credentials[name]}}
        for name, identity in manifest["images"].items()}})
    compose = DOCKER+["compose", "--project-name", args.project, "--env-file", str(args.env_file), "-f", str(args.compose), "-f", str(override)]
    # Validate without printing interpolated secrets.
    run(compose+["config", "--quiet"])
    if not args.execute:
        print("Release prepared and configuration checked; no running services changed.")
        return
    with open("/run/lock/terrafood-maintenance.lock", "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        # Desired revision is not a deployed marker. A failed build/apply remains retryable.
        run(compose+["up", "-d", "--no-build", "--no-deps", *SERVICES])
        deadline = time.monotonic()+150
        while time.monotonic() < deadline:
            try:
                if not healthy(args.health_url.rstrip("/")):
                    raise RuntimeError("public origin health failed")
                for service, image in manifest["images"].items():
                    container = capture(compose+["ps", "-q", service])
                    actual = capture(DOCKER+["inspect", container, "--format", "{{.Image}}"])
                    if actual != image:
                        raise RuntimeError("running artifact mismatch: "+service)
                run(compose+["exec", "-T", "agent-api", "python", "-c",
                    "import json,urllib.request;d=json.load(urllib.request.urlopen('http://127.0.0.1:8090/health',timeout=3));assert d['status']=='ok' and d['revision']=='"+manifest["revision"]+"'"])
                run(compose+["exec", "-T", "agent-mcp", "python", "-c",
                    "import urllib.request,urllib.error\ntry:\n urllib.request.urlopen('http://127.0.0.1:8091/mcp',timeout=3);raise RuntimeError('unauthenticated MCP admitted')\nexcept urllib.error.HTTPError as e:\n assert e.code==401"])
                save(args.deployed, {**manifest, "verified_at": time.time()})
                print("Release verified: "+manifest["revision"])
                return
            except Exception:
                time.sleep(3)
        raise RuntimeError("release health failed; deployed manifest unchanged; review migrations before any rollback")


def main():
    parser=argparse.ArgumentParser()
    commands=parser.add_subparsers(dest="command", required=True)
    build=commands.add_parser("prepare")
    build.add_argument("--repo", type=Path, required=True)
    build.add_argument("--revision", required=True)
    build.add_argument("--manifest", type=Path, required=True)
    deploy=commands.add_parser("apply")
    deploy.add_argument("--manifest", type=Path, required=True)
    deploy.add_argument("--compose", type=Path, required=True)
    deploy.add_argument("--env-file", type=Path, required=True)
    deploy.add_argument("--project", required=True)
    deploy.add_argument("--health-url", default="http://127.0.0.1:8180")
    deploy.add_argument("--deployed", type=Path, required=True)
    deploy.add_argument("--execute", action="store_true")
    args=parser.parse_args()
    if args.command=="prepare": prepare(args.repo,args.revision,args.manifest)
    else: apply(args)


if __name__=="__main__": main()
