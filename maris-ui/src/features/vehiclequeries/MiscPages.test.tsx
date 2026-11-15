import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarStatusChangePage, ConsistOrientationPage, DncSheetPage, ScramPage } from "./MiscPages";

const csrf = { "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

describe("Consist orientation (old VHCO01I)", () => {
  it("prints with the start dates and the chosen printer", async () => {
    const mw = fakeMiddleware({ ...csrf,
      "GET /api/v1/vehicle-queries/consist-orientation/options": () => ({ body: { title: "VHCO01I", fromDate: "2026-08-16", toDate: "2026-11-14", all: "ALL",
        printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] } }),
      "POST /api/v1/vehicle-queries/consist-orientation/print": () => ({ body: { message: "The report is submited" } }) });
    render(<AppProvider><ConsistOrientationPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No. From"), "ALL");
    await screen.findByRole("option", { name: "DUBLIN TMZ (DUBLNHP1)" });
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/consist-orientation/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/consist-orientation/print")[0].body).toEqual({ from: "ALL", to: "", fromDate: "2026-08-16", toDate: "2026-11-14", printer: "DUBLNHP1" });
  });

  it("shows the old message", async () => {
    fakeMiddleware({ ...csrf,
      "GET /api/v1/vehicle-queries/consist-orientation/options": () => ({ body: { title: "", fromDate: "", toDate: "", all: "ALL", printers: [{ description: "D", vtam: "P1" }] } }),
      "POST /api/v1/vehicle-queries/consist-orientation/print": () => ({ status: 400, body: { detail: "THE FROM CONSIST IS INVALID" } }) });
    render(<AppProvider><ConsistOrientationPage /></AppProvider>);
    await screen.findByRole("option", { name: "D (P1)" });
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "P1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("THE FROM CONSIST IS INVALID");
  });
});

describe("D and C sheet reprint (old VHDC01I)", () => {
  it("reprints a car's sheet", async () => {
    const mw = fakeMiddleware({ ...csrf,
      "GET /api/v1/vehicle-queries/dnc-sheet/options": () => ({ body: { title: "", yes: "Y", no: "N", printers: [{ location: "HAYWARD SHOP", vtam: "HAYWHP2" }] } }),
      "POST /api/v1/vehicle-queries/dnc-sheet/print": () => ({ body: { message: "You are printing the D and C sheet now" } }) });
    render(<AppProvider><DncSheetPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Print Form for Car"), "1101");
    await screen.findByRole("option", { name: "HAYWARD SHOP (HAYWHP2)" });
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "HAYWHP2");
    await userEvent.click(screen.getByRole("button", { name: "OK" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/dnc-sheet/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/dnc-sheet/print")[0].body).toEqual({ blankForm: "N", car: "1101", incident: "", printer: "HAYWHP2" });
  });
});

describe("SCRAM (old VHSCM1I)", () => {
  it("lists a home yard and pages to the next", async () => {
    const cells = (n: number) => [n, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    const totals = [{ scheduled: 4, actual: 3, difference: 1 }, { scheduled: 0, actual: 0, difference: 0 }, { scheduled: 0, actual: 0, difference: 0 }, { scheduled: 0, actual: 0, difference: 0 }];
    const mw = fakeMiddleware({ ...csrf,
      "GET /api/v1/vehicle-queries/scram/options": () => ({ body: { title: "", day: "MO", time: "AM", days: ["MO", "TU"], times: ["AM", "PM", "DA"], homeYards: ["CY", "DY"],
        homeYardHeading: "Home Yrd", groups: ["CONCORD", "DALY CITY", "HAYWARD", "RICHMOND"], columns: ["Sched", "Actual", "Diff"] } }),
      "GET /api/v1/vehicle-queries/scram": () => ({ body: { day: "MO", time: "AM", yards: [
        { homeYard: "CY", rows: [{ homeYard: "CY AM", cells: cells(11) }, { homeYard: "", cells: cells(0).map(() => 0) }], totals, message: null },
        { homeYard: "DY", rows: [{ homeYard: "DY AM", cells: cells(22) }], totals, message: null }] } }) });
    render(<AppProvider><ScramPage /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText(/Schedule DAY/)).toHaveValue("MO"));
    await userEvent.click(screen.getByRole("button", { name: "List Details" }));
    expect(await screen.findByText("CY AM")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/scram")[0].query)).toEqual({ day: "MO", time: "AM" });
    await userEvent.click(screen.getByRole("button", { name: "Nxt" }));
    expect(await screen.findByText("DY AM")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nxt" })).toBeDisabled();
  });
});

describe("Car status change (old VHCHG1I)", () => {
  it("reads the car, asks, and changes its status", async () => {
    const mw = fakeMiddleware({ ...csrf,
      "GET /api/v1/vehicle-queries/car-status-change/options": () => ({ body: { title: "", statuses: ["GO", "NO GO", "PM"], message: "Enter the Car Number",
        question: "DO YOU WISH TO MAKE THIS CHANGE" } }),
      "GET /api/v1/vehicle-queries/car-status-change/cars/1101": () => ({ body: { car: "1101", status: "NO GO", previousStatus: "GO", message: "Enter the New Vehicle Status" } }),
      "POST /api/v1/vehicle-queries/car-status-change/cars/1101": () => ({ body: { message: "The Vehicle Status Change Has Been Made" } }) });
    render(<AppProvider><CarStatusChangePage /></AppProvider>);
    expect(await screen.findByText("Enter the Car Number")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Car Number"), "1101");
    await userEvent.tab();
    expect(await screen.findByText("Enter the New Vehicle Status")).toBeInTheDocument();
    expect(screen.getAllByText("NO GO")).toHaveLength(2);                    // shown as the current status and offered in the list
    await userEvent.selectOptions(screen.getByLabelText("Change To Status"), "PM");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    await userEvent.click(await screen.findByRole("button", { name: "Yes" }));
    expect(await screen.findByText("The Vehicle Status Change Has Been Made")).toBeInTheDocument();
    expect(mw.calls("/api/v1/vehicle-queries/car-status-change/cars/1101").find(c => c.method === "POST")?.body).toEqual({ status: "PM" });
  });
});
