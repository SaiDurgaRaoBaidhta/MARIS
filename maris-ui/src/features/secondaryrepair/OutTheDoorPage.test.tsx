import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { OutTheDoorPage } from "./OutTheDoorPage";

const options = { doc: 1, faultCodes: ["F01", "F02"], workTypes: [{ code: "BENCH", description: "BENCH TEST" }] };
const open = { jobNo: "J12345", doc: 2, status: "OPN", canGoOut: true, message: "", dateIn: "11-01-2026", qtyIn: 1, dateOut: "2026-11-14", qtyOut: 1, qtyScrapped: 0 };

describe("Sub job out the door (old VHSR04I)", () => {
  it("finds an open sub job, fills the old defaults and logs it out deferred", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/secondary-repair/out-the-door/options": () => ({ body: options }),
      "GET /api/v1/secondary-repair/out-the-door/sub-job": () => ({ body: open }),
      "POST /api/v1/secondary-repair/out-the-door": () => ({ body: { message: "Sub Job J12345 - 02 has been successfully logged Out The Door  with a Deferred status.", status: "DEF" } }),
    });
    render(<AppProvider><OutTheDoorPage /></AppProvider>);
    await screen.findByRole("option", { name: "F02" });
    expect(screen.getByLabelText("Doc No.")).toHaveValue("1");
    expect(screen.getByRole("button", { name: "Update" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Job No."), "J12345");
    await userEvent.clear(screen.getByLabelText("Doc No."));
    await userEvent.type(screen.getByLabelText("Doc No."), "2");
    await userEvent.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByLabelText("Date Out")).toHaveValue("2026-11-14");
    expect(screen.getByLabelText("Qty Out")).toHaveValue("1");
    await userEvent.selectOptions(screen.getByLabelText("Diagnostic Fault"), "F02");
    await userEvent.selectOptions(screen.getByLabelText("Diagnostic Work Type"), "BENCH");
    await userEvent.click(screen.getByLabelText("Defer Sub Job"));
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("with a Deferred status.");
    expect(mw.calls("/api/v1/secondary-repair/out-the-door").at(-1)?.body).toMatchObject({ jobNo: "J12345", doc: 2, faultCode: "F02", workType: "BENCH",
      disposition: "DEFERRED", vendorId: "" });
  });

  it("shows why a sub job cannot go out and keeps Update off", async () => {
    fakeMiddleware({
      "GET /api/v1/secondary-repair/out-the-door/options": () => ({ body: options }),
      "GET /api/v1/secondary-repair/out-the-door/sub-job": () => ({ body: { ...open, status: "CLO", canGoOut: false, message: "Sub Job closed.", dateOut: null } }),
    });
    render(<AppProvider><OutTheDoorPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Job No."), "J12345");
    await userEvent.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByText("Sub Job closed.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Update" })).toBeDisabled();
  });

  it("asks for the vendor only when going out to a vendor, and shows the joined old messages", async () => {
    fakeMiddleware({
      "GET /api/v1/secondary-repair/out-the-door/options": () => ({ body: options }),
      "GET /api/v1/secondary-repair/out-the-door/sub-job": () => ({ body: open }),
      "POST /api/v1/secondary-repair/out-the-door": () => ({ status: 400, body: { detail: "Invalid Fault Cd, Vendor Id required", errors: ["Invalid Fault Cd.", "Vendor Id required."] } }),
    });
    render(<AppProvider><OutTheDoorPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Job No."), "J12345");
    await userEvent.click(screen.getByRole("button", { name: "Find" }));
    await screen.findByLabelText("Date Out");
    expect(screen.queryByLabelText("Vendor Id")).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Out To Vendor"));
    expect(screen.getByLabelText("Vendor Id")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Fault Cd, Vendor Id required");
  });
});
