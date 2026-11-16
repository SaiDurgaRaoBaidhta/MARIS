import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ScreenPage } from "./ScreenPage";

describe("ScreenPage routing", () => {
  it("opens a screen rebuilt on the clean API as its new page, not through the screen engine", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/ics/options": () => ({ body: { revenueDate: "2026-11-12", printers: [], yards: [], locations: [], messageCodes: [], warnings: [] } }) });
    render(<AppProvider><ScreenPage screenKey="egl:VHIC30I" label="ICS Reports" /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText("Revenue date")).toHaveValue("2026-11-12"));   // the value arrives after the first render
    expect(mw.calls("/api/v1/ui/open")).toHaveLength(0);
  });
});
