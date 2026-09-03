import os
import sys
import json
import base64
import requests

token = os.environ.get("GITHUB_TOKEN")
if not token:
    print("Error: GITHUB_TOKEN not found in environment")
    sys.exit(1)

headers = {
    "Authorization": f"token {token}",
    "Accept": "application/vnd.github.v3+json"
}

repo = "Andresaguiar22/panalink-ota"
tag = "v1.3.21"
apk_path = "Panalink-v1.3.21.apk"
manifest_path = "manifest.json"

sha256_hash = "f8a311075a282019496d9ad0333297cdbd12857d19f7823c486ccab1a4d2924c"

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

resp = requests.post(release_url, headers=headers, json=release_data)
if resp.status_code not in [200, 201]:
    print(f"Failed to create release: {resp.status_code} - {resp.text}")
    sys.exit(1)

release_info = resp.json()
release_id = release_info["id"]
upload_url_template = release_info["upload_url"]
base_upload_url = upload_url_template.split("{")[0]
print(f"Release created successfully with ID: {release_id}")

print("2. Uploading assets (APK and manifest.json)...")
# Upload APK
apk_upload_url = f"{base_upload_url}?name=Panalink-v1.3.21.apk"
apk_headers = headers.copy()
apk_headers["Content-Type"] = "application/vnd.android.package-archive"

with open(apk_path, "rb") as f:
    apk_data = f.read()

print(f"Uploading APK ({len(apk_data)} bytes)...")
resp_apk = requests.post(apk_upload_url, headers=apk_headers, data=apk_data)
if resp_apk.status_code not in [200, 201]:
    print(f"Failed to upload APK: {resp_apk.status_code} - {resp_apk.text}")
    sys.exit(1)
print("APK uploaded successfully.")

# Upload manifest.json as asset
manifest_upload_url = f"{base_upload_url}?name=manifest.json"
manifest_headers = headers.copy()
manifest_headers["Content-Type"] = "application/json"

with open(manifest_path, "rb") as f:
    manifest_data = f.read()

print("Uploading manifest.json asset...")
resp_manifest_asset = requests.post(manifest_upload_url, headers=manifest_headers, data=manifest_data)
if resp_manifest_asset.status_code not in [200, 201]:
    print(f"Failed to upload manifest.json asset: {resp_manifest_asset.status_code} - {resp_manifest_asset.text}")
    sys.exit(1)
print("manifest.json asset uploaded successfully.")

print("3. Updating manifest.json in repository main branch...")
content_url = f"https://api.github.com/repos/{repo}/contents/manifest.json"
resp_get = requests.get(content_url, headers=headers)
if resp_get.status_code != 200:
    print(f"Failed to get manifest.json sha: {resp_get.status_code} - {resp_get.text}")
    sys.exit(1)

current_sha = resp_get.json()["sha"]

with open(manifest_path, "r") as f:
    manifest_str = f.read()

encoded_manifest = base64.b64encode(manifest_str.encode("utf-8")).decode("utf-8")

update_data = {
    "message": "chore(ota): update manifest.json for v1.3.21 production signed release",
    "content": encoded_manifest,
    "sha": current_sha,
    "branch": "main"
}

resp_put = requests.put(content_url, headers=headers, json=update_data)
if resp_put.status_code not in [200, 201]:
    print(f"Failed to update manifest.json in repo: {resp_put.status_code} - {resp_put.text}")
    sys.exit(1)
print("manifest.json updated in main branch successfully.")

print("4. Publishing release (draft = false)...")
patch_url = f"https://api.github.com/repos/{repo}/releases/{release_id}"
patch_data = {
    "draft": False
}

resp_patch = requests.patch(patch_url, headers=headers, json=patch_data)
if resp_patch.status_code != 200:
    print(f"Failed to publish release: {resp_patch.status_code} - {resp_patch.text}")
    sys.exit(1)

print("OTA Release v1.3.21 published successfully and live!")
