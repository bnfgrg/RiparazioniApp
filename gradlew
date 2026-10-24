#!/bin/sh
# Gradle wrapper script
# Per build locali: richiede Gradle installato nel PATH (https://gradle.org/install/)
# Per GitHub Actions: il workflow usa direttamente 'gradle' via gradle/actions/setup-gradle

set -e

GRADLE_CMD=""
if command -v gradle > /dev/null 2>&1; then
    GRADLE_CMD="gradle"
elif [ -n "$GRADLE_HOME" ] && [ -f "$GRADLE_HOME/bin/gradle" ]; then
    GRADLE_CMD="$GRADLE_HOME/bin/gradle"
else
    echo "ERROR: Gradle non trovato." >&2
    echo "Installa Gradle da https://gradle.org/install/ oppure usa Android Studio." >&2
    exit 1
fi

exec "$GRADLE_CMD" "$@"
