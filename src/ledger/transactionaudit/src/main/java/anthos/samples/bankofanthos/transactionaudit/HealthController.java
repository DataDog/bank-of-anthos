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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class HealthController {

    @GetMapping("/ready")
    public String ready() {
        return "ok";
    }

    @GetMapping("/healthy")
    public String healthy() {
        return "ok";
    }

    @GetMapping("/version")
    public String version() {
        return System.getenv().getOrDefault("VERSION", "dev");
    }
}
