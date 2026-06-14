import { useCallback, useEffect, useState } from "react";
import { createItem, fetchItems, probeService } from "./api";
import { subscribeLastRequestId } from "./api-request";
import { formatProbeResult, probeResultClassName } from "./formatResult";
import {
  ensureSession,
  fetchCurrentSession,
  loginSession,
  logoutSession,
  refreshSessionId,
  type SessionData,
} from "./session";
import type { Item, ProbeResult, ServiceRow } from "./types";
import "./App.css";

const SERVICES: ServiceRow[] = [
  { id: "postgres", label: "Postgres" },
  { id: "redis", label: "Redis" },
  { id: "java", label: "Java" },
  { id: "rust", label: "Rust" },
  { id: "python", label: "Python" },
  { id: "prometheus", label: "Prometheus" },
  { id: "grafana", label: "Grafana" },
  { id: "elasticsearch", label: "Elasticsearch" },
  { id: "kibana", label: "Kibana" },
];

type ViewId = "connectivity" | "session" | "list-items" | "create-item" | "openapi";

type RowState = ProbeResult | { pending: true } | null;

export default function App() {
  const [activeView, setActiveView] = useState<ViewId>("connectivity");
  const [rows, setRows] = useState<Record<string, RowState>>({});
  const [items, setItems] = useState<Item[]>([]);
  const [itemsError, setItemsError] = useState<string | null>(null);
  const [itemsPending, setItemsPending] = useState(false);
  const [newItemName, setNewItemName] = useState("");
  const [createPending, setCreatePending] = useState(false);
  const [createMessage, setCreateMessage] = useState<string | null>(null);
  const [openapiSrc, setOpenapiSrc] = useState<string | null>(null);
  const [pageSessionId, setPageSessionId] = useState("…");
  const [pageSessionSummary, setPageSessionSummary] = useState("Loading session…");
  const [pageRedisKey, setPageRedisKey] = useState("…");
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [lastRequestId, setLastRequestId] = useState("—");
  const [sessionStatus, setSessionStatus] = useState("Loading session…");
  const [sessionResult, setSessionResult] = useState<string | null>(null);
  const [sessionEmail, setSessionEmail] = useState("");
  const [sessionUserId, setSessionUserId] = useState("");
  const [sessionPending, setSessionPending] = useState(false);

  useEffect(() => subscribeLastRequestId(setLastRequestId), []);

  function renderSessionStatus(data: SessionData | null) {
    if (!data?.sessionId) {
      setSessionStatus("No session.");
      setPageSessionSummary("No session.");
      setPageSessionId("—");
      setPageRedisKey("—");
      setIsLoggedIn(false);
      return;
    }
    const who =
      data.userId === 0 || !data.email
        ? `${data.name || "Guest"} (guest — sign in to bind a user)`
        : `${data.name} (${data.email})`;
    setSessionStatus(`${who} — session ${data.sessionId} — Redis key ${data.redisKey}`);
    setPageSessionSummary(who);
    setPageSessionId(data.sessionId);
    setPageRedisKey(data.redisKey || "—");
    setIsLoggedIn(data.userId > 0 && Boolean(data.email));
  }

  const refreshSessionHeader = useCallback(async () => {
    try {
      const session = await fetchCurrentSession();
      renderSessionStatus(session);
    } catch {
      try {
        const session = await ensureSession();
        renderSessionStatus(session);
      } catch {
        setPageSessionId("unavailable");
        setPageSessionSummary("Session unavailable (is Redis up?)");
        setPageRedisKey("—");
        setSessionStatus("Session unavailable.");
        setIsLoggedIn(false);
      }
    }
  }, []);

  useEffect(() => {
    void refreshSessionHeader();
  }, [refreshSessionHeader]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        void refreshSessionHeader();
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [refreshSessionHeader]);

  const refreshSessionView = useCallback(async () => {
    setSessionPending(true);
    setSessionResult("Refreshing…");
    try {
      const session = await fetchCurrentSession();
      renderSessionStatus(session);
      setSessionResult(JSON.stringify(session, null, 2));
    } catch (error) {
      renderSessionStatus(null);
      setSessionResult(error instanceof Error ? error.message : String(error));
    } finally {
      setSessionPending(false);
    }
  }, []);

  const rotateSessionId = useCallback(async () => {
    setSessionPending(true);
    setSessionResult("Issuing new session id…");
    try {
      const session = await refreshSessionId();
      renderSessionStatus(session);
      setSessionResult(`New session stored in Redis:\n${JSON.stringify(session, null, 2)}`);
    } catch (error) {
      renderSessionStatus(null);
      setSessionResult(error instanceof Error ? error.message : String(error));
    } finally {
      setSessionPending(false);
    }
  }, []);

  const submitSessionLogin = useCallback(async () => {
    setSessionPending(true);
    setSessionResult("Logging in…");
    const body: { email?: string; userId?: number } = {};
    const email = sessionEmail.trim();
    const userIdRaw = sessionUserId.trim();
    if (email) body.email = email;
    if (userIdRaw) body.userId = Number(userIdRaw);
    try {
      const session = await loginSession(body);
      renderSessionStatus(session);
      setSessionResult(JSON.stringify(session, null, 2));
    } catch (error) {
      renderSessionStatus(null);
      setSessionResult(error instanceof Error ? error.message : String(error));
    } finally {
      setSessionPending(false);
    }
  }, [sessionEmail, sessionUserId]);

  const submitSessionLogout = useCallback(async () => {
    setSessionPending(true);
    setSessionResult("Logging out…");
    try {
      await logoutSession();
      renderSessionStatus(null);
      const session = await ensureSession();
      renderSessionStatus(session);
      setSessionResult(`Logged out. New guest session:\n${JSON.stringify(session, null, 2)}`);
    } catch (error) {
      setSessionResult(error instanceof Error ? error.message : String(error));
    } finally {
      setSessionPending(false);
    }
  }, []);

  const submitHeaderAuth = useCallback(async () => {
    if (isLoggedIn) {
      await submitSessionLogout();
      return;
    }
    setActiveView("session");
  }, [isLoggedIn, submitSessionLogout]);

  const pingOne = useCallback(async (id: string) => {
    setRows((prev) => ({ ...prev, [id]: { pending: true } }));
    const result = await probeService(id);
    setRows((prev) => ({ ...prev, [id]: result }));
  }, []);

  const pingAll = useCallback(async () => {
    const pending = Object.fromEntries(SERVICES.map((s) => [s.id, { pending: true }]));
    setRows((prev) => ({ ...prev, ...pending }));
    await Promise.all(SERVICES.map((s) => pingOne(s.id)));
  }, [pingOne]);

  const loadItems = useCallback(async () => {
    setItemsPending(true);
    setItemsError(null);
    try {
      setItems(await fetchItems());
    } catch (error) {
      setItems([]);
      setItemsError(error instanceof Error ? error.message : String(error));
    } finally {
      setItemsPending(false);
    }
  }, []);

  useEffect(() => {
    if (activeView === "list-items") {
      void loadItems();
    }
    if (activeView === "session") {
      void refreshSessionView();
    }
    if (activeView === "openapi" && !openapiSrc) {
      setOpenapiSrc("/swagger-ui");
    }
  }, [activeView, loadItems, openapiSrc, refreshSessionView]);

  const submitItem = useCallback(async () => {
    const name = newItemName.trim();
    if (!name) {
      setCreateMessage("name must not be blank");
      return;
    }
    setCreatePending(true);
    setCreateMessage(null);
    try {
      const item = await createItem(name);
      setNewItemName("");
      setCreateMessage(`Created item #${item.id}: ${item.name}`);
    } catch (error) {
      setCreateMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setCreatePending(false);
    }
  }, [newItemName]);

  function renderCell(id: string) {
    const state = rows[id];
    if (!state) return <span className="status-pending">—</span>;
    if ("pending" in state) return <span className="status-pending">Pinging…</span>;
    return <span className={probeResultClassName(state)}>{formatProbeResult(state)}</span>;
  }

  return (
    <main>
      <h1>React Node</h1>
      <p className="page-subtitle">Use the menu on the left to open connectivity checks or action forms.</p>
      <p className="page-subtitle">
        Last request ID: <code>{lastRequestId}</code> (new id per API call — use to isolate actions in logs)
      </p>
      <p className="page-subtitle">{pageSessionSummary}</p>
      <p className="page-subtitle">
        Session ID: <code>{pageSessionId}</code> · Redis: <code>{pageRedisKey}</code>
        <button
          type="button"
          className="btn header-auth-btn"
          disabled={sessionPending}
          aria-label={isLoggedIn ? "Sign out" : "Sign in"}
          onClick={() => void submitHeaderAuth()}
        >
          {isLoggedIn ? "Logout" : "Login"}
        </button>
      </p>

      <div className="dashboard">
        <nav className="sidebar" aria-label="Dashboard menu">
          <p className="sidebar-title">Menu</p>
          <button
            type="button"
            className="sidebar-btn"
            aria-current={activeView === "connectivity" ? "page" : undefined}
            onClick={() => setActiveView("connectivity")}
          >
            Connectivity
          </button>
          <p className="sidebar-section">Actions</p>
          <button
            type="button"
            className="sidebar-btn sub"
            aria-current={activeView === "session" ? "page" : undefined}
            onClick={() => setActiveView("session")}
          >
            Session login
          </button>
          <button
            type="button"
            className="sidebar-btn sub"
            aria-current={activeView === "list-items" ? "page" : undefined}
            onClick={() => setActiveView("list-items")}
          >
            List items
          </button>
          <button
            type="button"
            className="sidebar-btn sub"
            aria-current={activeView === "create-item" ? "page" : undefined}
            onClick={() => setActiveView("create-item")}
          >
            Create item
          </button>
          <p className="sidebar-section">API</p>
          <button
            type="button"
            className="sidebar-btn sub"
            aria-current={activeView === "openapi" ? "page" : undefined}
            onClick={() => setActiveView("openapi")}
          >
            OpenAPI
          </button>
        </nav>

        <div className="main-panel">
          <section
            className="view-panel"
            aria-hidden={activeView !== "connectivity"}
            hidden={activeView !== "connectivity"}
          >
            <p className="panel-lead">
              Each <strong>Ping</strong> runs an outbound <code>GET</code> from this Express server and
              shows the HTTP status (or error) and how long it took.
            </p>
            <div className="toolbar">
              <button type="button" className="btn primary" onClick={() => void pingAll()}>
                Ping all services
              </button>
            </div>
            <div className="connectivity" aria-label="Stack connectivity">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Service</th>
                    <th scope="col">Test</th>
                    <th scope="col">Response</th>
                  </tr>
                </thead>
                <tbody>
                  {SERVICES.map((service) => (
                    <tr key={service.id} data-target={service.id}>
                      <td>{service.label}</td>
                      <td>
                        <button type="button" className="btn" onClick={() => void pingOne(service.id)}>
                          Ping
                        </button>
                      </td>
                      <td className="status-cell">{renderCell(service.id)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section
            className="view-panel"
            aria-hidden={activeView !== "session"}
            hidden={activeView !== "session"}
          >
            <h2 className="form-heading">Shared Redis session</h2>
            <p className="form-hint">
              A guest session is created automatically when this page loads. Login replaces it with a user-bound session in
              Redis at <code>exercises:session:{"{sessionId}"}</code>. Other apps authenticate with{" "}
              <code>Authorization: Bearer …</code>, <code>X-Session-ID</code>, or the{" "}
              <code>exercises_session</code> cookie.
            </p>
            <p className="form-hint">{sessionStatus}</p>
            <form
              className="items-form"
              onSubmit={(event) => {
                event.preventDefault();
                void submitSessionLogin();
              }}
            >
              <label htmlFor="session-email">User email</label>
              <input
                id="session-email"
                type="email"
                value={sessionEmail}
                onChange={(event) => setSessionEmail(event.target.value)}
                placeholder="jane@example.com"
                autoComplete="email"
              />
              <label htmlFor="session-user-id">Or user ID</label>
              <input
                id="session-user-id"
                type="number"
                min={1}
                step={1}
                value={sessionUserId}
                onChange={(event) => setSessionUserId(event.target.value)}
                placeholder="1"
              />
              <button type="submit" className="btn primary" disabled={sessionPending}>
                Login (store in Redis)
              </button>
              <button
                type="button"
                className="btn"
                disabled={sessionPending}
                onClick={() => void submitSessionLogout()}
              >
                Logout
              </button>
              <button
                type="button"
                className="btn"
                disabled={sessionPending}
                onClick={() => void rotateSessionId()}
              >
                New session ID (Redis)
              </button>
            </form>
            {sessionResult ? (
              <pre className="result-box" aria-live="polite">
                {sessionResult}
              </pre>
            ) : null}
          </section>

          <section
            className="view-panel"
            aria-hidden={activeView !== "list-items"}
            hidden={activeView !== "list-items"}
          >
            <h2 className="form-heading">List items</h2>
            <p className="form-hint">
              Loads rows from the shared PostgreSQL <code>items</code> table via{" "}
              <code>GET /api/items</code> (direct Postgres, session-protected when Redis is configured).
            </p>
            <div className="toolbar">
              <button
                type="button"
                className="btn primary"
                onClick={() => void loadItems()}
                disabled={itemsPending}
              >
                {itemsPending ? "Loading…" : "Refresh list"}
              </button>
            </div>
            {itemsError ? <p className="items-error">{itemsError}</p> : null}
            <div className="connectivity">
              <table>
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Name</th>
                    <th scope="col">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {items.length === 0 ? (
                    <tr>
                      <td colSpan={3} className="status-pending">
                        {itemsPending ? "Loading…" : "No items yet — open Create item or click Refresh."}
                      </td>
                    </tr>
                  ) : (
                    items.map((item) => (
                      <tr key={item.id}>
                        <td>{item.id}</td>
                        <td>{item.name}</td>
                        <td>{item.createdAt}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <section
            className="view-panel"
            aria-hidden={activeView !== "create-item"}
            hidden={activeView !== "create-item"}
          >
            <h2 className="form-heading">Create item</h2>
            <p className="form-hint">
              Persists a row to the shared PostgreSQL <code>items</code> table via{" "}
              <code>POST /api/items</code> (direct Postgres, session-protected when Redis is configured).
            </p>
            <form
              className="items-form"
              onSubmit={(event) => {
                event.preventDefault();
                void submitItem();
              }}
            >
              <label htmlFor="new-item-name">Name</label>
              <input
                id="new-item-name"
                value={newItemName}
                onChange={(event) => setNewItemName(event.target.value)}
                placeholder="Item name"
                autoComplete="off"
              />
              <button type="submit" className="btn primary" disabled={createPending}>
                {createPending ? "Saving…" : "Save item"}
              </button>
            </form>
            {createMessage ? (
              <pre
                className={`result-box${createMessage.startsWith("Created") ? "" : " status-err"}`}
                aria-live="polite"
              >
                {createMessage}
              </pre>
            ) : null}
          </section>

          <section
            className="view-panel"
            aria-hidden={activeView !== "openapi"}
            hidden={activeView !== "openapi"}
          >
            <h2 className="form-heading">OpenAPI</h2>
            <p className="form-hint">
              Interactive REST docs (Swagger UI) for <code>/api/items</code> (direct Postgres) and{" "}
              <code>/java/api/items</code> (Java proxy). Health, probe, and observability routes are
              excluded. JSON spec: <code>/api-docs/openapi.json</code>.
            </p>
            {openapiSrc ? (
              <iframe
                className="openapi-frame"
                title="Swagger UI"
                src={openapiSrc}
                loading="lazy"
                referrerPolicy="same-origin"
              />
            ) : null}
          </section>
        </div>
      </div>
    </main>
  );
}
