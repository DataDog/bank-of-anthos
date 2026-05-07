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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Injectable disk fault for observability demos.
 *
 * Generates a high volume of synchronous writes to a single file, repeatedly
 * overwriting the same bytes so the file does not grow. The goal is to drive
 * disk-write IOPS / throughput visible in observability tooling without
 * consuming significant disk space.
 *
 * Controlled by FAULT_DISK_ENABLED, FAULT_DISK_FILE_PATH,
 * FAULT_DISK_WRITE_SIZE_KB, FAULT_DISK_WRITE_INTERVAL_MS, and
 * FAULT_DISK_LOG_MESSAGE environment variables.
 */
@Component
public class DiskWriteFault {

    private static final Logger LOGGER = LogManager.getLogger(DiskWriteFault.class);

    private Path targetFile;

    @PostConstruct
    public void init() {
        if (!"true".equalsIgnoreCase(System.getenv("FAULT_DISK_ENABLED"))) {
            return;
        }

        int writeSizeKb = parseEnvInt("FAULT_DISK_WRITE_SIZE_KB", 64);
        int writeIntervalMs = parseEnvInt("FAULT_DISK_WRITE_INTERVAL_MS", 10);
        String filePathEnv = System.getenv("FAULT_DISK_FILE_PATH");
        String logMessageEnv = System.getenv("FAULT_DISK_LOG_MESSAGE");

        try {
            targetFile = filePathEnv != null
                ? Path.of(filePathEnv)
                : Files.createTempFile("fault-disk", ".dat");
        } catch (IOException e) {
            return;
        }

        final int finalWriteSizeKb = writeSizeKb;
        final int finalIntervalMs = writeIntervalMs;
        final Path file = targetFile;
        final String logMessage = (logMessageEnv != null && !logMessageEnv.isEmpty()) ? logMessageEnv : null;

        Thread thread = new Thread(() -> {
            byte[] buf = new byte[finalWriteSizeKb * 1024];
            Arrays.fill(buf, (byte) 1);
            // Outer loop reopens the file if anything goes wrong (open fails,
            // disk full, file removed underneath us). Keeps the fault alive
            // through transient IO errors instead of dying after one failure.
            while (!Thread.currentThread().isInterrupted()) {
                // "rwd" forces each write to be flushed to the underlying
                // device, so the IO is visible to disk-level metrics rather
                // than absorbed by the page cache.
                try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rwd")) {
                    while (!Thread.currentThread().isInterrupted()) {
                        raf.seek(0);
                        raf.write(buf);
                        if (logMessage != null) {
                            LOGGER.info(logMessage);
                        }
                        try {
                            Thread.sleep(finalIntervalMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } catch (IOException e) {
                    // Open or write failed; back off, then retry the open.
                    try {
                        Thread.sleep(Math.max(finalIntervalMs, 1000L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.setName("disk-fault");
        thread.start();
    }

    @PreDestroy
    public void cleanup() {
        if (targetFile != null) {
            try {
                Files.deleteIfExists(targetFile);
            } catch (IOException ignored) {
            }
        }
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
