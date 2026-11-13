# Testing: how every screen moves to the clean API

Every old screen moves to the clean API test first. A screen counts as done only when all five kinds of test below exist and pass.

Run everything with `./scripts/test-all.sh`. Add `--live` to also run the read-only tests against the real local backend. CI (`.github/workflows/ci.yml`) runs the same suite on every push.

## The five tests for one screen (example: ICS, old VHIC30I / VHIC31I)

| Test | Where | What it proves |
|---|---|---|
| API test | `maris-middleware/src/test/java/com/bart/maris/middleware/api/ics/IcsApiTest.java` | The API sends the right records to each backend program and returns the right JSON. It also checks the old messages for bad input and that bad input calls no program at all. Uses a fake backend. |
| Parity test | `.../api/ics/IcsParityTest.java` | Runs the **old screen** (in the screen engine) and the **new API** against the same scripted backend, and checks both send identical records. A rule left out of the new code shows up here. |
| Live test | `.../live/IcsLiveTest.java` (one class per module, `-Plive`) | Read-only calls against the real local MARIS give the expected rows. |
| UI test | `maris-ui/src/features/ics/IcsReportPage.test.tsx` | The page sends the right API requests, shows rows and errors, and keeps the old screen's rules (fields turned off, a required printer). Uses a scripted middleware (`src/test/fetch.ts`). |
| Coverage check | `.../coverage/CleanApiCoverageTest.java` + `src/test/resources/clean-apis/<module>.json` | Every screen listed as clean has all of the tests above, and the UI routes exactly those screens to their new pages (`maris-ui/src/features/<module>/screens.tsx`). |

## Steps for the next screen

1. Read the screen's EGL: `<SCREEN>Connections.egl` (which statement group each button runs) and `RUIFunctions.egl` (the checks, the `call` statements, the messages).
2. Write the API test and the parity test first, and watch them fail.
3. Write the service (`domain/<module>`) and the controller (`api/<module>`) until both tests pass.
4. Write the UI test, then the page. Add the page to `features/<module>/screens.tsx` and the screen to `clean-apis/<module>.json`.
5. For a read-only program, add a live test.
6. Run `./scripts/test-all.sh`.

Screens that change data are tested the same way. Their API and parity tests check the exact records sent to each program that writes, and the order of the calls. They never run against the real backend from a test.

Screens not converted yet keep running through the screen engine, so nothing breaks in between.
