#!/usr/bin/env python3
"""Actual HTTP/Session/SMTP regression against the explicitly disposable September audit stack.

All values that can identify a credential, code, cookie or private body stay in memory.
This suite never accepts a production URL/container/database. SQL mutation is used only
for the opt-in transient failure trigger, not for the business actions under test.
"""
import argparse
import concurrent.futures
import copy
import http.cookiejar
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib
import struct

PROJECT = "terra-audit-20260922"
DATABASE = "terra_audit_20260922"
MYSQL = PROJECT + "-mysql-1"
BACKEND = PROJECT + "-backend-1"
BASES = {"http://127.0.0.1:18380", "http://127.0.0.1:18381"}
MAIL = "http://127.0.0.1:18325"
DOCKER = ["/snap/docker/current/bin/docker", "--host", "unix:///var/run/docker.sock"] if Path("/snap/docker/current/bin/docker").exists() else ["docker"]


class CheckFailure(Exception):
    pass


def check(condition, safe_message):
    if not condition:
        raise CheckFailure(safe_message)


def command(arguments, *, input_text=None, timeout=30):
    result = subprocess.run(arguments, input=input_text, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise CheckFailure("isolated subprocess failed (output suppressed)")
    return result.stdout.strip()


def sql(statement, *, fixture_admin=False):
    shell = ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names -h127.0.0.1 -uroot "$MYSQL_DATABASE"'
             if fixture_admin else 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names -h127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE"')
    return command(DOCKER + ["exec", "-i", MYSQL, "sh", "-c",
        shell],
        input_text=statement)


def scalar(statement):
    return int(sql(statement))


def originals():
    return set(command(DOCKER + ["exec", BACKEND, "find", "/data/uploads", "-maxdepth", "1", "-type", "f", "-printf", "%f\n"]).splitlines())


def png():
    def chunk(kind, content):
        return struct.pack(">I", len(content)) + kind + content + struct.pack(">I", zlib.crc32(kind + content) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"\x00" + b"\x80\x30\x20\xff" * 2 + b"\x00" + b"\x80\x30\x20\xff" * 2)) + chunk(b"IEND", b"")


class Session:
    def __init__(self, base):
        check(base in BASES, "refusing non-audit URL")
        self.base = base
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf = None
        self.user_id = None

    def clone(self):
        duplicate = Session(self.base)
        for cookie in self.cookies:
            duplicate.cookies.set_cookie(copy.copy(cookie))
        duplicate.csrf = copy.copy(self.csrf)
        duplicate.user_id = self.user_id
        return duplicate

    def token(self):
        status, body, headers = self.request("GET", "/api/auth/csrf")
        check(status == 200 and isinstance(body, dict) and "token" in body, "CSRF acquisition failed")
        self.csrf = body

    def request(self, method, path, payload=None, *, expected=None, raw=None, content_type=None):
        check(path.startswith("/api/"), "unexpected HTTP path")
        headers = {"Accept": "application/json"}
        if method not in ("GET", "HEAD", "OPTIONS"):
            if self.csrf is None:
                self.token()
            headers[self.csrf["headerName"]] = self.csrf["token"]
            if expected is not None or self.user_id is not None:
                headers["X-Expected-User-Id"] = str(self.user_id if expected is None else expected)
        data = raw
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=20)
        except urllib.error.HTTPError as rejected:
            response = rejected
        with response:
            encoded = response.read(1_048_577)
            check(len(encoded) <= 1_048_576, "unexpected oversized response")
            try:
                body = json.loads(encoded) if encoded else None
            except (ValueError, UnicodeDecodeError):
                body = None
            return response.status, body, dict(response.headers)

    def login(self, username, password, role="USER", expected_status=200):
        status, body, _ = self.request("POST", "/api/auth/login", {"username": username, "password": password, "role": role})
        check(status == expected_status, "login status expected " + str(expected_status) + " got " + str(status))
        if status == 200:
            self.csrf = None
            self.user_id = body["id"]
            me_status, me, _ = self.request("GET", "/api/auth/me")
            check(me_status == 200 and me["id"] == self.user_id and me["role"] in (role, "SUB_ADMIN"), "login identity mismatch")
        return body

    def upload(self, content, filename="sample.png"):
        boundary = "AuditBoundary" + secrets.token_hex(12)
        raw = ("--" + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + filename + '"\r\nContent-Type: image/png\r\n\r\n').encode() + content + ("\r\n--" + boundary + "--\r\n").encode()
        return self.request("POST", "/api/images", raw=raw, content_type="multipart/form-data; boundary=" + boundary)


