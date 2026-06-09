export type ProbeResult = {
  ok: boolean;
  status: number | null;
  error: string | null;
  ms: number;
};

export type ServiceRow = {
  id: string;
  label: string;
};
