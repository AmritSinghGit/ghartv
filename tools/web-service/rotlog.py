#!/usr/bin/env python3
"""Copy stdin to a size-rotated log file (default 10 MB x 5). Used by run-web.sh so the player's
own (already redacted) log survives a reboot and cannot fill the disk."""
import logging.handlers, os, sys

os.umask(0o077)  # new and rotated log files are created 600 from the first line
path = sys.argv[1]
os.makedirs(os.path.dirname(path), mode=0o700, exist_ok=True)
h = logging.handlers.RotatingFileHandler(path, maxBytes=int(os.environ.get('LOG_MAX_BYTES', 10_000_000)),
                                         backupCount=int(os.environ.get('LOG_BACKUPS', 5)), encoding='utf-8')
os.chmod(path, 0o600) if os.path.exists(path) else None
for line in sys.stdin:
    h.emit(logging.makeLogRecord({'msg': line.rstrip('\n')}))
    if os.path.exists(path) and os.stat(path).st_mode & 0o077:
        os.chmod(path, 0o600)
