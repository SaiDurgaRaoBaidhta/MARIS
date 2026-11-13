import { act, render, screen } from "@testing-library/react";
import { fakeMiddleware } from "@/test/fetch";
import { expect, test } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { MapIsland } from "./MapIsland";

function Top() {
  const app = useApp();
  const p = app.stack[app.stack.length - 1];
  return <p data-testid="top">{p.kind === "screen" ? p.key : p.kind}</p>;
}

test("a map button opens MARIS's own screen through the middleware, not a stand-in", async () => {
  fakeMiddleware({});
  render(<AppProvider><MapIsland map={null} navWidth={276} /><Top /></AppProvider>);
  await screen.findByTestId("top");
  act(() => {
    dispatchEvent(new MessageEvent("message", { origin: location.origin, data: { source: "maris-map", type: "maris-map:screen", code: "VHYT09I" } }));
  });
  expect(screen.getByTestId("top")).toHaveTextContent("egl:VHYT09I");
});

test("out of full screen the map sits under its page header, as in the reference: path, title, code, and the page shell", async () => {
  fakeMiddleware({});
  render(<AppProvider><MapIsland map={{ yard: "HY", shop: false }} navWidth={276} /></AppProvider>);
  const layer = (await screen.findByTitle("Yard map")).parentElement!;
  const send = (data: object) => act(() => { dispatchEvent(new MessageEvent("message", { origin: location.origin, data: { source: "maris-map", ...data } })); });
  send({ type: "maris-map:shown", yard: "HY", shop: false, title: "VHYT07I - Hayward Yard Map - Inquiry" });
  send({ type: "maris-map:full", on: false });
  expect(layer.style.left).toBe("292px");
  expect(layer).toHaveClass("top-[166px]", "bottom-[52px]", "right-4");
  expect(screen.getByRole("heading", { name: "Hayward Yard Map - Inquiry" })).toBeInTheDocument();
  expect(screen.getByText("VHYT07I")).toBeInTheDocument();
  send({ type: "maris-map:full", on: true });
  expect(layer).toHaveClass("inset-0");
  expect(screen.queryByRole("heading", { name: "Hayward Yard Map - Inquiry" })).toBeNull();
});
