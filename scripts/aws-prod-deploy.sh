#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/aivle/app}"
ENV_FILE="$APP_DIR/.env.production"
COMPOSE_FILE="$APP_DIR/compose.prod.yaml"

ROLLBACK_IMAGES="$APP_DIR/rollback-images.env"
ROLLBACK_COMPOSE="$APP_DIR/rollback-compose.prod.yaml"
ROLLBACK_SCRIPT="$APP_DIR/rollback-last-deploy.sh"

TWIN_MANIFEST="${TWIN_MANIFEST:-/opt/aivle/private/twin-bank/twin_bank_manifest.json}"

ECR_REGISTRY="${ECR_REGISTRY:-663345616799.dkr.ecr.us-east-1.amazonaws.com}"
REPOSITORY="${GITHUB_REPOSITORY:-junwoooooooo/aivle_big_project}"

DEPLOY_SHA="${1:-}"

log() {
  printf '[deploy] %s\n' "$*"
}

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: deployment must run as root." >&2
  exit 2
fi

if [[ ! "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "ERROR: expected a full 40-character git SHA." >&2
  exit 2
fi

[[ -s "$ENV_FILE" ]] || {
  echo "ERROR: missing $ENV_FILE" >&2
  exit 1
}

[[ -s "$COMPOSE_FILE" ]] || {
  echo "ERROR: missing $COMPOSE_FILE" >&2
  exit 1
}

[[ -s "$TWIN_MANIFEST" ]] || {
  echo "ERROR: Twin Bank host mount source is missing." >&2
  exit 1
}

RAW_BASE="https://raw.githubusercontent.com/${REPOSITORY}/${DEPLOY_SHA}"

AI_IMAGE="${ECR_REGISTRY}/aivle-bp/ai:${DEPLOY_SHA}"
BACKEND_IMAGE="${ECR_REGISTRY}/aivle-bp/backend:${DEPLOY_SHA}"
FRONTEND_IMAGE="${ECR_REGISTRY}/aivle-bp/frontend:${DEPLOY_SHA}"

TMP_COMPOSE="$(mktemp)"
TMP_ROLLBACK="$(mktemp)"
TMP_IMAGES="$(mktemp)"

cleanup() {
  rm -f "$TMP_COMPOSE" "$TMP_ROLLBACK" "$TMP_IMAGES"
}
trap cleanup EXIT

set_env_value() {
  local key="$1"
  local value="$2"

  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

wait_healthy() {
  local service="$1"
  local timeout="${2:-300}"
  local deadline=$((SECONDS + timeout))
  local cid=""
  local state=""

  while (( SECONDS < deadline )); do
    cid="$(docker compose \
      --env-file "$ENV_FILE" \
      -f "$COMPOSE_FILE" \
      ps -q "$service" 2>/dev/null || true)"

    if [[ -n "$cid" ]]; then
      state="$(docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$cid" 2>/dev/null || true)"

      case "$state" in
        healthy|running)
          log "$service is $state"
          return 0
          ;;
        unhealthy|exited|dead)
          log "$service entered $state"
          return 1
          ;;
      esac
    fi

    sleep 5
  done

  log "$service health timeout"
  return 1
}

rollback_on_error() {
  local status=$?

  trap - ERR

  log "deployment failed; starting automatic rollback"

  if [[ -x "$ROLLBACK_SCRIPT" ]]; then
    "$ROLLBACK_SCRIPT" || {
      log "automatic rollback script failed"

      if [[ -x "$APP_DIR/rollback-known-good.sh" ]]; then
        log "trying emergency known-good rollback"
        "$APP_DIR/rollback-known-good.sh" || true
      fi
    }
  fi

  exit "$status"
}

log "snapshotting current production state"

grep -E '^(AI_IMAGE|BACKEND_IMAGE|FRONTEND_IMAGE)=' \
  "$ENV_FILE" > "$TMP_IMAGES"

for key in AI_IMAGE BACKEND_IMAGE FRONTEND_IMAGE; do
  count="$(grep -c "^${key}=" "$TMP_IMAGES" || true)"

  if [[ "$count" -ne 1 ]]; then
    echo "ERROR: expected exactly one ${key} in production env." >&2
    exit 1
  fi
done

install \
  -o root \
  -g root \
  -m 0600 \
  "$TMP_IMAGES" \
  "$ROLLBACK_IMAGES"

cp -a "$COMPOSE_FILE" "$ROLLBACK_COMPOSE"

log "preparing rollback script"

curl -fsSL \
  "${RAW_BASE}/scripts/aws-prod-rollback.sh" \
  -o "$TMP_ROLLBACK"

bash -n "$TMP_ROLLBACK"

install \
  -o root \
  -g root \
  -m 0700 \
  "$TMP_ROLLBACK" \
  "$ROLLBACK_SCRIPT"

log "validating new production compose"

curl -fsSL \
  "${RAW_BASE}/compose.prod.yaml" \
  -o "$TMP_COMPOSE"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$TMP_COMPOSE" \
  config --quiet

trap rollback_on_error ERR

install \
  -o root \
  -g root \
  -m 0644 \
  "$TMP_COMPOSE" \
  "$COMPOSE_FILE"

set_env_value AI_IMAGE "$AI_IMAGE"
set_env_value BACKEND_IMAGE "$BACKEND_IMAGE"
set_env_value FRONTEND_IMAGE "$FRONTEND_IMAGE"

chmod 600 "$ENV_FILE"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  config --quiet

log "pulling SHA-pinned images"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  pull ai-server backend frontend

for service in ai-server backend frontend; do
  log "deploying $service"

  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    up -d --no-deps --force-recreate "$service"

  wait_healthy "$service"
done

log "checking local frontend health"

curl -fsS \
  --retry 12 \
  --retry-delay 3 \
  http://127.0.0.1/healthz >/dev/null

printf '%s\n' "$DEPLOY_SHA" > "$APP_DIR/deployed-sha"
chmod 644 "$APP_DIR/deployed-sha"

trap - ERR

log "deployment completed: $DEPLOY_SHA"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  ps