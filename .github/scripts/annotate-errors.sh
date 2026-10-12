#!/usr/bin/env bash
# Turns Kotlin compiler errors and Gradle's "What went wrong" lines from a
# build log into GitHub annotations, so they show on the run's summary page.
log="${1:-build.log}"
[ -f "$log" ] || exit 0
grep -E '^e: |^> |What went wrong|FAILED' "$log" | head -40 | while IFS= read -r line; do
  echo "::error::${line//$'\r'/}"
done
exit 0
