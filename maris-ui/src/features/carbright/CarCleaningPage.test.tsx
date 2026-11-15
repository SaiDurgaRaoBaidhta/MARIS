import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarCleaningPage } from "./CarCleaningPage";

const YARDS = ["CY", "DY", "HY", "RY", "ALL"];
const PRINTERS = [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }];
const LIST = { homeYard: "CY", option: "last", cleaningDate: "2026-08-15", overdueDate: "2026-05-15", count: 2, message: null,
  cars: [{ car: "1101", location: "CY01", status: "GO", trainIncident: "", lastThoroughClean: "2026-07-01" },
         { car: "1102", location: "CY02", status: "NO GO", trainIncident: "T", lastThoroughClean: "2026-06-01" }] };
const base = { "GET /api/v1/car-bright/home-yards": () => ({ body: YARDS }), "GET /api/v1/car-bright/printers": () => ({ body: PRINTERS }) };
const page = async () => { render(<AppProvider><CarCleaningPage /></AppProvider>); await screen.findByLabelText("Home yard"); };

describe("Car thorough cleaning (old VHCBR1I / VHCBR2I)", () => {
  it("lists the cars for a home yard and option", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/car-bright/cleaning": () => ({ body: LIST }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "CY");
    await userEvent.click(screen.getByLabelText("Cleaning over due"));
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Cars" });
    expect(within(table).getByText("1102")).toBeInTheDocument();
    expect(within(table).getByText("NO GO T")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/car-bright/cleaning")[0].query)).toEqual({ homeYard: "CY", option: "overdue" });
  });

  it("asks for a home yard first, like the old screen", async () => {
    const mw = fakeMiddleware(base);
    await page();
    expect(screen.getByRole("button", { name: "List" })).toBeDisabled();
    expect(mw.calls("/api/v1/car-bright/cleaning")).toHaveLength(0);
  });

  it("shows the old message when no car is found", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/car-bright/cleaning": () => ({ body: { ...LIST, count: 0, cars: [], message: "No records found." } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("No records found.")).toBeInTheDocument();
  });

  it("shows the middleware's error", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/car-bright/cleaning": () => ({ status: 422, body: { detail: "Date car brit is not retrieved." } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Date car brit is not retrieved.");
  });

  it("prints on the chosen printer", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/car-bright/cleaning/print": () => ({ body: { message: "The report is submitted" } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "DY");
    expect(screen.getByRole("button", { name: "Print" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/car-bright/cleaning/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/car-bright/cleaning/print")[0].body).toEqual({ homeYard: "DY", option: "last", printer: "DUBLNHP1" });
  });
});
