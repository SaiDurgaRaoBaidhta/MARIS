# MARIS clean APIs: work split between Sai Durga Rao and Sai Teja

> **To an AI assistant reading this file with no other context:** this is the whole brief. Read it top to bottom, then
> read `docs/TESTING.md` and the finished ICS module named below, which is the reference implementation. Work only on
> the modules assigned to the person you are helping, and follow the "Shared files" and "Git" rules exactly. Ask the
> person which name is theirs if they have not said.

## 1. What the project is


* **MARIS** is BART's vehicle maintenance system. Its old web UI was IBM EGL Rich UI. Its **backend is unchanged** and
must not be changed: 640 REST services in `MARIS_RUI_Services_Web` on Tomcat (local: `http://localhost:8080`),
which run EGL programs against Oracle. Each service takes and returns **fixed-width records** (for example
`XDCONTROL`, `VHIC31A_CRIT`, `VHIC31A_DATA`).
* `maris-modernization/` holds the new system:
  * `maris-middleware/`: Spring Boot 3.5, Java 21. It sits between the new UI and the old backend. The new UI never
  talks to the backend directly.
  * `maris-ui/`: React 19 + TypeScript + Tailwind v4 + Vite.
  * The old EGL source (read-only, never edit) is at `~/Desktop/MARIS/MarisProd-1/MARIS_RUI/EGLSource/<module>/`.
* The middleware reaches every old screen in one of two ways:
  1. **Screen engine (today, all 215 screens):** the middleware runs the old screen's own EGL logic and the UI draws the

     old layout (`/api/v1/ui/...`). It works, but its API speaks "old screen" (window ids, `Push_Button3`).
  2. **Clean API (the work in this file):** one business-shaped endpoint per function, for example

     `GET /api/v1/ics/messages?revenueDate=2026-11-10`. The old screen's rules are rewritten in Java, the UI gets a new
       page, and each screen gets the five tests below. A screen moves from (1) to (2) when its clean API is done. Until
       then it keeps working through the engine, so nothing breaks in between.

## 2. The reference implementation: ICS (done)

Copy this structure for every module.

| Part | File |
|---|---|
| Service (the old rules) | `maris-middleware/src/main/java/com/bart/maris/middleware/domain/ics/IcsReportService.java` |
| Request / response types | `domain/ics/IcsCriteria.java`, `IcsOptions.java`, `IcsReport.java` |
| Module config (its beans) | `domain/ics/IcsConfig.java` |
| Controller (endpoints) | `api/ics/IcsController.java` (`GET /api/v1/ics/options`, `GET /api/v1/ics/messages`, `POST /api/v1/ics/messages/print`) |
| API test (fake backend) | `src/test/java/.../api/ics/IcsApiTest.java` (extends `support/ApiTestBase`) |
| Parity test (old screen vs new API) | `src/test/java/.../api/ics/IcsParityTest.java` (uses `support/Parity`) |
| Live test (real backend, read-only) | `src/test/java/.../live/IcsLiveTest.java` (extends `support/LiveTestBase`) |
| Coverage list | `src/test/resources/clean-apis/ics.json` |
| UI API calls | `maris-ui/src/features/ics/api.ts` |
| UI page + test | `maris-ui/src/features/ics/IcsReportPage.tsx`, `IcsReportPage.test.tsx` (uses `src/test/fetch.ts`) |
| UI routing | `maris-ui/src/features/ics/screens.tsx` (old screen code -> page; found automatically) |

Rules the ICS module follows, and every module must follow:

* **Tests first.** Write the API test and the parity test, see them fail, then write the code. The same applies to the UI
test and the page.
* **No copied texts or data.** Data always comes from the backend programs. The old screens' fixed texts (messages,
"ALL ..." labels) are read from the screen bundle with `ScreenBundle.promptText(...)` and
`ScreenBundle.assignedText(...)`, never typed into Java.
* **Call programs** through `domain/common/Programs` (`run`, `attempt`, `rows`, `text`, `number`):
  * URC 0, 4 and 100 count as success; 100 means no rows.
  * Any other URC becomes `LegacyBusinessException`, answered as 422 with the MARIS message.
  * Bad input becomes `InvalidRequestException`, answered as 400 with the old screen's message.
* **The parity test is the safety net.** Drive the old screen through the engine (`Parity.oldScreens()`) with the same
inputs, and compare the records each side sends to the backend (`Parity.lastSent(program)`).
* **Never run a writing program against the real backend in a test.** Live tests are read-only.

## 3. How to find a screen's rules

For screen `XXXXI` in EGL module `<module>`:

1. `MarisProd-1/MARIS_RUI/EGLSource/<module>/XXXXIConnections.egl` shows which button runs which statement group,

   which screen field is which record field, and which message boxes the screen has.
2. `MarisProd-1/MARIS_RUI/EGLSource/<module>/RUIFunctions.egl` has the statement groups: the checks, the

   `call "PGM"(...)` statements, the messages.
3. `maris-middleware/src/main/resources/legacy-catalog/services.json` and `records.json` list each program's parameters

   and the fields of its records (JSON field names, for example `revDate`).
4. To see the old screen run against the real backend, use the engine:

   `POST /api/v1/ui/open {"screen":"XXXXI"}`, then `/api/v1/ui/windows/{id}/events`. The response lists the programs it
     called.

## 4. Module ownership

Each module belongs to exactly one person. Its files live in folders named after the module, so the two people never
edit the same file.

| Name | Middleware packages | URL prefix | UI folder |
|---|---|---|---|
| `<pkg>` | `domain/<pkg>/`, `api/<pkg>/`, tests in `api/<pkg>/` and `live/<Pkg>LiveTest.java` | `/api/v1/<url>/` | `maris-ui/src/features/<pkg>/` |

### Sai Durga Rao: 112 screens (74 read-only, 34 update)

| EGL module | Package / UI folder | URL prefix | Screens |
|---|---|---|---|
| MRSVehicleIcsReports | `ics` | `/api/v1/ics/` | 3 |
| MRSVehicleQueries | `vehiclequeries` | `/api/v1/vehicle-queries/` | 52 |
| MRSVehiclePrevMaint | `prevmaint` | `/api/v1/preventive-maintenance/` | 7 |
| MRSVehicleCarWash | `carwash` | `/api/v1/car-wash/` | 4 |
| MRSVehicleCarBright | `carbright` | `/api/v1/car-bright/` | 3 |
| MRSWaysideMaintenance | `wayside` | `/api/v1/wayside/` | 2 |
| MRSVehicleIncdEvent | `incidentevents` | `/api/v1/incident-events/` | 6 |
| MRSVehicleIncident | `incidents` | `/api/v1/incidents/` | 8 |
| MRSVehicleRepair | `repair` | `/api/v1/repair/` | 4 |
| MRSSecondaryRepair | `secondaryrepair` | `/api/v1/secondary-repair/` | 12 |
| MRSVehicleMaintenance | `vehiclemaint` | `/api/v1/vehicle-maintenance/` | 11 |

