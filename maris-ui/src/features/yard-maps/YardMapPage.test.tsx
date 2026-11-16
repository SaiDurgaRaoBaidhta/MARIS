import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { YardMapPage } from "./YardMapPage";

vi.mock("./three/Map3D", () => ({ default: ({ model }: { model: { yard: string } }) => <div data-testid="map3d">3D map of {model.yard}</div> }));

const car = (n: string, stat = "GO", home = "HY") => ({ car: n, home, stat, from: "", reason: "", consist: "" });
const fleet = { yards: {}, mapYards: ["HY"], capacityKnown: false, fleet: { HY: [
  { id: "T-10", cap: 3, cars: [car("4124"), car("3183", "NO GO"), null] },
  { id: "1A", cap: 1, cars: [car("3142")] },
  { id: "T-95", cap: 2, cars: [car("4249"), car("3042", "PM", "DY")] }] } };
const routes = {
  "GET /api/v1/fleet": () => ({ body: fleet }),
  "GET /api/v1/yard-tracking/hayward-yard-map": () => ({ body: { cars: [{ car: "4124", label: "v4124" }, { car: "3183", label: "{3183" }] } }),
  "GET /api/v1/yard-tracking/maps/hayward-shop": () => ({ body: { cars: [{ car: "3142", label: "3142}" }] } }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
};

function Top() { const app = useApp(); const p = app.stack[app.stack.length - 1]; return <output data-testid="top">{p.kind === "screen" ? p.key : p.kind}</output>; }

describe("yard / shop maps in React (old viewer's drawing, MARIS cars)", () => {
  it("draws the Hayward yard with each car's old map text in its box, and lists the cars no box can show", async () => {
    fakeMiddleware(routes);
    render(<AppProvider><YardMapPage map={{ yard: "HY", shop: false }} navWidth={0} /></AppProvider>);
    const map = await screen.findByTestId("yard-map");
    await waitFor(() => expect(map.querySelector('.cell[data-track="T-10"][data-idx="0"]')).toHaveTextContent("v4124"));
    expect(map.querySelector('.cell[data-track="T-10"][data-idx="1"]')).toHaveTextContent("{3183");
    expect(map.querySelector('.cell[data-track="T-10"][data-idx="2"]')).toHaveClass("mv-empty");
    expect(map.querySelector('.cell[data-track="T-10"][data-idx="0"]')).toHaveClass("c-HY");
    expect(screen.getByText("PSEUDO TRACK")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Other cars on this yard \(2\)/ }));
    const list = screen.getByRole("list", { name: "Other cars on this yard" });
    expect(within(list).getByText("4249")).toBeInTheDocument();
    expect(within(list).getAllByText("T-95")).toHaveLength(2);
  });

  it("draws the shop map's lettered tracks in their boxes", async () => {
    fakeMiddleware(routes);
    render(<AppProvider><YardMapPage map={{ yard: "HY", shop: true }} navWidth={0} /></AppProvider>);
    const map = await screen.findByTestId("yard-map");
    await waitFor(() => expect(map.querySelector('.cell[data-track="S1"][data-idx="0"]')).toHaveTextContent("3142}"));
  });

  it("switches to 3D and back, and leaves full screen for the page layout", async () => {
    fakeMiddleware(routes);
    render(<AppProvider><YardMapPage map={{ yard: "HY", shop: false }} navWidth={0} /></AppProvider>);
    await waitFor(() => expect(document.querySelector('.cell[data-track="T-10"]')).toBeTruthy());
    await userEvent.click(screen.getByRole("button", { name: "3D" }));
    expect(await screen.findByTestId("map3d")).toHaveTextContent("3D map of HY");
    await userEvent.click(screen.getByRole("button", { name: "2D" }));
    await waitFor(() => expect(screen.queryByTestId("map3d")).toBeNull());
    await userEvent.click(screen.getByRole("button", { name: "Exit full screen" }));
    expect(screen.getByRole("heading", { name: "Hayward Yard Map" })).toBeInTheDocument();
  });

  it("a track tag opens the old track window, Return goes back", async () => {
    fakeMiddleware(routes);
    function Harness() { const app = useApp(); return <><button onClick={() => app.open({ kind: "screen", key: "egl:X", label: "X" })}>prev</button><Top /></>; }
    render(<AppProvider><Harness /><YardMapPage map={{ yard: "HY", shop: false }} navWidth={0} /></AppProvider>);
    const prev = await screen.findByText("prev");
    await act(async () => { prev.click(); });
    const tag = await screen.findByRole("button", { name: "Track T-10" });
    await userEvent.click(tag);
    expect(screen.getByTestId("top").textContent).toMatch(/^egl:VHYT08I/);
    await userEvent.click(screen.getAllByRole("button", { name: /Return/ }).at(-1)!);
    expect(screen.getByTestId("top").textContent).toBe("egl:X");
  });
});
