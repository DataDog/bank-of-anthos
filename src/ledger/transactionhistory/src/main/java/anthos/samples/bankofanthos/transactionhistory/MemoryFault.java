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

package anthos.samples.bankofanthos.transactionhistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Injectable memory fault for observability demos.
 *
 * Controlled by FAULT_MEMORY_ENABLED, FAULT_MEMORY_MB, FAULT_MEMORY_GROWTH_INTERVAL_MS,
 * and FAULT_MEMORY_STARTUP_DELAY_SECONDS environment variables.
 */
@Component
public class MemoryFault {

    private final List<byte[]> memoryHog = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!"true".equalsIgnoreCase(System.getenv("FAULT_MEMORY_ENABLED"))) {
            return;
        }

        int targetMb = parseEnvInt("FAULT_MEMORY_MB", 100);
        int growthIntervalMs = parseEnvInt("FAULT_MEMORY_GROWTH_INTERVAL_MS", 500);
        int startupDelaySeconds = parseEnvInt("FAULT_MEMORY_STARTUP_DELAY_SECONDS", 0);

        final int finalTargetMb = targetMb;
        final int finalIntervalMs = growthIntervalMs;
        final int finalStartupDelaySeconds = startupDelaySeconds;

        Thread thread = new Thread(() -> {
            if (finalStartupDelaySeconds > 0) {
                try {
                    Thread.sleep(finalStartupDelaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            int allocated = 0;
            while (allocated < finalTargetMb) {
                try {
                    byte[] chunk = new byte[1024 * 1024];
                    Arrays.fill(chunk, (byte) 1);
                    synchronized (memoryHog) {
                        memoryHog.add(chunk);
                    }
                    allocated++;
                } catch (OutOfMemoryError e) {
                    // Heap is full; keep pressing — when GC frees space, grab it back.
                }
                try {
                    Thread.sleep(finalIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.setName("memory-fault");
        thread.start();
    }

    private int parseEnvInt(String name, int defaultValue) {
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
}
