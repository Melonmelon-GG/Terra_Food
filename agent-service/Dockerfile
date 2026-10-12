FROM python:3.12-slim@sha256:2f17fc044b579bab302c2e8054d3a686e2cb9a83de48e70534b94cd8ebbe06a9

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

ARG SOURCE_REVISION=unknown
ENV SOURCE_REVISION=$SOURCE_REVISION
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
WORKDIR /app

COPY requirements.lock .
ARG PIP_INDEX_URL=https://pypi.org/simple
RUN --mount=type=cache,target=/root/.cache/pip pip install --require-hashes --timeout 60 --retries 3 --index-url "$PIP_INDEX_URL" -r requirements.lock
# Runtime never installs dependencies. Remove the base image's obsolete installer.
RUN python -m pip uninstall --yes pip

COPY app ./app

RUN useradd --create-home --uid 10001 terra \
    && chown -R terra:terra /app
USER terra

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
