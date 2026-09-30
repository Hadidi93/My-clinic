#!/usr/bin/env bash
# Runs the database security tests against a throw-away PostgreSQL database.
#
#   Usage: supabase/tests/run_local.sh
#
# Needs a PostgreSQL 15+ server you can connect to as a superuser. Connection
# settings come from the usual PG* environment variables (PGHOST, PGUSER, ...).
set -euo pipefail
cd "$(dirname "$0")/../.."

DB="${TEST_DB:-myclinic_security_test}"
psql -v ON_ERROR_STOP=1 -q -d postgres -c "drop database if exists $DB" -c "create database $DB"

# -o /dev/null hides query result tables; test notices and \echo still print.
run() { psql -v ON_ERROR_STOP=1 -q -X -o /dev/null -d "$DB" -f "$1"; }

run supabase/tests/00_supabase_stub.sql
for migration in supabase/migrations/*.sql; do
  echo "Applying $migration"
  run "$migration"
done
for test in supabase/tests/[1-9]*_tests.sql; do
  echo "Running $test"
  run "$test"
done
