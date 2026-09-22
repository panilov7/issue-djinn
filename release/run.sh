#!/usr/bin/env bash
# issue-djinn launcher: starts the app from the folder the release zip was
# extracted into. Requires Java 21+. Data directory, host, and port can be
# overridden with the ISSUE_DJINN_DATA_DIR, ISSUE_DJINN_HOST, and
# ISSUE_DJINN_PORT environment variables.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -f "${SCRIPT_DIR}/issue-djinn.jar" ]]; then
    echo "error: issue-djinn.jar not found next to run.sh" >&2
    exit 1
fi

exec java -jar "${SCRIPT_DIR}/issue-djinn.jar"