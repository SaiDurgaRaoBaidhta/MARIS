import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarHistoryPrintPage, ForceClearPage, PseudoCorrectionPage, ShopActivityPage } from "./YardTrackingMiscPages";

const wrap = (ui: React.ReactNode) => render(<AppProvider>{ui}</AppProvider>);
const PRINTERS = [{ index: 1, name: "PRT01", description: "SHOP CONCORD" }, { index: 2, name: "PRT02", description: "OFFICE" }];

describe("yard tracking pages (old VHYT31I, VHYT23I, VHYT22I, VHYT52I, VHYT54I)", () => {
  it("lists a yard's shop activity for a day and prints it on the chosen printer", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/shop-activity/options": () => ({ body: { yards: [{ code: "HY", name: "HAYWARD" }, { code: "CY", name: "CONCORD" }], yard: "HY", yardName: "HAYWARD", date: "11-14-2026", message: "" } }),
      "POST /api/v1/yard-tracking/shop-activity/list": () => ({ body: { yard: "CY", date: "11-14-2026", message: "", printable: true,
        totals: { arrivals: 4, departures: 3, releases: 2, remaining: 3, production: 5, productionGainLoss: -1, availabilityGainLoss: 2, available0400: 7, remaining3: 3 },
        cars: [{ car: "<1101", availabilityDate: "", availabilityTime: "", status: "GO", track: "S-1", position: 1, inShopDate: "", inShopTime: "", outShopDate: "", outShopTime: "", moveReason: "PM  PM INSPECTION" }] } }),
      "GET /api/v1/yard-tracking/printers": () => ({ body: PRINTERS }),
      "POST /api/v1/yard-tracking/shop-activity/print": () => ({ body: { printer: "PRT02" } }),
    });
    wrap(<ShopActivityPage />);
    const yard = await screen.findByRole("combobox");
    await screen.findByDisplayValue("11-14-2026");
    await userEvent.selectOptions(yard, "CY");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("<1101")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/shop-activity/list")[0].body).toEqual({ yard: "CY", date: "11-14-2026" });
    expect(screen.getByText("-1")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await userEvent.click(await screen.findByRole("radio", { name: /PRT02/ }));
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    expect(await screen.findByText("Sent to PRT02.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/shop-activity/print")[0].body).toEqual({ yard: "CY", date: "11-14-2026", printer: 2 });
  });

  it("prints a car's history and shows the middleware's refusal", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/printers": () => ({ body: PRINTERS }),
      "POST /api/v1/yard-tracking/car-history/print": r => ((r.body as { car: string }).car ? { body: { printer: "PRT01" } } : { status: 400, body: { detail: "A car number is required." } }),
    });
    wrap(<CarHistoryPrintPage />);
    await userEvent.click(await screen.findByRole("radio", { name: /PRT01/ }));
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("A car number is required.");
    await userEvent.type(screen.getByRole("textbox", { name: "Car No" }), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    expect(await screen.findByText("Sent to PRT01.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/car-history/print").at(-1)?.body).toEqual({ car: "1101", printer: 1 });
  });

  it("lists in-service trains and retrieves the selected one's cars", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/pseudo-correction": () => ({ body: { yard: "", yardChoosable: false, message: "",
        trains: [{ consist: "201-01", cars: ["<1101", "1102>"], status: "INS", noCars: "N" }, { consist: "305-02", cars: ["<1201"], status: "CON", noCars: "N" }] } }),
      "POST /api/v1/yard-tracking/pseudo-correction/retrieve": () => ({ body: { message: "Process started  Cars will be placed in your pseudo track", trains: [] } }),
    });
    wrap(<PseudoCorrectionPage />);
    const retrieve = await screen.findByRole("button", { name: "Retrieve Consist" });
    expect(retrieve).toBeDisabled();
    await userEvent.click(await screen.findByText("305-02"));
    await userEvent.click(retrieve);
    expect(await screen.findByText(/Process started/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/pseudo-correction/retrieve")[0].body).toMatchObject({ row: 2 });
  });

  it("force clears a train and shows the old screen's message", async () => {
    const mw = fakeMiddleware({ "POST /api/v1/yard-tracking/force-clear": () => ({ body: { message: "Process started. Train 101 will be cleared to CY" } }) });
    wrap(<ForceClearPage />);
    await userEvent.type(await screen.findByRole("textbox", { name: "In Service Train" }), "101");
    await userEvent.type(screen.getByRole("textbox", { name: "Clear to Location" }), "CY");
    await userEvent.click(screen.getByRole("button", { name: "Force Clear" }));
    expect(await screen.findByText(/will be cleared to CY/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/force-clear")[0].body).toEqual({ train: "101", location: "CY" });
  });
});
