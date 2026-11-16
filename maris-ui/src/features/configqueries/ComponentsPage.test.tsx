import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ComponentsPage } from "./ComponentsPage";

const OPTIONS = { equipmentTypes: ["TRUCK", "AXLE"], stocks: [{ stock: "12-34-56789", description: "TRUCK ASSY" }], serialStatuses: ["ACT", "SCR"],
  homeYards: ["CY", "DY", "HY", "RY"], warnings: [] };
const ROW = { car: "1001", homeYard: "CY", equipmentType: "TRUCK", serial: "SN1", trackingCode: "T1", dateRebuilt: "01-02-2020", employee: "", lastRemovedBy: "",
  manufacturerSerial: "", parentEquipmentType: "", parentPosition: "", parentSerial: "", parentStock: "", position: "A", purchaseOrder: "", configured: "Y",
  status: "ACT", shopLocation: "", stock: "12-34-56789", hours: 1234 };
const page = () => render(<AppProvider><ComponentsPage /></AppProvider>);

describe("config component inquiry (old VHCF16I)", () => {
  it("offers the old lists and asks for the components with the criteria chosen", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/config-queries/component-options": () => ({ body: OPTIONS }),
      "GET /api/v1/config-queries/components": () => ({ body: { limitReached: false, rows: [ROW] } }),
    });
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Stock number"), "12-34-56789");
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "CY");
    await userEvent.selectOptions(screen.getByLabelText("Status"), "ACT");
    await userEvent.type(screen.getByLabelText("Serial number"), "SN1");
    await userEvent.click(screen.getByLabelText("Stock No., Component Hours descending"));
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Components" });
    expect(within(table).getByText("1001")).toBeInTheDocument();
    expect(within(table).getByText("1234")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/config-queries/components")[0].query))
      .toEqual({ stock: "12-34-56789", serial: "SN1", status: "ACT", homeYard: "CY", equipmentType: "", sort: "2" });
  });

  it("keeps the old rule: a home yard needs a stock number", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/config-queries/component-options": () => ({ body: OPTIONS }) });
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Home yard"), "CY");
    expect(screen.getByRole("button", { name: "Display" })).toBeDisabled();
    expect(screen.getByText(/also choose a stock number/)).toBeInTheDocument();
    await waitFor(() => expect(mw.calls("/api/v1/config-queries/components")).toHaveLength(0));
  });

  it("shows the old screen's message (no data, data limit)", async () => {
    fakeMiddleware({
      "GET /api/v1/config-queries/component-options": () => ({ body: OPTIONS }),
      "GET /api/v1/config-queries/components": () => ({ body: { limitReached: true, message: "DATA LIMIT OF 213 ITEMS HAS BEEN REACHED, REFINE CRITERIA", rows: [ROW] } }),
    });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "Display" }));
    expect(await screen.findByRole("status")).toHaveTextContent("DATA LIMIT OF 213 ITEMS");
  });

  it("shows the middleware's error", async () => {
    fakeMiddleware({
      "GET /api/v1/config-queries/component-options": () => ({ body: OPTIONS }),
      "GET /api/v1/config-queries/components": () => ({ status: 422, body: { code: "BUSINESS_ERROR", detail: "COMPONENT Is Not Found" } }),
    });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("COMPONENT Is Not Found");
  });

  it("is routed from VHCF16I", () => {
    expect(CLEAN_PAGES.VHCF16I).toBeTypeOf("function");
  });
});
