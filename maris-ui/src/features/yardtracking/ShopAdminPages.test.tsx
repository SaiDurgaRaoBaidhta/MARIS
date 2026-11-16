import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { describe, expect, it } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ShopAdminMapPage, ShopMovePage } from "./ShopAdminPages";

const MAP = {
  map: "hayward", screen: "VHYT10I", yard: "HY", report: { whichReport: "SHOPSTAT", yard: "HY" },
  spots: [{ track: "TT", position: 1, clickable: true }, { track: "TT", position: 2, clickable: true }, { track: "1B", position: 1, clickable: true }],
  cars: [{ track: "TT", position: 2, car: "1101", consist: "201", orientation: "<", label: "<1101", status: "GO", moveReason: "M1", homeYard: "HY", colour: "GREEN" }],
};

function Opened() {
  const { stack } = useApp();
  const top = stack[stack.length - 1];
  return <p data-testid="opened">{top && top.kind === "screen" ? top.key : ""}</p>;
}

describe("shop administration map (old VHYT10I / VHYT95I)", () => {
  it("shows the cars on their spots and opens the car move after the second click", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/shop-admin/hayward": () => ({ body: MAP }),
      "POST /api/v1/yard-tracking/shop-admin/hayward/selection": req => {
        const spots = (req.body as { spots: string[] }).spots;
        return { body: spots.length < 2 ? { open: null, message: "", params: {} }
          : { open: "VHYT03I", message: "", params: { carNo: "1101", yrdTrkgYrd: "HY", yrdSubLoc: "TT", newYrdLoc: "1B", carNo2: "", posnNoFrom: 2, posnNoTo: 0, whichGui: "VHYT03I" } } };
      },
    });
    render(<AppProvider><ShopAdminMapPage map="hayward" code="VHYT10I" title="Hayward Shop Administration" /><Opened /></AppProvider>);
    const car = await screen.findByRole("button", { name: "Car 1101" });
    expect(car).toHaveTextContent("<1101");
    await userEvent.click(car);
    expect(await screen.findByText(/Now choose where car 1101 goes/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "1B spot 1" }));
    await waitFor(() => expect(screen.getByTestId("opened").textContent).toContain("egl:VHYT03I?carNo=1101"));
    expect(mw.calls("/api/v1/yard-tracking/shop-admin/hayward/selection").at(-1)?.body).toEqual({ spots: ["TT/2", "1B/1"] });
  });

  it("shows the old TOO MANY CLICKS message", async () => {
    fakeMiddleware({
      "GET /api/v1/yard-tracking/shop-admin/hayward": () => ({ body: MAP }),
      "POST /api/v1/yard-tracking/shop-admin/hayward/selection": () => ({ body: { open: null, message: "TOO MANY CLICKS", params: {} } }),
    });
    render(<AppProvider><ShopAdminMapPage map="hayward" code="VHYT10I" title="Hayward Shop Administration" /></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "Car 1101" }));
    expect(await screen.findByText("TOO MANY CLICKS")).toBeInTheDocument();
  });
});

const VIEW = {
  carNo: "1101", orientation: "<", status: "GO    TI", location: "TT - TRANSFER TABLE", yard: "HY - HAYWARD", currentReason: "M1  - WAITING",
  reasons: [{ code: "M1", text: "M1   - WAITING" }, { code: "P2", text: "P2   - PM" }], selectedReason: "M1",
  locations: [{ code: "TT", text: "TT - TRANSFER" }, { code: "1B", text: "1B - SHOP 1B" }], selectedLocation: "TT",
  compressAsked: false, insertLabel: [], message: "",
};

function Open({ query, children }: { query: string; children: React.ReactNode }) {
  const app = useApp();
  useEffect(() => { app.open({ kind: "screen", key: `egl:VHYT03I?${query}`, label: "VHYT03I" }); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  return <>{children}</>;
}

describe("shop car move (old VHYT03I / VHYT42I)", () => {
  it("opens with the map's values and sends Update with the choices", async () => {
    const mw = fakeMiddleware({
      "POST /api/v1/yard-tracking/shop-move": () => ({ body: VIEW }),
      "POST /api/v1/yard-tracking/shop-move/update": () => ({ body: { message: "Update successful", question: null } }),
    });
    render(<AppProvider><Open query="carNo=1101&yrdTrkgYrd=HY&yrdSubLoc=TT&newYrdLoc=TT&carNo2=&posnNoFrom=2&posnNoTo=5&whichGui=VHYT03I"><ShopMovePage /></Open></AppProvider>);
    expect(await screen.findByText(/GO\s+TI/)).toBeInTheDocument();
    expect((mw.calls("/api/v1/yard-tracking/shop-move").at(-1)?.body as { context: { posnNoTo: number } }).context.posnNoTo).toBe(5);
    await userEvent.selectOptions(screen.getByLabelText("New Reason Code"), "P2");
    await userEvent.selectOptions(screen.getByLabelText("New Shop Location"), "1B");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByText("Update successful")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/shop-move/update")[0].body).toMatchObject({ reason: "P2", location: "1B", reasonUnchanged: false });
  });

  it("asks the old reason check and goes on with Reason Is Not Changing", async () => {
    const mw = fakeMiddleware({
      "POST /api/v1/yard-tracking/shop-move": () => ({ body: VIEW }),
      "POST /api/v1/yard-tracking/shop-move/update": req => ({ body: (req.body as { reasonUnchanged: boolean }).reasonUnchanged
        ? { message: "Update successful", question: null }
        : { message: "", question: { title: "Reason Code Change Verification Window", text: "Reason code was not changed. Please verify current reason is still valid",
            keepButton: "Reason Is Not Changing", changeButton: "Change Reason", reason: "M1  - WAITING" } } }),
    });
    render(<AppProvider><Open query="carNo=1101&yrdTrkgYrd=HY"><ShopMovePage /></Open></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "Update" }));
    expect(await screen.findByRole("dialog", { name: "Reason Code Change Verification Window" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reason Is Not Changing" }));
    expect(await screen.findByText("Update successful")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/shop-move/update").at(-1)?.body).toMatchObject({ reasonUnchanged: true });
  });
});