### Sai Teja: 83 screens (42 read-only, 36 update)

| EGL module | Package / UI folder | URL prefix | Screens |
|---|---|---|---|
| MRSYardTrackingReport | `yardreports` | `/api/v1/yard-reports/` | 16 |
| MrsYardTrackingQueries | `yardqueries` | `/api/v1/yard-queries/` | 7 |
| MRSConfigurTrackQuery | `configqueries` | `/api/v1/config-queries/` | 4 |
| MRSYardTracking | `yardtracking` | `/api/v1/yard-tracking/` | 34 |
| MRSConfigureTracking | `configtracking` | `/api/v1/config-tracking/` | 3 |
| MRSVehicleConsist | `consist` | `/api/v1/consist/` | 10 |
| MRSTableMaintenanceApp2 | `tables2` | `/api/v1/tables-2/` | 5 |
| MRSTableMaintenanceApp3 | `tables3` | `/api/v1/tables-3/` | 4 |

**Not assigned:** `MRSCommonCode` (the shared message box XXM0I, security AFCX6I, printing XPRINTI), `MRSMainMenu`
(sign-on and main menus) and `MRSVehicleMenu` (menus). These are shared parts. Sign-on and menus are already done in the
new UI. If a module needs something from them, follow the shared-files rule.

**Order:** within each person's list, do the **read-only** screens of every module first, then the **update** screens.
Do one module at a time, on one branch, and merge it before starting the next.

## 5. Shared files: the rule that prevents merge conflicts

**Edit only your own module folders.** These belong to nobody, and changing them needs the other person's agreement
first. Make the change in its own small branch named `shared/<what>`, merge it, and tell the other person to pull it
before continuing:

* `maris-middleware/src/main/java/.../engine/`**, `legacy/*`*, `security/**`, `config/**`, `api/auth/**`, `api/ui/**`,
`api/error/GlobalExceptionHandler.java`, `domain/common/**`, `domain/InvalidRequestException.java`,
`domain/LegacyBusinessException.java`
* `maris-middleware/src/test/java/.../support/`**, `coverage/*`*, `live/LiveBackendTest.java`
* `maris-middleware/src/main/resources/`** (`application*.yml`, the generated `legacy-catalog/` and `egl-screens/`)
* `maris-middleware/pom.xml`, `maris-ui/package.json`, `package-lock.json` (new dependencies)
* `maris-ui/src/shared/`**, `src/app/*`*, `src/test/**`, `src/features/screens/**`, `src/api/openapi.yaml`
* `scripts/`**, `docs/TESTING.md`, `.github/*`*, and this file

**Nothing else is shared**, by design:

* Each module registers its own beans in its own `domain/<pkg>/<Pkg>Config.java`; there is no shared config to edit.
* Each module lists its finished screens in its own `src/test/resources/clean-apis/<pkg>.json`.
* Each module routes its UI pages in its own `maris-ui/src/features/<pkg>/screens.tsx`, which the UI finds by itself.
* Each module has its own live test class `live/<Pkg>LiveTest.java`.

**Helpers:** if a module needs a helper that is not in `domain/common`, put it in the module's own package first. Move it
to `domain/common` later, through a `shared/...` branch, only if both people need it.

**Class names** start with the module, for example `VehicleQueriesService` and `YardReportsController`, so two modules
never create the same class name. **Endpoint paths** always start with the module's URL prefix.

**Spring bean names** must be unique across the whole application, and git does not see a clash between two files. So
every `@Bean` method also starts with the module, for example `vehicleQueriesService()` in `VehicleQueriesConfig`, never
a generic name like `service()` or `programs()`. A clash makes the middleware refuse to start, which fails every test
after the merge.

## 6. Git

1. `main` always passes `./scripts/test-all.sh`.
2. Work on one branch per module: `sai-durga-rao/<pkg>` or `sai-teja/<pkg>`. Never commit directly to `main`.
3. Before opening a pull request:
   * `git fetch && git rebase origin/main`
   * `./scripts/test-all.sh`, which must print `All tests passed.`
   * if the module has read-only programs, also run with `--live` against the local backend.
4. A pull request contains one module, or one `shared/...` change, never both.
5. When a `shared/...` branch is merged, the other person rebases their branch on `main` the same day.
6. Turn on branch protection for `main` on GitHub: "Require a pull request" and "Require status checks to pass"

   (the `tests` workflow). A pull request with a failing test then cannot be merged.
7. Commit messages start with the module, for example `vehiclequeries: VHSV01I status vehicle report (API, parity, UI)`.

## 7. After every merge: how we know everything still works

Merging cleanly in git is not enough; the merged code has to pass all tests. After each merge into `main`:

1. CI runs `./scripts/test-all.sh` on `main`. It must be green before anyone merges again.
2. The other person rebases their branch on `main` the same day and runs `./scripts/test-all.sh`. If something fails,

   fix it on that branch before continuing; never merge red.
3. Once a week, or before a release, one person runs `./scripts/test-all.sh --live` on `main` against the local backend,

   and opens a few converted screens in the UI (`npm run dev`) to check them by eye.

What the tests catch after a merge, even when git reported no conflict:

| Problem | Caught by |
|---|---|
| Two modules define the same endpoint or the same bean name | the middleware does not start, so every API test fails |
| One screen claimed by both people | `CleanApiCoverageTest` (listed twice) and the UI tests (routed twice) |
| A shared helper changed in a way that breaks the other's module | that module's API and parity tests |
| A converted screen no longer behaves like the old one | its parity test |
| An old screen still on the engine broke | `OpenEveryScreenTest` and `EngineFlowTest` |
| The UI does not build | type check, lint and `npm run build` in `test-all.sh` |

## 8. Definition of done for one screen

- [ ] API test (`api/<pkg>/<Name>ApiTest`), written first: request -> exact records sent -> JSON back. It also covers
  bad input (old message, nothing called), no rows (URC 100) and a backend error (422 with the MARIS message).
- [ ] Parity test (`api/<pkg>/<Name>ParityTest`): the old screen in the engine and the new API send the same records
  (and, for lists, show the same rows). For an update screen it covers every button that writes, including the order
  of the program calls.
- [ ] Live test (`live/<Pkg>LiveTest`), read-only programs only: compares the API with the old screen on the real
  backend, with no fixed expected numbers.
- [ ] UI test (`features/<pkg>/*.test.tsx`), written first: the requests the page sends, the rows it shows, errors, and
  the old screen's rules (fields turned off, required choices).
