import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { OverridesPage } from "./OverridesPage";

const STATE = {
  yard: "CY", revenueDate: "11-15-2026", schedule: "WD", message: "Enter Make / Break OR Enter Single Car Override Request",
  times: { amMakeFrom: ["05:00:00", "06:00:00"], amMakeTo: ["05:00:00", "06:00:00"], amBreakFrom: [], amBreakTo: [], pmMakeFrom: [], pmMakeTo: [], pmBreakFrom: [], pmBreakTo: [] },
  statuses: { "ALL-STAT": "", "AM-MAKE-STAT": "Scheduled", "AM-BREAK-STAT": "", "PM-MAKE-STAT": "", "PM-BREAK-STAT": "" },
  trains: ["201", "305"], singleTrains: [{ train: "201", arrival: "09:10:00", breakScheduled: "Y", breakCancelled: "N", makeScheduled: "N", makeCancelled: "Y" }],
};

describe("make / break overrides page (old VHYT34I)", () => {
  it("opens the window, saves an AM make cancel between two times and shows the message", async () => {
    const mw = fakeMiddleware({
      "POST /api/v1/yard-queries/make-break-overrides/window": () => ({ body: STATE }),
      "POST /api/v1/yard-queries/make-break-overrides": () => ({ body: { ...STATE, message: "The Make/Break Overrides You Requested Were Added" } }),
    });
    render(<AppProvider><OverridesPage /></AppProvider>);
    expect(await screen.findByText(/Enter Make \/ Break/)).toBeInTheDocument();
    expect(screen.getByDisplayValue("11-15-2026")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "amMakeFrom" }), "05:00:00");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "amMakeTo" }), "06:00:00");
    const save = screen.getByRole("button", { name: "Make/Break Overrides" });
    expect(save).toBeDisabled();
    await userEvent.click(screen.getAllByRole("checkbox", { name: "Cancel" })[0]);
    await userEvent.click(save);
    expect(await screen.findByText("The Make/Break Overrides You Requested Were Added")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-queries/make-break-overrides")[0].body).toEqual({ yard: "", date: "11-15-2026",
      overrides: { times: { amMakeFrom: "05:00:00", amMakeTo: "06:00:00" }, boxes: { amMakeCancel: true } } });
  });

  it("picks a train and an arrival, shows what it is scheduled for and saves a single train override", async () => {
    const mw = fakeMiddleware({
      "POST /api/v1/yard-queries/make-break-overrides/window": () => ({ body: STATE }),
      "POST /api/v1/yard-queries/make-break-overrides/train": r => ({ body: { train: "201", arrivals: ["09:10:00", "17:20:00"],
        scheduledFor: (r.body as { arrival: string }).arrival ? "MAKE" : "" } }),
      "POST /api/v1/yard-queries/make-break-overrides/single-train": () => ({ body: { ...STATE, message: "The Scheduled Action Was Added Successfully" } }),
    });
    render(<AppProvider><OverridesPage /></AppProvider>);
    await screen.findByText(/Enter Make \/ Break/);
    const [trainBox, arrivalBox] = screen.getAllByRole("combobox").slice(-2);
    await userEvent.selectOptions(trainBox, "201");
    await screen.findByRole("option", { name: "17:20:00" });
    await userEvent.selectOptions(arrivalBox, "09:10:00");
    expect(await screen.findByText("MAKE")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("checkbox", { name: "Cancel make" }));
    await userEvent.click(screen.getByRole("button", { name: "Single Train Override" }));
    expect(await screen.findByText("The Scheduled Action Was Added Successfully")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-queries/make-break-overrides/single-train")[0].body).toEqual({ yard: "", date: "11-15-2026",
      override: { train: "201", arrival: "09:10:00", breakCancel: false, makeCancel: true, breakScheduled: false, makeScheduled: false } });
  });
});
