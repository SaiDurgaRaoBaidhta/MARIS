import { describe, expect, it, vi } from "vitest";

// one module has rebuilt the Concord yard inquiry map (VHYT70I) on the clean API; Richmond (VHYT90I) is not rebuilt
vi.mock("@/features/screens/cleanPages", () => ({ CLEAN_PAGES: { VHYT70I: () => null, VHYT71I: () => null, VHIC01: () => null, VHIC02: () => null } }));

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


  it("opens a rebuilt map's new page when the map is opened directly (Home, footer), inquiry or update by the user", () => {
    expect(resolve({ kind: "map", yard: "CY", shop: false, label: "Concord" })).toEqual({ kind: "screen", key: "yardmap:CY", label: "Concord" });
    expect(resolve({ kind: "map", yard: "CY", shop: false, label: "Concord" }, true)).toEqual({ kind: "screen", key: "yardmap:CY:u", label: "Concord" });
    // Berryessa has no yardmap: key; its screen index entries carry the map
    expect(resolve({ kind: "map", yard: "BS", shop: false, label: "Berryessa" })).toEqual({ kind: "screen", key: "egl:VHIC01", label: "Berryessa" });
    expect(resolve({ kind: "map", yard: "BS", shop: false, label: "Berryessa" }, true)).toEqual({ kind: "screen", key: "egl:VHIC02", label: "Berryessa" });
  });

  it("keeps the drawn map for a directly opened map that is not rebuilt", () => {
    expect(resolve({ kind: "map", yard: "RY", shop: false, label: "Richmond" })).toEqual({ kind: "map", yard: "RY", shop: false, label: "Richmond" });
    expect(resolve({ kind: "map", yard: "CY", shop: true, label: "Concord shop" })).toEqual({ kind: "map", yard: "CY", shop: true, label: "Concord shop" });
  });
});
