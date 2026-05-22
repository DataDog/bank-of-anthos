# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0

"""Market data service.

Serves quote data for a small set of equities and indices used by the bank's
investment dashboards. Quote data is generated in-process from a fixed seed
table — no external dependencies.
"""

import logging
import os
import random
import time

from flask import Flask, jsonify

from latency_fault import LatencyFault


_SEED_QUOTES = {
    "AAPL":  {"name": "Apple Inc.",                  "price": 224.31, "vol": 0.018},
    "MSFT":  {"name": "Microsoft Corp.",             "price": 438.14, "vol": 0.015},
    "GOOGL": {"name": "Alphabet Inc. Class A",       "price": 178.92, "vol": 0.021},
    "AMZN":  {"name": "Amazon.com Inc.",             "price": 192.06, "vol": 0.024},
    "NVDA":  {"name": "NVIDIA Corp.",                "price": 132.55, "vol": 0.038},
    "TSLA":  {"name": "Tesla Inc.",                  "price": 247.81, "vol": 0.047},
    "JPM":   {"name": "JPMorgan Chase & Co.",        "price": 213.40, "vol": 0.012},
    "BAC":   {"name": "Bank of America Corp.",       "price":  44.97, "vol": 0.016},
    "SPY":   {"name": "SPDR S&P 500 ETF Trust",      "price": 565.22, "vol": 0.008},
    "QQQ":   {"name": "Invesco QQQ Trust",           "price": 484.71, "vol": 0.011},
}


app = Flask(__name__)
logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO").upper())

_latency_fault = LatencyFault()


def _compute_quote(symbol):
    """Compute a current quote for the given symbol.

    Applies a small random walk to the seed price using each symbol's volatility.
    Latency-sensitive: this function is the hot path for every quote lookup.
    """
    _latency_fault.apply()
    seed = _SEED_QUOTES[symbol]
    drift = random.gauss(0, seed["vol"])
    price = round(seed["price"] * (1.0 + drift), 4)
    return {
        "symbol": symbol,
        "name": seed["name"],
        "price": price,
        "as_of": int(time.time()),
    }


@app.route("/version")
def version():
    return os.environ.get("VERSION", "dev"), 200


@app.route("/ready")
def ready():
    return "ok", 200


@app.route("/quote/<symbol>")
def get_quote(symbol):
    symbol = symbol.upper()
    if symbol not in _SEED_QUOTES:
        return jsonify({"error": "unknown symbol"}), 404
    return jsonify(_compute_quote(symbol))


@app.route("/quotes")
def get_quotes():
    return jsonify({"quotes": [_compute_quote(s) for s in _SEED_QUOTES]})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
