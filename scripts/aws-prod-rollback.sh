#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/aivle/app}"
ENV_FILE="$APP_DIR/.env.production"
COMPOSE_FILE="$APP_DIR/compose.prod.yaml"
ROLLBACK_IMAGES="$APP_DIR/rollback-images.env"
ROLLBACK_COMPOSE="$APP_DIR/rollback-compose.prod.yaml"

AWS_REGION="${AWS_REGION:-us-east-1}"
ECR_REGISTRY="${ECR_REGISTRY:-663345616799.dkr.ecr.us-east-1.amazonaws.com}"

log() {
  printf '[rollback] %s\n' "$*"
}

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: rollback must run as root." >&2
  exit 2
fi

[[ -s "$ENV_FILE" ]] || {
  echo "ERROR: missing $ENV_FILE" >&2
  exit 1
}

[[ -s "$ROLLBACK_IMAGES" ]] || {
  echo "ERROR: missing $ROLLBACK_IMAGES" >&2
  exit 1
}

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
  local timeout="${2:-240}"
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

unset AI_IMAGE BACKEND_IMAGE FRONTEND_IMAGE

while IFS='=' read -r key value; do
  case "$key" in
    AI_IMAGE|BACKEND_IMAGE|FRONTEND_IMAGE)
      printf -v "$key" '%s' "$value"
      ;;
  esac
done < "$ROLLBACK_IMAGES"

: "${AI_IMAGE:?rollback AI_IMAGE missing}"
: "${BACKEND_IMAGE:?rollback BACKEND_IMAGE missing}"
: "${FRONTEND_IMAGE:?rollback FRONTEND_IMAGE missing}"

log "restoring previous compose and image references"

if [[ -s "$ROLLBACK_COMPOSE" ]]; then
  cp -a "$ROLLBACK_COMPOSE" "$COMPOSE_FILE"
fi

set_env_value AI_IMAGE "$AI_IMAGE"
set_env_value BACKEND_IMAGE "$BACKEND_IMAGE"
set_env_value FRONTEND_IMAGE "$FRONTEND_IMAGE"

chmod 600 "$ENV_FILE"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  config --quiet

log "refreshing EC2 host ECR authentication"

if aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin "$ECR_REGISTRY"; then
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    pull ai-server backend frontend || \
    log "image pull failed; trying locally cached images"
else
  log "ECR login failed; trying locally cached images"
fi

for service in ai-server backend frontend; do
  log "restoring $service"

  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    up -d --no-deps --force-recreate "$service"

  wait_healthy "$service"
done

curl -fsS \
  --retry 10 \
  --retry-delay 2 \
  http://127.0.0.1/healthz >/dev/null

log "rollback completed"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  ps
