#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-8080}"

mvn -q -DskipTests compile
java -cp target/classes org.openjfx.network.NoteSyncServer "$PORT"
