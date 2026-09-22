#!/usr/bin/env bash
# Build the issue-djinn release zip.
#
# Reads the version from the pom of the checkout it runs in, runs the full
# Maven build with the test suite (`mvn verify` — a red suite aborts the
# release before anything is staged), stages the fast-jar layout into a
# versioned folder, and zips it.
#
# Zip layout (top folder issue-djinn-<version>/):
#   issue-djinn.jar — the fast-jar runner renamed; safe because its manifest
#                     Class-Path is relative to the jar's own location
#   everything else from target/quarkus-app — the fast-jar support
#   directories (app/, lib/, quarkus/) and emitted files, copied as-is
#   run.sh run.bat  — launcher templates from release/
#
# Output: target/release/issue-djinn-<version>.zip (target/ is gitignored).
set -euo pipefail

if ! REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    echo "error: build-release.sh must run inside the issue-djinn git repository" >&2
    exit 1
fi

# --- Version from this repo's pom (the <version> beside the issue-djinn artifactId) ---
VERSION="$(sed -n '/<artifactId>issue-djinn<\/artifactId>/{N; s/.*<version>\([^<]*\)<\/version>.*/\1/p;}' \
    "${REPO_ROOT}/pom.xml")"
if [[ -z "${VERSION}" ]]; then
    echo "error: could not read the project version from ${REPO_ROOT}/pom.xml" >&2
    exit 1
fi
if [[ ! "${VERSION}" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
    echo "error: unexpected version '${VERSION}' in ${REPO_ROOT}/pom.xml" >&2
    exit 1
fi

FAST_JAR_DIR="${REPO_ROOT}/target/quarkus-app"
STAGING_ROOT="${REPO_ROOT}/target/release"
STAGING_DIR="${STAGING_ROOT}/issue-djinn-${VERSION}"

echo "==> issue-djinn ${VERSION}: full Maven build with tests"
# A red suite must leave no zip: clear any earlier release output up front, so
# after this script exits target/release holds a zip only if this run was green.
rm -rf "${STAGING_ROOT}"
(cd "${REPO_ROOT}" && mvn -B verify)

echo "==> staging the fast-jar layout into ${STAGING_DIR}"
mkdir -p "${STAGING_DIR}"

shopt -s nullglob
for entry in "${FAST_JAR_DIR}"/*; do
    if [[ "$(basename "${entry}")" == "quarkus-run.jar" ]]; then
        cp "${entry}" "${STAGING_DIR}/issue-djinn.jar"
    else
        cp -R "${entry}" "${STAGING_DIR}/"
    fi
done
shopt -u nullglob
if [[ ! -f "${STAGING_DIR}/issue-djinn.jar" ]]; then
    echo "error: fast-jar runner not found in ${FAST_JAR_DIR}" >&2
    exit 1
fi

for launcher in run.sh run.bat; do
    if [[ ! -f "${REPO_ROOT}/release/${launcher}" ]]; then
        echo "error: launcher template release/${launcher} not found" >&2
        exit 1
    fi
    cp "${REPO_ROOT}/release/${launcher}" "${STAGING_DIR}/${launcher}"
done
chmod 755 "${STAGING_DIR}/run.sh"

echo "==> zipping"
python3 - "${STAGING_ROOT}" "issue-djinn-${VERSION}" <<'PYEOF'
import os, sys, zipfile

staging_root, top_dir = sys.argv[1], sys.argv[2]
zip_path = os.path.join(staging_root, top_dir + ".zip")
src_root = os.path.join(staging_root, top_dir)

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames.sort()
        for name in sorted(filenames):
            full = os.path.join(dirpath, name)
            # the arcname keeps the versioned top folder; ZipInfo.from_file
            # carries each file's Unix mode (including the exec bit) across
            zf.write(full, os.path.relpath(full, staging_root))
PYEOF

echo "release zip: ${STAGING_ROOT}/issue-djinn-${VERSION}.zip"