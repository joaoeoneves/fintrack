#!/usr/bin/env bash
# Quality gate shared by humans, Claude agents and CI.
#   scripts/gate.sh fast [test-filter...]  compile + ktlint + detekt + unit tests (optionally filtered)
#   scripts/gate.sh full                   everything CI checks: + lintDebug, release build, androidTest compile
# Examples:
#   scripts/gate.sh fast 'com.joaoeoneves.fintrack.ui.recurring.*'
#   scripts/gate.sh full
set -euo pipefail
cd "$(dirname "$0")/.."

mode="${1:-fast}"
shift || true

case "$mode" in
  fast)
    filters=()
    for f in "$@"; do filters+=(--tests "$f"); done
    ./gradlew --continue ktlintCheck detekt testDebugUnitTest "${filters[@]}"
    ;;
  full)
    ./gradlew --continue ktlintCheck detekt testDebugUnitTest lintDebug \
      assembleRelease compileDebugAndroidTestKotlin :baselineprofile:assemble
    ;;
  *)
    echo "usage: $0 fast [test-filter...] | full" >&2
    exit 2
    ;;
esac
