#!/bin/bash

PIPE="/tmp/adhoc_pipe"

# Check if the pipe exists
if [ ! -p "$PIPE" ]; then
    echo "Error: Pipe $PIPE not found. Is the ad-hoc network daemon running?"
    exit 1
fi

if [ "$1" == "discover" ]; then
    # If the command is 'discover', we don't need an IP or message
    echo "discover" > "$PIPE"
    exit 0
fi


# Check if at least one argument is given
if [ $# -lt 2 ]; then
    echo "Usage: $0 flood <target_ip> <message>"
    exit 1
fi

CMD="$1"
shift
IP="$1"
shift
MSG="$*"

# Send the command and message to the pipe
echo "$CMD $IP $MSG" > "$PIPE"


