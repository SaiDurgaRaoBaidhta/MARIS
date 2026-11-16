import { describe, expect, it, vi } from "vitest";

// a module has rebuilt some screens on the clean API; map screens route to the drawn map regardless
vi.mock("@/features/screens/cleanPages", () => ({ CLEAN_PAGES: { VHYT70I: () => null, VHYT71I: () => null, VHIC01: () => null, VHIC02: () => null, VHSW01I: () => null } }));

const { resolve } = await import("./router");

describe("resolve: which page a screen key opens", () => {
  it("opens the drawn map for every map screen, rebuilt or not", () => {
    expect(resolve({ kind: "screen", key: "yardmap:CY", label: "Concord yard" })).toEqual({ kind: "map", yard: "CY", shop: false, label: "Concord yard" });
    expect(resolve({ kind: "screen", key: "egl:VHYT70I", label: "Concord yard" })).toEqual({ kind: "map", yard: "CY", shop: false, label: "Concord yard" });
    expect(resolve({ kind: "screen", key: "egl:VHIC01", label: "Berryessa" })).toEqual({ kind: "map", yard: "BS", shop: false, label: "Berryessa" });
    expect(resolve({ kind: "screen", key: "egl:VHYT101I", label: "Daly City shop" })).toEqual({ kind: "map", yard: "DY", shop: true, label: "Daly City shop" });
  });

  it("keeps the drawn map for a directly opened map", () => {
    expect(resolve({ kind: "map", yard: "RY", shop: false, label: "Richmond" })).toEqual({ kind: "map", yard: "RY", shop: false, label: "Richmond" });
    expect(resolve({ kind: "map", yard: "CY", shop: true, label: "Concord shop" })).toEqual({ kind: "map", yard: "CY", shop: true, label: "Concord shop" });
  });

  it("opens a rebuilt non-map screen's new page", () => {
    expect(resolve({ kind: "screen", key: "egl:VHSW01I", label: "System wide" })).toEqual({ kind: "screen", key: "egl:VHSW01I", label: "System wide" });
  });
});
