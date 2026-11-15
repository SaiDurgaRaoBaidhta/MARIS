import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { NeverGoPage } from "./NeverGoPage";

const base = {
  "GET /api/v1/incidents/tracking-change/never-go/options": () => ({ body: { date: "11-15-2026", time: "0930", statuses: ["NVGO", "STOR"], canChangeLocation: true } }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
};
const show = () => render(<AppProvider><NeverGoPage /></AppProvider>);

describe("Never go (old VHCI03I)", () => {
  it("sends the change and shows the incident created", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/incidents/tracking-change/never-go": () => ({ body: { message: "Status of car 1101 changed to NVGO. I1234567 is created.",
      incident: "I1234567", status: "NVGO", statusDescription: "NEVER GO", maintenance: { from: "VHCI03", car: "1101", incident: "I1234567", status: "NVGO", eventType: "AWP", dateInitialized: "11/15/2026" } } }) });
    show();
    expect(await screen.findByDisplayValue("11-15-2026")).toBe(screen.getByLabelText("Status Chg Date"));
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.selectOptions(screen.getByLabelText("Car Status"), "NVGO");
    await userEvent.type(screen.getByLabelText("Narrative"), "DOOR");
    expect(screen.getByRole("button", { name: "Maint. Actions" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    expect(await screen.findByRole("status")).toHaveTextContent("I1234567 is created");
    expect(mw.calls("/api/v1/incidents/tracking-change/never-go")[0].body).toEqual({ car: "1101", status: "NVGO", date: "11-15-2026", time: "0930", narrative: "DOOR" });
    expect(screen.getByRole("button", { name: "Maint. Actions" })).toBeEnabled();
  });

  it("shows the old screen's message when refused", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/incidents/tracking-change/never-go": () => ({ status: 400, body: { detail: "Invalid Car No." } }) });
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Change" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Invalid Car No.");
  });
});