- [ ] The screen is listed in `clean-apis/<pkg>.json` and routed in `features/<pkg>/screens.tsx`.
- [ ] `./scripts/test-all.sh` passes.

## 9. Commands

```bash
./scripts/test-all.sh                    # everything, no backend needed
MARIS_LIVE_GROUP=<group> MARIS_LIVE_PASSWORD=<password> ./scripts/test-all.sh --live   # + real local backend (read-only)
cd maris-middleware && mvn test -Dtest='VehicleQueries*'                   # one module's middleware tests
cd maris-ui && npx vitest run src/features/vehiclequeries                  # one module's UI tests
```

Running the apps: see the middleware and UI READMEs (middleware on :8090 with `--spring.profiles.active=local`; UI with
`npm run dev` on :5173).

## 10. Every screen, by owner

*Kind:* **done** = already on the clean API; **read-only** = its programs only read; **update** = at least one of its
programs writes (in **bold**); **menu** = a menu, which stays as tiles in the new UI (no API needed). Program lists come
from the screen layouts' metadata. Always confirm them in the EGL before writing tests.

### Sai Durga Rao

#### MRSVehicleIcsReports -> `ics` (`/api/v1/ics/`), 3 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHIC30I | VHIC30 - ICS Reports Criteria Selection Window | done | MRSTABLEMAINTENANCE, VHIC30A, VHIC30B, VHIC30C, VHIC30E, WS70A01 |
| 2 | VHIC31I | VHIC31 - ICS / MARIS Message Received Report V.July .03.01 | done | VHIC31A |
| 3 | VHIC32I | VHIC32I - ICS / Maris Report Sub-Menu | read-only | – |

#### MRSVehicleQueries -> `vehiclequeries` (`/api/v1/vehicle-queries/`), 52 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHCHG1I | VHChg1 - Change Car Status Maint Function | read-only | MRSVEHICLECONSIST, MRSVEHICLEMAINTENANCE |
| 2 | VHCHR1I | VHCHR1I- Car Hours Report | read-only | MRSVEHICLEINCIDENT, VHCHR01, VHCHR1A, WS70A01 |
| 3 | VHCL02I | VHCL02 - CONSIST LOG | read-only | VHCL02B, VHCL02C |
| 4 | VHCO01I | VHCO01I - Consist Orientation Criteria | read-only | MRSVEHICLEPREVMAINT, VHCO011, VHSV011, WS70A01 |
| 5 | VHCP01I | VHCP01I - Cars Pulled from Yard/Shop Report Sept. 2002 | read-only | VHCP01A, VHCP01B, VHCP01C, VHCP01D, WS70A01 |
| 6 | VHCSH1I | VHCSH1I - Car Status History Report | read-only | MRSVEHICLEINCIDENT, VHCSH1A |
| 7 | VHCSH2I | VHcsh2I - Printer Selection Screen | read-only | VHCSH2A, WS70A01 |
| 8 | VHDC01I | Vhdc01I - Reprint D & C Sheet | read-only | PRINTBLANKDNC, PRINTDNC, VHDC011, VHDC012, VHDC013 |
| 9 | VHDP01I | VHDP01I - DPX / PBX Trains Cleared to a Different Location Report Oct. | read-only | VHDP01A, VHDP01B, VHDP01C, WS70A01 |
| 10 | VHDVS2I | vhDVS2 - Daily Vehicle Status | read-only | – |
| 11 | VHDVS3I | vhDVS3 - Daily Vehicle Status | read-only | – |
| 12 | VHDVS4I | VHdvs4I - Printer Selection Screen | read-only | VHDVS04, WS70A01 |
| 13 | VHFO01I | VHFO01I - Fixed Consist Orientation Report 07-01-02 | read-only | VHFO01A, VHFO01B, VHFO01E, WS70A01 |
| 14 | VHFR01I | VHFR01I - Fleet Report | read-only | VHFR01A, VHFR01C, WS70A01 |
| 15 | VHHF01I | VHHF01I - Home Yard Fleet Report | read-only | VHHF01A, VHHF01B |
| 16 | VHHF02I | VHhf02I - Printer Selection Screen | read-only | VHHF02A, WS70A01 |
| 17 | VHMC01I | VHMC01I - MT/CT and Yard Tracking Report SEPT. 02 | read-only | VHMC01A, VHMC01B, VHMC01C, VHMC01D, VHMC01E, WS70A01 |
| 18 | VHMR01I | VHMR01I - Reason Used by Shop Report Dec. 2002 | read-only | VHMR01A, VHMR01B, VHMR01C, VHMR01D, WS70A01 |
| 19 | VHMR02I | VHMR02I - Shop Status Reason 50 Report Dec. 2002 v. 1.0 | read-only | VHMR02A, VHMR02B, VHMR02C, VHMR02D, WS70A01 |
| 20 | VHMR03I | VHMR03I - Shop Status reason 099 report Dec. 02 v.1.0 | read-only | VHMR03A, VHMR03B, VHMR03C, VHMR03D, WS70A01 |
| 21 | VHNG01I | VHNG01I - No Go Cars moved from T-95 Sept 2002 | read-only | VHNG01A, VHNG01B, VHNG01C, VHNG01D, WS70A01 |
| 22 | VHOT01I | VHOT01I - Combined Trains Report Oct. 2002 v. 1.0 | read-only | VHOT01A, VHOT01B, VHOT01C, WS70A01 |
| 23 | VHPS01I | VHPS01I - Consists from Pseudo Trains Report Aug 2002 | read-only | VHPS01A, VHPS01B, VHPS01C, VHPS01E, WS70A01 |
| 24 | VHRC01I | VHRC01I - Repeater Cars Report Selection Criteria | read-only | MRSVEHICLEINCIDENT, VHRC011, VHYS01A, WS70A01 |
| 25 | VHRC02I | VHRC02I - Repeater Car Summary Report | read-only | VHRC02A |
| 26 | VHRC03I | VHRC03I - Revenue Cars - Daily Run Time/Consist Report March 03 | read-only | VHRC03A, VHRC03B, VHRC03C, WS70A01 |
| 27 | VHSCM1I | VHSCM1I - SCRAM Report | read-only | VHSCM1A, VHSCM1B, VHXREVA |
| 28 | VHST01I | VHST01 - Consist Tracking - Status Train Symptom Criteria | read-only | VHST011, WS70A01 |
| 29 | VHST02I | VHST02 - Consist Tracking - Status Train | read-only | VHST2A1 |
| 30 | VHST03I | VHST03 - Consist_lbl Tracking - Status Train - Symptom | read-only | VHST3A1, VHST3A3, VHST3A4 |
| 31 | VHST04I | VHST04 - Consist Tracking - Status Train Detail | read-only | VHST04A, VHST04B, VHST4A2 |
| 32 | VHST05I | VHST05 - Consist Tracking - Status Train Criteria | read-only | VHST011, WS70A01 |
| 33 | VHSV01I | VHSV01I - Status Vehicle Report Criteria | read-only | MRSVEHICLEINCIDENT, VHSV011, VHSV012, WS70A01 |
| 34 | VHSV02I | VHSV02 - Vehicle Status - Details Report from 03-01-1999 to 04-01-1999 | read-only | MRSVEHICLEMAINTENANCE, VHSV02A, VHSV02C |
| 35 | VHSV04I | VHSV04I - History Vehicle Report Criteria | read-only | MRSVEHICLEINCIDENT, VHSV011, VHSV012, WS70A01 |
| 36 | VHSV05I | VHSV05I - History Vehicle Report for the date range 01-01-2000 to 01-2 | read-only | MRSVEHICLEMAINTENANCE, VHSV05A, VHSV05C, VHSV05D, VHSV05F |
| 37 | VHSV06I | VHSV06I - Status / History Vehicle - Inquiry V1.OCT/2001.1.0 | read-only | MRSVEHICLEMAINTENANCE, VHSV06A, VHSV06B, VHSV06C, VHSV06D, VHSV06F, VHSV06N |
| 38 | VHSV07I | VHSV07I - History / Vehicle Report Oct. 02 v.1.0 | read-only | VHSV07A, VHSV07B, VHSV07C, WS70A01 |
| 39 | VHYS01I | VHYS01I - Home Yard Status Report Option | read-only | VHYS01A, VHYS01B, VHYS01C, WS70A01 |
| 40 | VHYS02I | VHYS02I - Home Yard Status | read-only | VHYS02A, VHYS02B |
| 41 | VHYS03I | VHYS03I - Home Yard Status - NOGO | read-only | VHYS02B, VHYS03A, VHYS03B |
| 42 | VHYS04I | VHYS04I - Home Yard Status Summary V 1 JAN 01.01 | read-only | VHYS04A |
| 43 | VHYS05I | VHYS05I - Home Yard Summary Report | read-only | VHYS05A |
| 44 | VHYS06I | VHYS06I - Yard Status Report Options | read-only | VHYS06A, VHYS06B, VHYS06C, WS70A01 |
| 45 | VHYS07I | VHYS07I - Yard Status | read-only | VHYS07A, VHYS07B |
| 46 | VHYS08I | VHYS08I - Yard Status - NOGO | read-only | VHYS07B, VHYS08A, VHYS08B |
| 47 | VHYS09I | VHYS09I - Yard Status Summary | read-only | VHYS09A |
| 48 | VHYS10I | VHYS10I - Yard Summary Report | read-only | VHYS10A |
| 49 | VHYTOTI | VHYTOTI - Out of Town - No Go Cars April 04 | read-only | VHYTOTA, VHYTOTB, WS70A01 |
| 50 | Vhpt01i | VHPT01I - Yard Tracking Pseudo Trains Report March 03 | read-only | VHPT01A, VHPT01B, VHPT01C, VHPT01E, WS70A01 |
| 51 | VHCL01I | VHCL01 - Consist Log - Criteria | update | VHCL011, **VHCL01E**, VHSV011, VHXREVA, WS70A01 |
| 52 | VHDVS1I | vhDVS1 - Daily Vehicle Status | update | VHDVS01, VHDVS03, **VHDVS11** |

