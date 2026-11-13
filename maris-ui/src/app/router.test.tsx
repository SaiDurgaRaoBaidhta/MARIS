import { describe, expect, it, vi } from "vitest";

// one module has rebuilt the Concord yard inquiry map (VHYT70I) on the clean API; Richmond (VHYT90I) is not rebuilt
vi.mock("@/features/screens/cleanPages", () => ({ CLEAN_PAGES: { VHYT70I: () => null } }));

const { resolve } = await import("./router");

describe("resolve: which page a screen key opens", () => {
  it("opens a rebuilt map screen's new page, not the drawn map", () => {
    expect(resolve({ kind: "screen", key: "yardmap:CY", label: "Concord yard" })).toEqual({ kind: "screen", key: "yardmap:CY", label: "Concord yard" });
    expect(resolve({ kind: "screen", key: "egl:VHYT70I", label: "Concord yard" }).kind).toBe("screen");
  });

  it("still opens the drawn map for a map screen that is not rebuilt", () => {
    expect(resolve({ kind: "screen", key: "yardmap:RY", label: "Richmond yard" })).toEqual({ kind: "map", yard: "RY", shop: false, label: "Richmond yard" });
    expect(resolve({ kind: "screen", key: "egl:VHYT90I", label: "Richmond yard" }).kind).toBe("map");
  });
});
