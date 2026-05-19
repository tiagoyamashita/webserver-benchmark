# Stack reach UI

Small **Vite** page that **GET-probes** the Java, Python, and Rust dashboard URLs from the browser, plus **Grafana**, **Prometheus**, **Elasticsearch**, and **Kibana** (Compose defaults on **3000** / **9090** / **9200** / **5601**). Each row has an **editable URL** aligned with [../DOCKER.md](../DOCKER.md). **PostgreSQL** (**5432**) is noted as TCP-only (no browser probe).

## Behaviour

1. **Inputs** — You can type any base URL; values persist in **`localStorage`** when you click **Save URLs**.
2. **Defaults** — If an input is empty, the probe uses, in order: saved value → matching **`VITE_*_EMBED_URL`** from the build (see `.env.example`) → for Java only, legacy **`VITE_EXERCISES_EMBED_URL`** → localhost defaults.
3. **Probe all again** — Re-runs every **GET** probe (apps + connectivity) without saving (uses current field text, or defaults when empty).
4. **Probe connectivity again** — Re-runs only Grafana / Prometheus / Elasticsearch / Kibana.
5. **CORS** — Probes use `fetch(..., { mode: 'cors' })`. Each target must allow **GET** from this UI’s origin or the browser will report a network / CORS-style error.

## Run

```bash
cd reach-ui
npm install
npm run dev
```

Open **http://127.0.0.1:5174/** (Vite default port in `vite.config.ts`).

Production build:

```bash
npm run build
npm run preview
```

## Configure defaults at build time

Copy `.env.example` to `.env`, set `VITE_*` values, then `npm run build`. User-edited URLs in the browser still override via **Save URLs** (stored locally).