def mail_code(address, after_ids):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        with urllib.request.urlopen(MAIL + "/api/v1/messages", timeout=5) as response:
            summary = json.load(response)
        for item in summary.get("messages", []):
            identity = item["ID"]
            if identity in after_ids:
                continue
            destinations = item.get("To", [])
            if not any(destination.get("Address", "").lower() == address.lower() for destination in destinations):
                continue
            with urllib.request.urlopen(MAIL + "/api/v1/message/" + urllib.parse.quote(identity, safe=""), timeout=5) as response:
                message = json.load(response)
            match = re.search(r"(?<![0-9])[0-9]{6}(?![0-9])", message.get("Text", ""))
            if match:
                return match.group(0)
        time.sleep(.2)
    raise CheckFailure("test SMTP message not received within budget")


def mailbox_ids():
    with urllib.request.urlopen(MAIL + "/api/v1/messages", timeout=5) as response:
        return {item["ID"] for item in json.load(response).get("messages", [])}


def signup(base, username, email, password):
    session = Session(base)
    session.token()
    status, challenge, _ = session.request("GET", "/api/auth/captcha")
    check(status == 200, "registration captcha unavailable")
    expression = re.fullmatch(r"([0-9]+) ([+-]) ([0-9]+) = \?", challenge["question"])
    check(expression is not None, "unexpected captcha format")
    left, operator, right = expression.groups()
    answer = int(left) + int(right) if operator == "+" else int(left) - int(right)
    previous = mailbox_ids()
    status, _, _ = session.request("POST", "/api/auth/registration-code", {"email": email, "captchaId": challenge["captchaId"], "captchaAnswer": str(answer)})
    check(status == 204, "registration mail request status " + str(status))
    code = mail_code(email, previous)
    status, body, _ = session.request("POST", "/api/auth/register", {"username": username, "password": password, "displayName": "Audit Reader", "email": email, "verificationCode": code})
    check(status == 201, "registration status " + str(status))
    session.login(username, password)
    check(session.user_id == body["id"], "registration/login account mismatch")
    return session


