import { describe, expect, it, vi } from "vitest";
import type { Page } from "@/shared/lib/app-state";
import {
  NO_YARD_MOVE, carClickTarget, mapButtons, screenPage, shopMoveClick, trackClickTarget, yardMoveClick, yardTrackClick,
} from "./actions";
import { mapScreen } from "./screens";

const keyOf = (p: Page) => (p.kind === "screen" ? p.key : p.kind);
function buttons(yard: string, shop: boolean, mode: "inquiry" | "update", extra: object = {}) {
  const opened: Page[] = [];
  const ctx = { yard, shop, mode, open: (p: Page) => opened.push(p), refresh: vi.fn(), exit: vi.fn(), maint: vi.fn(), ...extra };
  const list = mapButtons(ctx);
  const press = (label: string) => { list.find(b => b.label === label)!.run(); return keyOf(opened[opened.length - 1]); };
  return { list, press, ctx };
}

describe("the old map screens", () => {
  it("knows each yard's and shop's inquiry and update screen", () => {
    expect(mapScreen("HY", false, "inquiry")).toBe("VHYT07I");
    expect(mapScreen("HY", false, "update")).toBe("VHYT05I");
    expect(mapScreen("HY", true, "update")).toBe("VHYT10I");
    expect(mapScreen("DY", true, "inquiry")).toBe("VHYT100I");
    expect(mapScreen("BS", false, "update")).toBe("VHIC02");
    expect(mapScreen("DP", true, "inquiry")).toBe("");      // Dublin has no shop map
  });
});

describe("buttons, in the old screens' order and wiring", () => {
  it("yard inquiry (VHYT07I): Refresh Map, Pseudo Status Train, Print (VHYT24I), Return", () => {
    const { list, press, ctx } = buttons("HY", false, "inquiry");
    expect(list.map(b => b.label)).toEqual(["Refresh Map", "Pseudo Status Train", "Print", "Return"]);
    expect(press("Pseudo Status Train")).toBe("egl:VHYT09I?yrdTrkgYrd=HY");
    expect(press("Print")).toBe("egl:VHYT24I?yrdTrkgYrd=HY");
    list.find(b => b.label === "Return")!.run(); expect(ctx.exit).toHaveBeenCalled();
    list.find(b => b.label === "Refresh Map")!.run(); expect(ctx.refresh).toHaveBeenCalled();
  });

  it("yard update (VHYT71I): Shop Map opens the shop's inquiry map, the others pass the yard code", () => {
    const { list, press } = buttons("CY", false, "update");
    expect(list.map(b => b.label)).toEqual(["Shop Map", "Pseudo Stat Trn", "Tower Menu", "Reports", "Print", "Maint", "ReFresh", "Exit"]);
    expect(press("Shop Map")).toBe("egl:VHYT60I");
    expect(press("Tower Menu")).toBe("egl:VHMTOI");
    expect(press("Reports")).toBe("egl:VHYTM3I?yrdTrkgYrd=CY");
    expect(press("Print")).toBe("egl:VHYT24I?yrdTrkgYrd=CY");
  });

  it("a yard without a shop has no Shop Map; Dublin passes DPX", () => {
    const { list, press } = buttons("DP", false, "update");
    expect(list.map(b => b.label)).not.toContain("Shop Map");
    expect(press("Pseudo Stat Trn")).toBe("egl:VHYT09I?yrdTrkgYrd=DPX");
  });

  it("Maint is greyed out once the access check said no", () => {
    expect(buttons("DP", false, "update", { maintDenied: true }).list.find(b => b.label === "Maint")!.disabled).toBe(true);
  });

  it("shop inquiry (VHYT04I): Print asks VHYT27I for the shop status report", () => {
    const { list, press } = buttons("HY", true, "inquiry");
    expect(list.map(b => b.label)).toEqual(["ReFresh", "Print", "Return"]);
    expect(press("Print")).toBe("egl:VHYT27I?whichReport=SHOPSTAT&yrdTrkgYrd=HY");
  });

  it("shop update (VHYT10I): the car picked feeds SV / HV, Change Reason and the CI buttons; Yard Map is the inquiry map", () => {
    const none = buttons("HY", true, "update");
    expect(none.list.map(b => b.label)).toEqual(["ReFresh", "Change Reason", "SV / HV", "CI - Never Go", "CI - Service", "Yard Map",
      "Shop Menu", "Reports", "Print", "Exit"]);
    expect(none.list.find(b => b.label === "SV / HV")!.disabled).toBe(true);
    expect(none.press("Yard Map")).toBe("egl:VHYT07I");
    expect(none.press("Shop Menu")).toBe("egl:VHMVSI");
    const picked = buttons("HY", true, "update", { pick: { track: "1A", position: 1, car: "3142" } });
    expect(picked.list.find(b => b.label === "SV / HV")!.disabled).toBe(false);
    expect(picked.press("SV / HV")).toBe("egl:VHSV06I?carNo=3142");
    expect(picked.press("Change Reason")).toBe("egl:VHYT06I?yrdTrkgYrd=HY&yrdSubLoc=1A&carNo=3142&posnNo=1");
    expect(picked.press("CI - Service")).toBe("egl:VHCI02I?carNo=3142&whichGuiFrom=VHYT10I&yrdTrkgYrd=HY");
    expect(picked.press("CI - Never Go")).toBe("egl:VHCI03I?carNo=3142&whichGuiFrom=VHYT10I&yrdTrkgYrd=HY");
  });
});

