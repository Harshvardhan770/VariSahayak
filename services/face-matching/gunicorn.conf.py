"""Gunicorn configuration for the containerised face-matching service.

Read by `gunicorn --config gunicorn.conf.py app:app`. Everything is environment-driven so
the same image runs on a 2-vCPU VM and a larger one without a rebuild.
"""

import os

bind = f"{os.environ.get('HOST', '0.0.0.0')}:{os.environ.get('PORT', '8080')}"

# One worker by default, and that is not a placeholder. Each worker loads its own copy of
# the Facenet and RetinaFace weights — several hundred MB of resident memory apiece — so a
# second worker on a small VM buys queueing, not throughput. Scale with threads first, and
# only add workers once the machine has the RAM for them.
workers = int(os.environ.get("GUNICORN_WORKERS", "1"))
threads = int(os.environ.get("GUNICORN_THREADS", "4"))
worker_class = "gthread"

# Generous, because a cold container downloads model weights before it can answer, and a
# first request that arrives during that window must not be killed for being slow.
timeout = int(os.environ.get("GUNICORN_TIMEOUT", "120"))
graceful_timeout = 30
keepalive = 5

# TensorFlow leaks a little memory per inference. Recycling workers on a long-running VM
# turns a slow climb into a sawtooth that never reaches the OOM killer.
max_requests = int(os.environ.get("GUNICORN_MAX_REQUESTS", "500"))
max_requests_jitter = 50

# NOT enabled, deliberately. Preloading forks the app after MongoClient and TensorFlow have
# initialised, and both break when their file descriptors are shared between processes.
preload_app = False

accesslog = "-"
errorlog = "-"
loglevel = os.environ.get("LOG_LEVEL", "info").lower()

# The access log omits the request body by design: bodies here are base64 photographs.
access_log_format = '%(h)s "%(r)s" %(s)s %(b)s %(D)sus'
