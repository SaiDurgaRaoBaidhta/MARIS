import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ThoroughCleanPage } from "./ThoroughCleanPage";

const DEFAULTS = { yards: ["CY", "DY", "HY", "RY"], yard: "HY", date: "11-10-2026", time: "10.15", maxCars: 20 };
const base = { "GET /api/v1/car-bright/thorough-cleanings/defaults": () => ({ body: DEFAULTS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
const page = async () => { render(<AppProvider><ThoroughCleanPage /></AppProvider>); await screen.findByLabelText("Car 1"); };

describe("Car thorough clean (old VHCBP1I)", () => {
  it("opens with the old screen's yard, revenue date and time, and 20 car boxes", async () => {
    fakeMiddleware(base);
    await page();
    expect(screen.getByLabelText("Thorough Clean Location")).toHaveValue("HY");
    expect(screen.getByLabelText("Date Clean")).toHaveValue("11-10-2026");
    expect(screen.getByLabelText("Time Clean")).toHaveValue("10.15");
    expect(screen.getByLabelText("Car 20")).toBeInTheDocument();
  });

  it("adds the cars entered and starts again", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/car-bright/thorough-cleanings": () => ({ body: { message: "Car Thorough Cleaing records added successfully", cars: 2 } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Thorough Clean Location"), "CY");
    await userEvent.clear(screen.getByLabelText("Time Clean"));
    await userEvent.type(screen.getByLabelText("Time Clean"), "09:30");
    await userEvent.type(screen.getByLabelText("Car 1"), "1101");
    await userEvent.type(screen.getByLabelText("Car 3"), "1102");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("status")).toHaveTextContent("added successfully");
    const body = mw.calls("/api/v1/car-bright/thorough-cleanings")[0].body as { yard: string; time: string; cars: string[] };
    expect(body.yard).toBe("CY");
    expect(body.time).toBe("09:30");
    expect(body.cars.slice(0, 3)).toEqual(["1101", "", "1102"]);
    await waitFor(() => expect(screen.getByLabelText("Car 1")).toHaveValue(""));
  });

  it("shows the old screen's joined messages when the add is refused", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/car-bright/thorough-cleanings": () => ({ status: 400, body: { detail: "Time cannot be blank, First car no field cannot be blank" } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("First car no field cannot be blank");
  });
});
