#!/bin/bash
# Wait for PostgreSQL to be ready before starting Spring Boot

host="${SPRING_DATASOURCE_HOST:-postgres}"
port="${SPRING_DATASOURCE_PORT:-5432}"
max_attempts=60
attempt=0

echo "=== Waiting for PostgreSQL at ${host}:${port} ==="

while [ $attempt -lt $max_attempts ]; do
    # curl will fail quickly on TCP connection refused, succeed once port is open
    if curl -s --connect-timeout 2 "http://${host}:${port}" >/dev/null 2>&1; then
        echo "=== PostgreSQL port ${host}:${port} is reachable ==="
        sleep 2
        exec java -jar /app/sirius-web.jar
    fi
    # Also try raw TCP with bash /dev/tcp as fallback
    if (echo > /dev/tcp/${host}/${port}) 2>/dev/null; then
        echo "=== PostgreSQL port ${host}:${port} is reachable (via /dev/tcp) ==="
        sleep 2
        exec java -jar /app/sirius-web.jar
    fi
    attempt=$((attempt + 1))
    echo "Attempt ${attempt}/${max_attempts} - PostgreSQL not ready yet, waiting 2s..."
    sleep 2
done

echo "ERROR: PostgreSQL at ${host}:${port} not ready after ${max_attempts} attempts"
exit 1
