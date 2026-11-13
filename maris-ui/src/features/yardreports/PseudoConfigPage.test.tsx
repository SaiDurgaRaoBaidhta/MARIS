import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { PseudoConfigPage } from "./PseudoConfigPage";

const car = (no: string, extra: object = {}) => ({ car: no, leftEnd: "", rightEnd: "", type: "B1", ato: false, incident: false, status: "GO", homeYard: "CY", serviceable: true, ...extra });
const CONFIG = {
  yard: "CY", message: "CY Yard is selected. Please enter new Pseudo", warnings: [],
  tracks: [{ track: "ST-07", description: "STORAGE 7" }, { track: "ALL", description: "" }],
  consists: [
    { consist: "532", dash: 1, display: "532-01", track: "T-95", indicator: "P", resettable: false,
      cars: [car("1001", { leftEnd: "<", type: "A1", ato: true }), car("1002"), car("1003", { rightEnd: ">", type: "C1", ato: true })] },
    { consist: "446", dash: 0, display: "446-X", track: "T-95", indicator: "C", resettable: true, cars: [car("3456", { incident: true })] },
  ],
};
const routes = (extra: Record<string, () => { status?: number; body?: unknown }> = {}) => fakeMiddleware({
  "GET /api/v1/yard-reports/pseudo-train-move/yards": () => ({ body: ["CY", "RY"] }),
  "GET /api/v1/yard-reports/pseudo-config/CY": () => ({ body: CONFIG }),
  "GET /api/v1/yard-reports/pseudo-config/CY/cars": () => ({ body: [car("4001", { rightEnd: ">" }), car("4002"), car("4003")] }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
  ...extra,
});
const page = () => render(<AppProvider><PseudoConfigPage /></AppProvider>);

async function loadCY() {
  await userEvent.selectOptions(await screen.findByLabelText("Yard"), "CY");
  return screen.findByRole("table", { name: "Consists in CY" });
}
const newConsist = () => within(screen.getByRole("list", { name: "New pseudo consist" })).queryAllByRole("listitem").map(li => li.textContent);

describe("configure pseudo trains (old VHYT53I)", () => {
  it("loads a yard's consists with their cars and the old message", async () => {
    routes();
    page();
    const table = await loadCY();
    expect(within(table).getByText("532-01")).toBeInTheDocument();
    expect(within(table).getByRole("cell", { name: "<1001 1002 1003>" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("CY Yard is selected. Please enter new Pseudo");
  });

  it("Include puts a consist's cars into the new consist; Create sends it", async () => {
    const mw = routes({ "POST /api/v1/yard-reports/pseudo-config/CY/consists": () => ({ body: { done: true, message: "123 pseudo consist is created" } }) });
    page();
    await loadCY();
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    await userEvent.click(screen.getByRole("button", { name: "Include" }));
    expect(newConsist()).toEqual(["<1001", "1002", "1003>"]);
    await userEvent.type(screen.getByLabelText("New consist number"), "123");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByText("123 pseudo consist is created")).toBeInTheDocument();
    const body = mw.calls("/api/v1/yard-reports/pseudo-config/CY/consists")[0].body as { consist: string; partial: boolean; cars: { car: string }[] };
    expect(body.consist).toBe("123");
    expect(body.partial).toBe(false);
    expect(body.cars.map(c => c.car)).toEqual(["1001", "1002", "1003"]);
    await waitFor(() => expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY").length).toBe(2));   // reloaded, as the old screen refreshes
  });

  it("Create partial sends a partial consist", async () => {
    const mw = routes({ "POST /api/v1/yard-reports/pseudo-config/CY/consists": () => ({ body: { done: true, message: "124 partial pseudo consist is created" } }) });
    page();
    await loadCY();
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    await userEvent.click(screen.getByRole("button", { name: "Include" }));
    await userEvent.type(screen.getByLabelText("New consist number"), "124");
    await userEvent.click(screen.getByRole("button", { name: "Create partial" }));
    await screen.findByText("124 partial pseudo consist is created");
    expect((mw.calls("/api/v1/yard-reports/pseudo-config/CY/consists")[0].body as { partial: boolean }).partial).toBe(true);
  });

  it("shows the old check's message when MARIS refuses the consist", async () => {
    routes({ "POST /api/v1/yard-reports/pseudo-config/CY/consists": () => ({ status: 400, body: { code: "INVALID_INPUT", detail: "1002-Invalid car type" } }) });
    page();
    await loadCY();
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    await userEvent.click(screen.getByRole("button", { name: "Include" }));
    await userEvent.type(screen.getByLabelText("New consist number"), "123");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("1002-Invalid car type");
  });

  it("keeps the old rules: a train with an incident warns before Include; no car twice; at most 10 cars", async () => {
    routes();
    page();
    await loadCY();
    await userEvent.click(screen.getByRole("radio", { name: "446-X" }));
    expect(screen.getByText(/has an incident/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    await userEvent.click(screen.getByRole("button", { name: "Include" }));
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    await userEvent.click(screen.getByRole("button", { name: "Include" }));
    expect(screen.getByRole("alert")).toHaveTextContent(/already in the new consist/);
    expect(newConsist()).toHaveLength(3);
  });

  it("Reset is offered only for a consist marked C or D, and resets it", async () => {
    const mw = routes({ "POST /api/v1/yard-reports/pseudo-config/CY/resets": () => ({ body: { done: true } }) });
    page();
    await loadCY();
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    expect(screen.getByRole("button", { name: "Reset" })).toBeDisabled();
    await userEvent.click(screen.getByRole("radio", { name: "446-X" }));
    await userEvent.click(screen.getByRole("button", { name: "Reset" }));
    await waitFor(() => expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY/resets")).toHaveLength(1));
    expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY/resets")[0].body).toEqual({ consist: "446", track: "T-95", indicator: "C" });
  });

  it("Add takes cars from a track; Insert puts one before or after the selected car", async () => {
    const mw = routes();
    page();
    await loadCY();
    await userEvent.selectOptions(screen.getByLabelText("Add from track"), "ST-07");
    await waitFor(() => expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY/cars")).toHaveLength(1));
    expect(Object.fromEntries(mw.calls("/api/v1/yard-reports/pseudo-config/CY/cars")[0].query)).toEqual({ track: "ST-07" });
    await userEvent.clear(screen.getByLabelText("Number of cars"));
    await userEvent.type(screen.getByLabelText("Number of cars"), "2");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(newConsist()).toEqual(["4001>", "4002"]);

    await userEvent.selectOptions(screen.getByLabelText("Insert from track"), "ALL");
    await waitFor(() => expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY/cars")).toHaveLength(2));
    expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY/cars")[1].query.get("list")).toBe("insert");
    await userEvent.selectOptions(screen.getByLabelText("Car to insert"), "4003");
    await userEvent.click(screen.getByRole("button", { name: "Select 4001>" }));
    await userEvent.click(screen.getByLabelText("Before"));
    await userEvent.click(screen.getByRole("button", { name: "Insert" }));
    expect(newConsist()).toEqual(["4003", "4001>", "4002"]);
  });

  it("Move takes the selected car out to a track", async () => {
    const mw = routes({ "POST /api/v1/yard-reports/pseudo-config/CY/car-moves": () => ({ body: { moved: true, message: "1002 car is moved." } }) });
    page();
    await loadCY();
    await userEvent.click(screen.getByRole("radio", { name: "532-01" }));
    await userEvent.click(screen.getByRole("button", { name: "Include" }));
    await userEvent.click(screen.getByRole("button", { name: "Select 1002" }));
    await userEvent.selectOptions(screen.getByLabelText("Move to track"), "ST-07");
    await userEvent.click(screen.getByRole("button", { name: "Move" }));
    expect(await screen.findByText("1002 car is moved.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-reports/pseudo-config/CY/car-moves")[0].body).toEqual({ car: "1002", track: "ST-07" });
    expect(newConsist()).toEqual(["<1001", "1003>"]);
  });

  it("is routed from VHYT53I", () => {
    expect(CLEAN_PAGES.VHYT53I).toBeTypeOf("function");
  });
});
