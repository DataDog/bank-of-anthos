/*
 * Copyright 2020, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package anthos.samples.bankofanthos.ledgerwriter;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Injectable log-flood fault for observability demos.
 *
 * Emits a configurable number of varied, debug-style log lines on every
 * sampled request. Messages are drawn from a pool of templates that resemble
 * framework output (Hibernate SQL, HikariCP pool stats, RestTemplate calls,
 * JWT decode, GC pauses, etc.) so the flood looks like a misconfigured
 * verbose logger left enabled in production rather than synthetic noise.
 *
 * Controlled by FAULT_LOG_FLOOD_ENABLED, FAULT_LOG_FLOOD_COUNT,
 * FAULT_LOG_FLOOD_LEVEL, and FAULT_LOG_FLOOD_RATE environment variables.
 */
@Component
public class LogFloodFault {

    private static final Logger LOGGER =
        LogManager.getLogger(LogFloodFault.class);

    private final boolean enabled;
    private final int messagesPerRequest;
    private final double rate;
    private final Level level;

    public LogFloodFault() {
        this.enabled = "true".equalsIgnoreCase(
                System.getenv("FAULT_LOG_FLOOD_ENABLED"));
        this.messagesPerRequest = Math.max(0,
                parseEnvInt("FAULT_LOG_FLOOD_COUNT", 200));
        this.rate = clampRate(parseEnvDouble("FAULT_LOG_FLOOD_RATE", 1.0));
        this.level = parseLevel(
                System.getenv("FAULT_LOG_FLOOD_LEVEL"), Level.DEBUG);
    }

