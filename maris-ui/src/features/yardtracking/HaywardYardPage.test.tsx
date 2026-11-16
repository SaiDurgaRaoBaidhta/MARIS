import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { HaywardYardPage } from "./HaywardYardPage";

const MAP = { yard: "HY", pseudoTrack: "T-95", maintenance: false, tracks: [{ track: "T-10", spots: [1, 2] }, { track: "T-11", spots: [1] }],
  trackButtons: [{ label: "T10", code: "T-10" }, { label: "T11", code: "T-11" }],
  cars: [{ track: "T-10", position: 1, code: "T-1001", car: "1101", consist: "201", orientation: "<", label: "<1101", status: "GO", condition: "go",
    moveReason: "", homeYard: "HY" }] };
const routes = (extra = {}) => fakeMiddleware({ "GET /api/v1/yard-tracking/hayward-yard": () => ({ body: MAP }), "GET /api/v1/auth/csrf": () => ({ status: 204 }), ...extra });

describe("Hayward yard update map (old VHYT05I)", () => {
  it("shows the cars on their tracks; Maint is disabled when the access list says so", async () => {
    routes();
    render(<AppProvider><HaywardYardPage /></AppProvider>);
    expect(await screen.findByRole("button", { name: "<1101" })).toBeEnabled();
    expect(screen.getByRole("group", { name: "Track T-10" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maint" })).toBeDisabled();
  });

  it("sends the clicks so far and keeps them until the old selection is complete", async () => {
    const mw = routes({ "POST /api/v1/yard-tracking/hayward-yard/selection": () => ({ body: { open: null, params: {} } }) });
    render(<AppProvider><HaywardYardPage /></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "<1101" }));
    await waitFor(() => expect(mw.calls("/api/v1/yard-tracking/hayward-yard/selection")).toHaveLength(1));
    expect(mw.calls("/api/v1/yard-tracking/hayward-yard/selection")[0].body).toEqual({ clicks: [{ spot: "T-1001" }] });
    expect(await screen.findByRole("status")).toHaveTextContent("T-1001");
    await userEvent.click(screen.getByRole("button", { name: "T11" }));
    await waitFor(() => expect(mw.calls("/api/v1/yard-tracking/hayward-yard/selection")).toHaveLength(2));
    expect(mw.calls("/api/v1/yard-tracking/hayward-yard/selection")[1].body).toEqual({ clicks: [{ spot: "T-1001" }, { track: "T-11" }] });
  });
});
