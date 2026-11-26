#!/bin/sh
# Runs on the staging/production server from DEPLOY_PATH (next to .env, compose.deploy.yml and Caddyfile).
#   ./remote-deploy.sh <image-tag>     deploy a release, e.g. sha-1a2b3c4
#   ./remote-deploy.sh rollback        redeploy the release that was live before the current one
set -eu
cd "$(dirname "$0")"

[ -f .env ] || { echo "Missing .env: copy env.<environment>.example to .env and fill it in" >&2; exit 1; }

# Check every required setting before touching the running release.
case "$(sed -n 's/^SPRING_PROFILES_ACTIVE=//p' .env | tail -n 1)" in
  prod) ENVIRONMENT=production ;;
  staging) ENVIRONMENT=staging ;;
  *) echo "SPRING_PROFILES_ACTIVE in .env must be staging or prod" >&2; exit 1 ;;
esac
echo "==> Checking .env for $ENVIRONMENT"
sh ./check-env.sh "$ENVIRONMENT" .env

# No DB_URL means the database runs in the bundled container.
if [ -z "$(sed -n 's/^DB_URL=//p' .env | tail -n 1)" ]; then
  export COMPOSE_PROFILES=bundled-db
fi

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
