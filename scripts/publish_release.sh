#!/usr/bin/env bash
set -euo pipefail

# Listen Quran release publisher:
# Builds the signed release APK (unless --skip-build or --json-only),
# generates update.json (consumed directly from GitHub Releases CDN without API limits),
# and creates/updates the GitHub Release attaching both the APK and update.json.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REPO="tokitauhid/listen-quran"
NOTES_FILE=""
SKIP_BUILD=false
JSON_ONLY=false
LATEST_FLAG="--latest"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --notes-file)
            NOTES_FILE="$2"
            shift 2
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --json-only)
            JSON_ONLY=true
            shift
            ;;
        --no-latest)
            LATEST_FLAG=""
            shift
            ;;
        -h|--help)
            cat <<EOF
Usage: ./scripts/publish_release.sh [--notes-file <path>] [--skip-build] [--json-only] [--no-latest]

Reads versionName & versionCode from app/build.gradle.kts, builds the release APK,
generates update.json with exact SHA-256 + byte size, and uploads both
ListenQuran-v<versionName>.apk and update.json to GitHub Releases.
EOF
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

VERSION_NAME="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts | head -n1)"
VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' app/build.gradle.kts | head -n1)"

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
    echo "Failed to parse versionName/versionCode from app/build.gradle.kts" >&2
    exit 1
fi

TAG="v${VERSION_NAME}"
ASSET_NAME="ListenQuran-${TAG}.apk"
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT

APK_STAGE="$STAGE_DIR/$ASSET_NAME"
MANIFEST_STAGE="$STAGE_DIR/update.json"

if [[ "$JSON_ONLY" == true ]]; then
    echo "Generating update.json from existing GitHub release $TAG..."
    RELEASE_JSON="$(gh api "repos/${REPO}/releases/tags/${TAG}")"
    export RELEASE_JSON VERSION_NAME VERSION_CODE TAG ASSET_NAME REPO MANIFEST_STAGE
    python3 - << 'PY'
import json, os

rel = json.loads(os.environ["RELEASE_JSON"])
asset_name = os.environ["ASSET_NAME"]
assets = rel.get("assets", [])
match = next((a for a in assets if a.get("name") == asset_name), None)
if not match:
    match = next((a for a in assets if a.get("name", "").endswith(".apk")), None)
if not match:
    raise SystemExit(f"No APK asset found in GitHub release {os.environ['TAG']}")

manifest = {
    "tagName": rel.get("tag_name", os.environ["TAG"]),
    "versionName": os.environ["VERSION_NAME"],
    "versionCode": int(os.environ["VERSION_CODE"]),
    "title": rel.get("name") or f"Listen Quran {os.environ['TAG']}",
    "changelog": rel.get("body") or "",
    "htmlUrl": rel.get("html_url") or f"https://github.com/{os.environ['REPO']}/releases/tag/{os.environ['TAG']}",
    "publishedAt": rel.get("published_at") or "",
    "assetName": match["name"],
    "downloadUrl": match["browser_download_url"],
    "sizeBytes": int(match["size"]),
    "sha256": match.get("digest") or "",
}
with open(os.environ["MANIFEST_STAGE"], "w", encoding="utf-8") as f:
    json.dump(manifest, f, indent=2)
    f.write("\n")
PY
    gh release upload "$TAG" "$MANIFEST_STAGE" --repo "$REPO" --clobber
    echo "Uploaded update.json to $TAG cleanly."
    exit 0
fi

if [[ "$SKIP_BUILD" == false ]]; then
    echo "Running unit tests and assembling release APK for $TAG (code $VERSION_CODE)..."
    ./gradlew testDebugUnitTest assembleRelease --no-daemon
fi

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$RELEASE_APK" ]]; then
    echo "Missing release APK at $RELEASE_APK" >&2
    exit 1
fi

cp "$RELEASE_APK" "$APK_STAGE"
SHA256_HEX="$(sha256sum "$APK_STAGE" | awk '{print $1}')"
SIZE_BYTES="$(stat -c %s "$APK_STAGE")"
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
NOTES_CONTENT=""
if [[ -n "$NOTES_FILE" && -f "$NOTES_FILE" ]]; then
    NOTES_CONTENT="$(cat "$NOTES_FILE")"
fi

export TAG VERSION_NAME VERSION_CODE ASSET_NAME REPO SHA256_HEX SIZE_BYTES PUBLISHED_AT NOTES_CONTENT MANIFEST_STAGE
python3 - << 'PY'
import json, os

tag = os.environ["TAG"]
repo = os.environ["REPO"]
asset_name = os.environ["ASSET_NAME"]
manifest = {
    "tagName": tag,
    "versionName": os.environ["VERSION_NAME"],
    "versionCode": int(os.environ["VERSION_CODE"]),
    "title": f"Listen Quran {tag}",
    "changelog": os.environ["NOTES_CONTENT"],
    "htmlUrl": f"https://github.com/{repo}/releases/tag/{tag}",
    "publishedAt": os.environ["PUBLISHED_AT"],
    "assetName": asset_name,
    "downloadUrl": f"https://github.com/{repo}/releases/download/{tag}/{asset_name}",
    "sizeBytes": int(os.environ["SIZE_BYTES"]),
    "sha256": f"sha256:{os.environ['SHA256_HEX']}",
}
with open(os.environ["MANIFEST_STAGE"], "w", encoding="utf-8") as f:
    json.dump(manifest, f, indent=2)
    f.write("\n")
PY

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    echo "Release $TAG already exists; uploading updated APK and update.json..."
    gh release upload "$TAG" "$APK_STAGE" "$MANIFEST_STAGE" --repo "$REPO" --clobber
else
    CREATE_ARGS=(gh release create "$TAG" "$APK_STAGE" "$MANIFEST_STAGE" --repo "$REPO" --title "Listen Quran $TAG")
    if [[ -n "$NOTES_FILE" && -f "$NOTES_FILE" ]]; then
        CREATE_ARGS+=(--notes-file "$NOTES_FILE")
    else
        CREATE_ARGS+=(--generate-notes)
    fi
    if [[ -n "$LATEST_FLAG" ]]; then
        CREATE_ARGS+=("$LATEST_FLAG")
    fi
    "${CREATE_ARGS[@]}"
fi

echo "Published $TAG with $ASSET_NAME and update.json."
