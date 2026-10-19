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
import signal
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import urllib.request
import urllib.parse

SERVICES = ("backend", "web", "agent-api", "agent-mcp")
DOCKER = ["/snap/docker/current/bin/docker", "--host", "unix:///var/run/docker.sock"] if Path("/snap/docker/current/bin/docker").exists() else ["docker"]
# The native Compose plugin can read host-private /var/lib manifests; Snap's
# wrapper has a different filesystem view. Explicitly retain the local daemon.
COMPOSE = ["env","DOCKER_HOST=unix:///var/run/docker.sock","/snap/docker/current/usr/libexec/docker/cli-plugins/docker-compose"] if Path("/snap/docker/current/usr/libexec/docker/cli-plugins/docker-compose").exists() else DOCKER+["compose"]
BUILD = ["/snap/bin/docker", "--host", "unix:///var/run/docker.sock"] if Path("/snap/bin/docker").exists() else DOCKER


def run(args, **kwargs):
    timeout = kwargs.pop("timeout", 1800 if "build" in args else 30)
    # Terminate the client process group on timeout, rather than orphaning a child
    # CLI. A daemon-side build may still finish; only a complete manifest is usable.
    capture_output = kwargs.pop("capture_output", False)
    if capture_output:
        kwargs.update(stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    with subprocess.Popen(args, text=True, start_new_session=True, **kwargs) as process:
        try:
            stdout, stderr = process.communicate(timeout=timeout)
        except BaseException:
            try: os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError: pass
            try: process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate()
            raise
        result = subprocess.CompletedProcess(args, process.returncode, stdout, stderr)
        result.check_returncode()
        return result


def capture(args):
    return run(args, capture_output=True).stdout.strip()


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    with temporary.open('w') as stream:
        stream.write(json.dumps(data, indent=2)+"\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)
    descriptor=os.open(path.parent,os.O_RDONLY)
    try: os.fsync(descriptor)
    finally: os.close(descriptor)


def prepare(repo, revision, output):
    git = ["git", "-c", "safe.directory="+str(repo), "-C", str(repo)]
    sha = capture(git+["rev-parse", revision+"^{commit}"])
    images = {}
    build_root=os.environ.get('TERRA_RELEASE_BUILD_ROOT')
    if build_root: Path(build_root).mkdir(parents=True,exist_ok=True,mode=0o700)
    with tempfile.TemporaryDirectory(prefix="terra-release-",dir=build_root) as name:
        root = Path(name)
        archive = root / "source.tar"
        with archive.open("wb") as stream:
            subprocess.run(git+["archive", sha], check=True, stdout=stream)
        with tarfile.open(archive) as source:
            source.extractall(root / "source", filter="data")
        for component, dockerfile in (("backend", "ops/Dockerfile.backend"), ("web", "performance/Dockerfile.web"), ("agent", "agent-service/Dockerfile")):
            tag = "terrafood-"+component+":"+sha
            try:
                label=capture(DOCKER+["image","inspect",tag,"--format",'{{index .Config.Labels "org.opencontainers.image.revision"}}'])
                if label == sha:
                    images[component]=capture(DOCKER+["image","inspect",tag,"--format","{{.Id}}"])
                    continue
            except subprocess.CalledProcessError: pass
            context = root / "source" / "agent-service" if component == "agent" else root / "source"
            extra=[]
            if component=='agent' and os.environ.get('TERRA_PIP_INDEX_URL'):
                url=os.environ['TERRA_PIP_INDEX_URL']; parsed=urllib.parse.urlsplit(url)
                if parsed.scheme!='https' or parsed.username or parsed.password: raise ValueError('HTTPS package index without embedded credentials required')
                extra=['--build-arg','PIP_INDEX_URL='+url]
            run(BUILD+["build", "--label", "org.opencontainers.image.revision="+sha,
                        "--build-arg", "SOURCE_REVISION="+sha, *extra, "-t", tag,
                        "-f", str(root / "source" / dockerfile), str(context)])
            images[component] = capture(DOCKER+["image", "inspect", tag, "--format", "{{.Id}}"])
    images["agent-api"] = images["agent-mcp"] = images.pop("agent")
    html = capture(DOCKER+["run", "--rm", "--network", "none", "--label", "terrafood-release-probe=true",
                           "--entrypoint", "cat", images["web"], "/usr/share/nginx/html/index.html"])
    # capture() strips whitespace; verification uses the same normalization.
    save(output, {"schema": 2, "revision": sha, "images": images,
                  "web_entry_sha256": hashlib.sha256(html.encode()).hexdigest(), "prepared_at": time.time()})


def healthy(base, expected_html=None):
    def request(url): return urllib.request.Request(url,headers={'User-Agent':'TerraFood-Release/2.0'})
    for path in ("/", "/api/foods/catalog?page=1&pageSize=1"):
        with urllib.request.urlopen(request(base+path), timeout=5) as response:
            body = response.read(262144)
            if response.status != 200:
                return False
            if path == "/" and b"<html" not in body.lower():
                return False
            if path == "/" and expected_html:
                if hashlib.sha256(body.decode().strip().encode()).hexdigest() != expected_html:
                    return False
                assets = re.findall(r'(?:src|href)=["\'](/assets/[^"\']+\.(?:js|css))["\']', body.decode())
                if not assets or len(assets) > 8:
                    return False
                for asset in assets:
                    if ".." in asset or "?" in asset: return False
                    with urllib.request.urlopen(request(base+asset), timeout=5) as resource:
                        expected_type = "css" if asset.endswith(".css") else "javascript"
                        content_type = resource.headers.get("Content-Type", "")
                        if resource.status != 200 or expected_type not in content_type or not resource.read(1):
                            return False
            if path != "/" and not isinstance(json.loads(body).get("items"), list):
                return False
    return True


def validate_runtime(env_file):
    if env_file.stat().st_mode & 0o077:
        raise ValueError("runtime env file must be private (0600)")
    required = {"AGENT_INTERNAL_TOKEN", "MCP_INTERNAL_TOKEN", "MCP_BACKEND_TOKEN", "AGENT_CONTEXT_SECRET"}
    variables = dict(line.split("=", 1) for line in env_file.read_text().splitlines() if "=" in line and not line.lstrip().startswith("#"))
    if any(len(variables.get(key, "")) < 32 for key in required) or len({variables[key] for key in required}) != 4:
        raise ValueError("four distinct service credentials of at least 32 characters are required")


def validate_manifest(manifest):
    if not re.fullmatch(r"[0-9a-f]{40}",manifest.get("revision", "")):
        raise ValueError("release revision must be a full commit hash")
    if set(manifest["images"]) != set(SERVICES):
        raise ValueError("release must contain backend, web and both Agent components")
    for identity in manifest["images"].values():
        if not re.fullmatch(r'sha256:[0-9a-f]{64}',identity):
            raise ValueError("unresolved image identity")
        capture(DOCKER+["image", "inspect", identity, "--format", "{{.Id}}"])
        actual_revision = capture(DOCKER+["image", "inspect", identity, "--format", '{{index .Config.Labels "org.opencontainers.image.revision"}}'])
        if actual_revision != manifest["revision"]:
            raise ValueError("image source revision mismatch")


def compose_command(args, override):
    return COMPOSE+["--project-name", args.project, "--env-file", str(args.env_file), "-f", str(args.compose), "-f", str(override)]


def verify_running(args, manifest, override):
    if not healthy(args.health_url.rstrip("/"), manifest.get("web_entry_sha256")):
        raise RuntimeError("public origin health/artifact failed")
    compose = compose_command(args, override)
    for service, image in manifest["images"].items():
        container = capture(compose+["ps", "-q", service])
        state = json.loads(capture(DOCKER+["inspect", container]))[0]
        if state["Image"] != image or not state["State"]["Running"]:
            raise RuntimeError("running artifact mismatch: "+service)
        if state["State"].get("Health", {}).get("Status", "healthy") != "healthy":
            raise RuntimeError("container not healthy: "+service)
    run(compose+["exec", "-T", "agent-api", "python", "-c",
        "import json,urllib.request;d=json.load(urllib.request.urlopen('http://127.0.0.1:8090/health',timeout=3));assert d['status']=='ok' and d['revision']=='"+manifest["revision"]+"'"])
    run(compose+["exec", "-T", "agent-mcp", "python", "-c",
        "import urllib.request,urllib.error\ntry:\n urllib.request.urlopen('http://127.0.0.1:8091/mcp',timeout=3);raise RuntimeError('unauthenticated MCP admitted')\nexcept urllib.error.HTTPError as e:\n assert e.code==401"])


def validate_upload_readability(compose, backend_image):
    container=capture(compose+['ps','-aq','backend'])
    if not container: return
    config=json.loads(capture(compose+['config','--format','json']))
    directory=config['services']['backend'].get('environment',{}).get('UPLOAD_DIRECTORY','/data/uploads')
    if not directory.startswith('/'): raise ValueError('upload directory must be absolute')
    # The candidate's actual UID checks inherited uploads before any replacement.
    # No network, no writeable filesystem, and no database/model startup.
    command='test -d "$1" && test -r "$1" && test -x "$1" || exit 1; unreadable=$(find "$1" -type f ! -readable -print -quit) || exit 1; test -z "$unreadable"'
    run(DOCKER+['run','--rm','--network','none','--read-only','--label','terrafood-release-probe=true',
                '--volumes-from',container+':ro','--entrypoint','sh',backend_image,'-c',command,'sh',directory],capture_output=True)


def apply(args):
    manifest = json.loads(args.manifest.read_text())
    validate_manifest(manifest)
    validate_runtime(args.env_file)
    override = args.manifest.with_suffix(".compose.json")
    service_credentials = {
        "backend": ("AGENT_INTERNAL_TOKEN", "MCP_BACKEND_TOKEN", "AGENT_CONTEXT_SECRET"),
        "agent-api": ("AGENT_INTERNAL_TOKEN", "MCP_INTERNAL_TOKEN"),
        "agent-mcp": ("MCP_INTERNAL_TOKEN", "MCP_BACKEND_TOKEN"),
        "web": (),
    }
    # Interpolation references, never credential values, enter the manifest directory.
    # Explicitly propagate the new token names even with an older base Compose file.
    # Keep inherited build definitions syntactically valid; --no-build below is
    # the execution guard. Compose v5 rejects a null build on image-only services.
    save(override, {"services": {name: {"image": identity,
        "environment": {key: "${"+key+":?required service credential}" for key in service_credentials[name]}}
        for name, identity in manifest["images"].items()}})
    compose = compose_command(args, override)
    # Validate without printing interpolated secrets.
    run(compose+["config", "--quiet"])
    if not args.execute:
        print("Release prepared and configuration checked; no running services changed.")
        return
    validate_upload_readability(compose,manifest['images']['backend'])
    with open("/run/lock/terrafood-maintenance.lock", "w") as lock:
        deadline=time.monotonic()+30
        while True:
            try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB); break
            except BlockingIOError:
                if time.monotonic()>=deadline: raise RuntimeError('maintenance lock busy')
                time.sleep(1)
        # Desired revision is not a deployed marker. A failed build/apply remains retryable.
        run(compose+["up", "-d", "--no-build", "--no-deps", *SERVICES],timeout=180)
        deadline = time.monotonic()+150
        while time.monotonic() < deadline:
            try:
                verify_running(args, manifest, override)
                save(args.deployed.with_suffix('.compose.json'),json.loads(override.read_text()))
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


if __name__=="__main__":
    def stop(signum,frame): raise KeyboardInterrupt('release interrupted')
    signal.signal(signal.SIGTERM,stop)
    signal.signal(signal.SIGINT,stop)
    main()
