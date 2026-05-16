# Copyright 2021 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Injectable latency fault for observability demos.

Adds a configurable artificial delay to a configurable fraction of incoming
requests.

Controlled by FAULT_LATENCY_ENABLED, FAULT_LATENCY_MS,
FAULT_LATENCY_JITTER_MS, and FAULT_LATENCY_RATE environment variables.
"""

import os
import random
import time


class LatencyFault:

    def __init__(self):
        self._enabled = os.environ.get("FAULT_LATENCY_ENABLED", "").lower() == "true"
        self._base_ms = max(0, _parse_env_int("FAULT_LATENCY_MS", 2000))
        self._jitter_ms = max(0, _parse_env_int("FAULT_LATENCY_JITTER_MS", 0))
        self._rate = _clamp_rate(_parse_env_float("FAULT_LATENCY_RATE", 1.0))

    def apply(self):
        """Sleep the current thread by the configured delay if this request is
        sampled. Returns immediately when the fault is disabled or the request
        is not sampled.
        """
        if not self._enabled or self._rate <= 0.0 or (self._base_ms == 0 and self._jitter_ms == 0):
            return
        if self._rate < 1.0 and random.random() >= self._rate:
            return
        delay_ms = self._base_ms
        if self._jitter_ms > 0:
            delay_ms += random.randint(0, self._jitter_ms)
        if delay_ms <= 0:
            return
        time.sleep(delay_ms / 1000.0)


def _clamp_rate(value):
    if value < 0.0:
        return 0.0
    if value > 1.0:
        return 1.0
    return value


def _parse_env_int(name, default_value):
    value = os.environ.get(name)
    if value is None:
        return default_value
    try:
        return int(value)
    except ValueError:
        return default_value


def _parse_env_float(name, default_value):
    value = os.environ.get(name)
    if value is None:
        return default_value
    try:
        return float(value)
    except ValueError:
        return default_value
