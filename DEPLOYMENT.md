# Deploying Sahayak to the internet

Sahayak is fully containerized — the same two images run anywhere Docker runs.
This guide covers the recommended path (a small VPS) and what to change for it.

## What you need (one-time)

1. **A server**: any small VPS (Hetzner ~€4/mo, DigitalOcean, Lightsail, Oracle free tier) with Docker installed.
2. **A domain** (optional but recommended): e.g. `sahayak.yourname.dev`, pointed at the server's IP.
3. **Your AI key(s)**: at least one of `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`.
   ⚠ Free-tier Gemini keys allow only a handful of requests per minute — fine for testing,
   frustrating for real use. A paid key (any provider) is strongly recommended for a deployment.

## Steps

```bash
# On the server:
git clone https://github.com/Prabal-pratik-singh/Sahayak---your-self-hosted-ai-agent.git sahayak
cd sahayak
cp .env.example .env
nano .env        # fill in: an AI key, APP_SECRET (long random string), DB_PASSWORD, TZ
```

In `.env`, also set the public addresses (used for CORS + the LinkedIn OAuth redirect):

```
APP_FRONTEND_URL=https://sahayak.yourname.dev
APP_BASE_URL=https://sahayak.yourname.dev
APP_ALLOW_SIGNUPS=true          # set false after you create your account
```

Start everything:

```bash
docker compose --profile app up -d --build
```

The frontend serves on port **5173** and proxies `/api` to the backend internally,
so you only need to expose one port to the world.

## HTTPS (strongly recommended)

Put Caddy in front — it gets TLS certificates automatically:

```bash
# /etc/caddy/Caddyfile
sahayak.yourname.dev {
    reverse_proxy localhost:5173
}
```

With HTTPS in place, browser mic access (voice mode) works from anywhere,
and your login tokens are never sent in plain text.

## After it's live

- Open the site → create YOUR account first → set `APP_ALLOW_SIGNUPS=false` in `.env`
  and `docker compose --profile app up -d` again to lock signups.
- LinkedIn integration: update the redirect URL in your LinkedIn developer app to
  `https://sahayak.yourname.dev/api/integrations/linkedin/callback`.
- Updates: `git pull && docker compose --profile app up -d --build`.
- Backups: the data lives in the `sahayak-pgdata` Docker volume
  (`docker exec sahayak-postgres pg_dump -U agent agentdb > backup.sql`).

## Platform-as-a-service alternatives

Render / Railway / Fly.io all work: deploy `backend/Dockerfile` and `frontend/Dockerfile`
as two services plus a managed Postgres. Set the same environment variables; point the
frontend's nginx `proxy_pass` (frontend/nginx.conf) at the backend service's internal URL
instead of `http://backend:8080`. The single-VPS compose route above is simpler and cheaper.