#### MRSVehiclePrevMaint -> `prevmaint` (`/api/v1/preventive-maintenance/`), 7 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHHPD1I | VHHPD1 - Home Yard Vehicle PM Status | read-only | VHHPDA1 |
| 2 | VHHPD2I | VHHPD2I- HPD Report - Cars that are not in the Shop | read-only | VHHPD2A |
| 3 | VHHPM1I | VHHPM1 - Home Yard Vehicle PM Status - Criteria | read-only | MRSVEHICLEQUERIES, VHHPMA2, VHHPMA3, WS70A01 |
| 4 | VHHPM2I | VHHPM2 - Home Yard Vehicle PM Status | read-only | VHHPMA1 |
| 5 | VHHPR1I | VHHPR1 - Home Yard Vehicle PM Status | read-only | VHHPRA1 |
| 6 | VHPMR1I | VHPMR1 - Home Yard Vehicle PM Status | read-only | VHPMRA1 |
| 7 | VHPMS01I | VHPMS01I - PM Scheduling / Rescheduling | update | VHHPMA3, **VHPMS**, VHPMSA1, VHPMSA2, VHPMSA3, VHXREVA |

#### MRSVehicleCarWash -> `carwash` (`/api/v1/car-wash/`), 4 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHCWA2I | VHCWA2 - Consist Wash - Add | read-only | MRSVEHICLEINCDEVENT, MRSVEHICLEQUERIES |
| 2 | VHCWR1I | VHCWR1I - Cars Washed at Yard Report | read-only | MRSVEHICLEQUERIES |
| 3 | VHCWR2I | VHCWR2I - Car Wash Overdue for Home Yard Report | read-only | MRSVEHICLEQUERIES |
| 4 | VHCWA3I | VHCWA3 - Dummy Consist Wash - Add | update | MRSVEHICLEINCDEVENT, MRSVEHICLEINCIDENT, VHCWA3A, VHCWA3B, **VHCWA3D**, **VHCWA3E**, VHXREVC |

#### MRSVehicleCarBright -> `carbright` (`/api/v1/car-bright/`), 3 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHCBR1I | VHCBR1I - Car Thorough Cleaning Report | read-only | VHCBR1A, VHCBR1X |
| 2 | VHCBR2I | VHcbr2I - Printer Selection Screen | read-only | VHCBR02, WS70A01 |
| 3 | VHCBP1I | VHCBP1I - Car Thorough Clean Processing Screen | update | MRSVEHICLEINCDEVENT, **VHCBP1A**, VHCBR2C |

#### MRSWaysideMaintenance -> `wayside` (`/api/v1/wayside/`), 2 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | WS13I | WS13 -- Add Narrative | read-only | MRSWAYSIDEINCIDENT, WSX2A01, WSX3A |
| 2 | WS17I | WS17I - Search Stock No Table | read-only | MRSWAYSIDEREPAIR, WS17A05 |

