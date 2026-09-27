#!/bin/sh
set -eu
rm -rf out
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
exec java -cp out fieldservice.dashboard.FieldServiceDashboard \
  "${1:-wo-1042}" "${2:-acct-north}" "${3:-tech-17}" "${4:-completed}" \
  "${5:-photo-arrival}" "${6:-2}" "${7:-2026-09-26T08:30:00Z}"
