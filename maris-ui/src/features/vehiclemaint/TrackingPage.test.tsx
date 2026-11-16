import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { TrackingPage } from "./TrackingPage";

const B = "/api/v1/vehicle-maintenance/tracking-events";
const OPTIONS = {
  runDate: "11-15-2026", dateFrom: "01-01-2025", dateTo: "11-15-2026", searchEvents: [{ type: "ALL", subType: "", label: "ALL" }, { type: "AWP", subType: "1", label: "AWP-1 PARTS" }],
  entryEvents: [{ type: "AWP", subType: "1", label: "AWP-1 PARTS" }], statuses: ["ALL", "OPEN", "CLOSED"], entryStatuses: ["OPEN", "CLOSED"], locations: ["HY"],
  carTypes: ["ALL", "C2"], homeYards: ["ALL", "HY"], batteryLocations: ["HY"], sortOrders: ["Car No", "Event Type", "Stock"], defaultSortOrder: "1", queryOnly: false,
  questions: { remove: "Are You Sure You Want To Remove (DELETE) This Event?" },
};
const EVENT = { car: "1101", eventType: "AWP", eventSubType: "1", status: "OPEN", dateInitialized: "10-01-2026", timeInitialized: "10.00.00", trackingRefNo: "123456789",
  dateClosed: "", timeClosed: "", vehicleStatus: "NO GO", dateLastUpdated: "11-01-2026", homeYard: "HY", incident: "", comment: "OLD", location: "HY-T12", locationCompleted: "HY", active: "Y" };
const base = {
  [`GET ${B}/options`]: () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }),
  [`POST ${B}/search`]: () => ({ body: { events: [EVENT], message: "" } }),
  [`POST ${B}/selection`]: () => ({ body: { carType: "C2", batteryReplaced: "", status: "OPEN", location: "HY-T12", entryEventType: "AWP", entryEventSubType: "1", canWorkAssign: true, battery: false } }),
};
const show = () => render(<AppProvider><TrackingPage /></AppProvider>);

describe("Vehicle parts status / mod tracking (old VHVM11I)", () => {
  it("lists with the opening dates and sort, then updates the selected event", async () => {
    const mw = fakeMiddleware({ ...base, [`POST ${B}/update`]: () => ({ body: { message: "Event Entry Has Been UPDATED", question: null, incident: null } }) });
    show();
    expect(await screen.findByDisplayValue("01-01-2025")).toBe(screen.getByLabelText("From"));
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(mw.calls(`${B}/search`)[0].body).toMatchObject({ dateFrom: "01-01-2025", dateTo: "11-15-2026", sortOrder: "1" });
    await userEvent.click(await within(screen.getByRole("region", { name: "Events" })).findByText("123456789"));
    expect(await screen.findByLabelText("Comments")).toHaveValue("OLD");
    await userEvent.clear(screen.getByLabelText("Comments"));
    await userEvent.type(screen.getByLabelText("Comments"), "NEW");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("UPDATED");
    expect(mw.calls(`${B}/update`)[0].body).toMatchObject({ event: EVENT, comment: "NEW", entryEventType: "AWP", entryEventSubType: "1" });
  });

  it("answers the backend's question and sends again", async () => {
    let n = 0;
    const mw = fakeMiddleware({ ...base, [`POST ${B}/close`]: () => ({ body: n++ === 0 ? { message: null, question: "Respond YES to continue", incident: null } : { message: "CLOSED", question: null, incident: null } }) });
    show();
    await userEvent.click(await screen.findByRole("button", { name: "List" }));
    await userEvent.click(await screen.findByText("123456789"));
    await screen.findByDisplayValue("OLD");
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    await userEvent.click(await screen.findByRole("button", { name: "Yes" }));
    expect(await screen.findByRole("status")).toHaveTextContent("CLOSED");
    expect(mw.calls(`${B}/close`)[1].body).toMatchObject({ answer: true });
  });
});
