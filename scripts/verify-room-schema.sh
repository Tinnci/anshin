#!/usr/bin/env bash

set -euo pipefail

schema_dir="core/database/schemas/com.driezy.medlog.data.local.MedLogDatabase"
current_schema="$(
  find "$schema_dir" -maxdepth 1 -type f -name '*.json' -print |
    sort -V |
    tail -n 1
)"

if [[ -z "$current_schema" ]]; then
  echo "No Room schema snapshots found in $schema_dir." >&2
  exit 1
fi

case "$current_schema" in
  "$schema_dir"/*.json) ;;
  *)
    echo "Resolved schema path is outside the expected directory: $current_schema" >&2
    exit 1
    ;;
esac

backup_dir="$(mktemp -d)"
backup_schema="$backup_dir/$(basename "$current_schema")"
cp "$current_schema" "$backup_schema"

restore_on_error() {
  local exit_code=$?
  if [[ $exit_code -ne 0 && ! -f "$current_schema" ]]; then
    cp "$backup_schema" "$current_schema"
  fi
  rm -rf "$backup_dir"
  exit "$exit_code"
}
trap restore_on_error EXIT

rm -- "$current_schema"
./gradlew :core:database:kspDebugKotlin :core:database:copyRoomSchemas --rerun-tasks

if [[ ! -f "$current_schema" ]]; then
  echo "Room did not regenerate $current_schema." >&2
  exit 1
fi

git diff --exit-code -- "$schema_dir"
