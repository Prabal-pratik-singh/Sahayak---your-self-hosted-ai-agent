# Sahayak — your self-hosted AI agent

Tell it what you want in plain language, and it does the work: chats like a normal AI assistant, **sends email from your own mailbox**, **posts on your own LinkedIn**, and **runs tasks at a scheduled time** ("post this tomorrow at 6 PM").

Multi-user by design: anyone you share your server with creates their **own account** and connects their **own** email and LinkedIn. No third-party middleman (no Composio) — the server talks to Gmail/LinkedIn/etc. directly.

**Pick your AI brain:** works with **Claude (Anthropic)**, **ChatGPT (OpenAI)**, and **Gemini (Google)**. Configure any one — or several, and every user gets a dropdown in the chat to choose who answers.

**Stack:** Spring Boot 3.5 · Spring AI 1.1 · Claude / ChatGPT / Gemini · PostgreSQL · React (Vite)

---

## How it works

```
 React app (5173)  ──login token──▶  Spring Boot API (8080)
                                          │
                                     AgentService ──── Claude / ChatGPT / Gemini
                                          │            (whichever the user picked)
                                          │                   │
                                          │        decides which tools to call
                                          │                   │
                     ┌────────────────────┼───────────────────┘
                     │                    │
              SchedulerTools        EmailTools / LinkedInTools
                     │              (bound to YOUR connected accounts,
             scheduled_tasks         credentials encrypted in Postgres)
              (in Postgres)
                     ▲
        TaskRunner polls every 30s and executes due tasks as their owner,
        on the same AI provider the task was scheduled with
```

One brain, two modes:

- **Ask something** → Claude just answers (normal AI chat, with memory stored in Postgres).
- **Order something** → Claude picks a tool. Now = does it immediately. Later = calls `scheduleTask`, and `TaskRunner` executes it when the time comes. Scheduled tasks survive restarts and show up on the **dispatch board** in the UI.

Every tool is created per request and locked to the logged-in user, so one user's agent can never touch another user's tasks, mailbox, or LinkedIn.

---

## Run it — option A: everything in Docker (easiest)

You only need **Docker** installed.

```bash
# 1. Create your config
cp .env.example .env
#    → open .env and fill in at least one AI key (ANTHROPIC_API_KEY,
#      OPENAI_API_KEY or GEMINI_API_KEY), and ideally APP_SECRET

# 2. Build and start everything
docker compose --profile app up -d --build
```

Open **http://localhost:5173**, create your account, and start chatting.
First build takes a few minutes; after that it starts in seconds.

## Run it — option B: for development

You need **JDK 21+**, **Node 18+**, and **Docker** (for the database only). Maven is NOT needed — the project ships with the Maven Wrapper (`mvnw`).

```bash
# 1. Start Postgres
docker compose up -d

# 2. Start the backend (set the key(s) of whichever AI you use)
#    Windows PowerShell:
$env:ANTHROPIC_API_KEY = "sk-ant-..."     # and/or OPENAI_API_KEY / GEMINI_API_KEY
cd backend
.\mvnw spring-boot:run

#    Linux / macOS:
export ANTHROPIC_API_KEY=sk-ant-...
cd backend && ./mvnw spring-boot:run

# 3. Start the frontend (new terminal)
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

---

## First steps in the app

1. **Create account** — the first account on a fresh server is always allowed; set `APP_ALLOW_SIGNUPS=false` later to lock the door behind you.
2. Chat normally. Try: *"Remind yourself to say hello in 2 minutes"* → watch the dispatch board.
   If the server has more than one AI configured, a dropdown next to the message box picks who answers (Claude / ChatGPT / Gemini). All brains share the same chat memory, so you can switch mid-conversation. A scheduled task later runs on the same brain it was created with.
3. Open **Connections** (top right) to give the agent real powers:

### Connect your email (works with any provider)

The agent sends mail through your own account using SMTP (the standard "outgoing mail" protocol — the same thing your mail app uses).

**Gmail:**
1. Turn on **2-Step Verification**: https://myaccount.google.com/security
2. Create an **App Password**: https://myaccount.google.com/apppasswords (a 16-character password Google issues specifically for apps like this)
3. In Sahayak → Connections → *Connect email*: host `smtp.gmail.com`, port `587`, your Gmail address, and the App Password.

**Other providers:** Outlook = `smtp-mail.outlook.com:587` · Zoho = `smtp.zoho.com:587` · anything else: search "<provider> SMTP settings". Sahayak tests the login before saving, so wrong credentials fail immediately, not at send time.

Then try: *"Send an email to myself saying the agent is alive."*

### Connect LinkedIn

LinkedIn requires the **server owner** to register one (free) LinkedIn app — after that, every user of your Sahayak just clicks "Connect LinkedIn" and logs into their own profile.

One-time setup (server owner):
1. Go to https://developer.linkedin.com → **Create app** (it asks for a company page; a basic one you admin is fine).
2. In the app's **Products** tab, add **"Sign In with LinkedIn using OpenID Connect"** and **"Share on LinkedIn"**.
3. In the **Auth** tab, add this **Authorized redirect URL**:
   `http://localhost:8080/api/integrations/linkedin/callback`
   (replace host with your real backend address when deployed — same value as `APP_BASE_URL`)
