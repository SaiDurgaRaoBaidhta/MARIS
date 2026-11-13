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

test("out of full screen the map leaves the header, side menu and footer visible", async () => {
  fakeMiddleware({});
  render(<AppProvider><MapIsland map={{ yard: "CY", shop: false }} navWidth={276} /></AppProvider>);
  const layer = (await screen.findByTitle("Yard map")).parentElement!;
  act(() => { dispatchEvent(new MessageEvent("message", { origin: location.origin, data: { source: "maris-map", type: "maris-map:full", on: false } })); });
  expect(layer.style.left).toBe("276px");
  expect(layer).toHaveClass("top-16", "bottom-10");
  act(() => { dispatchEvent(new MessageEvent("message", { origin: location.origin, data: { source: "maris-map", type: "maris-map:full", on: true } })); });
  expect(layer).toHaveClass("inset-0");
});
