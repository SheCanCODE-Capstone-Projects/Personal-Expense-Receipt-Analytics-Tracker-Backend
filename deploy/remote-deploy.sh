#!/bin/sh
# Runs on the staging/production server from DEPLOY_PATH (next to .env, compose.deploy.yml and Caddyfile).
#   ./remote-deploy.sh <image-tag>     deploy a release, e.g. sha-1a2b3c4
#   ./remote-deploy.sh rollback        redeploy the release that was live before the current one
set -eu
cd "$(dirname "$0")"

[ -f .env ] || { echo "Missing .env: copy env.<environment>.example to .env and fill it in" >&2; exit 1; }

if [ "${1:-}" = "rollback" ]; then
  [ -f .previous-tag ] || { echo "No previous release recorded" >&2; exit 1; }
  TAG=$(cat .previous-tag)
else
  TAG=${1:?usage: remote-deploy.sh <image-tag>|rollback}
fi

compose() { IMAGE_TAG="$TAG" docker compose --env-file .env -f compose.deploy.yml "$@"; }

echo "==> Pulling $TAG"
compose pull app
echo "==> Starting $TAG"
compose up -d --wait --wait-timeout 180

# Record the release history only after the new version reports healthy.
if [ -f .current-tag ] && [ "$(cat .current-tag)" != "$TAG" ]; then
  cp .current-tag .previous-tag
fi
echo "$TAG" > .current-tag
docker image prune -f >/dev/null
echo "==> $TAG is live"