4. Copy the **Client ID** and **Client Secret** into your environment (`.env` for Docker, or exported variables) and restart the backend.

Each user then: Connections → **Connect LinkedIn** → LinkedIn login screen → done. Try: *"Draft a LinkedIn post about my new project — professional but energetic."*, then *"Post it tomorrow at 6 PM."*

> Note: LinkedIn access tokens last ~60 days and can't be auto-renewed on standard apps. When one expires, the connection shows **EXPIRED** and reconnecting takes two clicks.

---

## Settings (environment variables)

| Variable | Required | Default | What it does |
| --- | --- | --- | --- |
| `ANTHROPIC_API_KEY` | one of the three | — | Claude API key (https://console.anthropic.com) |
| `OPENAI_API_KEY` | one of the three | — | ChatGPT API key (https://platform.openai.com) |
| `GEMINI_API_KEY` | one of the three | — | Gemini API key (https://aistudio.google.com/apikey) |
| `APP_DEFAULT_AI` | no | first configured | Which provider answers by default: `anthropic` / `openai` / `gemini` |
| `APP_SECRET` | recommended | auto-generated to `<home>/.sahayak/app-secret.txt` | Long random string; protects logins and encrypts stored credentials |
| `ANTHROPIC_MODEL` | no | `claude-sonnet-5` | Which Claude model to use |
| `OPENAI_MODEL` | no | `gpt-5-mini` | Which OpenAI model to use |
| `GEMINI_MODEL` | no | `gemini-2.5-flash` | Which Gemini model to use |
| `ANTHROPIC_MAX_TOKENS` | no | `4096` | Max length of one Claude reply |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | no | localhost Postgres | Database connection |
| `APP_ALLOW_SIGNUPS` | no | `true` | `false` = no new accounts (first account always allowed) |
| `APP_CHAT_RATE_LIMIT` | no | `30` | Max chat messages per user per minute |
| `LINKEDIN_CLIENT_ID` / `LINKEDIN_CLIENT_SECRET` | for LinkedIn | — | From your LinkedIn developer app |
| `APP_FRONTEND_URL` | when deployed | `http://localhost:5173` | Browser app address (redirects + CORS) |
| `APP_BASE_URL` | when deployed | `http://localhost:8080` | Public backend address (LinkedIn redirect) |
| `TZ` (Docker) | no | `UTC` | Server timezone, so "tomorrow 6 PM" is your 6 PM |

---

## Safety rails (already built in)

- **Login required** for everything except register/login/health; sessions are 30-day tokens, stored hashed.
- **Per-user isolation**: tools are bound to the logged-in user server-side; prompts can't cross accounts.
- **Encrypted credentials**: SMTP passwords and LinkedIn tokens are AES-encrypted in the database.
- **Confirmation rule**: the system prompt makes the agent show the exact content and ask you to confirm before posting publicly or sending email (skipped for already-approved scheduled tasks). This is a strong instruction, not a hard technical block — keep the habit of reading before saying yes.
- **Honesty rule**: never claim success unless a tool call actually succeeded.
- **Rate limit** on chat (default 30/min/user), so a runaway loop can't burn your API credit.
- Scheduled tasks can be cancelled from the UI or by asking the agent; tasks interrupted by a crash/restart are marked FAILED instead of silently disappearing.

---

## Project structure

```
backend/
  src/main/java/com/sahayak/
    SahayakApplication.java       # boot + @EnableScheduling
    agent/
      Prompts.java                # system prompt (date-time + user's connections)
      AiConfig.java               # ChatClient + Postgres-backed chat memory
      AgentService.java           # runs one turn with the user's own tools
      ChatController.java         # POST /api/chat (validated + rate-limited)
    auth/                         # accounts: User, tokens, login/register, security config
    integrations/
      Connection*.java            # per-user connected apps, credentials encrypted
      email/                      # SMTP: settings, sender, LLM tool
      linkedin/                   # LinkedIn: OAuth flow, API calls, LLM tool
    tasks/                        # scheduler: entity, atomic claiming, runner, API
    common/                       # app secret, crypto, errors, rate limit, health
  src/test/java/...               # unit tests (crypto, scheduler, auth)
frontend/
  src/App.jsx                     # session + layout
  src/api.js                      # fetch wrapper with auth token
  src/components/                 # Login, Chat, DispatchBoard, Connections
docker-compose.yml                # Postgres (dev) or full stack (--profile app)
.env.example                      # config template for Docker
```

## API

All endpoints except the first three need an `Authorization: Bearer <token>` header.

| Method | Path | Body | Purpose |
| --- | --- | --- | --- |
| POST | /api/auth/register | `{name, email, password}` | Create account → `{token, user}` |
| POST | /api/auth/login | `{email, password}` | Log in → `{token, user}` |
| GET | /api/health | — | Is the server up |
| GET | /api/auth/me | — | Who am I |
| POST | /api/auth/logout | — | Revoke this token |
| POST | /api/chat | `{message, conversationId, provider?}` | Talk to the agent (`provider`: anthropic/openai/gemini) |
| GET | /api/models | — | Configured AI providers + default |
| GET | /api/tasks | — | My scheduled tasks |
| DELETE | /api/tasks/{id} | — | Cancel a pending task |
| GET | /api/connections | — | My connected apps |
| POST | /api/connections/email | `{host, port, username, password, fromAddress?}` | Connect email (verifies login) |
| DELETE | /api/connections/{id} | — | Remove a connection |
| GET | /api/integrations/linkedin/authorize | — | Get LinkedIn consent URL |

Errors always come back as `{ "error": "readable message" }`.

## Tests

```bash
cd backend
.\mvnw test        # Windows      (./mvnw test on Linux/macOS)
```

---

## Adding a new integration (for developers)

Each integration is three small pieces — copy the `email/` package as a template:

1. A **settings/credentials record** stored via `ConnectionService` (add a value to `Connection.Type`).
2. A **service** that talks to the external API.
3. A **tools class** with `@Tool` methods, created per request in `AgentService.run(...)` so it's bound to the current user.

Add a connect button/form in `frontend/src/components/Connections.jsx`, and a line in `ConnectionService.promptSummary(...)` so the model knows the tool exists.

---

## Troubleshooting

- **Startup fails: "No AI provider is configured"** → set at least one of `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY` in the same terminal (or `.env` for Docker) and start again.
- **Chat says "The AI service returned an error"** → wrong/expired API key, or no credit, on the provider you selected in the dropdown.
- **`Connection refused: localhost:5432`** → Postgres isn't up: `docker compose up -d`.
- **"Could not log in to that mailbox"** → for Gmail you need an App Password (normal password won't work); check host/port for other providers.
- **LinkedIn button says "not configured"** → set `LINKEDIN_CLIENT_ID`/`LINKEDIN_CLIENT_SECRET` and restart the backend.
- **LinkedIn connect loops back with an error** → the redirect URL in your LinkedIn app must EXACTLY match `APP_BASE_URL + /api/integrations/linkedin/callback`.
- **Frontend can't reach backend (dev)** → backend must be on 8080; Vite proxies `/api` there (see `frontend/vite.config.js`).
- **Scheduled task ran at a strange hour (Docker)** → set `TZ=Asia/Kolkata` (your zone) in `.env` and restart.
- **Changed `APP_SECRET` and connections stopped working** → expected: stored credentials can only be decrypted with the secret that encrypted them. Reconnect your apps.

---

## Where to take it next

1. **Streaming replies** — `chatClient.prompt().stream()` + SSE endpoint, typing effect in React.
2. **More integrations** — Telegram/Slack/webhooks fit the same 3-piece pattern.
3. **Recurring tasks** — add a cron column + next-run computation ("every Monday 9 AM").
4. **Human-in-the-loop UI** — approve/reject buttons for risky actions instead of chat confirmation.
5. **Conversation history UI** — chats are already stored per user in Postgres; add a sidebar to reopen them.
6. **Database migrations** — swap `ddl-auto: update` for Flyway before serious multi-user use.
7. **HTTPS deploy** — put both containers behind Caddy/nginx with a real domain and TLS.
