import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { IcsReportPage } from "./IcsReportPage";

const OPTIONS = {
  revenueDate: "2026-11-12",
  printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1", name: "DUBLNHP1", server: "S1" }, { description: "IT", vtam: "LKS11HP1", name: "LKS11HP1", server: "S1" }],
  yards: [{ code: "CY", description: "CONCORD YARD" }, { code: "ALL", description: "ALL YARDS" }],
  locations: [{ code: "A07", description: "A05, A07 AND A09" }, { code: "ALL", description: "ALL LOCATIONS" }],
  messageCodes: [{ code: "1F", description: "TRAIN DISPATCH" }, { code: "ALL", description: "ALL MESSAGES" }],
  warnings: [],
};
const MESSAGE = { received: "11-10-2026 00:58:14", type: "1F", consist: "532-01", consistTo: "", size: 4, location: "C55", icsLocation: "045",
  direction: "S", inZone: "", created: "11-10-2026 00:57:10", icsCreated: "11-10-2026 00:57:10", sequence: 948143, processed: "Y",
  function: "AUTO MT", completed: "11-10-2026 00:59:11", revenueDate: "11-10-2026", cars: ["3198}"] };

const page = () => render(<AppProvider><IcsReportPage /></AppProvider>);

describe("ICS reports page (old VHIC30I / VHIC31I)", () => {
  it("loads the options: the revenue date, the lists and the printers", async () => {
    fakeMiddleware({ "GET /api/v1/ics/options": () => ({ body: OPTIONS }) });
    page();
    await waitFor(() => expect(screen.getByLabelText("Revenue date")).toHaveValue("2026-11-12"));   // the value arrives after the first render
    expect(screen.getByLabelText("Yard")).toHaveValue("ALL");
    expect(within(screen.getByLabelText("Printer")).getByRole("option", { name: "DUBLIN TMZ (DUBLNHP1)" })).toBeInTheDocument();
  });

  it("Display asks for the messages with the chosen criteria and lists them", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/ics/options": () => ({ body: OPTIONS }),
      "GET /api/v1/ics/messages": () => ({ body: { revenueDate: "2026-11-10", count: 1, maxRowsReached: false, messages: [MESSAGE] } }),
    });
    page();
    const date = await screen.findByLabelText("Revenue date");
    await userEvent.clear(date);
    await userEvent.type(date, "2026-11-10");
    await userEvent.selectOptions(screen.getByLabelText("Yard"), "CY");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));

    const table = await screen.findByRole("table", { name: /ICS messages/ });
    expect(within(table).getByText("532-01")).toBeInTheDocument();
    expect(screen.getByText(/1 message/)).toBeInTheDocument();
    const q = mw.calls("/api/v1/ics/messages")[0].query;
    expect(Object.fromEntries(q)).toEqual({ revenueDate: "2026-11-10", order: "time", groupBy: "yard", yard: "CY", location: "ALL", messageCode: "ALL" });
  });

  it("shows the middleware's message when the date is refused", async () => {
    fakeMiddleware({
      "GET /api/v1/ics/options": () => ({ body: OPTIONS }),
      "GET /api/v1/ics/messages": () => ({ status: 400, body: { code: "INVALID_INPUT", detail: "You Have Entered an Invalid Date or A Future Date" } }),
    });
    page();
    await screen.findByLabelText("Revenue date");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("You Have Entered an Invalid Date or A Future Date");
    expect(screen.queryByRole("table", { name: /ICS messages/ })).not.toBeInTheDocument();
  });

  it("follows the old screen's rules: by location turns the yard list off; consist order turns message codes off", async () => {
    fakeMiddleware({ "GET /api/v1/ics/options": () => ({ body: OPTIONS }) });
    page();
    await screen.findByLabelText("Revenue date");
    expect(screen.getByLabelText("Location")).toBeDisabled();
    await userEvent.click(screen.getByLabelText("By location"));
    expect(screen.getByLabelText("Location")).toBeEnabled();
    expect(screen.getByLabelText("Yard")).toBeDisabled();
    await userEvent.click(screen.getByLabelText("Consist, date / time received"));
    expect(screen.getByLabelText("Message code")).toBeDisabled();
  });

  it("Print needs a printer, then sends the report to it", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/ics/options": () => ({ body: OPTIONS }),
      "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/ics/messages/print": () => ({ body: { message: "The Report You Requested Has Been Submitted, You Make Pick It Up at your Selected printer" } }),
    });
    page();
    await screen.findByLabelText("Revenue date");
    expect(screen.getByRole("button", { name: "Print" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "LKS11HP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/ics/messages/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/ics/messages/print")[0].body).toMatchObject({ revenueDate: "2026-11-12", printer: "LKS11HP1", yard: "ALL" });
    expect(await screen.findByText(/Has Been Submitted/)).toBeInTheDocument();
  });

  it("shows a list that failed to load as a warning", async () => {
    fakeMiddleware({ "GET /api/v1/ics/options": () => ({ body: { ...OPTIONS, locations: [], warnings: ["ICS LOCATIONS Is Not Found"] } }) });
    page();
    expect(await screen.findByText("ICS LOCATIONS Is Not Found")).toBeInTheDocument();
  });
});
