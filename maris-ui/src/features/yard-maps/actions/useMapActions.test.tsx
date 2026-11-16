import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import type { MapCar } from "../types";
import { MapButtons } from "./MapButtons";
import { useMapActions, type MapActions } from "./useMapActions";

const base = { "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
let actions: MapActions;

function Harness({ yard, shop, mode, refresh = () => {} }: { yard: string; shop: boolean; mode: "inquiry" | "update"; refresh?: () => void }) {
  const app = useApp();
  actions = useMapActions({ yard, shop, mode, refresh });
  const top = app.stack[app.stack.length - 1];
  return <><MapButtons buttons={actions.buttons} /><output aria-label="front">{top.kind === "screen" ? top.key : top.kind}</output></>;
}
const front = () => screen.getByLabelText("front").textContent;
const ready = () => screen.findByLabelText("front");
const car = (no: string, track: string, position: number): MapCar => ({ car: no, text: no, home: "CY", status: "GO", track, position });

test("the buttons open the old screens' children, and Alt + the old letter presses one", async () => {
  fakeMiddleware(base);
  const refresh = vi.fn();
  render(<AppProvider><Harness yard="HY" shop={false} mode="inquiry" refresh={refresh} /></AppProvider>);
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "Pseudo Status Train" }));
  expect(front()).toBe("egl:VHYT09I?yrdTrkgYrd=HY");
  await userEvent.keyboard("{Alt>}m{/Alt}");
  expect(refresh).toHaveBeenCalledOnce();
});

test("inquiry yard map: a track tag and the pseudo track open their cars (VHYT08I)", async () => {
  fakeMiddleware(base);
  render(<AppProvider><Harness yard="CY" shop={false} mode="inquiry" /></AppProvider>);
  await ready();
  act(() => actions.onTrack("ST-07"));
  expect(front()).toBe("egl:VHYT08I?yrdSubLoc=ST-07&yrdTrkgYrd=CY");
  act(() => actions.onPseudo());
  expect(front()).toBe("egl:VHYT08I?yrdSubLoc=T-95&yrdTrkgYrd=CY");
});

test("update yard map: three clicks open the car move; the selection shows the cars picked", async () => {
  fakeMiddleware(base);
  render(<AppProvider><Harness yard="CY" shop={false} mode="update" /></AppProvider>);
  await ready();
  act(() => actions.onCar(car("3001", "TF-01", 4), "TF-01", 4));
  act(() => actions.onCar(car("3002", "TF-01", 2), "TF-01", 2));
  expect(actions.selection).toEqual({ kind: "yard", track: "TF-01", from: 2, to: 4, clicks: 2 });
  act(() => actions.onCar(null, "ST-07", 1));
  expect(front()).toBe("egl:VHYT11I?yrdTrkgYrd=CY&yrdSubLoc=TF-01&fromYrdSubLoc=TF-01&toYrdSubLoc=ST-07&positionNoFrom=4&positionNoTo=2");
  expect(actions.selection).toEqual({ kind: "none" });
});

test("update shop map: picking a car enables SV / HV; a second box opens the shop car move", async () => {
  fakeMiddleware(base);
  render(<AppProvider><Harness yard="CY" shop mode="update" /></AppProvider>);
  await ready();
  expect(screen.getByRole("button", { name: "SV / HV" })).toBeDisabled();
  act(() => actions.onCar(car("4597", "2A", 1), "2A", 1));
  expect(screen.getByRole("button", { name: "SV / HV" })).toBeEnabled();
  act(() => actions.onCar(null, "2B", 1));
  expect(front()).toBe("egl:VHYT03I?carNo=4597&yrdTrkgYrd=CY&yrdSubLoc=2A&newYrdLoc=2B&carNo2=&posnNoFrom=1&posnNoTo=1&whichGui=VHYT03I&whichGuiFrom=VHYT61I");
});

test("shop inquiry map: a car opens its status / history (VHSV06I)", async () => {
  fakeMiddleware(base);
  render(<AppProvider><Harness yard="HY" shop mode="inquiry" /></AppProvider>);
  await ready();
  act(() => actions.onCar(car("3142", "1A", 1), "1A", 1));
  expect(front()).toBe("egl:VHSV06I?carNo=3142");
});

test("Maint asks the access check where one exists: allowed opens VHYTM6I, refused greys the button", async () => {
  const mw = fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/maps/dublin-update/maintenance-access": () => ({ body: { allowed: false } }) });
  render(<AppProvider><Harness yard="DP" shop={false} mode="update" /></AppProvider>);
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "Maint" }));
  await vi.waitFor(() => expect(screen.getByRole("button", { name: "Maint" })).toBeDisabled());
  expect(mw.calls("/api/v1/yard-tracking/maps/dublin-update/maintenance-access")).toHaveLength(1);
  expect(front()).toBe("home");
});

test("Maint on a map with no access check to ask opens the maintenance menu with the yard", async () => {
  fakeMiddleware(base);
  render(<AppProvider><Harness yard="CY" shop={false} mode="update" /></AppProvider>);
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "Maint" }));
  await vi.waitFor(() => expect(front()).toBe("egl:VHYTM6I?yrdTrkgYrd=CY"));
});
