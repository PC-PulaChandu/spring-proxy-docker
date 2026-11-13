#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu 24.04 server.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

echo "==> Installing Docker, Compose and Git"
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose-v2 git curl

echo "==> Enabling Docker at boot"
sudo systemctl enable --now docker

echo "==> Letting $USER run docker without sudo"
sudo usermod -aG docker "$USER"

if ! swapon --show | grep -q '/swapfile'; then
  echo "==> Creating a 2 GB swap file (the small server needs it to build Java images)"
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "Done. Log out and log back in so the docker group takes effect."