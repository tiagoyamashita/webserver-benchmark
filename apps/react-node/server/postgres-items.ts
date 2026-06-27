import { postgresProbeConfig } from "./postgres-probe.js";
import type { Item } from "./items.js";

export class DatabaseNotConfiguredError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DatabaseNotConfiguredError";
  }
}

function postgresConfigured(): boolean {
  return Boolean(
    process.env.DB_HOST?.trim() || process.env.PROBE_POSTGRES_HOST?.trim(),
  );
}

function applicationName(requestId?: string): string {
  const value = requestId
    ? `webserver-benchmark-react-node;req=${requestId}`
    : "webserver-benchmark-react-node";
  return value.length <= 63 ? value : value.slice(0, 63);
}

function formatCreatedAt(value: Date | string): string {
  if (value instanceof Date) {
    return value.toISOString().replace("+00:00", "Z");
  }
  return String(value).replace("+00:00", "Z");
}

type ItemRow = {
  id: string;
  name: string;
  created_at: Date | string;
};

function rowToItem(row: ItemRow): Item {
  return {
    id: Number(row.id),
    name: row.name,
    createdAt: formatCreatedAt(row.created_at),
  };
}

async function withClient<T>(
  requestId: string | undefined,
  fn: (client: import("pg").Client) => Promise<T>,
): Promise<T> {
  if (!postgresConfigured()) {
    throw new DatabaseNotConfiguredError(
      "Postgres not configured (set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)",
    );
  }
  let pg: typeof import("pg");
  try {
    pg = await import("pg");
  } catch {
    throw new DatabaseNotConfiguredError(
      "pg is not installed; rebuild react-node (podman compose build react-node)",
    );
  }
  const client = new pg.default.Client({
    ...postgresProbeConfig(),
    application_name: applicationName(requestId),
    connectionTimeoutMillis: 15_000,
  });
  try {
    await client.connect();
    return await fn(client);
  } finally {
    await client.end().catch(() => undefined);
  }
}

export async function listItemsFromPostgres(requestId?: string): Promise<Item[]> {
  return withClient(requestId, async (client) => {
    const result = await client.query<ItemRow>(
      "SELECT id, name, created_at FROM items ORDER BY id",
    );
    return result.rows.map(rowToItem);
  });
}

export async function insertItemIntoPostgres(
  name: string,
  requestId?: string,
): Promise<Item> {
  return withClient(requestId, async (client) => {
    const result = await client.query<ItemRow>(
      "INSERT INTO items (name, created_at) VALUES ($1, NOW()) RETURNING id, name, created_at",
      [name],
    );
    const row = result.rows[0];
    if (!row) {
      throw new Error("INSERT returned no row");
    }
    return rowToItem(row);
  });
}