class Suite:
    def __init__(self, args):
        self.args = args
        self.results = []
        self.state = {}
        self.prefix = "aud" + time.strftime("%H%M%S") + secrets.token_hex(3)
        self.password = "Audit" + secrets.token_hex(4) + "8"

    def case(self, identity, operation):
        started = time.monotonic()
        try:
            evidence = operation() or {}
            result = {"caseId": identity, "status": "PASS", "seconds": round(time.monotonic()-started, 3), "evidence": evidence}
        except Exception as error:
            # Assertion messages are authored safe text; unexpected exceptions never expose bodies.
            reason = str(error) if isinstance(error, CheckFailure) else type(error).__name__
            result = {"caseId": identity, "status": "FAIL", "seconds": round(time.monotonic()-started, 3), "reason": reason}
        self.results.append(result)
        print(identity + " " + result["status"], flush=True)

    def preflight(self):
        check(self.args.base in BASES, "non-isolated base refused")
        check(self.args.runtime_env.resolve() == Path("/home/juan/terra-audit-20260922/runtime.env"), "unexpected runtime env path")
        check(self.args.runtime_env.stat().st_mode & 0o077 == 0, "runtime credentials must be private")
        labels = json.loads(command(DOCKER + ["inspect", MYSQL, "--format", "{{json .Config.Labels}}"]));
        check(labels.get("com.docker.compose.project") == PROJECT, "database is not the audit compose project")
        check(sql("SELECT DATABASE();") == DATABASE, "database guard mismatch")
        check(scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='28' AND success=1;") == 1, "required identity migration missing")
        values = {}
        for line in self.args.runtime_env.read_text().splitlines():
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1); values[key] = value.strip().strip('"').strip("'")
        self.admin = Session(self.args.base)
        self.admin.login("audit_admin", values["AUDIT_ADMIN_PASSWORD"], role="ADMIN")
        anonymous = Session(self.args.base)
        status, body, _ = anonymous.request("GET", "/api/auth/me")
        check(status == 401 and body.get("code") == "AUTHENTICATION_REQUIRED", "anonymous authentication contract")
        self.state["admin"] = True
        return {"anonymousMe": 401, "schemaV28": True, "database": DATABASE}

    def same_name(self):
        username = self.prefix + "a"
        first = signup(self.args.base, username, username + "1@example.test", self.password)
        old_id = first.user_id
        payload = {"name": "Audit Identity Dish", "latitude": 30.5, "longitude": 104.0,
                   "summary": "Isolated audit fixture", "story": "Isolated audit fixture", "ingredients": "rice", "tagIds": []}
        status, food, _ = first.request("POST", "/api/foods", payload)
        check(status == 201, "food fixture creation status " + str(status))
        status, _, _ = self.admin.request("DELETE", "/api/admin/users/" + str(old_id))
        check(status == 204, "account deletion status " + str(status))
        replacement = signup(self.args.base, username, username + "2@example.test", self.password)
        check(replacement.user_id != old_id, "reused username inherited stable ID")
        denied = []
        for path in ("/api/auth/me", "/api/profile/check-ins", "/api/profile/favorites/page"):
            status, _, _ = first.request("GET", path)
            denied.append(status)
            check(status == 401, "old cookie authorized private endpoint")
        status, owned, _ = replacement.request("GET", "/api/foods/mine/page")
        check(status == 200 and all(item["id"] != food["id"] for item in owned["items"]), "same-name replacement inherited dish list")
        status, _, _ = replacement.request("PATCH", "/api/profile/foods/" + str(food["id"]), payload)
        check(status == 404, "same-name replacement may edit predecessor dish")
        check(scalar("SELECT COUNT(*) FROM food WHERE id=" + str(food["id"]) + " AND created_by_user_id IS NULL;") == 1, "deleted owner relationship not isolated")
        self.state.update(replacement=replacement, username=username, email=username+"2@example.test", food_id=food["id"])
        return {"oldCookieStatuses": denied, "replacementEdit": 404, "ownerNullRows": 1}

    def reset_concurrency(self):
        check("replacement" in self.state, "AUTH-01 prerequisite failed")
        one = self.state["replacement"]
        two = Session(self.args.base); two.login(self.state["username"], self.password)
        reset = Session(self.args.base); reset.token()
        before = scalar("SELECT auth_version FROM app_user WHERE id=" + str(one.user_id) + ";")
        previous = mailbox_ids()
        status, _, _ = reset.request("POST", "/api/auth/password-reset-code", {"username": self.state["username"], "email": self.state["email"]})
        check(status == 204, "reset mail request status " + str(status))
        code = mail_code(self.state["email"], previous)
        clients = [Session(self.args.base), Session(self.args.base)]
        for client in clients: client.token()
        passwords = ["NewAudit" + secrets.token_hex(3) + "1", "NewAudit" + secrets.token_hex(3) + "2"]
        barrier = threading.Barrier(2)
        def attempt(index):
            barrier.wait(timeout=5)
            return clients[index].request("POST", "/api/auth/password-reset", {"username": self.state["username"], "email": self.state["email"], "verificationCode": code, "newPassword": passwords[index]})[0]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as workers:
            outcomes = list(workers.map(attempt, (0, 1)))
        check(sorted(outcomes) == [204, 400], "same reset code did not have exactly one successful redemption")
        after = scalar("SELECT auth_version FROM app_user WHERE id=" + str(one.user_id) + ";")
        check(after == before + 1, "reset authentication version changed more than once")
        for old in (one, two):
            check(old.request("GET", "/api/auth/me")[0] == 401, "reset preserved an old session")
        Session(self.args.base).login(self.state["username"], self.password, expected_status=401)
        winner = outcomes.index(204)
        renewed = Session(self.args.base); renewed.login(self.state["username"], passwords[winner])
        self.state.update(replacement=renewed, new_password=passwords[winner])
        return {"concurrentResetStatuses": outcomes, "authVersionDelta": after-before, "oldSessionsRejected": 2, "newPasswordLogin": 200}

    def disable_recover(self):
        check("new_password" in self.state, "reset prerequisite failed")
        current = self.state["replacement"]
        held = current.clone()
        before = scalar("SELECT auth_version FROM app_user WHERE id=" + str(current.user_id) + ";")
        check(self.admin.request("PATCH", "/api/admin/users/"+str(current.user_id)+"/active", {"active": False})[0] == 200, "disable failed")
        # Do not touch the held Cookie during the inactive period.
        check(self.admin.request("PATCH", "/api/admin/users/"+str(current.user_id)+"/active", {"active": True})[0] == 200, "restore failed")
        check(held.request("GET", "/api/auth/me")[0] == 401, "restore resurrected pre-disable session")
        renewed = Session(self.args.base); renewed.login(self.state["username"], self.state["new_password"])
        after = scalar("SELECT auth_version FROM app_user WHERE id=" + str(current.user_id) + ";")
        check(after == before + 2, "disable/restore did not advance both authentication versions")
        self.state["replacement"] = renewed
        return {"heldOldSession": 401, "authVersionDelta": 2, "freshLogin": 200}

    def expected_identity(self):
        check("replacement" in self.state, "AUTH prerequisite failed")
        client = self.state["replacement"]
        before = scalar("SELECT COUNT(*) FROM user_review_item WHERE user_id="+str(client.user_id)+";")
        status, body, _ = client.request("PATCH", "/api/profile/signature", {"signature": "Private audit fixture"}, expected=client.user_id + 99999)
        check(status == 409 and body.get("code") == "IDENTITY_CHANGED", "stale identity header was not rejected")
        check(scalar("SELECT COUNT(*) FROM user_review_item WHERE user_id="+str(client.user_id)+";") == before, "rejected identity write altered database")
        check(client.request("GET", "/api/auth/me")[0] == 200, "identity mismatch invalidated current account")
        return {"status": 409, "reviewRowsDelta": 0, "currentSessionIntact": True}

    def upload_quota(self):
        upload_user = signup(self.args.base, self.prefix+"u", self.prefix+"u@example.test", self.password)
        self.state["upload_user"] = upload_user
        check(scalar("SELECT COUNT(*) FROM image_asset WHERE owner_user_id="+str(upload_user.user_id)+";") == 0, "upload fixture already has assets")
        before_files = originals()
        check(upload_user.upload(b"not an image")[0] == 400, "invalid file was not rejected")
        check(originals() == before_files, "invalid upload left a file")
        check(scalar("SELECT COUNT(*) FROM image_asset WHERE owner_user_id="+str(upload_user.user_id)+";") == 0, "invalid upload changed metadata")
        status, image, _ = upload_user.upload(png())
        check(status == 201, "first image upload status " + str(status))
        check(scalar("SELECT COUNT(*) FROM image_asset WHERE id="+str(image["resourceId"])+" AND owner_user_id="+str(upload_user.user_id)+" AND byte_size="+str(len(png()))+";") == 1, "upload ownership/byte size missing")
        clones = [upload_user.clone() for _ in range(3)]
        for clone in clones: clone.token()
        barrier = threading.Barrier(3)
        def attempt(index):
            barrier.wait(timeout=5)
            return clones[index].upload(png())[0]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as workers:
            outcomes = list(workers.map(attempt, range(3)))
        check(sorted(outcomes) == [201, 201, 429], "daily quota 3 was not enforced atomically")
        check(scalar("SELECT COUNT(*) FROM image_asset WHERE owner_user_id="+str(upload_user.user_id)+";") == 3, "quota metadata count mismatch")
        check(len(originals()-before_files) == 3, "quota rejection left an orphan file")
        return {"invalidStatus": 400, "parallelStatuses": outcomes, "databaseAssetCount": 3, "newOriginalFiles": 3}

    def upload_rollback(self):
        check("replacement" in self.state, "AUTH prerequisite failed")
        client = self.state["replacement"]
        before_count = scalar("SELECT COUNT(*) FROM image_asset;")
        before_files = originals()
        trigger = "audit_reject_image_" + self.prefix
        try:
            sql("CREATE TRIGGER "+trigger+" BEFORE INSERT ON image_asset FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit_fault_injection';", fixture_admin=True)
            status, _, _ = client.upload(png())
            check(status == 500, "injected database failure response expected 500 got " + str(status))
        finally:
            sql("DROP TRIGGER IF EXISTS "+trigger+";", fixture_admin=True)
        check(scalar("SELECT COUNT(*) FROM image_asset;") == before_count, "database failure left image metadata")
        check(originals() == before_files, "database failure left original/temp file")
        check(client.upload(png())[0] == 201, "upload did not recover after fixture trigger removal")
        return {"faultStatus": 500, "metadataDeltaDuringFailure": 0, "fileDeltaDuringFailure": 0, "recovery": 201, "fixture": "transient isolated SQL trigger"}

    def captcha_budget(self):
        client = Session(self.args.base); client.token()
        issued = 0; rejection = None
        for unused in range(25):
            status, body, headers = client.request("GET", "/api/auth/captcha")
            if status == 429:
                rejection = headers.get("Retry-After") or headers.get("retry-after")
                break
            check(status == 200, "captcha issuance returned unexpected status "+str(status))
            issued += 1
        check(rejection is not None and rejection.isdigit() and int(rejection)>0, "captcha budget did not return usable Retry-After")
        check(issued <= 10, "session captcha quota exceeded")
        return {"issuedBeforeRejection": issued, "rejectedStatus": 429, "retryAfterPresent": True}

    def run(self):
        self.case("PREFLIGHT-01", self.preflight)
        if self.state.get("admin"):
            for identity, operation in (("AUTH-01", self.same_name), ("AUTH-02-RESET", self.reset_concurrency),
                    ("AUTH-02-RECOVER", self.disable_recover), ("AUTH-03", self.expected_identity),
                    ("UPLOAD-QUOTA", self.upload_quota)):
                if not self.args.upload_rollback_only or identity == "AUTH-01":
                    self.case(identity, operation)
            if self.args.fault_injection:
                self.case("UPLOAD-ROLLBACK", self.upload_rollback)
            if not self.args.upload_rollback_only:
                self.case("CAPTCHA-BUDGET", self.captcha_budget)
        expected = 3 if self.args.upload_rollback_only else (8 if self.args.fault_injection else 7)
        passed = sum(item["status"]=="PASS" for item in self.results)
        report = {"runId": self.prefix, "scope": "isolated synthetic HTTP/MySQL/Redis Session/SMTP", "base": self.args.base,
                  "project": PROJECT, "passed": passed, "failed": sum(item["status"]=="FAIL" for item in self.results),
                  "requiredCaseCount": expected, "executedCaseCount": len(self.results), "cases": self.results,
                  "limitations": ["In-flight filter-to-service interleaving is covered separately; AUTH-01 tests old Cookies across account replacement.",
                                  "Captcha arithmetic is intentionally solved in the isolated test; this is not evidence of bot resistance.",
                                  "No real deployment or production data was tested."]}
        self.args.output.parent.mkdir(parents=True, exist_ok=True)
        self.args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n")
        self.args.output.chmod(0o600)
        print("HTTP audit: "+str(passed)+"/"+str(expected)+" passed; report saved", flush=True)
        return 0 if passed == expected and len(self.results)==expected else 1


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", choices=sorted(BASES), default="http://127.0.0.1:18380")
    parser.add_argument("--runtime-env", type=Path, default=Path("/home/juan/terra-audit-20260922/runtime.env"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fault-injection", action="store_true", help="Enable a transient failing INSERT trigger in the guarded disposable database")
    parser.add_argument("--upload-rollback-only", action="store_true", help="Run only preflight, account setup and upload fault regression")
    args=parser.parse_args()
    if args.upload_rollback_only: args.fault_injection=True
    return Suite(args).run()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print("HTTP audit runner failed: "+type(error).__name__, file=sys.stderr)
        sys.exit(2)
