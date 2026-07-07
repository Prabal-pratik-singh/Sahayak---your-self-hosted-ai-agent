# Sahayak — your self-hosted AI agent

Talk to it — by **voice or text** — and it does the work: answers anything, **checks live weather**, **reads web pages**, **remembers things about you**, **sends email from your own mailbox**, **posts on your own LinkedIn**, and **runs tasks at a scheduled time** ("post this tomorrow at 6 PM").

Multi-user by design: anyone you share your server with creates their **own account** and connects their **own** email and LinkedIn. No third-party middleman (no Composio) — the server talks to the real APIs directly.

**Pick your AI brain — 8 engines, most with free tiers.** The server owner can configure any subset, and **every user can also bring their own API key** (Settings → AI engine keys: verified on save, stored encrypted). Free keys for Gemini, Groq, GitHub Models, Cerebras, Mistral and OpenRouter mean a public server can cost the owner nothing — a server can even run with zero keys, BYOK-only.

| Engine | Free tier? | Can take actions? | Env var (server key) |
| --- | --- | --- | --- |
| Groq | ✅ generous | ⚡ yes | `GROQ_API_KEY` |
| Gemini (Google) | ✅ small | ⚡ yes | `GEMINI_API_KEY` |
| GitHub Models | ✅ any GitHub account | ⚡ yes | `GITHUB_MODELS_KEY` |
| Cerebras | ✅ | ⚡ yes | `CEREBRAS_API_KEY` |
| Mistral | ✅ | ⚡ yes | `MISTRAL_API_KEY` |
| OpenRouter | ✅ free models | 💬 chat only | `OPENROUTER_API_KEY` |
| Claude (Anthropic) | paid | ⚡ yes | `ANTHROPIC_API_KEY` |
| ChatGPT (OpenAI) | paid | ⚡ yes | `OPENAI_API_KEY` |

