#!/bin/sh
set -e

PORT="${PORT:-8080}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8081}"

# Write runtime config that the WasmJS app reads via window.__API_BASE__
echo "window.__API_BASE__ = \"${API_BASE_URL}\";" > /usr/share/nginx/html/config.js

# Substitute the port placeholder in the nginx config
sed -i "s/__PORT__/${PORT}/g" /etc/nginx/conf.d/default.conf

echo "Starting nginx on :${PORT}, API_BASE_URL=${API_BASE_URL}"
exec nginx -g 'daemon off;'
