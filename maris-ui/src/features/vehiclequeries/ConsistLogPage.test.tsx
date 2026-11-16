import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ConsistLogPage } from "./ConsistLogPage";

const consist = (n: number) => ({ consist: `2${String(n).padStart(2, "0")}-01`, origin: "CY01", outOfServiceLocation: "DY02", timeIn: "05:10:00",
  timeOut: "09:30:00", runTime: "04:20", swap: "", split: "", washed: "Y", carCount: 2, warning: n === 2,
  cars: [{ car: "<1101", shown: "$<1101", homeYard: "CY", warning: false }, { car: "1102>", shown: "1102>", homeYard: "HY", warning: true }] });
const base = {
  "GET /api/v1/vehicle-queries/consist-log/options": () => ({ body: { revenueDate: "2026-11-14", printers: [{ name: "DUBLIN TMZ", vtamName: "DUBLNHP1" }] } }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
};

describe("Consist log (old VHCL01I / VHCL02I)", () => {
  it("lists the consists four to a page", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/consist-log": () => ({ body: { criteria: { from: "201", to: "205", revenueDate: "11/12/2026",
      revenueDateDb2: "2026-11-12" }, consists: [1, 2, 3, 4, 5].map(consist), consistCount: 5, carCount: 10, pages: 2,
      message: "005 Consist(s) are selected and total number of cars is 0010" } }) });
    render(<AppProvider><ConsistLogPage /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText("Revenue Date")).toHaveValue("2026-11-14"));
    await userEvent.type(screen.getByLabelText("Consist No. From"), "201");
    await userEvent.type(screen.getByLabelText("Consist No. To"), "205");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByText("005 Consist(s) are selected and total number of cars is 0010")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/consist-log")[0].query)).toEqual({ from: "201", to: "205", revenueDate: "2026-11-14" });
    expect(screen.getByText("204-01")).toBeInTheDocument();
    expect(screen.queryByText("205-01")).toBeNull();
    expect(screen.getAllByText("$<1101")).toHaveLength(4);
    await userEvent.click(screen.getByRole("button", { name: "Nxt Page" }));
    expect(screen.getByText("205-01")).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
  });

  it("shows the old message for bad criteria", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/consist-log": () => ({ status: 400, body: { detail: "THE FROM CONSIST IS INVALID" } }) });
    render(<AppProvider><ConsistLogPage /></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("THE FROM CONSIST IS INVALID");
  });

  it("prints the log", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/consist-log/print": () => ({ body: { message: "Report submitted" } }) });
    render(<AppProvider><ConsistLogPage /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText("Revenue Date")).toHaveValue("2026-11-14"));
    await userEvent.type(screen.getByLabelText("Consist No. From"), "ALL");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/consist-log/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/consist-log/print")[0].body).toEqual({ from: "ALL", to: "", revenueDate: "2026-11-14", printer: "DUBLNHP1" });
  });
});
