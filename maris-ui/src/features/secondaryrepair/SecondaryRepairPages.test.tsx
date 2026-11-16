import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { JobStatusPage, ReprintPage, StockInquiryPage } from "./SecondaryRepairPages";

const JOB = { job: "A12345", status: "OPN", car: "1101", dept: "", equipmentType: "", equipmentLocation: "", equipmentId: "", incident: "", printControlNo: "", openDocs: 2, count: 2,
  subJobs: [{ doc: 1, stockNo: "STK", serialNo: "S1", faultCode: "", quantityIn: 1, quantityOut: 0, quantityScrapped: 0, dateIn: "", dateOut: "", area: "", workType: "", status: "OPN" },
            { doc: 2, stockNo: "STK", serialNo: "S2", faultCode: "", quantityIn: 1, quantityOut: 0, quantityScrapped: 0, dateIn: "", dateOut: "", area: "", workType: "", status: "OPN" }] };
const details = (doc: number) => ({ job: "A12345", doc, narrative: [`NARR ${doc}`], repairNarrative: [], repairs: [], labor: [] });

describe("Stock inquiry (old VHSR05I) and job history (VHSR10I)", () => {
  it("lists the jobs of a stock and opens a job's history", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/secondary-repair/stock-inquiry/options": () => ({ body: { statuses: [{ code: "OUT", description: "OUT THE DOOR" }], longTermStorage: ["Y", "N"] } }),
      "GET /api/v1/secondary-repair/stock-inquiry": () => ({ body: { stockNo: "STK123", description: "BRAKE CALIPER", count: 1, totalQuantityIn: 2, note: null, message: null,
        jobs: [{ job: "A12345", doc: 1, serialNo: "S1", quantityIn: 2, from: "", workType: "", dateIn: "", carOrEquipment: "", area: "", status: "OPN", dateOut: "" }] } }),
      "GET /api/v1/secondary-repair/jobs/A12345/history": () => ({ body: JOB }),
      "GET /api/v1/secondary-repair/jobs/A12345/sub-jobs/1": () => ({ body: details(1) }) });
    render(<AppProvider><StockInquiryPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Stock No"), "stk123");
    await userEvent.selectOptions(screen.getByLabelText("Sub job status"), "OUT");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("BRAKE CALIPER")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/secondary-repair/stock-inquiry")[0].query)).toEqual({ stockNo: "STK123", status: "OUT" });
    await userEvent.click(within(screen.getByRole("table", { name: "Jobs" })).getByRole("button", { name: "A12345" }));
    expect(await screen.findByText("NARR 1")).toBeInTheDocument();
  });

  it("shows the old message", async () => {
    fakeMiddleware({ "GET /api/v1/secondary-repair/stock-inquiry/options": () => ({ body: { statuses: [], longTermStorage: ["Y", "N"] } }),
      "GET /api/v1/secondary-repair/stock-inquiry": () => ({ status: 400, body: { detail: "Stock Number does not exist" } }) });
    render(<AppProvider><StockInquiryPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Stock No"), "X");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Stock Number does not exist");
  });
});

describe("Job status (old VHSR11I)", () => {
  it("lists an open job and pages through its sub jobs", async () => {
    fakeMiddleware({ "GET /api/v1/secondary-repair/jobs/A12345": () => ({ body: JOB }),
      "GET /api/v1/secondary-repair/jobs/A12345/sub-jobs/1": () => ({ body: details(1) }), "GET /api/v1/secondary-repair/jobs/A12345/sub-jobs/2": () => ({ body: details(2) }) });
    render(<AppProvider><JobStatusPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Job No"), "A12345");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("NARR 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByText("NARR 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });
});

describe("Reprint job form (old VHSR15I)", () => {
  it("uses the user group's default printer and submits", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/secondary-repair/reprint/options": () => ({ body: { printers: [{ description: "SHOP", vtam: "SHOPHP1" }], defaultPrinter: "SHOPHP1" } }),
      "GET /api/v1/auth/csrf": () => ({ status: 204 }), "POST /api/v1/secondary-repair/jobs/A1/reprint": () => ({ body: { message: "REPRINT FORM JOB HAS BEEN SUBMITTED" } }) });
    render(<AppProvider><ReprintPage /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText("Printer")).toHaveValue("SHOPHP1"));
    await userEvent.type(screen.getByLabelText("Job No"), "a1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/secondary-repair/jobs/A1/reprint")).toHaveLength(1));
    expect(mw.calls("/api/v1/secondary-repair/jobs/A1/reprint")[0].body).toEqual({ doc: 1, printer: "SHOPHP1" });
  });
});
