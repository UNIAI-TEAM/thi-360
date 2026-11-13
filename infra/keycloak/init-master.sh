#!/usr/bin/env bash
set -euo pipefail

KCADM="/opt/keycloak/bin/kcadm.sh"
KCADM_CFG="/tmp/kcadm.config"

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_ADMIN_USER="${THI360_KC_ADMIN_USER:-admin}"
KC_ADMIN_PASSWORD="${THI360_KC_ADMIN_PASSWORD:-admin-dev-only}"
PLATFORM_REALM="${THI360_KC_PLATFORM_REALM:-master}"
PLATFORM_ROLE="${THI360_KC_PLATFORM_ROLE:-PLATFORM_ADMIN}"
PLATFORM_CLIENT_ID="${THI360_KC_PLATFORM_CLIENT_ID:-thi360-platform}"
PLATFORM_CLIENT_SECRET="${THI360_KC_PLATFORM_CLIENT_SECRET:-thi360-platform-dev-only}"
DEV_PLATFORM_USER="${THI360_DEV_PLATFORM_USER:-platform}"
DEV_PLATFORM_PASSWORD="${THI360_DEV_PLATFORM_PASSWORD:-platform-dev-only}"

echo "Waiting for Keycloak at ${KC_URL}..."
for attempt in $(seq 1 30); do
  if "$KCADM" config credentials --config "$KCADM_CFG" \
      --server "$KC_URL" \
      --realm master \
      --user "$KC_ADMIN_USER" \
      --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 30 ]; then
    echo "Timed out waiting for Keycloak admin login at ${KC_URL}" >&2
    exit 1
  fi
  sleep 2
done

if ! "$KCADM" get "roles/${PLATFORM_ROLE}" -r "$PLATFORM_REALM" --config "$KCADM_CFG" >/dev/null 2>&1; then
  echo "Creating realm role ${PLATFORM_ROLE} in realm ${PLATFORM_REALM}..."
  "$KCADM" create roles -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -s "name=${PLATFORM_ROLE}" \
    -s "description=Thi 360 platform administrator"
fi

CLIENT_UUID=$("$KCADM" get clients -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
  -q "clientId=${PLATFORM_CLIENT_ID}" --fields id --format csv --noquotes | head -n1 | tr -d '\r')

if [ -z "$CLIENT_UUID" ]; then
  echo "Creating client ${PLATFORM_CLIENT_ID} in realm ${PLATFORM_REALM}..."
  "$KCADM" create clients -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -s "clientId=${PLATFORM_CLIENT_ID}" \
    -s "name=Thi 360 platform auth" \
    -s "enabled=true" \
    -s "protocol=openid-connect" \
    -s "publicClient=false" \
    -s "secret=${PLATFORM_CLIENT_SECRET}" \
    -s "standardFlowEnabled=false" \
    -s "directAccessGrantsEnabled=true" \
    -s "serviceAccountsEnabled=false" \
    -s 'attributes={"access.token.lifespan":"300"}'
  CLIENT_UUID=$("$KCADM" get clients -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -q "clientId=${PLATFORM_CLIENT_ID}" --fields id --format csv --noquotes | head -n1 | tr -d '\r')
else
  echo "Updating client ${PLATFORM_CLIENT_ID} in realm ${PLATFORM_REALM}..."
  "$KCADM" update "clients/${CLIENT_UUID}" -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -s "enabled=true" \
    -s "publicClient=false" \
    -s "secret=${PLATFORM_CLIENT_SECRET}" \
    -s "standardFlowEnabled=false" \
    -s "directAccessGrantsEnabled=true" \
    -s "serviceAccountsEnabled=false" \
    -s 'attributes={"access.token.lifespan":"300"}'
fi

AUDIENCE_MAPPER_NAME="${PLATFORM_CLIENT_ID}-audience"
if ! "$KCADM" get "clients/${CLIENT_UUID}/protocol-mappers/models" -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    --fields name --format csv --noquotes | tr -d '\r' | grep -qx "$AUDIENCE_MAPPER_NAME"; then
  echo "Adding audience mapper ${AUDIENCE_MAPPER_NAME} to client ${PLATFORM_CLIENT_ID}..."
  "$KCADM" create "clients/${CLIENT_UUID}/protocol-mappers/models" -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -s "name=${AUDIENCE_MAPPER_NAME}" \
    -s "protocol=openid-connect" \
    -s "protocolMapper=oidc-audience-mapper" \
    -s "consentRequired=false" \
    -s "config={\"included.client.audience\":\"${PLATFORM_CLIENT_ID}\",\"id.token.claim\":\"false\",\"access.token.claim\":\"true\"}"
fi
USER_ID=$("$KCADM" get users -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
  -q "username=${DEV_PLATFORM_USER}" -q "exact=true" --fields id --format csv --noquotes | head -n1 | tr -d '\r')

if [ -z "$USER_ID" ]; then
  echo "Creating dev user ${DEV_PLATFORM_USER} in realm ${PLATFORM_REALM}..."
  "$KCADM" create users -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -s "username=${DEV_PLATFORM_USER}" \
    -s "enabled=true" \
    -s "emailVerified=true" \
    -s "firstName=Platform" \
    -s "lastName=Admin"
  USER_ID=$("$KCADM" get users -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    -q "username=${DEV_PLATFORM_USER}" -q "exact=true" --fields id --format csv --noquotes | head -n1 | tr -d '\r')
fi

"$KCADM" set-password -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
  --userid "$USER_ID" --new-password "$DEV_PLATFORM_PASSWORD"

if ! "$KCADM" get "users/${USER_ID}/role-mappings/realm" -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    --fields name --format csv --noquotes | tr -d '\r' | grep -qx "$PLATFORM_ROLE"; then
  "$KCADM" add-roles -r "$PLATFORM_REALM" --config "$KCADM_CFG" \
    --uid "$USER_ID" --rolename "$PLATFORM_ROLE"
fi

echo "Master realm initialization complete."
