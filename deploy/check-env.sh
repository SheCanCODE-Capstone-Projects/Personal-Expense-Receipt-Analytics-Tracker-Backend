#!/bin/sh
# Checks that an environment file has everything the API needs, before anything is started.
#   sh deploy/check-env.sh development [.env]
#   sh deploy/check-env.sh staging     [/opt/expense-tracker/.env]
#   sh deploy/check-env.sh production  [/opt/expense-tracker/.env]
# Lists every problem at once and exits 1 if there are any. Values are never printed.
set -eu

ENVIRONMENT=${1:?usage: check-env.sh development|staging|production [env-file]}
FILE=${2:-.env}
case "$ENVIRONMENT" in
  development|staging|production) ;;
  *) echo "Unknown environment '$ENVIRONMENT': use development, staging or production" >&2; exit 2 ;;
esac
[ -f "$FILE" ] || { echo "✗ $FILE not found. Copy the matching example file and fill it in." >&2; exit 1; }

errors=0
fail() { echo "✗ $1" >&2; errors=$((errors + 1)); }
warn() { echo "! $1" >&2; }

# Read KEY=VALUE lines without executing the file; the last definition wins, as in Docker Compose.
get() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$FILE" | tail -n 1 \
    | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/"
}

need() {
  for key in "$@"; do
    value=$(get "$key")
    if [ -z "$value" ]; then
      fail "$key is missing or empty"
    else
      case "$value" in
        *change-me*|*example.com*|*your-*|you@gmail.com|you@yourdomain.com)
          fail "$key still has the placeholder value from the example file" ;;
      esac
    fi
  done
}

# ---- Email through Brevo: the HTTP API (BREVO_API_KEY) or the SMTP relay (SMTP_*) ----
need SMTP_FROM
BREVO_API_KEY=$(get BREVO_API_KEY)
if [ -n "$BREVO_API_KEY" ]; then
  need BREVO_API_KEY
  case "$BREVO_API_KEY" in
    xkeysib-*) ;;
    xsmtpsib-*) fail "BREVO_API_KEY is an SMTP key (xsmtpsib-…). Use an API key (xkeysib-…), or put the SMTP key in SMTP_PASSWORD instead" ;;
    *) fail "BREVO_API_KEY does not look like a Brevo API key (it starts with xkeysib-)" ;;
  esac
else
  need SMTP_USERNAME SMTP_PASSWORD
  case "$(get SMTP_PORT)" in ''|*[!0-9]*) [ -z "$(get SMTP_PORT)" ] || fail "SMTP_PORT must be a number" ;; esac
  case "$(get SMTP_HOST)" in
    ''|*brevo.com*|*sendinblue.com*)
      case "$(get SMTP_PASSWORD)" in
        ''|xsmtpsib-*) ;;
        xkeysib-*) fail "SMTP_PASSWORD is a Brevo API key (xkeysib-…). SMTP needs an SMTP key (xsmtpsib-…), or set it as BREVO_API_KEY" ;;
        *) warn "SMTP_PASSWORD does not look like a Brevo SMTP key (xsmtpsib-…)" ;;
      esac ;;
    *) warn "SMTP_HOST is not Brevo (smtp-relay.brevo.com)" ;;
  esac
fi

# ---- Database: an external server (DB_URL, e.g. Railway) or, on servers, the bundled container ----
DB_URL=$(get DB_URL)
if [ -n "$DB_URL" ]; then
  need DB_USERNAME DB_PASSWORD
  case "$DB_URL" in
    jdbc:postgresql://*) ;;
    postgresql://*|postgres://*)
      fail "DB_URL must be a JDBC URL: jdbc:postgresql://HOST:PORT/DB, with the user and password in DB_USERNAME / DB_PASSWORD" ;;
    *) fail "DB_URL must start with jdbc:postgresql://" ;;
  esac
  case "$DB_URL" in
    *localhost*|*127.0.0.1*|*sslmode=*) ;;
    *) warn "DB_URL points at a remote server without ?sslmode=require; the password would travel unencrypted" ;;
  esac
elif [ "$ENVIRONMENT" = development ]; then
  warn "DB_URL not set: using the local default jdbc:postgresql://localhost:5432/ExpenseTrackerSystem"
else
  need POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
fi

# ---- Server-only settings ----
if [ "$ENVIRONMENT" != development ]; then
  expected=staging; [ "$ENVIRONMENT" = production ] && expected=prod
  [ "$(get SPRING_PROFILES_ACTIVE)" = "$expected" ] || fail "SPRING_PROFILES_ACTIVE must be '$expected' on $ENVIRONMENT"
  need IMAGE DOMAIN JWT_SECRET CORS_ALLOWED_ORIGINS

  JWT_SECRET=$(get JWT_SECRET)
  if [ -n "$JWT_SECRET" ]; then
    [ "${#JWT_SECRET}" -ge 32 ] || fail "JWT_SECRET must be at least 32 characters (openssl rand -base64 48)"
    case "$JWT_SECRET" in dev-only*) fail "JWT_SECRET is the development default" ;; esac
  fi

  case "$(get AUTH_COOKIE_SAME_SITE)" in
    ''|Strict|Lax|None) ;;
    *) fail "AUTH_COOKIE_SAME_SITE must be Strict, Lax or None" ;;
  esac
  [ "$(get AUTH_COOKIE_SECURE)" != false ] || fail "AUTH_COOKIE_SECURE must not be false on $ENVIRONMENT"
fi

if [ "$ENVIRONMENT" = production ]; then
  CORS=$(get CORS_ALLOWED_ORIGINS)
  case "$CORS" in *localhost*|*127.0.0.1*|'*'|*,'*'*|'*',*) fail "CORS_ALLOWED_ORIGINS must list only the real frontend origin(s), not localhost or *" ;; esac
  case "$CORS" in ''|https://*) ;; *) fail "CORS_ALLOWED_ORIGINS must use https:// in production" ;; esac
  case "$(get DOMAIN)" in *localhost*|*127.0.0.1*) fail "DOMAIN must be the public DNS name" ;; esac
  [ "$(get APP_DEMO_SEED)" != true ] || fail "APP_DEMO_SEED must not be true in production"
  [ "$(get APP_AUTH_LOG_CODES)" != true ] || fail "APP_AUTH_LOG_CODES must not be true in production"
fi

if [ "$errors" -gt 0 ]; then
  echo "$errors problem(s) in $FILE for $ENVIRONMENT. Fix them before deploying." >&2
  exit 1
fi
echo "✓ $FILE is ready for $ENVIRONMENT"