describe("clicks", () => {
  it("update yard map: from car, to position on the same track, then the to track opens the car move (VHYT11I)", () => {
    let r = yardMoveClick(NO_YARD_MOVE, "CY", "TF-01", 3);
    r = yardMoveClick(r.state, "CY", "TF-01", 5);
    expect(r.open).toBeUndefined();
    r = yardMoveClick(r.state, "CY", "ST-07", 1);
    expect(keyOf(screenPage(r.open!))).toBe("egl:VHYT11I?yrdTrkgYrd=CY&yrdSubLoc=TF-01&fromYrdSubLoc=TF-01&toYrdSubLoc=ST-07&positionNoFrom=3&positionNoTo=5");
    expect(r.state.clicked).toBe(0);
  });

  it("a second click on another track keeps the first position as the end (VHYT71IS-CLICK)", () => {
    const r = yardMoveClick(yardMoveClick(NO_YARD_MOVE, "CY", "TF-01", 3).state, "CY", "ST-07", 6);
    expect(r.state).toMatchObject({ fromPos: 3, toPos: 3, clicked: 2 });
  });

  it("an update map's track tag opens that track's car move, with the positions picked", () => {
    expect(keyOf(screenPage(yardTrackClick(NO_YARD_MOVE, "HY", "T-95").open!))).toBe("egl:VHYT11I?yrdTrkgYrd=HY&yrdSubLoc=T-95&fromYrdSubLoc=T-95");
    const one = yardMoveClick(NO_YARD_MOVE, "HY", "T-10", 2).state;
    expect(yardTrackClick(one, "HY", "T-10").open!.params).toMatchObject({ positionNoFrom: 2, positionNoTo: 2 });
  });

  it("update shop map: the car, then where it goes, opens the shop car move (VHYT03I); the same box twice drops it", () => {
    const first = shopMoveClick(null, "HY", "VHYT10I", "1A", 1, "3142");
    expect(first.open).toBeUndefined();
    expect(shopMoveClick(first.state, "HY", "VHYT10I", "1A", 1, "3142").state).toBeNull();
    const r = shopMoveClick(first.state, "HY", "VHYT10I", "2B", 1, "");
    expect(r.open!.params).toEqual({ carNo: "3142", yrdTrkgYrd: "HY", yrdSubLoc: "1A", newYrdLoc: "2B", carNo2: "", posnNoFrom: 1, posnNoTo: 1,
      whichGui: "VHYT03I", whichGuiFrom: "VHYT10I" });
  });

  it("inquiry maps: a track opens its cars (VHYT08I); a shop car its status (VHSV06I); a yard car nothing", () => {
    expect(keyOf(screenPage(trackClickTarget("PB", "C1")))).toBe("egl:VHYT08I?yrdSubLoc=C1&yrdTrkgYrd=PBX");
    const car = { car: "4706", text: "4706V", home: "HY", status: "NO GO", track: "1C", position: 1 };
    expect(carClickTarget("HY", true, "inquiry", car)).toEqual({ screen: "VHSV06I", params: { carNo: "4706" } });
    expect(carClickTarget("HY", false, "inquiry", car)).toBeNull();
    expect(carClickTarget("HY", true, "inquiry", null)).toBeNull();
  });
});
