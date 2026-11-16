import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { AddCarPage, ChangeCarPage, StatusHistoryPage } from "./CarPages";

const OPTIONS = {
  carTypes: ["B2", "C2"], addCarTypes: ["B2", "C2"], statuses: ["GO", "PM", "STOR"], previousStatuses: ["GO", "PM"], homeYards: ["CY", "DY", "HY"],
  yesNo: ["Y", "N"], batteryLocations: ["CY", "HY"], lastPmLocations: ["CY"], whereIsCar: ["YRD"], locations: ["CY01"],
  cameras: [{ code: "H", description: "HIGH DEF" }], orientations: { C2: ["<", ">"] }, schedulePms: ["", "1", "2"], configStatuses: ["ACTIVE"],
  configTypes: ["CBTC"], addDefaults: { carType: "B2", vehStat: "STOR", dateBatteryRepl: "2026-11-15" },
};
const base = { "GET /api/v1/vehicle-maintenance/cars/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
const show = (ui: React.ReactNode) => render(<AppProvider>{ui}</AppProvider>);

describe("Add vehicle (old VHVM02I)", () => {
  it("sends the filled fields and shows the message", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-maintenance/cars": () => ({ body: { message: "The Vehicle Was Added" } }) });
    show(<AddCarPage />);
    await userEvent.type(await screen.findByLabelText("Car No"), "1999");
    await userEvent.selectOptions(screen.getByLabelText("Car Type"), "C2");
    await userEvent.selectOptions(screen.getByLabelText("Orient"), "<");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("status")).toHaveTextContent("The Vehicle Was Added");
    expect(mw.calls("/api/v1/vehicle-maintenance/cars")[0].body).toEqual({ carNo: "1999", carType: "C2", vagenOrientation: "<", dateBatteryRepl: "2026-11-15" });
  });

  it("Clear puts the old screen's defaults in, and errors show", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/vehicle-maintenance/cars": () => ({ status: 400, body: { detail: "You are missing values" } }) });
    show(<AddCarPage />);
    await userEvent.click(await screen.findByRole("button", { name: "Clear" }));
    expect(screen.getByLabelText("Veh Stat")).toHaveValue("STOR");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("You are missing values");
  });
});

describe("Update vehicle (old VHVM03I)", () => {
  it("locks what the group may not change and sends only the changes", async () => {
    const mw = fakeMiddleware({
      ...base,
      "GET /api/v1/vehicle-maintenance/cars/1101": () => ({ body: { fields: { carNo: "1101", vehStat: "GO", homeYrd: "CY", carType: "C2" }, editable: ["homeYrd"] } }),
      "POST /api/v1/vehicle-maintenance/cars/1101/changes": () => ({ body: { message: "Your Car Has Been Updated" } }),
    });
    show(<ChangeCarPage />);
    await userEvent.type(await screen.findByLabelText("Car No"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Find Car" }));
    expect(await screen.findByLabelText("Veh Stat")).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Home Yrd"), "DY");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Your Car Has Been Updated");
    expect(mw.calls("/api/v1/vehicle-maintenance/cars/1101/changes")[0].body).toEqual({ homeYrd: "DY" });
  });
});

describe("Incident status history (old VHVM06I)", () => {
  const entry = (timestamp: string, previousStatus: string, newStatus: string) => ({ incident: "29990010", timestamp, date: "2026-11-10", time: "10:15:00",
    previousStatus, newStatus, yard: "", subLocation: "", pseudoConsist: "", moveReason: "", homeYard: "CY", function: "VHVM06", action: "", userGroup: "BRIGHTCO",
    dateLastUpdated: "", terminalLastUpdated: "", timeLastUpdated: "" });
  const routes = {
    "GET /api/v1/auth/csrf": () => ({ status: 204 }),
    "GET /api/v1/vehicle-maintenance/status-history/options": () => ({ body: { yards: ["CY"], statuses: ["OPN", "WIP"], moveReasons: ["M1"] } }),
    "GET /api/v1/vehicle-maintenance/status-history/29990010": () => ({ body: [entry("TS1", "", "OPN"), entry("TS2", "OPN", "WIP")] }),
  };

  it("lists the entries, changes a picked one and deletes one", async () => {
    const mw = fakeMiddleware({ ...routes,
      "POST /api/v1/vehicle-maintenance/status-history/29990010/changes": () => ({ body: { message: "Updated", entries: [entry("TS1", "", "OPN")] } }),
      "POST /api/v1/vehicle-maintenance/status-history/29990010/delete": () => ({ body: { message: "DELETED", entries: [] } }) });
    show(<StatusHistoryPage />);
    await userEvent.type(await screen.findByLabelText("Incd No"), "29990010");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    const table = await screen.findByRole("table");
    expect(screen.getByRole("button", { name: "Update" })).toBeDisabled();
    await userEvent.click(within(table).getByText("TS2"));
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();
    await userEvent.clear(screen.getByLabelText("Psuedo Con No"));
    await userEvent.type(screen.getByLabelText("Psuedo Con No"), "201");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Updated");
    expect(mw.calls("/api/v1/vehicle-maintenance/status-history/29990010/changes")[0].body).toMatchObject({ timestamp: "TS2", pseudoConsist: "201", newStatus: "WIP" });
    await userEvent.click(within(screen.getByRole("table")).getByText("TS1"));
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-maintenance/status-history/29990010/delete")[0].body).toEqual({ timestamp: "TS1" }));
  });

  it("shows the old screen's checks", async () => {
    fakeMiddleware({ ...routes, "POST /api/v1/vehicle-maintenance/status-history/29990010": () => ({ status: 400, body: { detail: "You have entered an Invalid date" } }) });
    show(<StatusHistoryPage />);
    await userEvent.type(await screen.findByLabelText("Incd No"), "29990010");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    await userEvent.click(await screen.findByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("You have entered an Invalid date");
  });
});
