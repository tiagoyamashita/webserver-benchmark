export type ProbeResult = {
  ok: boolean;
  status: number | null;
  error: string | null;
  ms: number;
  kind?: "http" | "postgres" | "redis";
};

export type ServiceRow = {
  id: string;
  label: string;
};

export type Item = {
  id: number;
  name: string;
  createdAt: string;
};
