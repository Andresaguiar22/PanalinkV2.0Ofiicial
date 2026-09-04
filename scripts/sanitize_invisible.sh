#!/usr/bin/env bash
# Remove invisible Unicode characters (U+200B zero-width space, U+FEFF BOM)
# from text files. Run after any edit to keep the tree byte-clean.
# Uses: bash scripts/sanitize_invisible.sh

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

# U+200B = bytes 342 200 213; U+FEFF = bytes 357 273 277
if command -v perl >/dev/null 2>&1; then
  SANITIZER='perl -i -pe '\''s/\xE2\x80\x8B//g; s/\xEF\xBB\xBF//g'\'''
else
  SANITIZER='sed -i '\''s/\xe2\x80\x8b//g; s/\xef\xbb\xbf//g'\'''
fi

FILES="$(grep -rlP '\x{200B}|\x{FEFF}' \
  --include='*.kt'     --include='*.kts'    \
  --include='*.java'   --include='*.groovy'  \
  --include='*.sh'      --include='*.py'       \
  --include='*.md'      --include='*.txt'      \
  --include='*.json'    --include='*.xml'      \
  --include='*.properties' --include='*.gradle'  \
  . 2>/dev/null | grep -v '/\.git/' | grep -v '/build/' || true)"

if [ -z "$FILES" ]; then
  echo "sanitize: limpio - no files with invisible Unicode found."
  exit 0
fi

echo "sanitize: limpiando:"
echo "$FILES" | while IFS= read -r f; do
  echo "  - $f"
  eval "$SANITIZER -- \"$f\""
done

echo "sanitize: done."