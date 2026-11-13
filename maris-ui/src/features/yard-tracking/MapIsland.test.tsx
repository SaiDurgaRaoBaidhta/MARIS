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
  render(<AppProvider><MapIsland map={null} /><Top /></AppProvider>);
  await screen.findByTestId("top");
  act(() => {
    dispatchEvent(new MessageEvent("message", { origin: location.origin, data: { source: "maris-map", type: "maris-map:screen", code: "VHYT09I" } }));
  });
  expect(screen.getByTestId("top")).toHaveTextContent("egl:VHYT09I");
});
