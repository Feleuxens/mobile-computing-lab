#!/bin/bash

SCRIPT_DIR="$(dirname "$0")"
PID_FILE="$SCRIPT_DIR/adhoc.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "No PID file found. Is the ad-hoc network running?"
    exit 1
fi

PID=$(cat "$PID_FILE")

if kill -0 "$PID" 2>/dev/null; then
    echo "Stopping ad-hoc network with PID $PID"
    kill "$PID"
    sleep 2
    if kill -0 "$PID" 2>/dev/null; then
        echo "Force killing $PID"
        kill -9 "$PID"
    fi
    echo "Stopped."
    rm -f "$PID_FILE"
else
    echo "Process $PID not running. Cleaning up."
    rm -f "$PID_FILE"
fi

rm -f "$SCRIPT_DIR/adhoc.log"
rm -f "$SCRIPT_DIR/adhoc_start.log"