**"Actions" vs "chat only", in plain words:** an *actions* engine can really DO things — set reminders, check live weather, send your email, post for you. A *chat-only* engine can talk but not act (its free models can't use tools) — Sahayak labels this clearly in the dropdown and the AI itself will tell you to switch engines instead of pretending.

**Stack:** Spring Boot 3.5 · Spring AI 1.1 · Claude / ChatGPT / Gemini · PostgreSQL · React (Vite)

## What the agent can do

| Ability | How | Needs setup? |
| --- | --- | --- |
| Chat with **streaming replies** | any configured AI provider, token by token | just an API key |
| **Conversation history** | saved per user: sidebar, pin, rename-by-first-message, full-text search (Ctrl+K) | no |
| Talk & listen (voice) | push-to-talk in chat + hands-free **Voice mode with a wake word** | no — use Chrome/Edge |
| Live weather anywhere | built-in weather tool (Open-Meteo, no key) | no |
| Facts & background | built-in Wikipedia search tool | no |
| Read a web page you give it | built-in guarded page fetcher (private/internal addresses blocked) | no |
| Remember things long-term | "remember that ..." → saved notes, used in every future chat | no |
| Schedule anything for later | task board + calendar view + 30s runner | no |
| Send email as you | your SMTP account | Integrations page |
| Post on your LinkedIn | your LinkedIn via OAuth | Integrations page + LinkedIn app |
| Message on **Telegram** | your own free bot (@BotFather) | Integrations page |
| Post to **Discord / Slack** | channel incoming webhooks | Integrations page |

Honestly **not** supported (platform API restrictions, shown as-is in the app): WhatsApp personal chats, personal Instagram, X/Twitter (paid API), personal Facebook. The Integrations page explains each.

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
2. Chat normally. Try:
   - *"What's the weather in Delhi right now?"* → it uses its live weather tool.
   - *"Remember that I prefer short replies"* → saved as a note, applied in every future chat.
   - *"Remind yourself to say hello in 2 minutes"* → watch the dispatch board.
3. **Voice**: tap the **mic** button, speak, and it sends automatically when you pause. Turn on the **speaker** button to have replies read out loud. Uses the browser's built-in speech engine — free, no keys; listening needs Chrome or Edge (the mic button hides itself elsewhere), speaking works in all major browsers.
4. If the server has more than one AI configured, a dropdown next to the message box picks who answers (Claude / ChatGPT / Gemini). All brains share the same chat memory, so you can switch mid-conversation. A scheduled task later runs on the same brain it was created with.
5. Open **Connections** (top right) to give the agent real powers:

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

### Connect GitHub

Same shape as LinkedIn — the **server owner** registers one free GitHub OAuth app, then every user clicks "Connect GitHub." The agent can then create issues, list your repos, and search issues/PRs as you.

One-time setup (server owner):
1. Go to https://github.com/settings/developers → **OAuth Apps** → **New OAuth App**.
2. Fill in any **Application name** and **Homepage URL**, and set the **Authorization callback URL** to exactly:
   `http://localhost:8080/api/integrations/github/callback`
   (replace host with your real backend address when deployed — same value as `APP_BASE_URL`)
3. Create it, then **Generate a new client secret**. Copy the **Client ID** and **Client Secret**.
4. Put them in your environment as `GITHUB_OAUTH_CLIENT_ID` / `GITHUB_OAUTH_CLIENT_SECRET` (`.env` for Docker) and restart the backend.

Each user then: Integrations → **Connect GitHub** → GitHub authorize screen → done. Try: *"List my GitHub repos,"* then *"Open an issue in owner/repo titled 'Fix login bug'."*

> The GitHub integration (repos/issues) is **separate** from the GitHub Models AI provider (`GITHUB_MODELS_KEY`) — different feature, different setting.

### Connect Google Calendar

Same shape again — the **server owner** registers one free Google Cloud project, then every user clicks "Connect Google Calendar." The agent can then create events on their real calendar and read their upcoming schedule.

One-time setup (server owner):
1. Go to https://console.cloud.google.com → create a project (any name, free).
2. **APIs & Services → Library** → search "Google Calendar API" → **Enable**.
3. **APIs & Services → OAuth consent screen** → User type **External** → fill the app name + your email (the rest can stay empty).
4. **APIs & Services → Credentials** → **Create credentials → OAuth client ID** → type **Web application**, and add this exact **Authorized redirect URI**:
   `http://localhost:8080/api/integrations/google-calendar/callback`
   (replace host with your real backend address when deployed — same value as `APP_BASE_URL`)
5. Copy the **Client ID** and **Client Secret** into `GOOGLE_OAUTH_CLIENT_ID` / `GOOGLE_OAUTH_CLIENT_SECRET` (`.env` for Docker) and restart the backend.
6. While your consent screen is in **Testing** mode, add each user's Gmail address under **Test users** (up to 100). Alternatively publish the app: users then see Google's "unverified app" warning once and click *Advanced → Continue* — it works, Google just hasn't reviewed the app.

Each user then: Integrations → **Connect Google Calendar** → Google consent screen → done (once — the connection refreshes itself). Try: *"Put 'dentist' on my calendar tomorrow at 5 PM,"* then *"What's on my calendar this week?"*

### One-click Gmail via Composio (optional)

The direct email path (SMTP app password) keeps credentials on **your** server, but the setup trips up non-technical users. This optional path trades that privacy for one-click ease: users connect Gmail through [Composio](https://composio.dev)'s verified Google app instead.

**Be honest with your users (the UI already is):** with this path the Gmail token is held by Composio and mails go through their servers — unlike every direct integration. Composio's free tier covers personal use; heavy usage needs their paid plan (their pricing page is the source of truth).

One-time setup (server owner):
1. Create a free account at https://app.composio.dev and copy your **API key**.
2. Create an **auth config** for the **Gmail** toolkit, choosing **Composio-managed auth** (that's the whole point — their verified Google app). Copy its id (starts with `ac_`).
3. Put both in your environment as `COMPOSIO_API_KEY` / `COMPOSIO_GMAIL_AUTH_CONFIG_ID` (`.env` for Docker) and restart the backend.

Each user then: Integrations → Email card → **Connect Gmail via Composio (1 click)** → Google consent → done. Direct SMTP always wins if a user has both connected.

---

## Settings (environment variables)

| Variable | Required | Default | What it does |
| --- | --- | --- | --- |
| `ANTHROPIC_API_KEY` | optional* | — | Claude API key (https://console.anthropic.com) |
| `OPENAI_API_KEY` | optional* | — | ChatGPT API key (https://platform.openai.com) |
| `GEMINI_API_KEY` | optional* | — | Gemini API key (https://aistudio.google.com/apikey) |
| `GROQ_API_KEY` | optional* | — | Groq API key — free tier (https://console.groq.com/keys) |
| `GROQ_MODEL` | no |  `openai/gpt-oss-120b` | Which Groq model to use |
| `APP_DEFAULT_AI` | no | first configured | Default provider: `anthropic` / `openai` / `gemini` / `groq` |

\* Server keys are shared defaults. With none set, the server runs **BYOK-only**: each user adds their own key in **Settings → AI engine keys** (verified with a real test call, stored AES-encrypted, used only for their account).
| `APP_SECRET` | recommended | auto-generated to `<home>/.sahayak/app-secret.txt` | Long random string; protects logins and encrypts stored credentials |
| `ANTHROPIC_MODEL` | no | `claude-sonnet-5` | Which Claude model to use |
| `OPENAI_MODEL` | no | `gpt-5-mini` | Which OpenAI model to use |
| `GEMINI_MODEL` | no | `gemini-2.5-flash` | Which Gemini model to use |
| `ANTHROPIC_MAX_TOKENS` | no | `4096` | Max length of one Claude reply |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | no | localhost Postgres | Database connection |
| `APP_ALLOW_SIGNUPS` | no | `true` | `false` = no new accounts (first account always allowed) |
| `APP_CHAT_RATE_LIMIT` | no | `30` | Max chat messages per user per minute |
| `LINKEDIN_CLIENT_ID` / `LINKEDIN_CLIENT_SECRET` | for LinkedIn | — | From your LinkedIn developer app |
| `GITHUB_OAUTH_CLIENT_ID` / `GITHUB_OAUTH_CLIENT_SECRET` | for GitHub | — | From your GitHub OAuth app (separate from `GITHUB_MODELS_KEY`) |
| `GOOGLE_OAUTH_CLIENT_ID` / `GOOGLE_OAUTH_CLIENT_SECRET` | for Google Calendar | — | From your Google Cloud OAuth client (Calendar API enabled) |
| `COMPOSIO_API_KEY` / `COMPOSIO_GMAIL_AUTH_CONFIG_ID` | for one-click Gmail | — | From your Composio account (optional third-party path) |
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
    conversations/                # sidebar chats: titles, pinning, history, full-text search
    web/                          # internet tools: weather, Wikipedia, guarded page fetch
    notes/                        # long-term memory: notes the agent keeps per user
    integrations/
      Connection*.java            # per-user connected apps, credentials encrypted
      email/                      # SMTP: settings, sender, LLM tool
      linkedin/                   # LinkedIn: OAuth flow, API calls, LLM tool
      messaging/                  # Telegram bot + Discord/Slack webhooks + LLM tools
    tasks/                        # scheduler: entity, atomic claiming, runner, API
    common/                       # app secret, crypto, errors, rate limit, schema fixes
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
| POST | /api/chat/stream | same | Same, but the reply streams as Server-Sent Events |
| GET | /api/models | — | Configured AI providers + default |
| GET/POST | /api/conversations | `{title?}` | List / create sidebar conversations |
| PATCH/DELETE | /api/conversations/{id} | `{title?, pinned?}` | Rename, pin or delete (deletes its messages too) |
| GET | /api/conversations/{id}/messages | — | Full message history of one conversation |
| GET | /api/conversations/search?q= | — | Full-text search across your chats |
| POST | /api/contact | `{name, email, message}` | Public contact form (stored in DB, rate-limited) |
| POST | /api/connections/telegram | `{botToken, chatId}` | Connect your Telegram bot (sends a test message) |
| POST | /api/connections/discord | `{webhookUrl}` | Connect a Discord channel webhook |
| POST | /api/connections/slack | `{webhookUrl}` | Connect a Slack channel webhook |
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
- **Chat says the rate limit is used up** → free-tier AI keys (especially Gemini) allow only a handful of requests per minute/day. Wait a minute, switch provider in the dropdown, or upgrade the key.
- **Deploying to a real server** → see `DEPLOYMENT.md`.
- **`Connection refused: localhost:5432`** → Postgres isn't up: `docker compose up -d`.
- **"Could not log in to that mailbox"** → for Gmail you need an App Password (normal password won't work); check host/port for other providers.
- **LinkedIn button says "not configured"** → set `LINKEDIN_CLIENT_ID`/`LINKEDIN_CLIENT_SECRET` and restart the backend.
- **LinkedIn connect loops back with an error** → the redirect URL in your LinkedIn app must EXACTLY match `APP_BASE_URL + /api/integrations/linkedin/callback`.
- **GitHub button says "not configured"** → set `GITHUB_OAUTH_CLIENT_ID`/`GITHUB_OAUTH_CLIENT_SECRET` and restart the backend.
- **GitHub connect shows a redirect mismatch** → the OAuth app's **Authorization callback URL** must EXACTLY match `APP_BASE_URL + /api/integrations/github/callback`.
- **Frontend can't reach backend (dev)** → backend must be on 8080; Vite proxies `/api` there (see `frontend/vite.config.js`).
- **Mic button is missing** → your browser has no speech recognition (Firefox/Safari) — use Chrome or Edge. Voice *output* (speaker button) works everywhere.
- **Every chat message failed with "already exists" (pre-0.3.0)** → fixed: the chat-history table's conversation-id column was too short for real browser ids; 0.3.0 widens it automatically at startup.
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
