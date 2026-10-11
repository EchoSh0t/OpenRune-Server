#!/usr/bin/env bash
# Runs the game server and starts it again whenever the web panel asks for a restart.
# The panel writes .data/restart-request ("restart" or "rebuild") right before the server exits;
# any other exit (Ctrl+C, crash, panel shutdown) ends this script.
set -u
cd "$(dirname "$0")"

MARKER=".data/restart-request"
export OPENRUNE_SUPERVISED=1
rm -f "$MARKER"

while true; do
    ./gradlew run
    if [ ! -f "$MARKER" ]; then
        break
    fi
    request=$(cat "$MARKER")
    rm -f "$MARKER"

    if [ "$request" = "rebuild" ]; then
        echo "Web panel requested a cache rebuild before restarting."
        if ! ./gradlew buildCache; then
            echo "Cache rebuild failed; not restarting."
            exit 1
        fi
    fi
    echo "Restarting server..."
done
