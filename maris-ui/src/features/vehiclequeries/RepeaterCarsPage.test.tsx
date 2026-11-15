import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { RepeaterCarsPage } from "./RepeaterCarsPage";

const OPTIONS = { homeYards: ["CY", "HY", "ALL"], homeYard: "ALL", printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] };
const LIST = { homeYard: "CY", car: "1101", total: 1, footer: null, incidents: [{ car: "1101", location: "CY01", status: "NO GO  T", vehicleStatus: "NO GO",
  incidentIndicator: "T", incident: "29990010", date: "2026-10-02", time: "10:15", hoursBetween: 12, hoursFromLast: 30, symptom: "B1" }] };
const base = { "GET /api/v1/vehicle-queries/repeater-cars/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

describe("Repeater cars (old VHRC01I / VHRC02I)", () => {
  it("starts on the old screen's yard and lists a car's incidents", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/repeater-cars": () => ({ body: LIST }) });
    render(<AppProvider><RepeaterCarsPage /></AppProvider>);
    expect(await screen.findByLabelText("Home Yard")).toHaveValue("ALL");
    await userEvent.selectOptions(screen.getByLabelText("Home Yard"), "CY");
    await userEvent.type(screen.getByLabelText("Cars"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Incidents" });
    expect(within(table).getByText("29990010")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/repeater-cars")[0].query)).toEqual({ homeYard: "CY", car: "1101" });
  });

  it("shows the old message", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/repeater-cars": () => ({ status: 400, body: { detail: "Only Hard Copy can be requested." } }) });
    render(<AppProvider><RepeaterCarsPage /></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Only Hard Copy can be requested.");
  });

  it("prints a hard copy", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/repeater-cars/print": () => ({ body: { message: "Submitted" } }) });
    render(<AppProvider><RepeaterCarsPage /></AppProvider>);
    await userEvent.selectOptions(await screen.findByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/repeater-cars/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/repeater-cars/print")[0].body).toEqual({ homeYard: "ALL", car: "", printer: "DUBLNHP1" });
  });
});
