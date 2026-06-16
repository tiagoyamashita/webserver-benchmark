import { postgresProbeConfig } from "./postgres-probe.js";

import bcrypt from "bcryptjs";

export type UserRow = {
  id: number;
  name: string;
  email: string;
};

export type UserAuthRow = UserRow & {
  passwordHash: string | null;
};

export async function findUserAuthByEmail(
  email: string,
  requestId?: string,
): Promise<UserAuthRow | null> {
  const config = postgresProbeConfig();
  const pg = await import("pg");
  const client = new pg.default.Client({
    ...config,
    application_name: requestId ? `exercises-react-node:${requestId}` : "exercises-react-node",
    connectionTimeoutMillis: 15_000,
  });
  try {
    await client.connect();
    const result = await client.query<{
      id: string;
      name: string;
      email: string;
      password_hash: string | null;
    }>(
      "SELECT id, name, email, password_hash FROM users WHERE LOWER(email) = LOWER($1) LIMIT 1",
      [email],
    );
    const row = result.rows[0];
    if (!row) {
      return null;
    }
    return {
      id: Number(row.id),
      name: row.name,
      email: row.email,
      passwordHash: row.password_hash,
    };
  } finally {
    await client.end().catch(() => undefined);
  }
}

export async function createUser(
  name: string,
  email: string,
  password: string,
  requestId?: string,
): Promise<UserRow> {
  const config = postgresProbeConfig();
  const pg = await import("pg");
  const client = new pg.default.Client({
    ...config,
    application_name: requestId ? `exercises-react-node:${requestId}` : "exercises-react-node",
    connectionTimeoutMillis: 15_000,
  });
  const passwordHash = await bcrypt.hash(password, 10);
  try {
    await client.connect();
    const result = await client.query<{ id: string; name: string; email: string }>(
      "INSERT INTO users (name, email, password_hash, created_at) VALUES ($1, $2, $3, NOW()) RETURNING id, name, email",
      [name, email, passwordHash],
    );
    const row = result.rows[0];
    if (!row) {
      throw new Error("insert failed");
    }
    return { id: Number(row.id), name: row.name, email: row.email };
  } finally {
    await client.end().catch(() => undefined);
  }
}

export function verifyPassword(raw: string, passwordHash: string | null): boolean {
  if (!raw || !passwordHash) {
    return false;
  }
  return bcrypt.compareSync(raw, passwordHash);
}

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
