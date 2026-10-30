#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p deploy/certs
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout deploy/certs/privkey.pem \
  -out deploy/certs/fullchain.pem \
  -subj "/C=CN/ST=Guangdong/L=Guangzhou/O=Campus Select/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
chmod 600 deploy/certs/privkey.pem
echo "Generated deploy/certs/fullchain.pem and deploy/certs/privkey.pem"
