import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CommentsPage, InDoorPage, JobSplitPage, LaborPage, RepairDetailPage } from "./SecondaryRepairJobPages";

const LISTS = { workTypes: [{ code: "REPAIR", description: "REPAIR" }], repairAreas: [{ code: "R1", description: "ELECTRONICS" }], workType: "REPAIR" };
const JOB = { job: "B00042", status: "OPN", car: "1101", dept: "", equipmentType: "", equipmentId: "", nextDoc: 2, repairArea: "R1",
  subJobs: [{ doc: 1, repairArea: "R1", comment: "FIRST" }] };
const ui = (n: React.ReactNode) => render(<AppProvider>{n}</AppProvider>);

describe("In the door (old VHSR01I) and job split (VHSR08I)", () => {
  it("creates a job, then opens its job split and a sub job's comments", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/secondary-repair/in-door/options": () => ({ body: { ...LISTS, departments: ["A", "W"], equipmentIdDepartments: ["A", "D"] } }),
      "POST /api/v1/secondary-repair/jobs": () => ({ body: { job: "B00042", message: "Job No B00042 has been created.", overThreshold: false } }),
      "GET /api/v1/secondary-repair/job-split/options": () => ({ body: LISTS }),
      "GET /api/v1/secondary-repair/job-split/B00042": () => ({ body: JOB }),
      "GET /api/v1/secondary-repair/comments/kinds": () => ({ body: ["JOB", "REPAIR"] }),
      "GET /api/v1/secondary-repair/comments": () => ({ body: { job: "B00042", doc: 1, kind: "JOB", count: 0, comment: null } }) });
    ui(<InDoorPage />);
    await userEvent.type(await screen.findByLabelText("Car"), "1101");
    await userEvent.selectOptions(screen.getByLabelText("Rep Area"), "R1");
    await userEvent.type(screen.getByLabelText("Stock No"), "123456789");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Job No B00042 has been created.");
    expect(mw.calls("/api/v1/secondary-repair/jobs")[0].body).toMatchObject({ car: "1101", repairArea: "R1", stockNo: "123456789", workType: "REPAIR", quantity: 1 });
    await userEvent.click(screen.getByRole("button", { name: "Job Split" }));
    const table = await screen.findByRole("table", { name: "Previous sub job actions" });
    expect(screen.getByText("New sub job — doc 2")).toBeInTheDocument();
    await userEvent.click(within(table).getByRole("button", { name: "Comments" }));
    expect(await screen.findByText("No comments yet.")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/secondary-repair/comments")[0].query)).toEqual({ jobNo: "B00042", doc: "1", kind: "JOB" });
  });

  it("shows the old message of a refused sub job", async () => {
    fakeMiddleware({ "GET /api/v1/secondary-repair/job-split/options": () => ({ body: LISTS }),
      "GET /api/v1/secondary-repair/job-split/B00042": () => ({ body: JOB }),
      "POST /api/v1/secondary-repair/job-split/B00042/sub-jobs": () => ({ status: 400, body: { detail: "Stock No required" } }) });
    ui(<JobSplitPage jobNo="B00042" />);
    await userEvent.click(await screen.findByRole("button", { name: "Create" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Stock No required");
  });
});

describe("Comments (old VHSR02I)", () => {
  it("pages, changes and adds comments", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/secondary-repair/comments/kinds": () => ({ body: ["JOB", "REPAIR"] }),
      "GET /api/v1/secondary-repair/comments": () => ({ body: { job: "A1", doc: 1, kind: "REPAIR", count: 2, comment: { number: 1, text: "ONE", userGroup: "G", date: "", time: "" } } }),
      "GET /api/v1/secondary-repair/comments/2": () => ({ body: { number: 2, text: "TWO", userGroup: "G", date: "", time: "" } }),
      "POST /api/v1/secondary-repair/comments/2": () => ({ body: { message: "Job repr comment updated successfully" } }),
      "POST /api/v1/secondary-repair/comments": () => ({ body: { message: "Job repr comment added successfully.", comments: { job: "A1", doc: 1, kind: "REPAIR", count: 3,
        comment: { number: 3, text: "THREE", userGroup: "G", date: "", time: "" } } } }) });
    ui(<CommentsPage jobNo="A1" subJobDocNo={1} jobIndicator="REPAIR" />);
    await userEvent.click(await screen.findByRole("button", { name: "Nxt" }));
    expect(await screen.findByDisplayValue("TWO")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("updated successfully");
    await userEvent.type(screen.getByLabelText("New comment"), "THREE");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByDisplayValue("THREE")).toBeInTheDocument();
    expect(mw.calls("/api/v1/secondary-repair/comments").find(c => c.method === "POST")?.body).toEqual({ jobNo: "A1", doc: 1, kind: "REPAIR", text: "THREE" });
  });
});

describe("Repair detail (old VHSR09I) and labor detail (VHSR03I)", () => {
  it("adds repairs, goes on to the labor detail, adds labor and closes", async () => {
    const labor = { job: "A1", doc: 1, status: "REP", dateIn: "2026-11-01", dateOut: "2026-11-10", labor: [], canClose: true, note: null, comments: [{ number: 1, text: "C", userGroup: "G", date: "", time: "" }] };
    const mw = fakeMiddleware({
      "GET /api/v1/secondary-repair/repair-detail": () => ({ body: { job: "A1", doc: 1, status: "REP", repairs: [] } }),
      "POST /api/v1/secondary-repair/repair-detail": () => ({ body: { message: "Repair Details added.", detail: { job: "A1", doc: 1, status: "REP", repairs: [] } } }),
      "GET /api/v1/secondary-repair/labor": () => ({ body: labor }),
      "POST /api/v1/secondary-repair/labor/close": () => ({ body: { message: "Job Closed." } }) });
    ui(<RepairDetailPage />);
    await userEvent.type(await screen.findByLabelText("Job No"), "a1");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    await userEvent.type(await screen.findByLabelText("stockNo 1"), "444");
    await userEvent.click(screen.getByRole("button", { name: "Add Repair" }));
    expect(await screen.findByRole("table", { name: "Labor details" })).toBeInTheDocument();
    expect(mw.calls("/api/v1/secondary-repair/repair-detail").find(c => c.method === "POST")?.body).toMatchObject({ jobNo: "A1", doc: 1, noRepair: false,
      lines: [{ stockNo: "444", quantity: 1 }] });
    await userEvent.click(screen.getByRole("button", { name: "Close Job" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Job Closed.");
  });

  it("shows the old line messages of refused labor", async () => {
    fakeMiddleware({ "GET /api/v1/secondary-repair/labor": () => ({ body: { job: "A1", doc: 1, status: "REP", dateIn: "", dateOut: "", labor: [], canClose: false,
        note: "Repair Details required to Close this job.", comments: [] } }),
      "POST /api/v1/secondary-repair/labor": () => ({ status: 400, body: { detail: "Line: 01 - Empl No required" } }) });
    ui(<LaborPage jobNo="A1" subJobDocNo={1} />);
    expect(await screen.findByText("Repair Details required to Close this job.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Close Job" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Hours 1"), "2");
    await userEvent.click(screen.getByRole("button", { name: "Add Labor" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Line: 01 - Empl No required");
  });
});
