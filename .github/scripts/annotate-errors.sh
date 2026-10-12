#!/usr/bin/env bash
# Turns Kotlin compiler errors and Gradle's "What went wrong" section from a
# build log into GitHub annotations, so they show on the run's summary page.
log="${1:-build.log}"
[ -f "$log" ] || exit 0
{
  grep -E '^e: ' "$log" | head -25
  # The explanation Gradle prints after "What went wrong:" (up to the next blank line).
  awk '/What went wrong/{p=1; n=0} p{print; n++} p && (/^$/ || n>15){p=0}' "$log" | grep -v '^$' | head -30
} | while IFS= read -r line; do
  echo "::error::${line//$'\r'/}"
done
# Failed unit tests: the first line of each failure message.
find . -path '*/build/test-results/*' -name '*.xml' 2>/dev/null | while read -r f; do
  grep -o '<failure message="[^"]*"' "$f" | head -5 | sed 's/<failure message="//; s/"$//' | while IFS= read -r m; do
    echo "::error::$(basename "$f" .xml): ${m:0:400}"
  done
done
exit 0
