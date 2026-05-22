# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0

"""Upstream FX rates client.

Fetches a fresh rate table over HTTP from the configured FX provider. When the
stale-data fault is enabled the upstream URL is overridden so the read fails
deterministically; the calling service then logs the failure and serves its
cached rate table stale.
"""

import os

import requests


class RatesSourceError(Exception):
    """Raised when the upstream rates provider cannot be read."""


class RatesSource:
    def __init__(self, url, timeout_seconds):
        self._configured_url = url
        self._timeout_seconds = timeout_seconds
        self._stale_data_fault_enabled = (
            os.environ.get("FAULT_STALE_DATA_ENABLED", "").lower() == "true"
        )
        self._fault_url = os.environ.get(
            "FAULT_STALE_DATA_URL", "http://192.168.1.100/v1/rates"
        )

    @property
    def configured(self):
        """True if an upstream is configured (or the fault is forcing a fetch)."""
        return self._stale_data_fault_enabled or bool(self._configured_url)

    def fetch(self):
        url = self._fault_url if self._stale_data_fault_enabled else self._configured_url
        try:
            response = requests.get(url, timeout=self._timeout_seconds)
            response.raise_for_status()
            payload = response.json()
            rates = payload.get("rates")
            if not isinstance(rates, dict):
                raise RatesSourceError(f"upstream response missing 'rates' object (url={url})")
            return {k.upper(): float(v) for k, v in rates.items()}
        except requests.exceptions.RequestException as err:
            raise RatesSourceError(f"upstream rates fetch failed (url={url}): {err}") from err
        except (ValueError, TypeError) as err:
            raise RatesSourceError(f"upstream rates response was malformed (url={url}): {err}") from err
