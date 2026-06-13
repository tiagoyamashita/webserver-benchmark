# React Node

**React** front end + **Node/Express** API for stack connectivity probes (replaces the old static Vite **reach-ui** page).

The browser calls **`/api/probe/:id`** on this server; Express performs outbound checks to Java, Python, Rust, Prometheus, Grafana, Elasticsearch, Kibana (**HTTP GET**), or **Postgres** (**`SELECT 1`** via `DB_*` env vars).

## Run locally

```bash
cd react-node
npm install
npm run dev
```

Open **http://127.0.0.1:5174/**.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Express + Vite middleware (HMR) on port **5174** |
| `npm run build` | Vite client build + compile server to `dist/` |
| `npm start` | Production server (`NODE_ENV=production`) |
| `npm test` | Vitest (React components + Express API) |

## Configure probe targets

Set **`PROBE_*_URL`** env vars (see `.env.example`). Root **`docker-compose.yml`** sets these to Compose service names for the **`react-node`** container.

**Postgres items:** **`GET /api/items`** and **`POST /api/items`** persist directly to the shared `items` table (same env as probes: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`).

**Java items proxy:** the UI can also load demo rows via Express → Java **`GET /java/api/items`** (Flyway seed in `java/src/main/resources/db/migration/V2__seed_items.sql`). Override upstream Java with **`ITEMS_BASE_URL`** if needed.

## Stack integration

Other apps link to **http://127.0.0.1:5174/** and server-side stack pings use **`APP_STACK_REACT_NODE_BASE_URL`** (see root `docker-compose.yml`).
