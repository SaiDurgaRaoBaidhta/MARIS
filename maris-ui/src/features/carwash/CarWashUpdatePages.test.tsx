import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarsWashPage, ConsistWashPage } from "./CarWashUpdatePages";

describe("Car wash of a consist (old VHCWA2I)", () => {
  const start = { washDate: "2026-11-14", yards: ["DY", "HY", "CY", "RY"], message: "Enter Data" };

  it("finds the consist's cars and records the wash", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/car-wash/consist-washes/start": () => ({ body: start }),
      "GET /api/v1/car-wash/consist-washes/consist": () => ({ body: { consist: "201", date: "2026-11-14", dashNo: 2, cars: ["1101", "1802"], found: true,
        alreadyWashed: false, message: "Enter Yard and Time Consist was Washed" } }),
      "POST /api/v1/car-wash/consist-washes": () => ({ body: { message: "Car Wash History Records Were Updated" } }),
    });
    render(<AppProvider><ConsistWashPage /></AppProvider>);
    expect(await screen.findByText("Enter Data")).toBeInTheDocument();
    expect(screen.getByLabelText("Wash date")).toHaveValue("2026-11-14");
    expect(screen.getByRole("button", { name: "Update" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Consist No."), "201");
    await userEvent.click(screen.getByRole("button", { name: "Get cars" }));
    const cars = await screen.findByRole("list", { name: "Cars in consist" });
    expect(within(cars).getByText("1802")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Wash location"), "HY");
    await userEvent.type(screen.getByLabelText("Wash time"), "0715");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Car Wash History Records Were Updated");
    expect(mw.calls("/api/v1/car-wash/consist-washes").at(-1)?.body).toEqual({ consist: "201", date: "2026-11-14", time: "0715", yard: "HY" });
  });

  it("does not offer Update for a consist already washed", async () => {
    fakeMiddleware({
      "GET /api/v1/car-wash/consist-washes/start": () => ({ body: start }),
      "GET /api/v1/car-wash/consist-washes/consist": () => ({ body: { consist: "201", date: "2026-11-14", dashNo: 2, cars: ["1101"], found: true,
        alreadyWashed: true, message: "This Consist Has Already Been Washed on the Date You Entered" } }),
    });
    render(<AppProvider><ConsistWashPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Consist No."), "201");
    await userEvent.click(screen.getByRole("button", { name: "Get cars" }));
    expect(await screen.findByText("This Consist Has Already Been Washed on the Date You Entered")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Update" })).toBeDisabled();
  });
});

describe("Car wash of single cars (old VHCWA3I)", () => {
  const start = { washDate: "2026-11-14", consist: "088", yards: ["DY", "HY", "CY", "RY"], message: "Please Enter Data" };

  it("checks the time when it is left, then records the cars", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/car-wash/car-washes/start": () => ({ body: start }),
      "GET /api/v1/car-wash/car-washes/time": () => ({ body: { time: "07:15:00", valid: true, message: "Enter Car numbers and press Update button to update" } }),
      "POST /api/v1/car-wash/car-washes": () => ({ body: { message: "Car Wash History records were updated. Enter more data.", cars: ["1101"] } }),
    });
    render(<AppProvider><CarsWashPage /></AppProvider>);
    expect(await screen.findByDisplayValue("088")).toBe(screen.getByLabelText("Dummy consist No."));
    await userEvent.selectOptions(screen.getByLabelText("Wash location"), "CY");
    await userEvent.type(screen.getByLabelText("Time washed"), "0715");
    await userEvent.tab();
    expect(await screen.findByText("Enter Car numbers and press Update button to update")).toBeInTheDocument();
    expect(screen.getByLabelText("Time washed")).toHaveValue("07:15:00");
    await userEvent.type(screen.getByLabelText("Car 1"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Car Wash History records were updated.");
    const body = mw.calls("/api/v1/car-wash/car-washes").at(-1)?.body as { cars: string[]; consist: string };
    expect(body.cars).toHaveLength(10);
    expect(body.cars[0]).toBe("1101");
    expect(body.consist).toBe("088");
  });

  it("keeps Update off while the time is wrong and shows the old message", async () => {
    fakeMiddleware({
      "GET /api/v1/car-wash/car-washes/start": () => ({ body: start }),
      "GET /api/v1/car-wash/car-washes/time": () => ({ body: { time: "25:01:00", valid: false, message: "THE TIME(HOURS) YOU ENTERED IS OUT OF RANGE" } }),
    });
    render(<AppProvider><CarsWashPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Time washed"), "2501");
    await userEvent.tab();
    expect(await screen.findByText("THE TIME(HOURS) YOU ENTERED IS OUT OF RANGE")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Update" })).toBeDisabled();
  });

  it("shows the old message when a car is invalid", async () => {
    fakeMiddleware({
      "GET /api/v1/car-wash/car-washes/start": () => ({ body: start }),
      "GET /api/v1/car-wash/car-washes/time": () => ({ body: { time: "07:15:00", valid: true, message: "ok" } }),
      "POST /api/v1/car-wash/car-washes": () => ({ status: 400, body: { detail: "Car with B is invalid car number; Please re enter the correct car number", invalidBoxes: [1] } }),
    });
    render(<AppProvider><CarsWashPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Time washed"), "0715");
    await userEvent.tab();
    await screen.findByText("ok");
    await userEvent.type(screen.getByLabelText("Car 1"), "9999");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Car with B is invalid car number");
  });
});
