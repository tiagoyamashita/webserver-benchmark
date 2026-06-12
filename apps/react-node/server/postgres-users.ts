import { postgresProbeConfig } from "./postgres-probe.js";

export type UserRow = {
  id: number;
  name: string;
  email: string;
};

export async function findUserByEmail(
  email: string,
  requestId?: string,
): Promise<UserRow | null> {
  const config = postgresProbeConfig();
  const pg = await import("pg");
  const client = new pg.default.Client({
    ...config,
    application_name: requestId ? `exercises-react-node:${requestId}` : "exercises-react-node",
    connectionTimeoutMillis: 15_000,
  });
  try {
    await client.connect();
    const result = await client.query<{ id: string; name: string; email: string }>(
      "SELECT id, name, email FROM users WHERE LOWER(email) = LOWER($1) LIMIT 1",
      [email],
    );
    const row = result.rows[0];
    if (!row) {
      return null;
    }
    return { id: Number(row.id), name: row.name, email: row.email };
  } finally {
    await client.end().catch(() => undefined);
  }
}

export async function findUserById(userId: number, requestId?: string): Promise<UserRow | null> {
  const config = postgresProbeConfig();
  const pg = await import("pg");
  const client = new pg.default.Client({
    ...config,
    application_name: requestId ? `exercises-react-node:${requestId}` : "exercises-react-node",
    connectionTimeoutMillis: 15_000,
  });
  try {
    await client.connect();
    const result = await client.query<{ id: string; name: string; email: string }>(
      "SELECT id, name, email FROM users WHERE id = $1 LIMIT 1",
      [userId],
    );
    const row = result.rows[0];
    if (!row) {
      return null;
    }
    return { id: Number(row.id), name: row.name, email: row.email };
  } finally {
    await client.end().catch(() => undefined);
  }
}
