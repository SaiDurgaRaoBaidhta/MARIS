import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { NarrativePage } from "./NarrativePage";

const first = { incdNo: "29990010", reprDocNo: 0, currentRow: 1, maxRows: 2, userGrp: "BRIGHTCO", dateNarr: "11-01-2026", timeNarr: "10:15", narr: "FIRST", ciPart: "", canPrev: false, canNext: true };
const second = { ...first, currentRow: 2, narr: "MOVED", ciPart: "CI / ACTION", canPrev: true, canNext: false };
const params = new URLSearchParams({ incdNo: "29990010", currentRow: "1", maxRows: "2", narr: "FIRST", timeNarr: "10.15.00" });
const show = () => render(<AppProvider><NarrativePage params={params} /></AppProvider>);

describe("Narrative window (old VHNAR01I)", () => {
  it("opens with the parent's narrative and steps to the next one", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/vehicle-maintenance/narratives/opening": () => ({ body: first }),
      "GET /api/v1/vehicle-maintenance/narratives/step": () => ({ body: second }),
    });
    show();
    expect(await screen.findByDisplayValue("FIRST")).toBeInTheDocument();
    expect(mw.calls("/api/v1/vehicle-maintenance/narratives/opening")[0].query.get("incdNo")).toBe("29990010");
    expect(screen.getByRole("button", { name: "Prv" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Nxt" }));
    expect(await screen.findByDisplayValue("MOVED")).toBeInTheDocument();
    expect(screen.getByTestId("ci-part")).toHaveTextContent("CI / ACTION");
    expect(mw.calls("/api/v1/vehicle-maintenance/narratives/step")[0].query.get("direction")).toBe("next");
  });

  it("adds a narrative and shows the new last one", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/vehicle-maintenance/narratives/opening": () => ({ body: first }),
      "POST /api/v1/vehicle-maintenance/narratives": () => ({ body: { message: "Incd Narrative added successfully.", narrative: { ...first, currentRow: 3, maxRows: 3, narr: "NEW" } } }),
    });
    show();
    await userEvent.type(await screen.findByLabelText("New narrative"), "NEW");
    await userEvent.click(screen.getByRole("button", { name: "Add Narrative" }));
    expect(await screen.findByText("Incd Narrative added successfully.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/vehicle-maintenance/narratives")[0].body).toMatchObject({ incdNo: "29990010", maxRows: 2, narrative: "NEW" });
    expect(screen.getByTestId("narr-no")).toHaveTextContent("3");
  });

  it("rewrites the narrative shown, keeping its CI part, and shows the middleware's refusal", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/vehicle-maintenance/narratives/opening": () => ({ body: second }),
      "POST /api/v1/vehicle-maintenance/narratives/update": req => (req.body as { narrative: string }).narrative.trim()
        ? { body: { message: "Incd Narrative updated successfully." } } : { status: 400, body: { detail: "Invalid Narrative." } },
    });
    show();
    const box = await screen.findByLabelText("Current narrative");
    await userEvent.clear(box);
    await userEvent.click(screen.getByRole("button", { name: "Update Narrative" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Narrative.");
    await userEvent.type(box, "MOVED AGAIN");
    await userEvent.click(screen.getByRole("button", { name: "Update Narrative" }));
    await waitFor(() => expect(screen.getByText("Incd Narrative updated successfully.")).toBeInTheDocument());
    expect(mw.calls("/api/v1/vehicle-maintenance/narratives/update")[1].body).toMatchObject({ narrative: "MOVED AGAIN", ciPart: "CI / ACTION", currentRow: 2 });
  });
});
