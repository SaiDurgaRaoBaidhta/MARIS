import { act, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { MapCar, MapModel, MapSpec } from "../types";
import hy from "./__fixtures__/hy.json";
import { drawPlanSource } from "./planSource";

/* jsdom has no WebGL: the 3D engine (the old viewer's M3D, three.js) is replaced by a recorder; it is checked in a real
 * browser (headless Chrome) instead. Here: the drawing the engine reads, and the React wiring around it. */
const built: { f: HTMLElement; s: Record<string, unknown>; root: HTMLElement; view: HTMLElement }[] = [];
const size = vi.fn(), dispose = vi.fn();
vi.mock("./m3dLegacy", () => ({
  createM3D: () => ({ build: (f: HTMLElement, s: Record<string, unknown>, root: HTMLElement, view: HTMLElement) => { built.push({ f, s, root, view }); return { size, dispose }; } }),
}));
const { Map3D } = await import("./Map3D");

const shopModel = (): MapModel => ({ yard: "HY", shop: true, spec: hy.shopSpec as unknown as MapSpec, cells: hy.cells as MapModel["cells"], others: [] });

describe("the drawing the 3D stage is built from", () => {
  it("draws every cell of the shop with the car's text, home colour, real status and its track and index", () => {
    const p = drawPlanSource(hy.shopSpec as unknown as MapSpec, shopModel());
    const cell = p.root.querySelector('.cell[data-track="S1"][data-idx="1"]') as HTMLElement;
    expect(cell.textContent).toBe("{3258");
    expect(cell.classList.contains("c-HY")).toBe(true);
    expect(cell.dataset.st).toBe("NO GO");                     // the real status, not a demo one
    expect(p.carOf(cell)?.car).toBe("3258");
    expect(p.root.querySelector(".canvas.shop .map-svg")).not.toBeNull();
    expect(p.root.querySelectorAll(".tag, .tlabel").length).toBeGreaterThan(10);
  });

  it("colours the update map by status", () => {
    const p = drawPlanSource(hy.shopSpec as unknown as MapSpec, shopModel(), true);
    expect((p.root.querySelector('.cell[data-track="S1"][data-idx="1"]') as HTMLElement).classList.contains("c-NOGO")).toBe(true);
  });
});

describe("<Map3D>", () => {
  it("builds the stage from the drawing, hands car and track clicks back, and disposes it on unmount", () => {
    built.length = 0;
    const onCarClick = vi.fn<(c: MapCar) => void>(), onTrackClick = vi.fn<(t: string) => void>();
    const { unmount, container } = render(<Map3D model={shopModel()} onCarClick={onCarClick} onTrackClick={onTrackClick} />);
    expect(built).toHaveLength(1);
    const { f, root, view, s } = built[0];
    expect(f.parentElement?.className).toBe("mv-body");          // the old card layout the engine expects
    expect(container.querySelector(".mvx-view .mv.is3d .mv-body")).not.toBeNull();
    expect(view.dataset.id).toBe("shop:HY");
    expect(s.acts).toEqual({});
    expect(size).toHaveBeenCalled();
    act(() => (root.querySelector('.cell[data-track="S1"][data-idx="1"]') as HTMLElement).click());
    expect(onCarClick).toHaveBeenCalledWith(expect.objectContaining({ car: "3258", text: "{3258" }));
    const tag = root.querySelector(".tag[data-open], .tlabel[data-open]") as HTMLElement;
    act(() => tag.click());
    expect(onTrackClick).toHaveBeenCalledWith(tag.dataset.open);
    unmount();
    expect(dispose).toHaveBeenCalled();
    expect(container.querySelector(".mv")).toBeNull();
  });
});
