import { useCallback, useState } from "react";
import { probeService } from "./api";
import { formatProbeResult, probeResultClassName } from "./formatResult";
import type { ProbeResult, ServiceRow } from "./types";
import "./App.css";

const SERVICES: ServiceRow[] = [
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
    </main>
  );
}
