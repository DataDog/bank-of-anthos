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

package anthos.samples.bankofanthos.balancereader;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Injectable unhandled-exception fault for observability demos.
 *
 * Causes a configurable fraction of balance requests to throw an unchecked
 * exception that is not caught by the controller, leaving the framework's
 * default error handler to surface it.
 *
 * Controlled by FAULT_UNHANDLED_EXCEPTION_ENABLED,
 * FAULT_UNHANDLED_EXCEPTION_RATE, and FAULT_UNHANDLED_EXCEPTION_MESSAGE
 * environment variables.
 */
@Component
public class UnhandledExceptionFault {

    private static final String DEFAULT_MESSAGE =
        "unexpected error retrieving account balance";

    private final boolean enabled;
    private final double rate;
    private final String message;

    public UnhandledExceptionFault() {
        this.enabled = "true".equalsIgnoreCase(
            System.getenv("FAULT_UNHANDLED_EXCEPTION_ENABLED"));
        this.rate = clampRate(
            parseEnvDouble("FAULT_UNHANDLED_EXCEPTION_RATE", 0.5));
        String messageEnv = System.getenv("FAULT_UNHANDLED_EXCEPTION_MESSAGE");
        this.message = messageEnv != null ? messageEnv : DEFAULT_MESSAGE;
    }

    /**
     * Throws a RuntimeException with the configured message if this request
     * is sampled. Returns immediately when the fault is disabled or the
     * request is not sampled.
     */
    public void maybeThrow() {
        if (!enabled || rate <= 0.0) {
            return;
        }
        if (rate >= 1.0 || ThreadLocalRandom.current().nextDouble() < rate) {
            throw new RuntimeException(message);
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
