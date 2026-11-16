import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { PseudoTrainMovePage } from "./PseudoTrainMovePage";

const page = () => render(<AppProvider><PseudoTrainMovePage /></AppProvider>);
const yards = () => ({ body: ["CY", "RY", "HY"] });

describe("move a pseudo train to another yard (old VHIC03I)", () => {
  it("offers the yards MARIS lists", async () => {
    fakeMiddleware({ "GET /api/v1/yard-reports/pseudo-train-move/yards": yards });
    page();
    const to = await screen.findByLabelText("To yard");
    expect(within(to).getAllByRole("option").map(o => o.textContent)).toEqual(["Choose a yard", "CY", "RY", "HY"]);
  });

  it("Move sends the cars and the yards, and shows the outcome", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-reports/pseudo-train-move/yards": yards,
      "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/yard-reports/pseudo-train-move": () => ({ body: { moved: true, message: "Cars are successfully moved from CY, to RY" } }),
    });
    page();
    await userEvent.selectOptions(await screen.findByLabelText("From yard"), "CY");
    await userEvent.clear(screen.getByLabelText("From position"));
    await userEvent.type(screen.getByLabelText("From position"), "3");
    await userEvent.clear(screen.getByLabelText("To position"));
    await userEvent.type(screen.getByLabelText("To position"), "5");
    await userEvent.selectOptions(screen.getByLabelText("To yard"), "RY");
    await userEvent.click(screen.getByRole("button", { name: "Move" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Cars are successfully moved from CY, to RY");
    await waitFor(() => expect(mw.calls("/api/v1/yard-reports/pseudo-train-move")).toHaveLength(1));
    expect(mw.calls("/api/v1/yard-reports/pseudo-train-move")[0].body).toEqual({ fromYard: "CY", fromPosition: 3, toPosition: 5, toYard: "RY" });
  });

  it("shows the old message when the move is refused or there is no room", async () => {
    fakeMiddleware({
      "GET /api/v1/yard-reports/pseudo-train-move/yards": yards,
      "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/yard-reports/pseudo-train-move": () => ({ status: 400, body: { code: "INVALID_INPUT", detail: "Cars or consists can not be moved from the same yard." } }),
    });
    page();
    await screen.findByLabelText("To yard");
    await userEvent.click(screen.getByRole("button", { name: "Move" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Cars or consists can not be moved from the same yard.");
  });

  it("a move with no room is not shown as done", async () => {
    fakeMiddleware({
      "GET /api/v1/yard-reports/pseudo-train-move/yards": yards,
      "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/yard-reports/pseudo-train-move": () => ({ body: { moved: false, message: "No room for the cars move to the new yard T-95" } }),
    });
    page();
    await screen.findByLabelText("To yard");
    await userEvent.click(screen.getByRole("button", { name: "Move" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No room for the cars move to the new yard T-95");
  });

  it("Clear removes the message, as the old Clear button", async () => {
    fakeMiddleware({
      "GET /api/v1/yard-reports/pseudo-train-move/yards": yards,
      "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/yard-reports/pseudo-train-move": () => ({ body: { moved: false, message: "No room for the cars move to the new yard T-95" } }),
    });
    page();
    await screen.findByLabelText("To yard");
    await userEvent.click(screen.getByRole("button", { name: "Move" }));
    await screen.findByRole("alert");
    await userEvent.click(screen.getByRole("button", { name: "Clear" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("is routed from VHIC03I", () => {
    expect(CLEAN_PAGES.VHIC03I).toBeTypeOf("function");
  });
});
