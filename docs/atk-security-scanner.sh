FRONTEND_URL="${FRONTEND_URL:-http://localhost:8080}"
VALID_ROUTE="${VALID_ROUTE:-/transactions/search}"
for ((i=1;i<=250;i++));
do
# Target existing service's routes
curl ${FRONTEND_URL}/${VALID_ROUTE} -A dd-test-scanner-log;
# Target non existing service's routes
curl ${FRONTEND_URL}/non-existing-route -A dd-test-scanner-log;
done
