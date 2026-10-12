#!/usr/bin/env python3
"""Read-only by default. Applying reviewed ownership never assigns another user."""
import argparse, hashlib, json, pathlib, subprocess, sys


def sql_text(value):
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--defaults-extra-file", required=True, help="Private MySQL option file; password is never a command argument")
    parser.add_argument("--database", required=True)
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--apply-reviewed-manifest", type=pathlib.Path)
    parser.add_argument("--reviewer")
    args = parser.parse_args()
    def query(sql):
        run = subprocess.run([args.mysql, "--defaults-extra-file=" + args.defaults_extra_file,
                              "--batch", "--skip-column-names", "--database=" + args.database],
                             input=sql, text=True, capture_output=True, timeout=30)
        if run.returncode:
            # Do not reproduce SQL/credentials in diagnostic output.
            raise RuntimeError("MySQL operation failed; inspect the database operator log")
        return run.stdout.strip()
    if not args.apply_reviewed_manifest:
        identity_schema = query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='food' AND column_name='created_by_user_id';")
        if identity_schema == "0":
            result = query("SELECT JSON_OBJECT('foodId',f.id,'candidateCurrentUserId',u.id,'status','PRE_V22_UNPROVEN_USERNAME_MATCH','foodCreatedAt',f.created_at,'accountCreatedAt',u.created_at) FROM food f LEFT JOIN app_user u ON u.username=f.created_by ORDER BY f.id;")
            print(json.dumps({"mode":"read-only", "warning":"Matching a current username is not ownership evidence", "rows":[json.loads(line) for line in result.splitlines() if line]}, ensure_ascii=False, indent=2))
            return
        schema = query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='food' AND column_name='ownership_status';")
        if schema == "0":
            result = query("SELECT JSON_OBJECT('foodId',f.id,'linkedUserId',f.created_by_user_id,'status','PRE_V28_REVIEW_REQUIRED','foodCreatedAt',f.created_at,'accountCreatedAt',u.created_at) FROM food f LEFT JOIN app_user u ON u.id=f.created_by_user_id WHERE f.created_by_user_id IS NOT NULL ORDER BY f.id;")
        else:
            result = query("SELECT JSON_OBJECT('foodId',f.id,'linkedUserId',f.created_by_user_id,'status',f.ownership_status,'foodCreatedAt',f.created_at,'accountCreatedAt',u.created_at) FROM food f LEFT JOIN app_user u ON u.id=f.created_by_user_id WHERE f.ownership_status <> 'VERIFIED' ORDER BY f.id;")
        print(json.dumps({"mode":"read-only", "rows":[json.loads(line) for line in result.splitlines() if line]}, ensure_ascii=False, indent=2))
        return
    if not args.reviewer or not args.reviewer.strip() or len(args.reviewer)>120:
        raise ValueError("A reviewer (1-120 characters) is required")
    manifest = json.loads(args.apply_reviewed_manifest.read_text(encoding="utf-8"))
    if not isinstance(manifest,list) or not manifest or len(manifest)>100:
        raise ValueError("Manifest must contain 1-100 reviewed records")
    completed=[]
    for row in manifest:
        food_id=int(row["foodId"]);user_id=int(row["expectedUserId"])
        if food_id<1 or user_id<1: raise ValueError("Positive IDs required")
        action=row["action"]
        if action not in ("verify", "quarantine"): raise ValueError("Only verify/quarantine actions are supported")
        expected=row.get("expectedStatus", "LEGACY_UNVERIFIED")
        if expected not in ("VERIFIED", "LEGACY_UNVERIFIED"): raise ValueError("Invalid expected status")
        target="VERIFIED" if action=="verify" else "LEGACY_UNVERIFIED"
        evidence=pathlib.Path(row["evidenceFile"]).resolve()
        if not evidence.is_file() or evidence.stat().st_size==0: raise ValueError("Nonempty independent evidence file required")
        digest=hashlib.sha256(evidence.read_bytes()).hexdigest()
        reviewer=sql_text(args.reviewer)
        # Lock and compare the exact existing link. No username matching and no reassignment.
        sql=f"""START TRANSACTION;
SET @matched=0;
SELECT COUNT(*) INTO @matched FROM food WHERE id={food_id} AND created_by_user_id={user_id} AND ownership_status='{expected}' FOR UPDATE;
INSERT INTO food_ownership_review(food_id,previous_user_id,retained_user_id,previous_status,resulting_status,evidence_sha256,reviewer)
SELECT {food_id},{user_id},{user_id},'{expected}','{target}','{digest}',{reviewer} WHERE @matched=1;
UPDATE food SET ownership_status='{target}' WHERE id={food_id} AND created_by_user_id={user_id} AND ownership_status='{expected}' AND @matched=1;
SELECT @matched;
COMMIT;"""
        result=query(sql)
        if result != "1": raise RuntimeError("Ownership changed or row missing; refresh the precheck before retrying")
        completed.append({"foodId":food_id,"status":target,"evidenceSha256":digest})
    print(json.dumps({"mode":"reviewed-apply", "completed":completed}, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    try: main()
    except Exception as error:
        print("Ownership review failed: " + str(error), file=sys.stderr)
        sys.exit(1)