#### MRSVehicleIncdEvent -> `incidentevents` (`/api/v1/incident-events/`), 6 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHIE01I | VHIE01I - Revenue Incident Log | read-only | VHIE01A, VHIE01B |
| 2 | VHIE02I | VHIE02 - Reports Criteria Selection Window | read-only | VHIE02A, VHIE02B, VHIE02C, WS70A01 |
| 3 | VHIE06I | VHIE06 - Train or Car Look Up | read-only | VHIE03H, VHIE06A |
| 4 | VHIE03I | VHIE03I - Revenue Event Log | update | MRSVEHICLEMENU, **VHEVL1F**, **VHEVLX**, **VHEVLY**, VHIE01B, VHIE03A, VHIE03B, VHIE03C, VHIE03D, VHIE03E, VHIE03F, VHIE03G, VHIE03H, **VHIE04A**, **VHIE04B**, VHIE04C, VHIE04D, VHINC1A, VHINC1B, **VHINC1X**, VHINC3A, VHINC6A, **VHINC8E**, VHINCE, VHINXA, **VHINXB**, **VHINXC** |
| 5 | VHIE04I | VHIE04I - Event Log Detail | update | **VHEVL1F**, **VHEVLX**, **VHEVLY**, VHIE03B, VHIE03C, VHIE03D, **VHIE04B**, VHIE04C, VHINC1A, VHINC1B, VHINC3A, VHINC6A, **VHINC8E**, VHINCE, VHINXA, **VHINXB**, **VHINXC** |
| 6 | VHIE07I | VHIE07 - Search Event and Incident Data | update | VHIE07A, VHIE07B, **VHIE07C** |

#### MRSVehicleIncident -> `incidents` (`/api/v1/incidents/`), 8 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHCI02I | VHCI02I - Vehicle Tracking Change Incident | update | VHCI01A, VHCI01B, VHCI01C, VHCI01D, VHCI01E, **VHCI01F**, VHCI02B, **VHCI02D**, VHCI02G, **VHCIP**, **VHCIQ**, **VHCIR**, **VHCIS**, **VHCIT**, **VHCIU**, **VHCIW**, **VHCIX**, **VHCIY**, **VHCIZ**, VHINC6A, VHINCD, **VHINVA**, **VHINVB**, VHINXA, **VHINXB**, **VHINXD**, VHXREVA |
| 2 | VHCI03I | VHCI03I - Vehicle Tracking Change Incident Never GO | update | VHCI01B, VHCI01E, VHCI02B, VHCI02G, VHCI03E, **VHCIV**, **VHINVA**, **VHINVB**, VHINXA, **VHINXB**, **VHINXD**, VHXREVA |
| 3 | VHINC01I | VHINC01I - Vehicle Tracking (Incident Train) | update | VH01A02, VHCI02G, VHINC1A, VHINC1B, **VHINC1C**, VHINC1D, **VHINC1F**, VHINCD, VHINCE, **VHINVC**, VHXREVA |
| 4 | VHINC03I | VHINC03I - Vehicle Tracking | update | VHCI02G, VHINC1A, VHINC1D, VHINC3A, VHINC3B, VHINC6A, VHINCD, VHINCXA, **VHINCZ**, **VHINVB**, VHXREVA |
| 5 | VHINC06I | VHINC06I - Vehicle Tracking (Incident Veh - Revenue) | update | VH01A02, VHCI02G, VHINC1A, VHINC1D, VHINC3A, VHINC3C, VHINC6A, VHINCD, VHINCE, **VHINCX**, **VHINVC**, VHXREVC |
| 6 | VHINC08I | VHINC08I - Vehicle Tracking | update | VH01A02, VHCI02G, VHINC1A, VHINC1D, VHINC3A, VHINC6A, **VHINC8E**, VHINCXA, **VHINCY**, **VHINVB**, VHINXA, **VHINXB**, **VHINXC**, VHXREVA |
| 7 | VHINC09I | VHINC09I - Vehicle Tracking (Incident Veh - Update) | update | VHINC1A, **VHINC1C**, VHINC1D, VHINC9A |
| 8 | VHINC10I | VHINC10I - Vehicle Tracking (Incident Veh - Update) V1 MARCH 01.01 | update | MRSVEHICLEMENU, VHINC1D, VHINC9A, **VHINCXB**, **VHINCXC**, VHXREVA |

#### MRSVehicleRepair -> `repair` (`/api/v1/repair/`), 4 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHRV01I | VHRV01I - Vehicle Repair Tracking | update | MRSVEHICLEINCIDENT, VHRV01A, **VHRV01B**, VHRV01C, VHRV01D, VHRV01E, VHRV01F |
| 2 | VHRV02I | VHRV02I - Vehicle Tracking - Repair Component Screen | update | VHRV02A, **VHRV02B**, **VHRV02C**, **VHRV02D**, VHRV02E, VHRV02F |
| 3 | VHRV03I | VHRV03I - Vehicle Repair Tracking (RVE) | update | MRSVEHICLEINCIDENT, VHRV01A, VHRV01F, **VHRV03A** |
| 4 | VHRV04I | VHRV04I - Vehicle Repair Tracking Maintenance | update | MRSVEHICLEINCIDENT, VHRV01A, **VHRV01B**, VHRV01C, VHRV01D, VHRV01E, VHRV01F, **VHRV4A1**, **VHRV4A2** |

#### MRSSecondaryRepair -> `secondaryrepair` (`/api/v1/secondary-repair/`), 12 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHSR05I | VHSR05I - Secondary Repair - INQUIRY STOCK | read-only | VHSR05A, VHSR05B, VHSR05C |
| 2 | VHSR10I | VHSR10I - Secondary Repair - HISTORY JOB | read-only | VHSR09A, VHSR10A, VHSR11A, VHSRJNX, VHSRJRN |
| 3 | VHSR11I | VHSR11 - Secondary Repair - STATUS JOB | read-only | VHSR11A, VHSRJNX |
| 4 | VHSR15I | VHSR15I - Secondary Repair - REPRINT JOB FORM | read-only | VHSR15A, VHSR15B, VHSR15C |
| 5 | VHSR01I | VHSR01I - Secondary Repair - IN THE DOOR | update | MRSVEHICLEINCIDENT, VHSR01A, VHSR01B, VHSR01C, VHSR01D, **VHSR01E**, VHSRRA, VHSRUD, VHSRVS, VHSRWT |
| 6 | VHSR02I | VHSR02I - Secondary Repair - COMMENTS MAINTENANCE | update | **VHSR02A**, **VHSR02B**, VHSR02C, VHSR02D, VHSR02E, VHSR02F |
| 7 | VHSR03I | VHSR03I - Secondary Repair - LABOR DETAIL | update | VHSR03A, VHSR03B, VHSR03C, **VHSR03D**, **VHSR03E**, VHSR03F, VHSR09A, VHSRWEL |
| 8 | VHSR04I | VHSR04I - Secondary Repair - OUT THE DOOR | update | VHSR02E, VHSR04A, **VHSR04B**, VHSRWT |
| 9 | VHSR08I | VHSR08I - Secondary Repair - JOB SPLIT | update | VHSR01A, VHSR01D, VHSR08A, VHSR08B, **VHSR08C**, VHSRRA, VHSRVS, VHSRWT |
| 10 | VHSR09I | VHSR09I - Secondary Repair - REPAIR DETAIL | update | VHSR09A, VHSR09B, VHSR09C, **VHSR09D**, VHSR09E, VHSR09F, VHSR09G, VHSR09H |
| 11 | VHMSRI | VHMSRI - Secondary Repair Menu | menu | – |
| 12 | VHMSUB1I | VHMSUB1I - Secondary Repair Sub Menu | menu | – |

