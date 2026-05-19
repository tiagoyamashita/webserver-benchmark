type Service = { id: string; label: string };

const services: Service[] = [
  { id: "java", label: "Java" },
  { id: "rust", label: "Rust" },
  { id: "python", label: "Python" },
  { id: "prometheus", label: "Prometheus" },
  { id: "grafana", label: "Grafana" },
  { id: "elasticsearch", label: "Elasticsearch" },
];

type ProbeResult = { ok: boolean; status: number | null; error: string | null; ms: number };

async function probe(id: string): Promise<ProbeResult> {
  const start = performance.now();
  try {
    const res = await fetch(`/api/probe/${id}`, {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
    });
    const ms = Math.round(performance.now() - start);
    return { ok: res.ok, status: res.status, error: res.ok ? null : res.statusText || null, ms };
  } catch (e) {
    const ms = Math.round(performance.now() - start);
    return { ok: false, status: null, error: e instanceof Error ? e.message : String(e), ms };
  }
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function formatResult(r: ProbeResult): string {
  const time = `<span class="time">${r.ms} ms</span>`;
  if (r.ok && r.status != null) {
    return `<span class="status-ok">HTTP ${r.status}</span> · ${time}`;
  }
  if (r.status != null) {
    return `<span class="status-warn">HTTP ${r.status}</span> · ${time}`;
  }
  return `<span class="status-err">${escapeHtml(r.error ?? "Unreachable")}</span> · ${time}`;
}

function setStatus(id: string, html: string) {
  const cell = document.querySelector(`tr[data-target="${id}"] .status-cell`);
  if (cell) cell.innerHTML = html;
}

async function pingOne(svc: Service) {
  setStatus(svc.id, '<span class="status-pending">Pinging…</span>');
  setStatus(svc.id, formatResult(await probe(svc.id)));
}

const app = document.getElementById("app");
if (!app) throw new Error("#app missing");

const rows = services
  .map(
    (s) => `
  <tr data-target="${s.id}">
    <td>${escapeHtml(s.label)}</td>
    <td><button type="button" class="btn ping-btn" data-target="${s.id}">Ping</button></td>
    <td class="status-cell"><span class="status-pending">—</span></td>
  </tr>`,
  )
  .join("");

app.innerHTML = `
<style>
  :root {
    color-scheme: dark;
    --bg: #121212;
    --text: #e8e8ea;
    --muted: #a3a3a8;
    --border: #3f3f46;
    --th-bg: #27272a;
    --code-bg: #27272a;
    --ok: #4ade80;
    --warn: #fbbf24;
    --err: #f87171;
  }
  html, body {
    margin: 0;
    min-height: 100%;
    background: var(--bg);
    color: var(--text);
  }
  body {
    font-family: system-ui, -apple-system, sans-serif;
    padding: 1.5rem;
    max-width: min(52rem, calc(100vw - 3rem));
    box-sizing: border-box;
  }
  h1 { font-size: 1.5rem; font-weight: 700; margin: 0 0 0.35rem; letter-spacing: 0.02em; }
  .subtitle { color: var(--muted); font-size: 0.9rem; margin: 0 0 1.25rem; line-height: 1.45; }
  .toolbar { margin: 0 0 1rem; }
  .btn {
    cursor: pointer;
    padding: 0.35rem 0.75rem;
    font-size: 0.85rem;
    font-weight: 600;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: var(--code-bg);
    color: var(--text);
  }
  .btn:hover { filter: brightness(1.08); }
  .btn.primary { border-color: var(--ok); color: var(--ok); }
  .connectivity {
    border: 1px solid var(--border);
    border-radius: 10px;
    overflow: hidden;
    background: rgba(128, 128, 128, 0.05);
  }
  table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
  th, td { padding: 0.5rem 0.65rem; text-align: left; border-bottom: 1px solid var(--border); }
  th { background: var(--th-bg); font-size: 0.78rem; font-weight: 600; color: var(--muted); }
  tr:last-child td { border-bottom: none; }
  .status-cell { font-family: ui-monospace, monospace; font-size: 0.85rem; }
  .status-pending { color: var(--muted); font-style: italic; }
  .status-ok { color: var(--ok); font-weight: 600; }
  .status-warn { color: var(--warn); font-weight: 600; }
  .status-err { color: var(--err); font-weight: 600; }
  .time { color: var(--muted); }
  code { font-size: 0.9em; padding: 0.1rem 0.3rem; border-radius: 4px; background: var(--code-bg); }
</style>
<h1>Reach UI</h1>
<p class="subtitle">
  Connectivity from this page: each <strong>Ping</strong> runs an outbound
  <code>GET</code> and shows the HTTP status (or error) and how long it took.
</p>
<div class="toolbar">
  <button type="button" class="btn primary" id="ping-all">Ping all</button>
</div>
<section class="connectivity">
  <table>
    <thead>
      <tr><th scope="col">Service</th><th scope="col">Test</th><th scope="col">Response</th></tr>
    </thead>
    <tbody id="connectivity-rows">${rows}</tbody>
  </table>
</section>
`;

const byId = new Map(services.map((s) => [s.id, s]));

document.getElementById("connectivity-rows")?.addEventListener("click", (ev) => {
  const btn = (ev.target as HTMLElement).closest(".ping-btn");
  if (!btn) return;
  const id = btn.getAttribute("data-target");
  const svc = id ? byId.get(id) : undefined;
  if (svc) void pingOne(svc);
});

document.getElementById("ping-all")?.addEventListener("click", () => {
  for (const s of services) setStatus(s.id, '<span class="status-pending">Pinging…</span>');
  void Promise.all(services.map((s) => pingOne(s)));
});
