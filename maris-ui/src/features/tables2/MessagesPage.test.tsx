import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { MessagesPage } from "./MessagesPage";

const OPTIONS = { yards: ["HY", "CY"], tranCodes: ["05"], locations: ["HY01"], scanners: [{ id: "A-HY01-N", scannerId: "A", location: "HY01", direction: "N" }],
  processedCodes: ["Y"], fromDate: "11-14-2026", fromTime: "09:00:00", toTime: "12:00:00", warnings: [] };
const LISTING = { messages: [{ tsMsgRcvd: "2026-11-14-10.15.00.000000", msgTranCd: "05", conNo: "201", conCurrYrd: "HY", conCurrLoc: "HY01", scanner: "A",
  scanDirection: "N", msgProcessedCd: "Y", msgLength: 42, dateTrans: "2026-11-14" }], message: "", compareFrom: "2026-11-14-09.00.00.000000",
  compareTo: "2026-11-14-12.00.00.000000", threshold: 50, toBeProcessed: 3 };
const routes = (extra = {}) => fakeMiddleware({ "GET /api/v1/tables-2/messages/options": () => ({ body: OPTIONS }),
  "POST /api/v1/tables-2/messages/list": () => ({ body: LISTING }), "GET /api/v1/auth/csrf": () => ({ status: 204 }), ...extra });
const page = () => render(<AppProvider><MessagesPage /></AppProvider>);

describe("MRS messages received (old VHCCS04I and its windows)", () => {
  it("proposes MARIS's date and times and lists the messages for the criteria", async () => {
    const mw = routes();
    page();
    await waitFor(() => expect(screen.getByLabelText("Date")).toHaveValue("11-14-2026"));
    await userEvent.selectOptions(screen.getByLabelText("Scan Id"), "A-HY01-N");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Messages received" });
    expect(within(table).getByText("201")).toBeInTheDocument();
    expect(mw.calls("/api/v1/tables-2/messages/list")[0].body).toMatchObject({ fromDate: "11-14-2026", scanner: "A-HY01-N" });
    expect(screen.getByText(/Number of Msgs To Be Proc: 3/)).toBeInTheDocument();
  });

  it("a chosen message opens its detail and Save sends it", async () => {
    const mw = routes({ "POST /api/v1/tables-2/messages/update": () => ({ body: { ok: true, message: "UPDATE SUCCESSFUL" } }) });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    await userEvent.click(await screen.findByText("201"));
    const detail = screen.getByRole("region", { name: "Message detail" });
    await userEvent.clear(within(detail).getByLabelText("Msg Data"));
    await userEvent.type(within(detail).getByLabelText("Msg Data"), "NEW");
    await userEvent.click(within(detail).getByRole("button", { name: "Save" }));
    expect(await screen.findByText("UPDATE SUCCESSFUL")).toBeInTheDocument();
    expect(mw.calls("/api/v1/tables-2/messages/update")[0].body).toMatchObject({ tsMsgRcvd: "2026-11-14-10.15.00.000000", msgData: "NEW" });
  });

  it("shows a refused list's old message", async () => {
    routes({ "POST /api/v1/tables-2/messages/list": () => ({ status: 400, body: { detail: "INVALID FROM DATE" } }) });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("INVALID FROM DATE");
  });

  it("the cars of the chosen message, and the hard copy needs a printer", async () => {
    const mw = routes({
      "GET /api/v1/tables-2/messages/cars": () => ({ body: { cars: [{ timestamp: "T", carNo: "1101", carOrder: 1, conNo: "201", dashNo: 2,
        dateInService: "2026-11-14", orientation: "<", orientationDirection: "N" }], orientations: ["<", ">"] } }),
      "GET /api/v1/tables-2/messages/print-options": () => ({ body: { printers: [{ name: "DUBLIN", vtam: "DUBLNHP1" }], tranCodes: [], yards: [], locations: [],
        scanners: [], processedCodes: [], sorts: ["Date - Time", "Consist Number"], warnings: [] } }),
      "POST /api/v1/tables-2/messages/print": () => ({ body: { submitted: true } }),
    });
    page();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    await userEvent.click(await screen.findByText("201"));
    await userEvent.click(screen.getByRole("button", { name: "Msg Rcv Cars" }));
    expect(await screen.findByRole("table", { name: "Cars" })).toHaveTextContent("1101");
    expect(mw.calls("/api/v1/tables-2/messages/cars")[0].query.get("timestamp")).toBe("2026-11-14-10.15.00.000000");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    const hard = await screen.findByRole("region", { name: "Hard copy" });
    expect(within(hard).getByRole("button", { name: "Hard Copy" })).toBeDisabled();
    await userEvent.selectOptions(await within(hard).findByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(within(hard).getByLabelText("Consist Number"));
    await userEvent.click(within(hard).getByRole("button", { name: "Hard Copy" }));
    await waitFor(() => expect(mw.calls("/api/v1/tables-2/messages/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/tables-2/messages/print")[0].body).toMatchObject({ printer: "DUBLNHP1", sort: 2 });
  });

  it("is the page of the old screens", () => {
    for (const code of ["VHCCS04I", "VHCCS05I", "VHCCS06I", "VHCCS07I", "VHCCS18I"]) expect(CLEAN_PAGES[code]).toBeDefined();
  });
});