#### MRSVehicleMaintenance -> `vehiclemaint` (`/api/v1/vehicle-maintenance/`), 11 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHNAR02I | VHNAR02 - Incident Narrative Display Screen | read-only | VHNARX |
| 2 | VHVM12I | VHVM12 - Printer Selection Gui for Reports | read-only | VHVM12A, WS70A01 |
| 3 | VHVM13i | VHVM13 - Add Mods | read-only | MRSVEHICLEMENU, VHVM11E, VHVM13A, VHVM13B, VHVM13C, WS70A01 |
| 4 | VHNAR01I | VHNAR01I - Narrative Maintenance Screen | update | VHXNR1A, **VHXNR2A**, **VHXNR3A**, VHXNR4A, VHXNR5A, VHXNR6A, **VHXNR7A**, **VHXNR8A** |
| 5 | VHVM02I | VHVM02 - Vehicle Maintenance - Add | update | **VHVM1A3**, VHVM1A6, VHVM1A7, VHVM1AA |
| 6 | VHVM03I | VHVM03 - Vehicle Maintenance - Edit Vehicle | update | MRSVEHICLECONSIST, **VHVM1A4**, VHVM1A5, VHVM1A6, VHVM1A7, VHVM1A9 |
| 7 | VHVM06I | Vhvm06i - Vehicle Incident Status History Maintenance | update | MRSTABLEMAINTENANCE, VHVM6A1, **VHVM6A2**, **VHVM6A3**, **VHVM6A4**, VHVM6A6 |
| 8 | VHVM08I | VHVM08 - Test Hours Update | update | VHVM1A5, **VHVM8A1** |
| 9 | VHVM10I | VHVM10I - Consist Table Maintenance | update | MRSAFCMAINTENANCE, MRSVEHICLEMENU, VHVM10A, **VHVM10C** |
| 10 | VHVM11I | VHVM11I - Vehicle Parts Status / Mod Tracking | update | VHVM11A, **VHVM11B**, **VHVM11C**, VHVM11E, VHVM11F, VHVM11G, VHVM11H, **VHVM11J**, **VHVM11K**, VHVM11L, VHVM1A5, VHVMDTA |
| 11 | VHVM19I | VHVM19I - Consist History Table Maintenance | update | MRSYARDTRACKINGQUERIES, VHVM19A, **VHVM19B** |

### Sai Teja

#### MRSYardTrackingReport -> `yardreports` (`/api/v1/yard-reports/`), 16 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHIC01 | VHIC01 - Berryessa Station(inquiry) | read-only | VHIC01A |
| 2 | VHIC02 | VHIC02 - Berryessa Station (Update) | read-only | VH01A02, VHIC01A |
| 3 | VHSW01I | VHSW01I - BART System Wide Map | read-only | VHSW01A |
| 4 | VHYT100I | VHYT100I - Daly City Shop Map(Inquiry) | read-only | – |
| 5 | VHYT101I | VHYT101I - Daly City Shop Update | read-only | – |
| 6 | VHYT105I | VHY105I - Daly City Yard Map(Inquiry) | read-only | – |
| 7 | VHYT106I | VHY106I - Daily City Yard Map (Update) | read-only | – |
| 8 | VHYT61I | VHYT61I - Concord Shop Map(Update) | read-only | – |
| 9 | VHYT70I | VHYT70I - Concord Yard Map (Inquiry) | read-only | – |
| 10 | VHYT71I | VHYT71I - Concord yard Map(Update) | read-only | – |
| 11 | VHYT80I | VHYT80I - Millbrae W-Line Storage | read-only | VHYT80A |
| 12 | VHYT81I | VHYT81I - Millbrae Storage Update Map | read-only | – |
| 13 | VHYT90I | VHYT90I - Richmond Yard Map (Inquiry) | read-only | – |
| 14 | VHYT91I | VHYT91I - Richmond Yard Map (Update) V 01-10-03 1.1 | read-only | – |
| 15 | VHIC03I | VHIC03I - Move Pseudo Train to another Yard | update | **VHIC03A**, VHYT09A |
| 16 | VHYT53I | VHYT53I - Configure Pseudo(s) 08-23-04 | update | VHYT53B, VHYT53C, VHYT53D, VHYT53E, **VHYT53F**, **VHYT53G**, **VHYT53H**, **VHYT53K**, VHYT53L, VHYT53M |

#### MrsYardTrackingQueries -> `yardqueries` (`/api/v1/yard-queries/`), 7 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHYT25I | VHYT25I | read-only | VHYT25A, VHYT30A |
| 2 | VHYT27I | VHYT27 - Reports Criteria Selection Window | read-only | MRSTABLEMAINTENANCE, VHYT27A, VHYT27B, VHYT27C, VHYT27D, VHYT27E, VHYT27F, VHYT27G, WS70A01 |
| 3 | VHYT29I | VHYT29 - Car Location History Report | read-only | VHYT29A |
| 4 | VHYT33I | VHYT33 - Yard Vehicle Status History Report | read-only | VHYT33A |
| 5 | VHYT26I | VHYT26I | update | VHYT26A, VHYT26B, **VHYT26C**, VHYT30A |
| 6 | VHYT32I | VHYT32 - REASON OUT OF SERVICE - DETAIL or SUMMARY | update | VHYT32A, **VHYT32B**, VHYT32C, **VHYT32D** |
| 7 | VHYT34I | VHYT34 - Make/Break Overrides | update | VHYT34A, **VHYT34B**, VHYT34C, VHYT34D, VHYT34E, **VHYT34F**, **VHYT34G**, VHYT34H, VHYT34J, VHYT34K, VHYT34L, VHYT34M, VHYT34N, VHYT34P |

#### MRSConfigurTrackQuery -> `configqueries` (`/api/v1/config-queries/`), 4 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHCF16I | VHCF16I - Config Component Inquiry | read-only | VHCF16A, VHCF16B, VHCF16C, VHCF16D |
| 2 | VHCF21I | VHCF21I - ADD/SCRAP COMPONENT | update | VHCF21A, VHCF21B, VHCF21C, **VHCF21D**, VHCF21E |
| 3 | VHCF35I | VHCF35I - Truck Build GUI V1 08-29-2008 CGJ | update | VHCF35B, VHCF35C, VHCF35D, **VHCF35G**, **VHCF35H**, VHCF37F |
| 4 | VHCF37I | VHCF37I - Truck Rebuilt View GUI | update | VHCF35B, VHCF35C, VHCF35D, **VHCF35G**, **VHCF35H**, VHCF37F |

