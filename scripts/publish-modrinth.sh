#!/usr/bin/env bash
set -euo pipefail

: "${MODRINTH_TOKEN:?Set MODRINTH_TOKEN}"
: "${MODRINTH_PROJECT_ID:?Set MODRINTH_PROJECT_ID}"

JAR="${1:-build/libs/dynamic_clothing_system-1.0.0-beta.3.jar}"
VERSION="${2:-1.0.0-beta.3}"

if [[ ! -f "$JAR" ]]; then
  echo "JAR not found: $JAR" >&2
  exit 1
fi

DATA=$(cat <<JSON
{
  "name": "Sandy's Dynamic Clothing $VERSION",
  "version_number": "$VERSION",
  "changelog": "Initial public beta of Sandy's Dynamic Clothing.",
  "dependencies": [
    {
      "project_id": "vvuO3ImH",
      "version_id": "yohfFbgD",
      "dependency_type": "required"
    }
  ],
  "game_versions": ["1.21.1"],
  "version_type": "beta",
  "loaders": ["neoforge"],
  "featured": true,
  "status": "listed",
  "requested_status": "listed",
  "project_id": "$MODRINTH_PROJECT_ID",
  "file_parts": ["file"],
  "primary_file": "file",
  "environment": "client_and_server"
}
JSON
)

curl --fail-with-body -X POST \
  -H "Authorization: $MODRINTH_TOKEN" \
  -F "data=$DATA;type=application/json" \
  -F "file=@$JAR;type=application/java-archive" \
  https://api.modrinth.com/v2/version
