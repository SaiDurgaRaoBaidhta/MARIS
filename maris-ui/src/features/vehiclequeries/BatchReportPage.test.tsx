import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { BatchReportPage } from "./BatchReportPage";

const PRINTERS = [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }];
const option = (report: string, button: string, extra: object = {}) => ({
  report, screen: "VHMC01I", title: "VHMC01I - MT/CT and Yard Tracking Report", button, dates: true, fromDate: "2026-10-01", toDate: "2026-10-31",
  yards: [{ code: "CY", label: "CY -Concord Yard" }, { code: "HY", label: "HY -Hayward Yard" }], yardRequired: true, minutes: 10, printers: PRINTERS, ...extra,
});
const base = {
  "GET /api/v1/vehicle-queries/batch-reports/clear-train-moves/options": () => ({ body: option("clear-train-moves", "Clear Train - Print") }),
  "GET /api/v1/vehicle-queries/batch-reports/make-train-moves/options": () => ({ body: option("make-train-moves", "Make Train - Print") }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
};
const page = async () => {
  render(<AppProvider><BatchReportPage code="VHMC01I" reports={["clear-train-moves", "make-train-moves"]} /></AppProvider>);
  await screen.findByLabelText("Printer");
};

describe("Batch print reports (old VHMC01I and the other report screens)", () => {
  it("starts with the old screen's dates, first yard and minutes, and one button per report", async () => {
    fakeMiddleware(base);
    await page();
    expect(screen.getByText("VHMC01I - MT/CT and Yard Tracking Report")).toBeInTheDocument();
    expect(screen.getByLabelText("From date")).toHaveValue("2026-10-01");
    expect(screen.getByLabelText("Yard")).toHaveValue("CY");
    expect(screen.getByLabelText("Minutes greater than")).toHaveValue(10);
    expect(screen.getByRole("button", { name: "Clear Train - Print" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Make Train - Print" })).toBeDisabled();
  });

  it("prints the chosen report with the criteria", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/batch-reports/make-train-moves/print": () => ({ body: { message: "Job is submitted" } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Yard"), "HY");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Make Train - Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/batch-reports/make-train-moves/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/batch-reports/make-train-moves/print")[0].body)
      .toEqual({ fromDate: "2026-10-01", toDate: "2026-10-31", yard: "HY", minutes: 10, printer: "DUBLNHP1" });
    expect(await screen.findByText("Job is submitted")).toBeInTheDocument();
  });

  it("shows the old screen's message for bad criteria", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/batch-reports/clear-train-moves/print": () => ({ status: 400, body: { detail: "INVALID TO DATE" } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Clear Train - Print" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("INVALID TO DATE");
  });

  it("hides what a report does not have", async () => {
    fakeMiddleware({ "GET /api/v1/vehicle-queries/batch-reports/combined-trains/options": () => ({ body: option("combined-trains", "Hard Copy", { dates: true, yards: [], yardRequired: false, minutes: null }) }) });
    render(<AppProvider><BatchReportPage code="VHOT01I" reports={["combined-trains"]} /></AppProvider>);
    await screen.findByLabelText("Printer");
    expect(screen.queryByLabelText("Yard")).toBeNull();
    expect(screen.queryByLabelText("Minutes greater than")).toBeNull();
    expect(screen.getByRole("button", { name: "Hard Copy" })).toBeInTheDocument();
  });
});
