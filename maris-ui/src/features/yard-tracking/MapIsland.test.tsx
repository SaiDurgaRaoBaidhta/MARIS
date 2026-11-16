import { act, render, screen } from "@testing-library/react";
import { fakeMiddleware } from "@/test/fetch";
import { expect, test, vi } from "vitest";
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
  expect(layer).toHaveClass("top-[166px]", "bottom-0", "right-4");
  expect(screen.getByRole("heading", { name: "Hayward Yard Map - Inquiry" })).toBeInTheDocument();
  expect(screen.getByText("VHYT07I")).toBeInTheDocument();
  send({ type: "maris-map:full", on: true });
  expect(layer).toHaveClass("inset-0");
  expect(screen.queryByRole("heading", { name: "Hayward Yard Map - Inquiry" })).toBeNull();
});

test("leaving a map for any other page leaves the browser's full screen: it is for the maps only", async () => {
  fakeMiddleware({});
  const exit = vi.fn(() => Promise.resolve());
  Object.defineProperty(document, "fullscreenElement", { configurable: true, get: () => document.body });
  Object.defineProperty(document, "exitFullscreen", { configurable: true, value: exit });
  const { rerender } = render(<AppProvider><MapIsland map={{ yard: "HY", shop: false }} navWidth={276} /></AppProvider>);
  await screen.findByTitle("Yard map");
  expect(exit).not.toHaveBeenCalled();
  rerender(<AppProvider><MapIsland map={null} navWidth={276} /></AppProvider>);
  expect(exit).toHaveBeenCalledOnce();
  Object.defineProperty(document, "fullscreenElement", { configurable: true, get: () => null });
});

test("closing a map goes back once: the page goes back when the map starts closing, and its final 'closed' adds no second back", async () => {
  fakeMiddleware({});
  function Pages() {
    const app = useApp();
    return <><button onClick={() => { app.open({ kind: "screen", key: "egl:A", label: "A" }); app.open({ kind: "screen", key: "egl:B", label: "B" }); }}>push</button><Top /></>;
  }
  render(<AppProvider><MapIsland map={null} navWidth={276} /><Pages /></AppProvider>);
  const push = await screen.findByText("push");
  act(() => push.click());
  expect(screen.getByTestId("top")).toHaveTextContent("egl:B");
  const send = (type: string) => act(() => { dispatchEvent(new MessageEvent("message", { origin: location.origin, data: { source: "maris-map", type } })); });
  send("maris-map:closing");
  expect(screen.getByTestId("top")).toHaveTextContent("egl:A");
  send("maris-map:closed");
  expect(screen.getByTestId("top")).toHaveTextContent("egl:A");
});
