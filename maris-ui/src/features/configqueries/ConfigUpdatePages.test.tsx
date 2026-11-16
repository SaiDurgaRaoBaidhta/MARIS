import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { AxlePage, SerialAddPage, TruckPage } from "./ConfigUpdatePages";

const wrap = (ui: React.ReactNode) => render(<AppProvider>{ui}</AppProvider>);
const PARTS = [
  { key: "axle1", name: "AXLE1", serial: "AX1", highlighted: false }, { key: "axle2", name: "AXLE2", serial: "", highlighted: false },
];
const TRUCK = { message: "", car: "", dateRebuilt: "2020-05-06", status: "REBUILT", location: "HY", comments: "", components: PARTS, axleParts: [], updatable: true, notices: [] };

describe("config tracking query screens that write (old VHCF21I, VHCF37I, VHCF35I, VHCF06I)", () => {
  it("are the pages of the old screens", () => {
    for (const code of ["VHCF21I", "VHCF37I", "VHCF35I", "VHCF06I"]) expect(CLEAN_PAGES[code]).toBeDefined();
  });

  it("adds a serial with the chosen type and stock, and shows the old message", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/config-queries/serial-add/options": () => ({ body: { componentTypes: ["AXLE"], actions: ["ADD"], date: "11-16-2026", notices: [] } }),
      "GET /api/v1/config-queries/serial-add/stocks": () => ({ body: { stocks: ["12-34-56789"], notices: [] } }),
      "POST /api/v1/config-queries/serial-add": () => ({ body: { added: true, message: "THE COMPONENT ACTION WAS SUCCESSFULLY PERFORMED", notices: [] } }),
    });
    wrap(<SerialAddPage />);
    await screen.findByRole("option", { name: "AXLE" });
    await userEvent.selectOptions(screen.getByLabelText("Component Type"), "AXLE");
    await screen.findByRole("option", { name: "12-34-56789" });
    await userEvent.selectOptions(screen.getByLabelText("Stock No"), "12-34-56789");
    await userEvent.type(screen.getByLabelText("Serial No."), "SN9");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("SUCCESSFULLY PERFORMED");
    expect(mw.calls("/api/v1/config-queries/serial-add").at(-1)?.body).toEqual({ componentType: "AXLE", stock: "12-34-56789", serial: "SN9", date: "11-16-2026" });
  });

  it("builds a truck: new serials typed and parts taken off go to the build", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/config-queries/trucks/options": () => ({ body: { stocks: [{ stock: "17-35-88520", description: "TRUCK X" }], statuses: ["REPAIR"], locations: ["HY"], notices: [] } }),
      "GET /api/v1/config-queries/trucks": () => ({ body: TRUCK }),
      "POST /api/v1/config-queries/trucks/build": () => ({ body: { ...TRUCK, message: "ALL COMPONENTS WERE ADDED CORRECTLY", updatable: false } }),
    });
    wrap(<TruckPage build />);
    await screen.findByRole("option", { name: "17-35-88520 TRUCK X" });
    await userEvent.selectOptions(screen.getByLabelText("Truck Stock Number / Desc"), "17-35-88520");
    await userEvent.type(screen.getByLabelText("Truck"), "TRK1");
    await userEvent.click(screen.getByRole("button", { name: "Retrieve" }));
    await userEvent.type(await screen.findByLabelText("AXLE2"), "NEW2");
    await userEvent.click(screen.getByRole("button", { name: "On" }));
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByText(/ALL COMPONENTS WERE ADDED CORRECTLY/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/config-queries/trucks/build").at(-1)?.body).toMatchObject({ stock: "17-35-88520", serial: "TRK1", components: { axle2: "NEW2" }, takeOff: ["axle1"] });
  });

  it("the inquiry has no Update", async () => {
    fakeMiddleware({ "GET /api/v1/config-queries/trucks/options": () => ({ body: { stocks: [], statuses: [], locations: [], notices: [] } }) });
    wrap(<TruckPage build={false} />);
    expect(await screen.findByRole("button", { name: "Retrieve" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Update" })).toBeNull();
  });

  it("updates an axle with the changed values and checked boxes", async () => {
    const values = { axleSerial: "AX1", axleStatus: "STO", car: "", homeYard: "HY", axleRebuiltDate: "03-04-2019" };
    const mw = fakeMiddleware({
      "GET /api/v1/config-queries/axles": () => ({ body: { message: "", values, checkable: ["axleDateUpdate"], updatable: true, notices: [] } }),
      "POST /api/v1/config-queries/axles/update": () => ({ body: { message: "UPDATED", values, checkable: [], updatable: false, notices: [] } }),
    });
    wrap(<AxlePage />);
    await userEvent.type(await screen.findByLabelText("Serial"), "AX1");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const date = await screen.findByLabelText("Axle - Recondition Date");
    await userEvent.clear(date);
    await userEvent.type(date, "11-01-2025");
    await userEvent.click(screen.getByLabelText("axleDateUpdate"));
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByText("UPDATED")).toBeInTheDocument();
    expect(mw.calls("/api/v1/config-queries/axles/update").at(-1)?.body).toEqual({ kind: "axle", serial: "AX1", values: { axleRebuiltDate: "11-01-2025" }, checks: ["axleDateUpdate"] });
  });
});
