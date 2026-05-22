# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0

"""FX rates service.

Serves foreign-exchange rates fetched from an upstream FX provider. Rates are
cached in-memory; if the upstream read fails the cache is served stale.
"""

import logging
import os
import threading
import time

from flask import Flask, jsonify

from rates_source import RatesSource, RatesSourceError


_BASE_RATES = {
    "USD": 1.00,
    "EUR": 0.92,
    "GBP": 0.79,
    "JPY": 156.40,
    "CAD": 1.37,
    "AUD": 1.51,
    "CHF": 0.89,
    "CNY": 7.24,
    "INR": 83.50,
    "MXN": 17.05,
}


app = Flask(__name__)
logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO").upper())

_cache = {"rates": dict(_BASE_RATES), "fetched_at": time.time()}
_cache_lock = threading.Lock()

_rates_source = RatesSource(
    url=os.environ.get("FX_UPSTREAM_URL", ""),
    timeout_seconds=float(os.environ.get("FX_UPSTREAM_TIMEOUT_SECONDS", "2.0")),
)


def _refresh_or_serve_stale():
    if not _rates_source.configured:
        with _cache_lock:
            return dict(_cache["rates"]), _cache["fetched_at"], False
    try:
        fresh = _rates_source.fetch()
        with _cache_lock:
            _cache["rates"] = fresh
            _cache["fetched_at"] = time.time()
            return dict(_cache["rates"]), _cache["fetched_at"], False
    except RatesSourceError as err:
        app.logger.error("failed to read FX rates from upstream data source: %s", err)
        with _cache_lock:
            return dict(_cache["rates"]), _cache["fetched_at"], True


@app.route("/version")
def version():
    return os.environ.get("VERSION", "dev"), 200


@app.route("/ready")
def ready():
    return "ok", 200


@app.route("/rates")
def get_rates():
    rates, fetched_at, stale = _refresh_or_serve_stale()
    return jsonify({"base": "USD", "rates": rates, "fetched_at": fetched_at, "stale": stale})


@app.route("/rates/<base>/<quote>")
def get_pair(base, quote):
    base = base.upper()
    quote = quote.upper()
    rates, fetched_at, stale = _refresh_or_serve_stale()
    if base not in rates or quote not in rates:
        return jsonify({"error": "unknown currency"}), 404
    rate = rates[quote] / rates[base]
    return jsonify({"base": base, "quote": quote, "rate": rate, "fetched_at": fetched_at, "stale": stale})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
