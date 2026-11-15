import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ConsistSymptomPage, StatusTrainPage } from "./ConsistTrackingPage";

const BASE = "/api/v1/vehicle-queries/consist-tracking";
const OPTIONS = { reports: ["Summary List", "Car Details"], printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] };
const TRAINS = { from: "100", to: "999", trains: [{ consist: "201-01", origin: "Concord", dateInService: "11-10-2026", timeInService: "05:10:00", mark: "TI",
  orderLocation: "M1-N", orientation: "A", warning: false, cars: [
    { car: "1101", leftMark: "L", rightMark: "", homeYard: "CY", status: "GO", alert: false, usable: false, warning: false },
    { car: "1102", leftMark: "", rightMark: "R", homeYard: "CY", status: "NO GO", alert: true, usable: false, warning: true }] }] };
const DETAILS = { from: "201", to: "201", trains: [{ consist: "201-01", origin: "Richmond", dateInService: "10-01-2026", timeInService: "06:00:00",
  cars: [{ car: "1101", homeYard: "RY", pm: "00121", washDays: 0, clean: "BST" }, { car: "1102", homeYard: "RY", pm: "PMS", washDays: 3, clean: "BS" }] }] };

describe("Consist tracking (old VHST05I / VHST02I / VHST04I)", () => {
  it("shows the summary list for ALL consists", async () => {
    const mw = fakeMiddleware({ [`GET ${BASE}/options`]: () => ({ body: OPTIONS }), [`GET ${BASE}/status-trains`]: () => ({ body: TRAINS }) });
    render(<AppProvider><StatusTrainPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No. From"), "ALL");
    await userEvent.click(await screen.findByLabelText("Summary List"));
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const consist = await screen.findByRole("article", { name: "Consist 201-01" });
    expect(consist).toHaveTextContent("Concord");
    expect(consist).toHaveTextContent("TI");
    expect(within(consist).getByText(/L1101/)).toBeInTheDocument();
    expect(within(consist).getByText(/1102R/)).toHaveClass("text-red-700");
    expect(mw.calls(`${BASE}/status-trains`)[0].query.get("from")).toBe("ALL");
  });

  it("shows each car's PM, wash and cleaning for the car details report", async () => {
    fakeMiddleware({ [`GET ${BASE}/options`]: () => ({ body: OPTIONS }), [`GET ${BASE}/car-details`]: () => ({ body: DETAILS }) });
    render(<AppProvider><StatusTrainPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No. From"), "201");
    await userEvent.click(await screen.findByLabelText("Car Details"));
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Cars of 201-01" });
    expect(table).toHaveTextContent("00121");
    expect(table).toHaveTextContent("PMS");
    expect(table).toHaveTextContent("BST");
  });

  it("shows the old message for a bad range and prints to the chosen printer", async () => {
    const mw = fakeMiddleware({
      [`GET ${BASE}/options`]: () => ({ body: OPTIONS }),
      [`GET ${BASE}/status-trains`]: () => ({ status: 400, body: { detail: "THE TO CONSIST NUMBER CANNOT BE LESS THAN FROM CONSIST" } }),
      [`POST ${BASE}/print`]: () => ({ body: { message: "Report submitted." } }) });
    render(<AppProvider><StatusTrainPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No. From"), "300");
    await userEvent.type(screen.getByLabelText("Consist No. To"), "200");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("CANNOT BE LESS");
    await userEvent.selectOptions(await screen.findByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Report submitted.");
    expect(mw.calls(`${BASE}/print`)[0].body).toEqual({ report: 1, from: "300", to: "200", printer: "DUBLNHP1" });
  });
});

describe("Consist symptom view (old VHST01I / VHST03I)", () => {
  it("shows the consist's cars with the symptom counts", async () => {
    fakeMiddleware({ [`GET ${BASE}/options`]: () => ({ body: OPTIONS }), [`GET ${BASE}/symptoms`]: () => ({ body: { consist: "201", symptom: "0120",
      dateFrom: "2026-01-01", dateTo: "2026-11-13", trains: [{ consist: "201-01", origin: "Daly City", dateInService: "11-10-2026", timeInService: "06:00:00", mark: "",
        cars: [{ car: "1101", leftMark: "L", rightMark: "", software: true, symptomCount: 3, homeYard: "DY", alert: false, usable: true }] }] } }) });
    render(<AppProvider><ConsistSymptomPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No."), "201");
    await userEvent.type(screen.getByLabelText("Symptom Code"), "0120");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Cars of 201-01" });
    expect(within(table).getByText("$L1101")).toHaveClass("text-green-700");
    expect(table).toHaveTextContent("3");
  });
});
