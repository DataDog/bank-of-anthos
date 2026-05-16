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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Injectable error fault for observability demos.
 *
 * Causes a configurable fraction of incoming requests to fail with a
 * configurable error message (HTTP 500).
 *
 * Controlled by FAULT_ERROR_ENABLED, FAULT_ERROR_RATE, and
 * FAULT_ERROR_MESSAGE environment variables.
 */
@Component
public class ErrorFault {

    private static final String DEFAULT_MESSAGE =
        "Simulated transaction processing error";

    private final boolean enabled;
    private final double rate;
    private final String message;

    public ErrorFault() {
        this.enabled = "true".equalsIgnoreCase(System.getenv("FAULT_ERROR_ENABLED"));
        this.rate = clampRate(parseEnvDouble("FAULT_ERROR_RATE", 1.0));
        String configured = System.getenv("FAULT_ERROR_MESSAGE");
        this.message = (configured == null || configured.isEmpty())
            ? DEFAULT_MESSAGE : configured;
    }

    /**
     * Throw a 500 ResponseStatusException with the configured message if this
     * request is sampled. Returns immediately when the fault is disabled or
     * the request is not sampled.
     */
    public void apply() {
        if (!enabled || rate <= 0.0) {
            return;
        }
        if (rate < 1.0 && ThreadLocalRandom.current().nextDouble() >= rate) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
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
