import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { StockSearchPage } from "./StockSearchPage";

/* the app first checks the session ("Loading MARIS…"), then shows the page */
const page = async () => { render(<AppProvider><StockSearchPage /></AppProvider>); await screen.findByLabelText("Description"); };

describe("Stock number search (old WS17I)", () => {
  it("searches by description and department and lists the stock numbers", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/wayside/stock-numbers": () => ({ body: { count: 1, message: null,
      stockNumbers: [{ dept: "E", stockNo: "123456789", description: "BRAKE SHOE" }] } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Description"), "BRAKE");
    await userEvent.type(screen.getByLabelText("Department"), "e");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    const table = await screen.findByRole("table", { name: "Stock numbers" });
    expect(within(table).getByText("123456789")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/wayside/stock-numbers")[0].query)).toEqual({ description: "BRAKE", dept: "E" });
  });

  it("keeps the old field sizes: 10 characters of description, 1 of department", async () => {
    fakeMiddleware({});
    await page();
    expect(screen.getByLabelText("Description")).toHaveAttribute("maxLength", "10");
    expect(screen.getByLabelText("Department")).toHaveAttribute("maxLength", "1");
  });

  it("shows MARIS's message when nothing is found", async () => {
    fakeMiddleware({ "GET /api/v1/wayside/stock-numbers": () => ({ body: { count: 0, stockNumbers: [], message: "STOCK NO Is Not Found" } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("STOCK NO Is Not Found")).toBeInTheDocument();
  });

  it("shows the middleware's error", async () => {
    fakeMiddleware({ "GET /api/v1/wayside/stock-numbers": () => ({ status: 422, body: { detail: "STOCK Is Not Found" } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("STOCK Is Not Found");
  });

  it("Clear empties the criteria and the list", async () => {
    fakeMiddleware({ "GET /api/v1/wayside/stock-numbers": () => ({ body: { count: 1, message: null, stockNumbers: [{ dept: "E", stockNo: "1", description: "X" }] } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Description"), "X");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    await screen.findByRole("table", { name: "Stock numbers" });
    await userEvent.click(screen.getByRole("button", { name: "Clear" }));
    expect(screen.getByLabelText("Description")).toHaveValue("");
    expect(screen.queryByRole("table", { name: "Stock numbers" })).not.toBeInTheDocument();
  });
});
