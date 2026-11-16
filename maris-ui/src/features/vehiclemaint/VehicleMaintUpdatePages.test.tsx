import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarHoursTestPage, ConsistHistoryPage, ConsistStatusPage } from "./VehicleMaintUpdatePages";

const VM = "/api/v1/vehicle-maintenance";
const show = async (ui: React.ReactNode) => { render(<AppProvider>{ui}</AppProvider>); };
const CAR = { car: "1101", location: "HY01", cumulativeHours: 51234, hoursToNextPm: 40, hoursFromLastPm: 210, whereIsCar: "YRD" };
const COLUMNS = [{ heading: "Con No", field: "conNo" }, { heading: "Con Stat", field: "conStat" }, { heading: "Rev Date", field: "dateInsrvRev" }];
const ROWS = [
  { conNo: "201", conStat: "SRV", dashNo: 1, dateInsrv: "2026-11-10", timeInsrv: "05.00.00", dateInsrvRev: "2026-11-09", dateInsrvIcs: "", timeInsrvIcs: "", noCarsInd: "N", dateOutsrv: "" },
  { conNo: "202", conStat: "CON", dashNo: 2, dateInsrv: "2026-11-10", timeInsrv: "06.00.00", dateInsrvRev: "2026-11-10", dateInsrvIcs: "", timeInsrvIcs: "", noCarsInd: "", dateOutsrv: "" },
];
const STATUSES = ["CAR", "CON", "SRV", "SWP", "OUT"];

describe("Car hours test update (old VHVM08I)", () => {
  it("finds the car, previews the hours and sends the test hours", async () => {
    const mw = fakeMiddleware({
      [`GET ${VM}/car-hours/1101`]: () => ({ body: CAR }),
      [`POST ${VM}/car-hours/1101/test-hours`]: () => ({ body: { message: "The Vehicle Hours Have Been Updated", hours: CAR } }),
    });
    await show(<CarHoursTestPage />);
    await userEvent.type(await screen.findByLabelText("Enter Car Number"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    await waitFor(() => expect(screen.getByLabelText("Current Location")).toHaveValue("HY01"));   // the value arrives after the first render
    await userEvent.type(screen.getByLabelText("Enter the Test Hours"), "25");
    expect(screen.getByLabelText("New Hours to Next PM")).toHaveValue("15");
    expect(screen.getByLabelText("New Hours From Last PM")).toHaveValue("235");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    await waitFor(() => expect(mw.calls(`${VM}/car-hours/1101/test-hours`)).toHaveLength(1));
    expect(mw.calls(`${VM}/car-hours/1101/test-hours`)[0].body).toEqual({ testHours: "25" });
    expect(await screen.findByText("The Vehicle Hours Have Been Updated")).toBeInTheDocument();
  });

  it("shows the old screen's message for an unknown car", async () => {
    fakeMiddleware({ [`GET ${VM}/car-hours/9999`]: () => ({ status: 400, body: { detail: "Car Number Not Found" } }) });
    await show(<CarHoursTestPage />);
    await userEvent.type(await screen.findByLabelText("Enter Car Number"), "9999");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Car Number Not Found");
  });
});

describe("Consist status maintenance (old VHVM10I)", () => {
  it("retrieves the consists, and updates the selected one", async () => {
    const mw = fakeMiddleware({
      [`GET ${VM}/consist-status/options`]: () => ({ body: { statuses: STATUSES, columns: COLUMNS } }),
      [`GET ${VM}/consist-status`]: () => ({ body: { rows: ROWS, message: "" } }),
      [`POST ${VM}/consist-status/update`]: () => ({ body: { message: "The data record has been successfully Updated" } }),
    });
    await show(<ConsistStatusPage />);
    await userEvent.type(await screen.findByLabelText("Begin Consist"), "201");
    await waitFor(() => expect(screen.getByRole("button", { name: "Retrieve" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "Retrieve" }));
    const table = await screen.findByRole("table");
    expect(within(table).getAllByRole("columnheader").map(h => h.textContent)).toEqual(["Con No", "Con Stat", "Rev Date"]);
    expect(mw.calls(`${VM}/consist-status`)[0].query.get("from")).toBe("201");
    await userEvent.click(within(table).getByText("201"));
    await userEvent.selectOptions(screen.getByLabelText("Con Stat"), "OUT");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    await waitFor(() => expect(mw.calls(`${VM}/consist-status/update`)).toHaveLength(1));
    expect(mw.calls(`${VM}/consist-status/update`)[0].body).toMatchObject({ conNo: "201", status: "OUT", dateInsrvRev: "2026-11-09", dateOutsrv: "", noCarsInd: "N" });
    expect(await screen.findByText("The data record has been successfully Updated")).toBeInTheDocument();
    expect(mw.calls(`${VM}/consist-status`)).toHaveLength(2);
  });

  it("shows the full-list note", async () => {
    fakeMiddleware({
      [`GET ${VM}/consist-status/options`]: () => ({ body: { statuses: STATUSES, columns: COLUMNS } }),
      [`GET ${VM}/consist-status`]: () => ({ body: { rows: ROWS, message: "Max of 182 Rows Reached" } }),
    });
    await show(<ConsistStatusPage />);
    await userEvent.type(await screen.findByLabelText("Begin Consist"), "100");
    await waitFor(() => expect(screen.getByRole("button", { name: "Retrieve" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "Retrieve" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Max of 182 Rows Reached");
  });
});

describe("Consist history maintenance (old VHVM19I)", () => {
  it("starts on the revenue date and sends the selected record's key", async () => {
    const mw = fakeMiddleware({
      [`GET ${VM}/consist-history/options`]: () => ({ body: { revDate: "11-13-2026", statuses: STATUSES, columns: COLUMNS } }),
      [`GET ${VM}/consist-history`]: () => ({ body: { rows: ROWS, message: "" } }),
      [`POST ${VM}/consist-history/update`]: () => ({ body: { message: "The Consist History Record was Updated" } }),
    });
    await show(<ConsistHistoryPage />);
    expect(await screen.findByDisplayValue("11-13-2026")).toBeInTheDocument();
    await userEvent.type(await screen.findByLabelText("Begin Consist"), "201");
    await userEvent.click(screen.getByRole("button", { name: "Retrieve" }));
    expect(mw.calls(`${VM}/consist-history`)[0].query.get("revDate")).toBe("11-13-2026");
    await userEvent.click(await screen.findByText("202"));
    await userEvent.type(screen.getByLabelText("Date ICS"), "11-12-2026");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    await waitFor(() => expect(mw.calls(`${VM}/consist-history/update`)).toHaveLength(1));
    expect(mw.calls(`${VM}/consist-history/update`)[0].body).toMatchObject({ conNo: "202", dashNo: 2, dateInsrv: "2026-11-10", timeInsrv: "06.00.00",
      dateInsrvRev: "2026-11-10", status: "CON", dateInsrvIcs: "11-12-2026" });
    expect(await screen.findByText("The Consist History Record was Updated")).toBeInTheDocument();
  });
});