#### MRSYardTracking -> `yardtracking` (`/api/v1/yard-tracking/`), 34 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHYT01I | VHYT01I - Pseudo Status Train Report | read-only | VHYT01A, WS70A01 |
| 2 | VHYT02I | VHYT02I - Yard Detail Report | read-only | VHYT02A, VHYT02B, WS70A01 |
| 3 | VHYT04I | VHYT04I - Hayward Shop Map - Inquiry | read-only | – |
| 4 | VHYT05I | VHYT05I - Hayward Yard Map(Update) | read-only | – |
| 5 | VHYT09I | VHYT09I - PSEUDO STATUS TRAIN VER - FEB-2002 - 1.0 | read-only | VHYT09A, VHYT09B, VHYT09C |
| 6 | VHYT10I | VHYT10I - Hayward Shop Map - Update V1.NOV01.01 | read-only | – |
| 7 | VHYT22I | VHYT22I - Yard Tracking SVHV Report | read-only | VHYT22A, WS70A01 |
| 8 | VHYT23I | VHYT23I - Shop Activity Report | read-only | VHYT23A, WS70A01 |
| 9 | VHYT24I | VHYT24I - Yard Tracking Inventory Report | read-only | VHYT02C, VHYT02E, VHYT02F, VHYT02G, VHYT24A, VHYT24B, VHYT24C, VHYT24D, VHYT24E, WS70A01 |
| 10 | VHYT40I | VHYT40I - Dublin Pleasanton Map | read-only | – |
| 11 | VHYT41I | VHYT41I - Dublin Pleasanton Map (Update) | read-only | – |
| 12 | VHYT42I | Reason Code Change Verification Window | read-only | – |
| 13 | VHYT50I | VHYT50I - Pittsburg / Baypoint Map | read-only | – |
| 14 | VHYT51I | VHYT51I - PITTSBURG BAYPOINT MAP (UPDATE) | read-only | – |
| 15 | VHYT95I | VHYT95I - Richmond Shop Map(Update) | read-only | – |
| 16 | VHYT96I | VHYT96I - Richmond Shop Map(Inquiry) | read-only | – |
| 17 | VHYTM5I | VHYTM5I - Virtual Dummy Menu | read-only | – |
| 18 | Vhyt07i | VHYT07I - Hayward Yard Map - Inquiry | read-only | – |
| 19 | Vhyt60i | VHYT60I - Concord Shop Map(Inquiry) | read-only | – |
| 20 | VHYT03I | VHYT03I - Vehicle Shop Location Change | update | VHYT03A, VHYT03B, VHYT03D, VHYT03E, VHYT03F, **VHYT03G**, **VHYT03H**, VHYT03J |
| 21 | VHYT06I | VHYT06I - Change Reason Code VER - FEB-02 01 | update | VHYT06A, VHYT06B, VHYT06C, **VHYT06D**, **VHYT06E** |
| 22 | VHYT11I | VHYT11I - Yard Detail Update | update | VH01A02, VHYT08A, VHYT08B, VHYT08C, VHYT08F, VHYT08H, VHYT11A, **VHYT11B**, VHYT11C, **VHYT11F** |
| 23 | VHYT12I | VHYT12I - PSEUDO MAKE TRAIN | update | VHYT12A, VHYT12B, VHYT12C, **VHYT12D**, **VHYT12E** |
| 24 | VHYT13I | VHYT13I-Change Orientation | update | VHYT13A, VHYT13B, **VHYT13C**, **VHYT13D**, VHYT13E, **VHYT13F**, VHYT13G |
| 25 | VHYT14I | VHYT14I - Fix Order for Cars | update | VHYT14A, VHYT14B, **VHYT14C**, **VHYT14D**, **VHYT14E**, **VHYT14F** |
| 26 | VHYT31I | VHYT31I - SHOP ACTIVITY | update | VHYT31A, **VHYT31B**, **VHYT31E**, VHYT31H |
| 27 | VHYT52I | VHYT52I - Pseudo Correction | update | VHYT52A, **VHYT52B** |
| 28 | VHYT54I | VHYT54I - Force Clear 08-04 | update | VHYT54A, VHYT54B, **VHYT54C** |
| 29 | Vhyt08i | VHYT08I - Yard Detail Inquiry | update | VH01A02, VHYT08A, VHYT08B, VHYT08C, VHYT08D, VHYT08E, VHYT08F, **VHYT08G**, VHYT08H |
| 30 | VHYTM1I | VHYTM1I - Vehicle Yard Tracking Inquiry Menu | menu | – |
| 31 | VHYTM2I | Vehicle Yard Tracking Update Menu | menu | – |
| 32 | VHYTM3I | VHYTM3I - Vehicle Yard Tracking Report Menu | menu | – |
| 33 | VHYTM4I | VHYTM4I - Yard Tracking Update Menu | menu | – |
| 34 | VHYTM6I | VHYTM6I | menu | VH01A02 |

#### MRSConfigureTracking -> `configtracking` (`/api/v1/config-tracking/`), 3 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VFCF05I | VHCF05I - Print | read-only | VHCF05A, WS70A01 |
| 2 | VHCF01I | VHCF01I - Vehicle Component / Configuration Tracking Inventory - 02-20 | update | MRSVEHICLEMENU, VHCF01A, VHCF01B, VHCF01C, VHCF01D, VHCF01F, VHCF01G, VHCF01H, VHCF01J, VHCF01K, VHCF01L, VHCF01M, VHCF01N, VHCF01P, **VHCF01Q**, **VHCF01S**, **VHCF01T**, **VHCF01U**, VHCF01V, VHCF01W, VHCF01X, **VHCF01Y** |
| 3 | VHCF06I | VHCF06I - Secondary Repair - Axle Configuration | update | VHCF01J, VHCF01P, VHCF06A, VHCF06C, VHCF06D, VHCF06E, **VHCF06F**, **VHCF06G**, **VHCF06H**, **VHCF06I**, **VHCF06J**, VHCF06K, **VHCF06L**, **VHCF06M** |

