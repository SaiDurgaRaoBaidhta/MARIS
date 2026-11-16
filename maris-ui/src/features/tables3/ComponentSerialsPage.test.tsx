import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ComponentSerialsPage } from "./ComponentSerialsPage";

const OPTIONS = { stocks: [{ stock: "123456789", description: "TRUCK ASSY", text: "123456789 TRUCK ASSY" }] };
const ROW = { stockNo: "123456789", cmpntSerialNo: "SN1", carNo: "1101", homeYrd: "CY", dateAxle: "2020-01-02", serialStat: "ACT" };
const FORM = { form: { ...ROW, dateAxle: "01-02-2020" }, comments: "OLD", lists: { VHCF50B: [{ code: "CY", text: "CY" }, { code: "HY", text: "HY" }] }, warnings: [] };
const page = () => render(<AppProvider><ComponentSerialsPage /></AppProvider>);

describe("component serials (old VHCF49I, VHCF50I, VHCF51I, VHCF52I)", () => {
  it("is the page of all four old screens", () => {
    for (const code of ["VHCF49I", "VHCF50I", "VHCF51I", "VHCF52I"]) expect(CLEAN_PAGES[code]).toBeDefined();
  });

  it("lists with the criteria and shows the old message", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/tables-3/component-serials/options": () => ({ body: OPTIONS }),
      "GET /api/v1/tables-3/component-serials": () => ({ body: { rows: [ROW], message: "MORE THAN 90 ROWS FOR SELECTED CRITERIA" } }),
    });
    page();
    await screen.findByRole("option", { name: "123456789 TRUCK ASSY" });
    await userEvent.selectOptions(screen.getByLabelText("Stock Number"), "123456789");
    await userEvent.type(screen.getByLabelText("Car Number"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(within(await screen.findByRole("table", { name: "Component serials" })).getByText("SN1")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("MORE THAN 90 ROWS");
    expect(Object.fromEntries(mw.calls("/api/v1/tables-3/component-serials")[0].query)).toEqual({ car: "1101", stock: "123456789", serial: "" });
  });

  it("updates a row: sends the row as listed, the edited form and the comments", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/tables-3/component-serials/options": () => ({ body: OPTIONS }),
      "GET /api/v1/tables-3/component-serials": () => ({ body: { rows: [ROW] } }),
      "POST /api/v1/tables-3/component-serials/form": () => ({ body: FORM }),
      "POST /api/v1/tables-3/component-serials/update": () => ({ body: { ok: true, message: "UPDATE SUCCESSFUL" } }),
    });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    await userEvent.click(within(await screen.findByRole("table", { name: "Component serials" })).getByText("SN1"));
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    const axle = await screen.findByLabelText("Date Axle");
    expect(axle).toHaveValue("01-02-2020");
    expect(screen.getByLabelText("Stock No")).toBeDisabled();
    await userEvent.clear(axle);
    await userEvent.type(axle, "01-05-2020");
    await userEvent.selectOptions(screen.getByLabelText("Home Yrd"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("UPDATE SUCCESSFUL")).toBeInTheDocument();
    const body = mw.calls("/api/v1/tables-3/component-serials/update")[0].body as { original: Record<string, string>; form: Record<string, string>; comments: string };
    expect(body.original).toEqual(ROW);
    expect(body.form).toMatchObject({ dateAxle: "01-05-2020", homeYrd: "HY" });
    expect(body.comments).toBe("OLD");
  });

  it("shows a refused update's message", async () => {
    fakeMiddleware({
      "GET /api/v1/tables-3/component-serials/options": () => ({ body: OPTIONS }),
      "GET /api/v1/tables-3/component-serials": () => ({ body: { rows: [ROW] } }),
      "POST /api/v1/tables-3/component-serials/form": () => ({ body: FORM }),
      "POST /api/v1/tables-3/component-serials/update": () => ({ status: 422, body: { ok: false, message: "DATE AXLE NOT VALID", detail: "DATE AXLE NOT VALID" } }),
    });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    await userEvent.click(within(await screen.findByRole("table", { name: "Component serials" })).getByText("SN1"));
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    await userEvent.click(await screen.findByRole("button", { name: "Save" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("DATE AXLE NOT VALID");
  });

  it("opens the serial hours of every serial when no row is picked, and updates one", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/tables-3/component-serials/options": () => ({ body: OPTIONS }),
      "GET /api/v1/tables-3/component-serials": () => ({ body: { rows: [ROW] } }),
      "GET /api/v1/tables-3/serial-hours": () => ({ body: { rows: [{ stockNo: "123456789", cmpntSerialNo: "SN1", cmpntCumHrs: 1200, cmpntCumMin: 30, dateHrsPosted: "2024-02-03" }] } }),
      "POST /api/v1/tables-3/serial-hours/update": () => ({ body: { ok: true, message: "UPDATE SUCCESFUL" } }),
    });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    await userEvent.click(await screen.findByRole("button", { name: "Serial No Hrs" }));
    expect(Object.fromEntries(mw.calls("/api/v1/tables-3/serial-hours")[0].query)).toEqual({ stock: "", serial: "" });
    await userEvent.click(within(await screen.findByRole("table", { name: "Hours" })).getByText("1200"));
    const hours = screen.getByLabelText("Cum hours");
    await userEvent.clear(hours);
    await userEvent.type(hours, "1300");
    await userEvent.click(screen.getByRole("button", { name: "Update hours" }));
    expect(await screen.findByText("UPDATE SUCCESFUL")).toBeInTheDocument();
    expect(mw.calls("/api/v1/tables-3/serial-hours/update")[0].body).toMatchObject({ stock: "123456789", serial: "SN1", cumulativeHours: 1300, cumulativeMinutes: 30 });
  });
});
