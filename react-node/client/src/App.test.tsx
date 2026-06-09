import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("App", () => {
  it("renders stack services and pings one target", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ok: true, status: 200, error: null, ms: 12 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    render(<App />);
    expect(screen.getByRole("heading", { name: "React Node" })).toBeInTheDocument();
    expect(screen.getByText("Java")).toBeInTheDocument();

    await userEvent.click(screen.getAllByRole("button", { name: "Ping" })[0]);

    await waitFor(() => {
      expect(screen.getByText("HTTP 200 · 12 ms")).toBeInTheDocument();
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/probe/java", expect.any(Object));
  });
});
