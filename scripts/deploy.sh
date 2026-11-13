#!/usr/bin/env bash
# Deploys the latest main branch on the server.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "ERROR: .env not found. Copy .env.example to .env and set real passwords." >&2
  exit 1
fi

echo "==> Pulling the latest code"
git pull --ff-only

echo "==> Building images one at a time (saves memory on a small server)"
for svc in backend-service-1 backend-service-1b backend-service-2 proxy-service; do
  docker compose build "$svc"
done

echo "==> Starting the stack"
docker compose up -d

echo "==> Waiting for the proxy to answer"
for i in $(seq 1 40); do
  if curl -fs http://localhost:8080/backend2/hello > /dev/null; then
    echo "Proxy is up."
    break
  fi
  if [ "$i" -eq 40 ]; then
    echo "ERROR: the proxy did not start in time" >&2
    docker compose logs --tail=50 proxy-service
    exit 1
  fi
  sleep 3
done

echo "==> Smoke test"
curl -fs http://localhost:8080/backend1/hello | grep -q "Hello from Backend Service 1"
curl -fs http://localhost:8080/backend2/hello | grep -q "Hello from Backend Service 2"

echo "==> Status"
docker compose ps
echo "Deployed successfully."