#### MRSVehicleConsist -> `consist` (`/api/v1/consist/`), 10 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHTR02I | VHTR02I - Pop Up Message | read-only | – |
| 2 | VHTS01I | VHTS01 - Train Swap | read-only | MRSVEHICLEINCIDENT, VH01A02, VHMT016, VHTS1A9, VHTS2A1, VHTS2A2, VHTS2A3, VHTSX, VHXCONA, VHXCONB, VHXREVA |
| 3 | VHCC01I | Vhcc01 - Change Consist | update | VH01A02, VHCC011, **VHCC013**, VHCC014, VHCC015, VHCC016, **VHCC017**, VHMT014, VHXREVC |
| 4 | VHCM01I | VHCM01I - Combine Train | update | VH01A02, VHCM011, VHCM012, VHCM013, VHCM014, **VHCM015**, VHCM016, VHCM017, VHCM018, VHMT016 |
| 5 | VHCT01I | Vhct01 - Clear Train | update | VH01A02, VHCT011, **VHCT012**, VHCT013, VHCT014, VHCT015, VHCT016, **VHCT017**, **VHCT018**, VHCT019, VHCT01A, VHXREVC |
| 6 | VHCY01I | VHCY01 - Change Yard | update | MRSVEHICLEMAINTENANCE, VHCY1A1, VHCY1A2, **VHCY1A3**, VHCY1A4 |
| 7 | VHLT01I | Pseudo Train Lay-up Screen | update | VHLT009, VHLT010, VHLT011, **VHLT012**, VHLT013, VHLT014, VHLT015, VHLT016, **VHLT017**, **VHLT018**, VHLT019, VHLT01A, VHLT020, VHLT021, VHLT022, VHTR012, VHXREVC |
| 8 | VHMT01I | Vhmt01I - Make Train | update | VH01A02, VHMT011, **VHMT012**, **VHMT013**, VHMT014, VHMT015, VHMT016 |
| 9 | VHSC01I | VHSC01I - Split Consist | update | MRSVEHICLEINCIDENT, VH01A02, VHMT016, VHSC2A1, VHSC2A2, **VHSCX**, VHSCY, VHTS1A9, VHTS2A2, VHTS2A3, VHXCONB, VHXREVA |
| 10 | VHTR01I | VHTR01I - Pseudo Train Reconfigure | update | VHLT010, VHLT011, VHLT013, VHLT014, VHLT015, VHLT016, **VHLT017**, **VHLT018**, VHLT019, VHSC2A1, VHTR011, VHTR012, VHTR013, VHTR014, VHTR015, **VHTR016**, VHTR01A, VHXREVC |

#### MRSTableMaintenanceApp2 -> `tables2` (`/api/v1/tables-2/`), 5 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | Vhccs18i | Vhccs18i | read-only | VHCCSHA, VHCCSHB, VHCCSHC, VHCCSHD, VHCCSHE, VHCCSHF, WS70A01 |
| 2 | VHCCS04I | VHCCS04I - MRS Messages Received V October 04.01 | update | MRSTABLEMAINTENANCE, VHCCS4A, **VHCCS4B**, VHCCS4E, VHCCS4F, VHCCS4G, VHCCS4H, VHCCS4I |
| 3 | VHCCS05I | VHCCS05I | update | VHCCS5B, **VHCCS5C**, **VHCCS5D**, VHCCS5E, **VHCCS5M**, **VHCCS5N** |
| 4 | VHCCS06I | VHCCS06I | update | VHCCS6A, **VHCCS6B**, **VHCCS6C**, **VHCCS6D**, VHCCS6E |
| 5 | VHCCS07I | VHCCS07I - Msgs Rcv Errors Jan. 03. | update | VHCCS7A, **VHCCS7B**, **VHCCS7C**, **VHCCS7D** |

#### MRSTableMaintenanceApp3 -> `tables3` (`/api/v1/tables-3/`), 4 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHCF49I | VHCF49I - MRS_Serial_No Feb 2005 | update | VHCF49A, VHCF49B, **VHCF49D**, VHCF49E |
| 2 | VHCF50I | VHCF50I - MRS_Serial_No - Detail view Feb. 2005 | update | **VHCF50A**, VHCF50B, VHCF50C, **VHCF50D**, VHCF50E, VHCF50F, VHCF50G, VHCF50H, VHCF50I, VHCF50J, VHCF50K, VHCF50L, VHCF50M |
| 3 | VHCF51I | VHCF51I - MRS_Serial_No_Hrs 4-05 | update | VHCF51A, **VHCF51B** |
| 4 | VHCF52I | VHCF52I - MRS_Serial_Geo_Dat 05-05 | update | VHCF52A, **VHCF52B**, VHCF52C |

### Not assigned (shared parts)

#### MRSCommonCode, 3 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | AFCX6I |  | read-only | – |
| 2 | XPRINTI |  | read-only | – |
| 3 | XXM0I |  | read-only | – |

#### MRSMainMenu, 4 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | MRS00I | Maris Main Menu - MRS00I | read-only | – |
| 2 | MRS02I | MRS00 -- New Maris Signon | read-only | MRS02A |
| 3 | MRS99I | MRS99 -- About the New Maris System | read-only | – |
| 4 | MRSX0I |  | read-only | – |

#### MRSVehicleMenu, 13 screens

| # | Screen | Title | Kind | Programs it calls (writes in **bold**) |
|---|---|---|---|---|
| 1 | VHMARI | VHMARI - Vehicle Management Audit Reports | read-only | – |
| 2 | VH001I | VH001 - Trouble Desk Main Menu | menu | VH01A01, VH01A02 |
| 3 | VHMCFI | VHMCFI - Configuration / Component Tracking Report Menu | menu | – |
| 4 | VHMCMI | VHMCMI - Central Manager Main Menu | menu | VH01A01 |
| 5 | VHMCRI | Central Manager's Reports | menu | – |
| 6 | VHMENI | VHMENI - Vehicle Maintenance Engineering/Maint Administrator | menu | VH01A01, VH01A02 |
| 7 | VHMMMI | VHMMMi - DB Maintenance Menu | menu | VH01A01 |
| 8 | VHMOTDI | VHMOTDI - Old Trouble Desk Menu Items | menu | – |
| 9 | VHMREI | VHMREi - Reliability Engineering Menu | menu | VH01A01 |
| 10 | VHMSSI | VHMSSI - Vehicle Shop Scheduler Main Menu | menu | VH01A01, VH01A02 |
| 11 | VHMTOI | VHMTOI - Yard Towers/Line Management Menu | menu | VH01A01, VH01A02 |
| 12 | VHMVSI | VHMVSI - Vehicle Shops Main Menu | menu | VH01A01, VH01A02 |
| 13 | VHMYTI | VHMYTI - Yard Tracking Report Menu | menu | – |

## 11. Screens with no clean API to build

* The 14 `mf:`* screens (for example VHCF03I, VHSR12I) were never moved to the web in the old system: their EGL is
marked TODO, and there is no logic or service to call.
* The `web:`* and `rc:`* pages are ColdFusion / report pages outside the EGL backend. They need their own source code
first.
