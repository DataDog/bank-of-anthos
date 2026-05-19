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

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Injectable latency fault for observability demos.
 *
 * Adds a configurable artificial delay to a configurable fraction of incoming
 * requests.
 *
 * Controlled by FAULT_LATENCY_ENABLED, FAULT_LATENCY_MS,
 * FAULT_LATENCY_JITTER_MS, and FAULT_LATENCY_RATE environment variables.
 */
@Component
public class LatencyFault {

    private final boolean enabled;
    private final long baseMs;
    private final long jitterMs;
    private final double rate;

    public LatencyFault() {
        this.enabled = "true".equalsIgnoreCase(System.getenv("FAULT_LATENCY_ENABLED"));
        this.baseMs = Math.max(0L, parseEnvLong("FAULT_LATENCY_MS", 2000L));
        this.jitterMs = Math.max(0L, parseEnvLong("FAULT_LATENCY_JITTER_MS", 0L));
        this.rate = clampRate(parseEnvDouble("FAULT_LATENCY_RATE", 1.0));
    }

    /**
     * Sleep the current thread by the configured delay if this request is
     * sampled. Returns immediately when the fault is disabled or the request
     * is not sampled.
     */
    public void apply() {
        if (!enabled || rate <= 0.0 || baseMs == 0L && jitterMs == 0L) {
            return;
        }
        if (rate < 1.0 && ThreadLocalRandom.current().nextDouble() >= rate) {
            return;
        }
        long delay = baseMs;
        if (jitterMs > 0) {
            delay += ThreadLocalRandom.current().nextLong(jitterMs + 1);
        }
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private static long parseEnvLong(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
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
