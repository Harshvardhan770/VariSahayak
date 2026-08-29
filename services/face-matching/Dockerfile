# VARI Sahayak Lost & Found face-matching service.
#
# Target: a Linux Google Cloud Compute Engine VM running Docker. The image is CPU-only —
# DeepFace pulls TensorFlow, and the published wheels run happily without a GPU on the
# general-purpose machine types this service is sized for.

FROM python:3.10-slim-bookworm

# OpenCV needs these even in the headless build: libglib for its threading primitives, and
# libgomp for the OpenMP runtime TensorFlow links against. Installing them here rather than
# reaching for the full opencv-python keeps the image about 200 MB smaller.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libglib2.0-0 \
        libgomp1 \
        curl \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    # DeepFace downloads model weights on first use and caches them under $HOME. Pinned to
    # a known directory so a mounted volume can persist them: without that, every container
    # restart re-downloads Facenet and RetinaFace before it can serve a single request.
    DEEPFACE_HOME=/opt/deepface \
    # Keras 3 is not compatible with DeepFace 0.0.93; tf-keras is installed and this is what
    # makes TensorFlow use it.
    TF_USE_LEGACY_KERAS=1 \
    # Silences TensorFlow's startup banner, which otherwise fills the container log with
    # CPU-instruction notices on every boot.
    TF_CPP_MIN_LOG_LEVEL=2

WORKDIR /app

# Dependencies first, in their own layer: application edits are frequent and this layer is
# expensive, so keeping them separate means a code change rebuilds in seconds.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Non-root. The service reads uploads from untrusted callers and runs a large native stack;
# there is no reason for any of it to be able to write outside its own cache.
RUN useradd --create-home --uid 10001 faceapp \
    && mkdir -p ${DEEPFACE_HOME} \
    && chown -R faceapp:faceapp /app ${DEEPFACE_HOME}
USER faceapp

EXPOSE 8080

# Liveness only, matching what /health actually reports. A health check that touched
# MongoDB would restart a perfectly healthy container during a database blip.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1:${PORT:-8080}/health || exit 1

# --preload is deliberately absent. PyMongo clients and TensorFlow sessions must not be
# inherited across fork(): each worker builds its own on first request, which is exactly
# what repository.get_client()'s PID guard is for.
CMD ["sh", "-c", "exec gunicorn --config gunicorn.conf.py app:app"]
