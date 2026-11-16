import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarLocationPage, ConsistMaintenancePage, TrainSwapPage } from "./ConsistMaintenancePages";

const result = (extra: object = {}) => ({ message: "", notices: [], question: null, fields: {}, cars: [], swaps: [], ...extra });

describe("consist maintenance (old VHMT01I, VHCM01I, VHCT01I, VHCC01I, VHSC01I, VHTS01I, VHCY01I)", () => {
  it("routes the seven old screens to these pages", () => {
    for (const code of ["VHMT01I", "VHCM01I", "VHCT01I", "VHCC01I", "VHSC01I", "VHTS01I", "VHCY01I"]) expect(CLEAN_PAGES[code]).toBeDefined();
  });

  it("make train sends the consist, date, time and cars, and shows the screen's message and cars", async () => {
    const mw = fakeMiddleware({ "POST /api/v1/consist/make-train": () => ({ body: result({ message: "TRAIN CREATED",
      cars: [{ position: "1", car: "1101", location: "CY", orientation: "<", softwareVersion: "", error: "" }] }) }) });
    render(<AppProvider><ConsistMaintenancePage kind="makeTrain" /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No"), "201");
    await userEvent.type(screen.getByLabelText("Cars 1"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByRole("status")).toHaveTextContent("TRAIN CREATED");
    expect(within(screen.getByRole("table", { name: "Cars" })).getByText("1101")).toBeInTheDocument();
    const sent = mw.calls("/api/v1/consist/make-train")[0].body as { consist: string; cars: string[] };
    expect(sent.consist).toBe("201");
    expect(sent.cars[0]).toBe("1101");
  });

  it("asks the old screen's question and sends the request again with the answer", async () => {
    let n = 0;
    const mw = fakeMiddleware({ "POST /api/v1/consist/clear-train": () => ({ body: n++ === 0
      ? result({ question: { name: "vhct01_con_trouble", text: "Consist has open trouble - want to continue (Y/N)?" } })
      : result({ message: "Clear Train completed" }) }) });
    render(<AppProvider><ConsistMaintenancePage kind="clear" /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No"), "201");
    await userEvent.click(screen.getByRole("button", { name: "Clear Train" }));
    await userEvent.click(await screen.findByRole("button", { name: /yes|ok/i }));
    expect(await screen.findByRole("status")).toHaveTextContent("Clear Train completed");
    const calls = mw.calls("/api/v1/consist/clear-train");
    expect(calls).toHaveLength(2);
    expect((calls[1].body as { answers: Record<string, boolean> }).answers.vhct01_con_trouble).toBe(true);
  });

  it("train swap sends the pairs and shows the swap list", async () => {
    fakeMiddleware({ "POST /api/v1/consist/swap": () => ({ body: result({ message: "Swap done", swaps: [{ "CON-FROM": "201", "CON-TO": "305" }] }) }) });
    render(<AppProvider><TrainSwapPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("From Consist"), "201");
    await userEvent.type(screen.getByLabelText("To Consist"), "305");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Swap done");
    expect(within(screen.getByRole("table", { name: "List of Swapped Consists" })).getByText("305")).toBeInTheDocument();
  });

  it("move a car looks the car up, then moves it", async () => {
    fakeMiddleware({
      "GET /api/v1/consist/car-location/1101": () => ({ body: { car: "1101", currentLocation: "CY", track: "", position: "", movable: true,
        message: "Enter the Move To Yard or Location" } }),
      "POST /api/v1/consist/car-location/1101/move": () => ({ body: { message: "CAR 1101 CHANGED FROM CY TO RY", notices: [], moved: true } }),
    });
    render(<AppProvider><CarLocationPage /></AppProvider>);
    await userEvent.type(await screen.findByRole("textbox", { name: "Car" }), "1101");
    await userEvent.tab();
    expect(await screen.findByText("CY")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("New Location"), "RY");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByText("CAR 1101 CHANGED FROM CY TO RY")).toBeInTheDocument();
  });
});
