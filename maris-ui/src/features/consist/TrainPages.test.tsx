import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { LayUpPage, ReconfigurePage } from "./TrainPages";

const csrf = { "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

describe("lay-up train (old VHLT01I)", () => {
  const routes = (train: object, extra = {}) => fakeMiddleware({
    ...csrf,
    "GET /api/v1/consist/lay-up/options": () => ({ body: { yards: ["CY", "HY"], yard: "HY", time: "23:50:00", revenueDate: "2026-11-14" } }),
    "GET /api/v1/consist/lay-up/tracks": () => ({ body: ["T-10", "T-11"] }),
    "GET /api/v1/consist/lay-up/trains/201": () => ({ body: train }),
    ...extra,
  });

  it("keeps Lay-up disabled and shows the old message for a train that cannot be laid up", async () => {
    routes({ consist: "201", layUp: false, pseudoConsist: "", message: "Consist not In Service" });
    render(<AppProvider><LayUpPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Train"), "201");
    await userEvent.tab();
    expect(await screen.findByRole("alert")).toHaveTextContent("Consist not In Service");
    expect(screen.getByRole("button", { name: "Lay-up Train" })).toBeDisabled();
  });

  it("lays up the train with the chosen track and time and shows the old message", async () => {
    const mw = routes({ consist: "201", layUp: true, pseudoConsist: "201", message: "" },
      { "POST /api/v1/consist/lay-up": () => ({ body: { message: "TRAIN 201 SUCCESSFULLY LAYUP TO HY" } }) });
    render(<AppProvider><LayUpPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Train"), "201");
    await userEvent.tab();
    await screen.findByRole("option", { name: "T-10" });
    await userEvent.selectOptions(screen.getByLabelText("Transfer/Storage Track"), "T-10");
    const time = screen.getByLabelText("Time (HHMM)");
    await userEvent.clear(time);
    await userEvent.type(time, "2359");
    await waitFor(() => expect(screen.getByRole("button", { name: "Lay-up Train" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "Lay-up Train" }));
    expect(await screen.findByRole("status")).toHaveTextContent("SUCCESSFULLY LAYUP");
    expect(mw.calls("/api/v1/consist/lay-up")[0].body).toEqual({ yard: "HY", track: "T-10", consist: "201", time: "2359" });
  });
});

describe("reconfigure train (old VHTR01I / VHTR02I)", () => {
  const STATE = { cars: ["1101<", "1102", "1103", "1104>"], left: { consist: "", cars: [] }, right: { consist: "", cars: [] }, newPseudo: "",
    reconfigure: true, split: true, pseudoEnabled: false, message: "" };

  it("sends the steps, asks VHTR02I's question and reconfigures on Yes", async () => {
    const mw = fakeMiddleware({
      ...csrf,
      "GET /api/v1/consist/reconfigure/options": () => ({ body: { yards: ["HY"], time: "23:50:00", splitPositions: [3, 4] } }),
      "POST /api/v1/consist/reconfigure/state": () => ({ body: STATE }),
      "POST /api/v1/consist/reconfigure": req => ({ body: (req.body as { confirmed: boolean }).confirmed
        ? { message: "TRAIN 201 SUCCESSFULLY RE-CONFIGURATION" } : { message: "", question: "Verify reconfiguration is correct" } }),
    });
    render(<AppProvider><ReconfigurePage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Train"), "201");
    await userEvent.tab();
    await userEvent.click(await screen.findByRole("button", { name: "Split before car 3" }));
    await waitFor(() => expect(mw.calls("/api/v1/consist/reconfigure/state").length).toBeGreaterThanOrEqual(2));
    expect(mw.calls("/api/v1/consist/reconfigure/state").at(-1)?.body).toEqual({ action: 2, consist: "201", splits: [3] });
    await userEvent.click(screen.getByRole("button", { name: "Reconfigure" }));
    expect(await screen.findByRole("dialog", { name: "Confirm reconfiguration" })).toHaveTextContent("Verify reconfiguration is correct");
    await userEvent.click(screen.getByRole("button", { name: "Yes" }));
    expect(await screen.findByRole("status")).toHaveTextContent("SUCCESSFULLY RE-CONFIGURATION");
  });
});
