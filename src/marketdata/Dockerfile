# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0

FROM python:3.14.4-slim AS base

FROM base AS builder
COPY requirements.txt .
RUN pip install --prefix="/install" -r requirements.txt

FROM base
WORKDIR /app
COPY --from=builder /install /usr/local

ENV PYTHONUNBUFFERED=1
ENV PORT=8080

COPY . .
RUN chmod a+r *

EXPOSE 8080
CMD ["python", "marketdata.py"]
