import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { NarrativePage } from "./NarrativePage";

const narr = (row: number) => ({ text: `LINE ${row}`, date: "2026-11-01", time: "10.00.00", userGroup: "WAYSIDE", row, maxRows: 3, hasPrev: row > 1, hasNext: row < 3 });
const base = { "GET /api/v1/auth/csrf": () => ({ status: 204 }), "GET /api/v1/wayside/narratives/latest": () => ({ body: narr(3) }),
  "GET /api/v1/wayside/narratives/step": (r: { query: URLSearchParams }) => ({ body: narr(Number(r.query.get("row")) - 1) }) };

describe("Wayside narrative (old WS13I)", () => {
  it("shows the latest narrative of an incident and steps back with Prv", async () => {
    const mw = fakeMiddleware(base);
    render(<AppProvider><NarrativePage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Incident"), "29990010");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    expect(await screen.findByText("LINE 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nxt" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Prv" }));
    expect(await screen.findByText("LINE 2")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/wayside/narratives/step")[0].query)).toEqual({ incident: "29990010", direction: "prev", row: "3", maxRows: "3" });
  });

  it("adds a narrative for the context given", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/wayside/narratives": () => ({ body: { ...narr(3), row: 4, maxRows: 4, text: "CHECKED" } }) });
    render(<AppProvider><NarrativePage context={{ incident: "29990010", repairDoc: "2" }} /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Additional Narrative"), "CHECKED");
    await userEvent.click(screen.getByRole("button", { name: "Add Narrative" }));
    expect(await screen.findByText("CHECKED")).toBeInTheDocument();
    await waitFor(() => expect(mw.calls("/api/v1/wayside/narratives")[0].body).toEqual({ incident: "29990010", repairDoc: 2, text: "CHECKED" }));
  });

  it("shows the middleware's error", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/wayside/narratives/latest": () => ({ status: 400, body: { detail: "Give an incident, a repair document or a PM (department and equipment)." } }) });
    render(<AppProvider><NarrativePage /></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "Show" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Give an incident");
  });
});
