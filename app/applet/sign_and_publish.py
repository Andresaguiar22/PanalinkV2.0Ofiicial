import os
import sys
import subprocess
import base64
import json
import urllib.request
import urllib.error

# 1. Decode keystore from environment or secrets
keystore_b64 = os.environ.get("KEYSTORE_FILE")
store_pass = os.environ.get("KEYSTORE_PASSWORD", "22386582")
key_alias = os.environ.get("KEY_ALIAS", "panalink-key")
key_pass = os.environ.get("KEY_PASSWORD", "22386582")

if not keystore_b64:
    print("Error: KEYSTORE_FILE environment variable not found")
    sys.exit(1)

# Write keystore file
keystore_path = "release-key.jks"
try:
    decoded_bytes = base64.b64decode(keystore_b64.strip())
    with open(keystore_path, "wb") as f:
        f.write(decoded_bytes)
    print("Keystore decoded and written to release-key.jks successfully.")
except Exception as e:
    print(f"Failed to decode keystore: {e}")
    sys.exit(1)

apk_input = "app/build/outputs/apk/release/app-release.apk"
if not os.path.exists(apk_input):
    print(f"Error: Release APK not found at {apk_input}")
    sys.exit(1)

# 2. Find apksigner and zipalign from Android SDK build-tools
build_tools_dir = "/opt/android/sdk/build-tools"
versions = os.listdir(build_tools_dir) if os.path.exists(build_tools_dir) else []
versions.sort(reverse=True)

apksigner_path = None
zipalign_path = None

for v in versions:
    as_path = os.path.join(build_tools_dir, v, "apksigner")
    za_path = os.path.join(build_tools_dir, v, "zipalign")
    if os.path.exists(as_path):
        apksigner_path = as_path
    if os.path.exists(za_path):
        zipalign_path = za_path
    if apksigner_path and zipalign_path:
        break

if not apksigner_path:
    print("Error: apksigner not found in build-tools")
    sys.exit(1)

print(f"Using apksigner at: {apksigner_path}")
print(f"Using zipalign at: {zipalign_path}")

aligned_apk = "app/build/outputs/apk/release/app-release-aligned.apk"

# Zipalign
if zipalign_path:
    print("Running zipalign...")
    subprocess.run([zipalign_path, "-v", "-f", "4", apk_input, aligned_apk], check=True)
    target_apk = aligned_apk
else:
    target_apk = apk_input

# 3. Sign APK with both V1 (JAR) and V2 (APK Signature Scheme v2) enabled explicitly
print("Signing APK with apksigner (V1 + V2 enabled)...")
sign_cmd = [
    apksigner_path, "sign",
    "--ks", keystore_path,
    "--ks-pass", f"pass:{store_pass}",
    "--ks-key-alias", key_alias,
    "--key-pass", f"pass:{key_pass}",
    "--v1-signing-enabled", "true",
    "--v2-signing-enabled", "true",
    target_apk
]
subprocess.run(sign_cmd, check=True)

# Verify signature
print("Verifying signature...")
verify_cmd = [apksigner_path, "verify", "--verbose", target_apk]
subprocess.run(verify_cmd, check=True)

# Copy signed apk to final name
final_apk_name = "Panalink-v1.3.21.apk"
subprocess.run(["cp", target_apk, final_apk_name], check=True)

# Compute SHA256
import hashlib
sha256_hash = hashlib.sha256(open(final_apk_name, "rb").read()).hexdigest()
print(f"Final APK SHA256: {sha256_hash}")

# 4. Publish to OTA GitHub repo
token = os.environ.get("GITHUB_TOKEN")
if not token:
    print("Warning: GITHUB_TOKEN not found, skipping automated OTA publishing.")
    sys.exit(0)

repo = "Andresaguiar22/panalink-ota"
tag = "v1.3.21"
manifest_path = "manifest.json"

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
        err_msg = e.read().decode("utf-8")
        print("HTTPError", e.code, "for", url, ":", err_msg)
        sys.exit(1)

print("1. Creating GitHub release (draft) for v1.3.21...")
release_url = f"https://api.github.com/repos/{repo}/releases"
release_data = {
    "tag_name": tag,
    "target_commitish": "main",
    "name": "Panalink v1.3.21 (Production Signed - V1+V2)",
    "body": f"## Panalink v1.3.21 (Production Signed)\n- Firmware firmado con V1 (JAR) y V2 habilitados para compatibilidad total de instalación en todos los dispositivos Android.\n- Optimizaciones de estabilidad offline y sincronización multimedia.\n\n**SHA-256:** `{sha256_hash}`\n\n_Release creado por un agente de IA en nombre del mantenedor._",
    "draft": True,
    "prerelease": False
}
status, release_info = api_request(release_url, method="POST", data=release_data)
release_id = release_info["id"]
upload_url_template = release_info["upload_url"]
base_upload_url = upload_url_template.split("{")[0]
print(f"Release created: {release_id}")

print("2. Uploading assets...")
apk_upload_url = f"{base_upload_url}?name=Panalink-v1.3.21.apk"
with open(final_apk_name, "rb") as f:
    apk_data = f.read()
api_request(apk_upload_url, method="POST", data=apk_data, content_type="application/vnd.android.package-archive")
print("APK uploaded.")

manifest_upload_url = f"{base_upload_url}?name=manifest.json"
with open(manifest_path, "rb") as f:
    manifest_data = f.read()
api_request(manifest_upload_url, method="POST", data=manifest_data, content_type="application/json")
print("manifest.json asset uploaded.")

print("3. Updating manifest.json in repository main branch...")
content_url = f"https://api.github.com/repos/{repo}/contents/manifest.json"
status, content_info = api_request(content_url, method="GET")
current_sha = content_info["sha"]

with open(manifest_path, "r") as f:
    manifest_str = f.read()
encoded_manifest = base64.b64encode(manifest_str.encode("utf-8")).decode("utf-8")
update_data = {
    "message": "chore(ota): update manifest.json for v1.3.21 production signed release (V1+V2)",
    "content": encoded_manifest,
    "sha": current_sha,
    "branch": "main"
}
api_request(content_url, method="PUT", data=update_data)
print("manifest.json updated in main.")

print("4. Publishing release...")
patch_url = f"https://api.github.com/repos/{repo}/releases/{release_id}"
api_request(patch_url, method="PATCH", data={"draft": False})
print("OTA Release v1.3.21 published successfully and live!")
