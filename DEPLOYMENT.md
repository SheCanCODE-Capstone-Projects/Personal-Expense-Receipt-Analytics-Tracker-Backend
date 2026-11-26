# Deployment Guide

For how to call the API once it is running, see [API.md](API.md).

The API moves through three environments:

| Environment | Where it runs | How it is deployed | Spring profile |
|---|---|---|---|
| **Development** | Your machine | `docker compose -f compose.dev.yml up --build` | `dev` |
| **Staging** | A Linux server | Automatically, on every push to `main` that passes tests | `staging` |
| **Production** | A second Linux server | Same image as staging, after a reviewer approves it in GitHub | `prod` |

```
push to main ─▶ test ─▶ build image sha-abc1234 ─▶ deploy staging ─▶ [approve] ─▶ deploy production
                 │            (pushed to ghcr.io)        │ smoke test            │ smoke test
                 └─ a failure stops the pipeline ────────┴─ a failure stops promotion
```

Each image is built once and tagged with its commit (`sha-abc1234`). Production runs the exact image that passed staging.

---

## 1. Development (local)

**Requirements:** Docker with Compose v2.

```bash
docker compose -f compose.dev.yml up --build
```

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | API docs |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8025 | Mailpit inbox: every sign-in code email arrives here |

The stack seeds the demo users `amina@demo.local`, `jean@demo.local` and `eric@demo.local`. To sign in, request a code in Swagger and read it in Mailpit.

To reset everything, including the database:
```bash
docker compose -f compose.dev.yml down -v
```

To run without Docker, start a local Postgres and run `sh mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. The defaults are listed in `.env.example`.

To run from the IDE or `mvnw`, copy `.env.example` to `.env`, fill it in, and check it:
```bash
sh deploy/check-env.sh development .env
```
`DB_URL` can point at a local Postgres or a hosted one such as Railway (see [Using Railway PostgreSQL](#using-railway-postgresql)).

### Tests

The tests use a separate database, `ExpenseTrackerSystem_test`, which is rebuilt on every run. Create it once:
```bash
createdb -U postgres ExpenseTrackerSystem_test
sh mvnw test
```

---

## 2. One-time GitHub setup

1. **Allow Actions to publish images.** In **Settings → Actions → General → Workflow permissions**, choose *Read and write permissions*.
2. **Create two environments** under **Settings → Environments**: `staging` and `production`.
3. **Protect production.** Under `production` → *Required reviewers*, add the people who approve releases. You can also limit *Deployment branches* to `main`.
4. **Add these to *each* environment** (staging values in `staging`, production values in `production`):

   | Kind | Name | Value |
   |---|---|---|
   | Secret | `DEPLOY_HOST` | Server hostname or IP |
   | Secret | `DEPLOY_USER` | SSH user on the server, e.g. `deploy` |
   | Secret | `DEPLOY_SSH_KEY` | That user's **private** key (see 3.3) |
   | Secret | `DEPLOY_KNOWN_HOSTS` | Output of `ssh-keyscan <host>`, so the server's identity is verified |
   | Variable | `DEPLOY_PATH` | `/opt/expense-tracker` |
   | Variable | `APP_URL` | `https://staging-api.example.com`, or the production URL |

Images are published to `ghcr.io/shecancode-capstone-projects/personal-expense-receipt-analytics-tracker-backend`.

---

## 3. One-time server setup (repeat for staging and for production)

Any Ubuntu 22.04/24.04 VM with 1 vCPU and 2 GB RAM is enough. Use one server per environment.

**3.1 Point DNS at the server.** Create an `A` record, e.g. `staging-api.example.com → <server IP>`. Caddy needs it to issue the HTTPS certificate.

**3.2 Install Docker and open ports 80/443:**
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

**3.3 Create the deploy user and key:**
```bash
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy
sudo mkdir -p /opt/expense-tracker && sudo chown deploy:deploy /opt/expense-tracker

# On your laptop: create a key used only for deployment
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions"
# Put deploy_key.pub into /home/deploy/.ssh/authorized_keys on the server.
# Paste deploy_key (the private key) into the DEPLOY_SSH_KEY secret, then delete it locally.
ssh-keyscan <server-host>       # paste the output into DEPLOY_KNOWN_HOSTS
```

**3.4 Create the environment file.** Copy `deploy/env.staging.example` (or `deploy/env.production.example`) to `/opt/expense-tracker/.env` on the server and fill in **every** value:
```bash
sudo -u deploy nano /opt/expense-tracker/.env
chmod 600 /opt/expense-tracker/.env
```

| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` or `prod` |
| `IMAGE` | Leave as given |
| `DOMAIN` | The DNS name from 3.1 |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Generate the password with `openssl rand -base64 32` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | A real mail provider, e.g. Brevo, Mailgun, SES, or a Gmail app password |
| `CORS_ALLOWED_ORIGINS` | The deployed frontend URL(s). Production refuses `localhost` and `*` |
| `JWT_SECRET` | `openssl rand -base64 48`, with a different value per environment. Changing it signs everyone out |
| `AUTH_COOKIE_SAME_SITE` | Optional. Leave `Strict` when the frontend and API share a site (`app.example.com` + `api.example.com`); use `None` when they don't |

**3.5 Check the file before the first deploy:**
```bash
cd /opt/expense-tracker && sh check-env.sh staging .env      # or: production
```
`remote-deploy.sh` runs the same check on every release. If anything is wrong, it lists **every** problem and stops before the running version is touched.

---

## Environment requirements (checked before every deploy)

`deploy/check-env.sh` enforces this table. ✓ = required, — = not used.

| Setting | Development | Staging | Production |
|---|---|---|---|
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | ✓ | ✓ | ✓ |
| Database: `DB_URL` + `DB_USERNAME` + `DB_PASSWORD` (external, e.g. Railway) | optional (local default) | ✓ one of the two | ✓ one of the two |
| Database: `POSTGRES_DB` + `POSTGRES_USER` + `POSTGRES_PASSWORD` (bundled container) | — | ✓ one of the two | ✓ one of the two |
| `SPRING_PROFILES_ACTIVE` | — (`dev`/`demo` optional) | `staging` | `prod` |
| `IMAGE`, `DOMAIN` | — | ✓ | ✓ |
| `JWT_SECRET` (≥ 32 characters, not the dev default) | optional | ✓ | ✓ |
| `CORS_ALLOWED_ORIGINS` | optional | ✓ | ✓ `https://` only, no `localhost` or `*` |
| `AUTH_COOKIE_SECURE` | optional | not `false` | not `false` |
| `AUTH_COOKIE_SAME_SITE` | optional | `Strict`/`Lax`/`None` | `Strict`/`Lax`/`None` |
| `APP_DEMO_SEED`, `APP_AUTH_LOG_CODES` | optional | optional | must not be `true` |

Rules for every environment:
- **Placeholders:** example values such as `change-me` or `example.com` count as missing.
- **`DB_URL`:** must be a JDBC URL (`jdbc:postgresql://…`). A remote database without `sslmode=require` gets a warning.

Checks at each layer:
1. **GitHub job:** `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_PATH` and an `https://` `APP_URL` must be set in that GitHub Environment.
2. **Server:** `check-env.sh` checks the server's `.env`, as above.
3. **Startup:** the `staging`/`prod` profiles have no defaults, and `ProductionSafetyCheck` refuses unsafe production settings.

## Running the API on Railway

The Railway app reads its settings from **Service → Variables**. Set:

| Variable | Value |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | The Railway database, in the JDBC form shown below |
| `JWT_SECRET` | **Required.** Output of `openssl rand -base64 48`. Without it, tokens are signed with the development key published in this repository, and the log warns about it at startup |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | The mail account that sends sign-in codes |
| `CORS_ALLOWED_ORIGINS` | The frontend URL(s), e.g. `https://my-frontend.vercel.app`. Swagger on the API's own domain works without being listed |
| `AUTH_COOKIE_SAME_SITE` | `None` if the frontend is on another domain (e.g. Vercel); otherwise leave unset |
| `APP_DEMO_SEED` | `true` once to load the demo data into an empty database |
| `SPRING_PROFILES_ACTIVE` | Optional. `prod` turns on the production safety checks; then also set `SWAGGER_ENABLED=true` to keep Swagger |

Opening the bare URL redirects to Swagger UI. Railway's HTTPS proxy forwards `X-Forwarded-Proto`, and the app uses it, so Swagger calls the API over `https`.

## Using Railway PostgreSQL

Railway shows a URL of the form `postgresql://USER:PASSWORD@HOST:PORT/DATABASE`. Spring needs it split into three settings:
```
DB_URL=jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require
DB_USERNAME=USER
DB_PASSWORD=PASSWORD
```
- **Where to set it:** put these in `.env` locally, or in the server's `.env` for staging/production. When `DB_URL` is set, the bundled `db` container is not started.
- **One database per environment:** create a separate Railway database for development, staging and production, so test data never mixes with real data.
- **Schema:** tables are created on first start.
- **Backups:** with Railway, back up from Railway's dashboard instead of the `pg_dump` commands in §5.

---

## 4. Deploying

### 4.1 Staging (automatic)
1. Merge a pull request into `main`. The `CI` checks must pass on the PR first.
2. The **Deploy** workflow runs the tests, builds `sha-<commit>`, and deploys it to staging.
3. The job ends with a smoke test that requires `APP_URL/actuator/health` to report `UP`.
4. Check staging: sign in through the staging frontend, import `samples/statement-october-2026.csv` twice, and confirm the second import adds nothing.

### 4.2 Production (approval)
1. In **Actions → Deploy**, the run waits at *deploy-production* with **Review deployments**.
2. A required reviewer clicks **Approve and deploy**.
3. The workflow rolls out the same `sha-<commit>` image and runs the smoke test against the production URL.

To redeploy without a new commit, use **Actions → Deploy → Run workflow**.

### 4.3 Rollback
On the affected server:
```bash
cd /opt/expense-tracker
./remote-deploy.sh rollback          # back to the previous release
./remote-deploy.sh sha-1a2b3c4       # or to any earlier tag listed in GitHub Packages
```
`.current-tag` and `.previous-tag` in that directory record what is live. If a release fails its health check, the previous container keeps running and the release history is left unchanged.

---

## 5. Operations

**Logs:**
```bash
cd /opt/expense-tracker && docker compose --env-file .env -f compose.deploy.yml logs -f app
```

**Back up the database.** Run this daily from cron, and copy the file off the server:
```bash
docker compose --env-file .env -f compose.deploy.yml exec -T db \
  sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > backup-$(date +%F).sql.gz
```

**Restore:**
```bash
gunzip -c backup.sql.gz | docker compose --env-file .env -f compose.deploy.yml exec -T db \
  sh -c 'psql -U "$POSTGRES_USER" "$POSTGRES_DB"'
```

**Schema changes.** Hibernate applies additive changes on startup (`ddl-auto=update`). It cannot change a column's type or drop columns. Before a release that does either, back up the database and apply the change by hand, or add a migration tool such as Flyway.
