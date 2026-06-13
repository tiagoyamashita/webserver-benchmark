import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("App", () => {
  it("renders stack services and pings one target", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith("/java/api/items")) {
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({ ok: true, status: 200, error: null, ms: 12 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    render(<App />);
    expect(screen.getByRole("heading", { name: "React Node" })).toBeInTheDocument();
    expect(screen.getByText("Java")).toBeInTheDocument();

    const javaRow = screen.getByText("Java").closest("tr");
    const javaPing = javaRow?.querySelector("button");
    expect(javaPing).toBeTruthy();
    await userEvent.click(javaPing!);

    await waitFor(() => {
      expect(screen.getByText("HTTP 200 · 12 ms")).toBeInTheDocument();
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/probe/java", expect.any(Object));
  });
});
