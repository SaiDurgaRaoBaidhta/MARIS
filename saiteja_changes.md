# Sai Teja: changes and pending work

Work on Sai Teja's part of `docs/API_WORK_SPLIT.md` (83 screens), plus the map and UI fixes needed to show real MARIS data.
Branch: `sai-teja/all-changes` (based on `shared/accurate-screen-kinds`). Nothing here is merged to `main` yet.

## Done: 21 of 83 screens

Each screen has: clean API, API test, parity test (old screen vs new API), live test (read-only programs), UI page + UI
test, and its entry in `maris-middleware/src/test/resources/clean-apis/<module>.json`.

| Module | Screens done | Endpoints (`/api/v1/...`) |
|---|---|---|
| yardreports (MRSYardTrackingReport) | all 16: VHIC01, VHIC02, VHSW01I, VHYT100I, VHYT101I, VHYT105I, VHYT106I, VHYT61I, VHYT70I, VHYT71I, VHYT80I, VHYT81I, VHYT90I, VHYT91I, VHIC03I, VHYT53I | `yard-reports/maps/{map}`, `system-wide`, `pseudo-train-move`, `pseudo-config/{yard}` |
| yardqueries (MrsYardTrackingQueries) | 4 of 7 (read-only): VHYT25I, VHYT27I, VHYT29I, VHYT33I | `yard-queries/shop-status/{yard}`, `report-options`, `reports/print`, `car-history`, `status-history` |
| configqueries (MRSConfigurTrackQuery) | 1 of 4 (read-only): VHCF16I | `config-queries/component-options`, `components` |

## Done: maps and UI (real data, no demo)

- Yard and shop maps (the team's drawn 2D/3D viewer from `maris-2-v4.html`) show MARIS's cars from the DB (`/api/v1/fleet`, program VHSW01A), with the real signed-on session.
- Every car is in the box the old MARIS screen shows it in. The boxes come from the old screens themselves (`maris-ui/tools/egl-map-boxes.mjs`, `maris-ui/tools/match-map-boxes.mjs`). Cars shown vs MARIS: Concord 215/215, Richmond 253/253, Pittsburg 47/47, Daly City 114/114, Berryessa 40/40, Dublin 26/26, Hayward 213/216 (the 3 have no box on the old screen either), Millbrae 9/55 (see pending).
- Demo mode removed completely: no mock API, no `fleet.json` / `userGroups.json`, no demo users; sign-on is MARIS's own (DB).
- Maps always open in 2D (3D is one click away).
- Out of full screen, a map looks like the reference: page header (path, title, screen code), one map card, and the app's header, side menu and footer.
- Map buttons (Pseudo Status Train, Tower Menu, Reports, Maint) open MARIS's own screens through the middleware, not the viewer's stand-ins.
- Car move / detail windows centre on the map.
- Favicon is the BART "ba" mark only.

## Shared files changed (need Sai Durga Rao's agreement, `API_WORK_SPLIT.md` section 5)

- `maris-ui/src/app/router.tsx`, `src/app/App.tsx`, `src/shared/**`, `src/features/screens/**`: map routing, demo-mode removal.
- `maris-ui/public/legacy/maris-map.html`, `tools/build-map-island.mjs`: real data, 2D, layout, box mapping.
- `maris-ui/index.html`, `public/favicon.png`.

## Pending: 62 screens

Read-only first, then update screens (doc order).

| Module | Read-only left | Update left |
|---|---|---|
| yardtracking (34) | 19: VHYT01I, 02I, 04I, 05I, 07I, 09I, 10I, 22I, 23I, 24I, 40I, 41I, reason-code verification, 50I, 51I, 60I, 95I, 96I, VHYTM5I | 15: VHYT03I, 06I, 08I, 11I, 12I, 13I, 14I, 31I, 52I, 54I, … |
| configtracking (3) | VHCF05I | VHCF01I, VHCF06I |
| consist (10) | VHTR02I, VHTS01 | Vhcc01, VHCM01I, Vhct01, VHCY01, lay-up, Vhmt01I, VHSC01I, VHTR01I |
| tables2 (5) | Vhccs18i | VHCCS04I, 05I, 06I, 07I |
| tables3 (4) | VHCF51I, VHCF52I | VHCF49I, VHCF50I |
| yardqueries | none | VHYT26I, VHYT32, VHYT34 |
| configqueries | none | VHCF21I, VHCF35I, VHCF37I |

## Pending: other

- Millbrae map: the drawing has no rows for MARIS tracks TM4..TM7 (46 cars). Add them to the drawing (~1-2 h).
- Split this branch for review: one `shared/...` PR (UI shell, map viewer) and one PR per module.
- Engine defects found by the parity tests (shared engine code, raise with Sai Durga Rao): SMALLINT parameter comparison (GiveAColor), drop-downs bound to `DATA_ROW_of_X`, event-to-attribute connections, `YARD-SELECTION_of_...` variable part, Text5 refresh on VHYT25I, unsubscripted array assignment (VHYT53H CAR-NO).
- `docs/API_WORK_SPLIT.md` gives the old EGL source as `~/Desktop/MARIS/MarisProd-1/...`; on this machine it is `~/Desktop/Maris-dev/MarisProd2/MARIS_RUI/EGLSource/`.

## Estimate

About 5 weeks for the 62 screens one module at a time; about 3 weeks with 2-3 modules in parallel and same-day reviews.