    /**
     * Emit the configured number of log lines if this request is sampled.
     * Returns immediately when the fault is disabled or the request is not
     * sampled.
     */
    public void apply() {
        if (!enabled || messagesPerRequest == 0 || rate <= 0.0) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rate < 1.0 && rnd.nextDouble() >= rate) {
            return;
        }
        for (int i = 0; i < messagesPerRequest; i++) {
            LOGGER.log(level, generateMessage(rnd));
        }
    }

    private static String generateMessage(ThreadLocalRandom rnd) {
        return MESSAGE_GENERATORS[rnd.nextInt(MESSAGE_GENERATORS.length)]
                .apply(rnd);
    }

    @FunctionalInterface
    private interface MessageGenerator {
        String apply(ThreadLocalRandom rnd);
    }

    private static final MessageGenerator[] MESSAGE_GENERATORS =
        new MessageGenerator[] {
            rnd -> String.format(
                "Cache lookup key=acct:%s hit=%s loaderTimeMs=%d size=%d",
                randomAccount(rnd), rnd.nextBoolean(),
                rnd.nextInt(1, 12), rnd.nextInt(50, 500)),
            rnd -> String.format(
                "HikariPool-1 - Pool stats (total=%d, active=%d, idle=%d, "
                    + "waiting=%d)",
                rnd.nextInt(20, 50), rnd.nextInt(0, 8),
                rnd.nextInt(0, 25), rnd.nextInt(0, 3)),
            rnd -> String.format(
                "Executing prepared statement [SELECT id, amount, "
                    + "from_acct, to_acct FROM transactions "
                    + "WHERE request_uuid = ?] params=[%s]",
                UUID.randomUUID()),
            rnd -> String.format(
                "Hibernate: select transactio0_.id as id1_0_, "
                    + "transactio0_.amount as amount2_0_ from transactions "
                    + "transactio0_ where transactio0_.id=%d",
                rnd.nextLong(10000L, 9999999L)),
            rnd -> String.format(
                "RestTemplate %s %s -> %d in %dms",
                randomMethod(rnd), randomUrl(rnd),
                randomStatus(rnd), rnd.nextInt(2, 80)),
            rnd -> String.format(
                "Decoded JWT subject=user_%d iat=%d exp=%d "
                    + "issuer=bank-of-anthos",
                rnd.nextInt(1000, 9999),
                epochMinusSeconds(rnd, 600),
                epochPlusSeconds(rnd, 60, 3600)),
            rnd -> String.format(
                "Beginning JDBC transaction tx=%s "
                    + "isolation=READ_COMMITTED timeout=30s readOnly=false",
                shortUuid()),
            rnd -> String.format(
                "Refreshing bean 'transactionValidator' scope=singleton "
                    + "lazy=false phase=%d",
                rnd.nextInt(0, 5)),
            rnd -> String.format(
                "Mapped {POST /transactions, produces "
                    + "[application/json]} onto "
                    + "LedgerWriterController.addTransaction(...) "
                    + "handler=%s",
                shortUuid()),
            rnd -> String.format(
                "TLS handshake complete cipher=TLS_AES_256_GCM_SHA384 "
                    + "protocol=TLSv1.3 peer=%s:%d",
                randomIp(rnd), rnd.nextInt(1024, 65535)),
            rnd -> String.format(
                "GC pause (G1 Evacuation Pause) Young: %dM->%dM(%dM) %dms",
                rnd.nextInt(200, 800), rnd.nextInt(80, 200),
                rnd.nextInt(800, 1024), rnd.nextInt(2, 40)),
            rnd -> String.format(
                "DNS resolved host=%s -> %s in %dms (cached=%s)",
                randomHost(rnd), randomIp(rnd),
                rnd.nextInt(0, 12), rnd.nextBoolean()),
            rnd -> String.format(
                "ThreadPoolTaskExecutor[Running, pool size=%d, active=%d, "
                    + "queued=%d, completed=%d]",
                rnd.nextInt(8, 32), rnd.nextInt(0, 8),
                rnd.nextInt(0, 4), rnd.nextLong(1000L, 100000L)),
            rnd -> String.format(
                "Flushing session: %d inserts, %d updates, %d deletes "
                    + "(elapsed=%dms)",
                rnd.nextInt(0, 4), rnd.nextInt(0, 2),
                rnd.nextInt(0, 2), rnd.nextInt(0, 8)),
            rnd -> String.format(
                "Recorded counter transactions.processed value=1 "
                    + "tags=[env=production, routing=%s, region=us-central1]",
                randomRoutingNum(rnd)),
            rnd -> String.format(
                "Authentication successful subject=user_%d roles=[CUSTOMER] "
                    + "sessionId=%s",
                rnd.nextInt(1000, 9999), shortUuid()),
            rnd -> String.format(
                "Resolved view 'forward:/transactions' for handler "
                    + "[LedgerWriterController#addTransaction] in %dms",
                rnd.nextInt(0, 4)),
            rnd -> String.format(
                "Validating JSR-303 constraints on Transaction[uuid=%s] "
                    + "-> 0 violations",
                shortUuid()),
            rnd -> String.format(
                "Evicted %d entries from cache 'duplicateRequestUuids' "
                    + "(size=%d, hits=%d, misses=%d, hitRate=%.2f)",
                rnd.nextInt(0, 10), rnd.nextInt(50, 500),
                rnd.nextLong(1000L, 50000L),
                rnd.nextLong(50L, 5000L),
                0.80 + rnd.nextDouble() * 0.19),
            rnd -> String.format(
                "Acquired connection conn_%d from datasource "
                    + "(acquireTimeMs=%d, leaked=false, age=%ds)",
                rnd.nextInt(1, 50), rnd.nextInt(0, 6),
                rnd.nextInt(1, 3600)),
            rnd -> String.format(
                "HTTP request headers: Authorization=Bearer ***, "
                    + "X-Request-ID=%s, User-Agent=%s",
                UUID.randomUUID(), randomUserAgent(rnd)),
            rnd -> String.format(
                "Datasource health check: roundTripMs=%d, "
                    + "validationQuery='SELECT 1', result=ok",
                rnd.nextInt(1, 9)),
        };

    private static String randomAccount(ThreadLocalRandom rnd) {
        return String.valueOf(rnd.nextLong(1000000000L, 9999999999L));
    }

    private static String randomRoutingNum(ThreadLocalRandom rnd) {
        return String.valueOf(rnd.nextInt(100000000, 999999999));
    }

    private static String randomMethod(ThreadLocalRandom rnd) {
        String[] methods = {"GET", "POST", "PUT", "DELETE"};
        return methods[rnd.nextInt(methods.length)];
    }

    private static String randomUrl(ThreadLocalRandom rnd) {
        String[] hosts = {
            "balancereader:8080",
            "userservice:8080",
            "contacts:8080",
        };
        String[] paths = {
            "/balances/" + randomAccount(rnd),
            "/healthz",
            "/version",
            "/ready",
            "/users/" + rnd.nextInt(1000, 9999),
        };
        return "http://" + hosts[rnd.nextInt(hosts.length)]
                + paths[rnd.nextInt(paths.length)];
    }

    private static int randomStatus(ThreadLocalRandom rnd) {
        int[] statuses = {200, 200, 200, 200, 201, 204, 304, 400, 404, 500};
        return statuses[rnd.nextInt(statuses.length)];
    }

    private static String randomIp(ThreadLocalRandom rnd) {
        return String.format("10.%d.%d.%d",
                rnd.nextInt(0, 255),
                rnd.nextInt(0, 255),
                rnd.nextInt(1, 255));
    }

    private static String randomHost(ThreadLocalRandom rnd) {
        String[] hosts = {
            "balancereader.default.svc.cluster.local",
            "ledger-db.default.svc.cluster.local",
            "userservice.default.svc.cluster.local",
            "contacts.default.svc.cluster.local",
        };
        return hosts[rnd.nextInt(hosts.length)];
    }

    private static String randomUserAgent(ThreadLocalRandom rnd) {
        String[] agents = {
            "Java/21.0.1",
            "Apache-HttpClient/5.2",
            "okhttp/4.12.0",
            "kube-probe/1.28",
        };
        return agents[rnd.nextInt(agents.length)];
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static long epochMinusSeconds(ThreadLocalRandom rnd, int max) {
        return System.currentTimeMillis() / 1000L - rnd.nextInt(0, max);
    }

    private static long epochPlusSeconds(
            ThreadLocalRandom rnd, int min, int max) {
        return System.currentTimeMillis() / 1000L + rnd.nextInt(min, max);
    }

    private static double clampRate(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static Level parseLevel(String value, Level defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Level parsed = Level.getLevel(value.toUpperCase());
        return parsed != null ? parsed : defaultValue;
    }

    private static int parseEnvInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseEnvDouble(String name, double defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
