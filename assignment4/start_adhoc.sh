#!/bin/bash

SCRIPT_DIR="$(dirname "$0")"
PYTHON_SCRIPT="$SCRIPT_DIR/adhoc.py"
PID_FILE="$SCRIPT_DIR/adhoc.pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Ad-hoc network already running with PID $(cat "$PID_FILE")"
    exit 1
fi

# Load environment variables from .env
set -o allexport
source .env
set +o allexport

echo "Starting ad-hoc network..."
nohup python3 "$PYTHON_SCRIPT" > adhoc_start.log 2>&1 &
echo $! > "$PID_FILE"
echo "Started with PID $(cat "$PID_FILE")"
