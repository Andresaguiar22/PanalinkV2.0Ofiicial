import os
import sys
import json
import base64
import urllib.request
import urllib.error

token = os.environ.get("GITHUB_TOKEN")
if not token:
    print("Error: GITHUB_TOKEN not found in environment")
    sys.exit(1)

repo = "Andresaguiar22/panalink-ota"
tag = "v1.3.21"
apk_path = "Panalink-v1.3.21.apk"
manifest_path = "manifest.json"
sha256_hash = "f8a311075a282019496d9ad0333297cdbd12857d19f7823c486ccab1a4d2924c"

headers = {
    "Authorization": f"token {token}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "Panalink-OTA-Publisher"
}

def api_request(url, method="GET", data=None, content_type="application/json"):
    req_headers = headers.copy()
    req_data = None
    if data is not None:
        if isinstance(data, dict):
            req_data = json.dumps(data).encode("utf-8")
            req_headers["Content-Type"] = content_type
        else:
            req_data = data
            req_headers["Content-Type"] = content_type

    req = urllib.request.Request(url, data=req_data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode("utf-8")
            return response.status, json.loads(res_body) if res_body else {}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        print(f"HTTPError {e.code} for {url}: {err_body}")
        sys.exit(1)

print("1. Creating GitHub release (draft)...")
release_url = f"https://api.github.com/repos/{repo}/releases"
release_data = {
    "tag_name": tag,
    "target_commitish": "main",
    "name": "Panalink v1.3.21 (Production Signed)",
    "body": f"## Panalink v1.3.21 (Production Signed)\n- Panalink V2.0 Oficial - Release de Producción firmado digitalmente\n- Optimizaciones avanzadas de estabilidad offline y gestión de colas multimedia\n- Correcciones de sincronización en tiempo real y robustez en canal de actualizaciones\n\n**SHA-256:** `{sha256_hash}`\n\n_Release creado por un agente de IA en nombre del mantenedor._",
    "draft": True,
    "prerelease": False
}

status, release_info = api_request(release_url, method="POST", data=release_data)
release_id = release_info["id"]
upload_url_template = release_info["upload_url"]
base_upload_url = upload_url_template.split("{")[0]
print(f"Release created successfully with ID: {release_id}")

print("2. Uploading assets (APK and manifest.json)...")
# Upload APK
apk_upload_url = f"{base_upload_url}?name=Panalink-v1.3.21.apk"
with open(apk_path, "rb") as f:
    apk_data = f.read()

print(f"Uploading APK ({len(apk_data)} bytes)...")
status, _ = api_request(apk_upload_url, method="POST", data=apk_data, content_type="application/vnd.android.package-archive")
print("APK uploaded successfully.")

# Upload manifest.json as asset
manifest_upload_url = f"{base_upload_url}?name=manifest.json"
with open(manifest_path, "rb") as f:
    manifest_data = f.read()

print("Uploading manifest.json asset...")
status, _ = api_request(manifest_upload_url, method="POST", data=manifest_data, content_type="application/json")
print("manifest.json asset uploaded successfully.")

print("3. Updating manifest.json in repository main branch...")
content_url = f"https://api.github.com/repos/{repo}/contents/manifest.json"
status, content_info = api_request(content_url, method="GET")
current_sha = content_info["sha"]

with open(manifest_path, "r") as f:
    manifest_str = f.read()

encoded_manifest = base64.b64encode(manifest_str.encode("utf-8")).decode("utf-8")

update_data = {
    "message": "chore(ota): update manifest.json for v1.3.21 production signed release",
    "content": encoded_manifest,
    "sha": current_sha,
    "branch": "main"
}

status, _ = api_request(content_url, method="PUT", data=update_data)
print("manifest.json updated in main branch successfully.")

print("4. Publishing release (draft = false)...")
patch_url = f"https://api.github.com/repos/{repo}/releases/{release_id}"
patch_data = {
    "draft": False
}

status, _ = api_request(patch_url, method="PATCH", data=patch_data)
print("OTA Release v1.3.21 published successfully and live!")
