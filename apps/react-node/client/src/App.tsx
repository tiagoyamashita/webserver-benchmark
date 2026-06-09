import { useCallback, useEffect, useState } from "react";
import { createItem, fetchItems, probeService } from "./api";
import { formatProbeResult, probeResultClassName } from "./formatResult";
import type { Item, ProbeResult, ServiceRow } from "./types";
import "./App.css";

const SERVICES: ServiceRow[] = [
  { id: "postgres", label: "Postgres" },
  { id: "java", label: "Java" },
  { id: "rust", label: "Rust" },
  { id: "python", label: "Python" },
  { id: "prometheus", label: "Prometheus" },
  { id: "grafana", label: "Grafana" },
  { id: "elasticsearch", label: "Elasticsearch" },
  { id: "kibana", label: "Kibana" },
];

type RowState = ProbeResult | { pending: true } | null;

export default function App() {
  const [rows, setRows] = useState<Record<string, RowState>>({});
  const [items, setItems] = useState<Item[]>([]);
  const [itemsError, setItemsError] = useState<string | null>(null);
  const [itemsPending, setItemsPending] = useState(false);
  const [newItemName, setNewItemName] = useState("");
  const [createPending, setCreatePending] = useState(false);

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
    void loadItems();
  }, [loadItems]);

  const submitItem = useCallback(async () => {
    const name = newItemName.trim();
    if (!name) {
      setItemsError("name must not be blank");
      return;
    }
    setCreatePending(true);
    setItemsError(null);
    try {
      await createItem(name);
      setNewItemName("");
      await loadItems();
    } catch (error) {
      setItemsError(error instanceof Error ? error.message : String(error));
    } finally {
      setCreatePending(false);
    }
  }, [loadItems, newItemName]);

  function renderCell(id: string) {
    const state = rows[id];
    if (!state) return <span className="status-pending">—</span>;
    if ("pending" in state) return <span className="status-pending">Pinging…</span>;
    return <span className={probeResultClassName(state)}>{formatProbeResult(state)}</span>;
  }

  return (
    <main>
      <h1>React Node</h1>
      <p className="subtitle">
        Stack connectivity from this Express server: each <strong>Ping</strong> runs an outbound{" "}
        <code>GET</code> and shows the HTTP status (or error) and how long it took.
      </p>
      <div className="toolbar">
        <button type="button" className="btn primary" onClick={() => void pingAll()}>
          Ping all
        </button>
      </div>
      <section className="connectivity" aria-label="Stack connectivity">
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
      </section>

      <section className="items-panel" aria-label="Postgres items via Java API">
        <h2>Postgres items</h2>
        <p className="subtitle">
          Loaded via Express → Java <code>GET /api/items</code> (Flyway seed + shared{" "}
          <code>items</code> table).
        </p>
        <div className="toolbar">
          <button type="button" className="btn" onClick={() => void loadItems()} disabled={itemsPending}>
            {itemsPending ? "Loading…" : "Refresh"}
          </button>
        </div>
        {itemsError ? <p className="items-error">{itemsError}</p> : null}
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
                  {itemsPending ? "Loading…" : "No items yet"}
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
        <form
          className="items-form"
          onSubmit={(event) => {
            event.preventDefault();
            void submitItem();
          }}
        >
          <label htmlFor="new-item-name">Add item</label>
          <input
            id="new-item-name"
            value={newItemName}
            onChange={(event) => setNewItemName(event.target.value)}
            placeholder="Item name"
          />
          <button type="submit" className="btn primary" disabled={createPending}>
            {createPending ? "Saving…" : "Create"}
          </button>
        </form>
      </section>
    </main>
  );
}
