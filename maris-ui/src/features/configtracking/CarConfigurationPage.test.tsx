import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarConfigurationPage } from "./CarConfigurationPage";

const OPTIONS = { actions: ["NO ACTION SELECTED", "LIST DATA", "GEOMETRY DATA"], trucks: ["Truck X", "Truck Y"], locations: ["CY", "HY"] };
const CAR = { message: "", homeYard: "HY", values: { "X-TRUCK-SERIAL": "TRKX1", "AXLE-SERIAL-01": "AX1", "FLANGE-1": "0.000" }, highlighted: {}, updatable: true, notices: [] };
const page = () => render(<AppProvider><CarConfigurationPage /></AppProvider>);

describe("car configuration (old VHCF01I, VFCF05I)", () => {
  it("is the page of both old screens", () => {
    for (const code of ["VHCF01I", "VFCF05I"]) expect(CLEAN_PAGES[code]).toBeDefined();
  });

  it("lists a car and sends only the changed values with the highlights to Update", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/config-tracking/cars/options": () => ({ body: OPTIONS }),
      "GET /api/v1/config-tracking/cars/1101": () => ({ body: CAR }),
      "POST /api/v1/config-tracking/cars/1101": () => ({ body: { ...CAR, message: "COMPONENT(S) SELECTED ARE REMOVED" } }),
    });
    page();
    await screen.findByRole("option", { name: "GEOMETRY DATA" });
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.selectOptions(screen.getByLabelText("Action"), "GEOMETRY DATA");
    await userEvent.click(screen.getByRole("button", { name: "List / Refresh" }));
    expect(await screen.findByDisplayValue("TRKX1")).toBeInTheDocument();
    const flange = screen.getByLabelText("FLANGE-1");
    await userEvent.clear(flange);
    await userEvent.type(flange, "1.25");
    await userEvent.click(screen.getByLabelText("highlight Axle Ser#1"));
    await userEvent.type(screen.getByLabelText("Employee No"), "12345");
    await userEvent.selectOptions(screen.getByLabelText("Location"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByText(/COMPONENT\(S\) SELECTED ARE REMOVED/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/config-tracking/cars/1101").at(-1)?.body).toEqual({
      action: "GEOMETRY DATA", truck: "X", values: { "FLANGE-1": "1.25" }, highlight: ["AXLE1"], employee: "12345", location: "HY",
    });
  });

  it("prints the car's report on the chosen printer", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/config-tracking/cars/options": () => ({ body: OPTIONS }),
      "GET /api/v1/config-tracking/printers": () => ({ body: { printers: [{ description: "SHOP", vtamName: "PRT01" }], warnings: [] } }),
      "POST /api/v1/config-tracking/cars/1101/print": () => ({ status: 202 }),
    });
    page();
    await userEvent.type(await screen.findByLabelText("Car No"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await userEvent.selectOptions(await screen.findByLabelText("Printer"), "PRT01");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    expect(await screen.findByText("Sent to the printer.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/config-tracking/cars/1101/print").at(-1)?.body).toEqual({ printer: "PRT01" });
  });
});
