#!/usr/bin/env python3
"""Publish the just-built APK as an OTA release to Andresaguiar22/panalink-ota."""

import base64
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
DEFAULT_OTA_REPO = "Andresaguiar22/panalink-ota"
APK_RELPATH = "app/build/outputs/apk/release/app-release.apk"
MANIFEST_RELPATH = "manifest.json"


def fail(msg):
    print(f"::error::publish_ota: {msg}", file=sys.stderr)
    sys.exit(1)


def api(url, token, method="GET", payload=None, headers=None):
    req_headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "openhands",
        "Content-Type": "application/json",
    }
    if headers:
        req_headers.update(headers)
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return (r.status, json.loads(raw)) if raw else (r.status, None)
    except urllib.error.HTTPError as e:
        return (e.code, e.reason)


def upload_binary(url, token, data, content_type):
    req_headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "openhands",
        "Content-Type": content_type,
    }
    req = urllib.request.Request(url, data=data, headers=req_headers, method="POST")
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return (r.status, json.loads(raw)) if raw else (r.status, None)
    except urllib.error.HTTPError as e:
        return (e.code, e.body.read() if hasattr(e, "body") else e.reason)


def main():
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        fail("GITHUB_TOKEN env var is required")
    ota_repo = os.environ.get("OTA_REPO", DEFAULT_OTA_REPO)
    if not os.path.isfile(APK_RELPATH):
        fail(f"APK not found: {APK_RELPATH} - run assembleRelease first")
    if not os.path.isfile(MANIFEST_RELPATH):
        fail(f"{MANIFEST_RELPATH} not found in repo root")

    with open(APK_RELPATH, "rb") as f:
        apk_bytes = f.read()
    apk_sha256 = hashlib.sha256(apk_bytes).hexdigest()

    manifest = json.load(open(MANIFEST_RELPATH, encoding="utf-8"))
    version_code = int(manifest["versionCode"])
    raw_version_name = str(manifest["versionName"])
    version_name = raw_version_name.lstrip("v")
    tag = os.environ.get("OTA_TAG") or f"v{version_name}"
    apk_name = f"Panalink-v{version_name}.apk"
    download_url = f"https://github.com/{ota_repo}/releases/download/{tag}/{apk_name}"

    manifest["sha256"] = apk_sha256
    manifest["downloadUrl"] = download_url
    manifest["apkUrl"] = download_url
    manifest["versionCode"] = version_code
    manifest["versionName"] = f"v{version_name}"
    manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    changelog = manifest.get("changelog") or []

    # 1. Find existing release with same tag (idempotent re-run)
    status, releases = api(f"{API}/repos/{ota_repo}/releases", token)
    if status != 200:
        fail(f"cannot list releases: {status} {releases}")
    existing = next((r for r in releases if r["tag_name"] == tag), None) if releases else None
    if existing:
        release_id = existing["id"]
        print(f"found existing release {tag} (id {release_id}); re-uploading assets")
        for asset in existing.get("assets", []):
            api(f"{API}/repos/{ota_repo}/releases/assets/{asset['id']}", token, method="DELETE")
        body = existing["body"]
    else:
        body = "\n".join(f"- {c}" for c in changelog)
        release_payload = {
            "tag_name": tag,
            "target_commitish": "main",
            "name": f"Panalink v{version_name} (Production Signed)",
            "body": body,
            "draft": True,
            "prerelease": False,
        }
        status, release_info = api(f"{API}/repos/{ota_repo}/releases", token, method="POST", payload=release_payload)
        if status not in (200, 201):
            fail(f"cannot create release: {status} {release_info}")
        release_id = release_info["id"]
        print(f"created draft release {tag} (id {release_id})")

    # 2. Upload APK (replaces stale asset so sha256 matches the served bytes)
    apk_url = f"{UPLOADS}/repos/{ota_repo}/releases/{release_id}/assets?name=" + urllib.parse.quote(apk_name)
    status, upload_info = upload_binary(apk_url, token, apk_bytes, "application/vnd.android.package-archive")
    if status not in (200, 201):
        fail(f"APK upload failed: {status} {upload_info}")

    # 3. Update main manifest (source of truth for the app)
    status, blob_info = api(f"{API}/repos/{ota_repo}/contents/{MANIFEST_RELPATH}", token)
    if status != 200:
        fail(f"cannot read main manifest: {status} {blob_info}")
    put_payload = {
        "message": f"chore(ota): publish {tag} (versionCode {version_code}, sha256 {apk_sha256[:12]})",
        "content": base64.b64encode(manifest_bytes).decode(),
        "sha": blob_info["sha"],
        "branch": "main",
    }
    status, _ = api(f"{API}/repos/{ota_repo}/contents/{MANIFEST_RELPATH}", token, method="PUT", payload=put_payload)
    if status not in (200, 201):
        fail(f"manifest.json update on main failed: {status}")

    # 4. Attach manifest as release asset;
    man_url = f"{UPLOADS}/repos/{ota_repo}/releases/{release_id}/assets?name=manifest.json"
    status, _ = upload_binary(man_url, token, manifest_bytes, "application/json")
    if status not in (200, 201):
        fail(f"manifest asset upload failed: {status}")

    # 5. Finalize (only on explicit env OTA_PUBLISH so CI can build without auto-releasing)
    if os.environ.get("OTA_PUBLISH") in ("1", "true", "yes", "on"):
        status, _ = api(f"{API}/repos/{ota_repo}/releases/{release_id}", token, method="PATCH", payload={"draft": False})
        if status != 200:
            fail(f"cannot publish draft release: {status}")
        print(f"OTA release {tag} is now LIVE.")
    else:
        print(f"OTA release {tag} left as DRAFT. Set OTA_PUBLISH=1 to go live.")


if __name__ == "__main__":
    main()
