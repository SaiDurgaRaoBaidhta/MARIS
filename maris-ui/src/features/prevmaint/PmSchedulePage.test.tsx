import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { PmSchedulePage } from "./PmSchedulePage";

const OPTIONS = { options: [{ option: 1, label: "Schedule PM", button: "Schedule" }, { option: 2, label: "ReSchedule PM", button: "Reschedule" }],
  pmTypes: ["1", "2", "3", "4", "5", "6", "A", "B", "C", "D"] };
const base = { "GET /api/v1/preventive-maintenance/schedule/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }),
  "GET /api/v1/preventive-maintenance/schedule/description": () => ({ body: { pmType: "3", description: "PM INSPECTION" } }) };
const page = async () => { render(<AppProvider><PmSchedulePage /></AppProvider>); await screen.findByLabelText("Car No"); };

describe("Schedule PM (old VHPMS01I)", () => {
  it("looks up the PM description when leaving the PM type", async () => {
    const mw = fakeMiddleware(base);
    await page();
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.type(screen.getByLabelText("PM Type"), "03");
    await userEvent.tab();
    expect(await screen.findByText("PM INSPECTION")).toBeInTheDocument();
    expect(screen.getByLabelText("PM Type")).toHaveValue("3");
    expect(Object.fromEntries(mw.calls("/api/v1/preventive-maintenance/schedule/description")[0].query)).toEqual({ car: "1101", pmType: "03" });
  });

  it("reschedules with the option chosen, the button named like the old one", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/preventive-maintenance/schedule": () => ({ body: { message: "PM has been Rescheduled - Incident 29990099 updated", incident: "29990099" } }) });
    await page();
    await userEvent.click(screen.getByLabelText("ReSchedule PM"));
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.type(screen.getByLabelText("PM Type"), "C");
    await userEvent.click(screen.getByRole("button", { name: "Reschedule" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Incident 29990099 updated");
    await waitFor(() => expect(mw.calls("/api/v1/preventive-maintenance/schedule")[0].body).toEqual({ car: "1101", pmType: "C", option: 2 }));
  });

  it("shows the old screen's message when the save is refused", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/preventive-maintenance/schedule": () => ({ status: 400, body: { detail: "Invalid Car No" } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "Schedule" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Car No");
  });
});
