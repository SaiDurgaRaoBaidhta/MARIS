# maris-middleware

Spring Boot 3.5 / Java 21 service between the new React UI and the existing EGL backend.

- **UI side:** JSON REST under `/api/v1/...`, session cookie + CSRF, errors as RFC 9457 problem details.
OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html` (off in `prod`).
- **Backend side:** the same JSON-RPC calls the old browser UI makes to
`MARIS_RUI_Services_Web/restservices/<Program>Service`, with records in the same fixed byte layout.
**No backend code or configuration is changed.**

## How the translation works

| Piece | What it does |
|---|---|
| `legacy-catalog/` (generated) | 640 service methods, 1,226 record layouts, 74 message texts, read from the EGL source by `tools/egl-catalog-generator`. Every layout is cross-checked against the browser's own generated record definitions (0 differences). |
| `legacy.codec` | JSON ⇄ record bytes. Matches the gt2 browser layer: text ISO-8859-1 space-padded; `num` ASCII zoned decimal; `decimal`/`pacf` packed; binary big-endian; `%xx` escaping. Values that don't fit are refused (the browser truncated silently). |
| `legacy.client` | JSON-RPC over HTTP, timeouts, circuit breaker, reads the nested `{"contents": "{\"contents\":…}"}` reply. |
| `legacy.control` | `XDCONTROL` → return code, message code/inserts, row count, paging flags; message text from table XXM0IT1. |
| `legacy.LegacyProgramService` | One call: `execute("VHXCONA", Map.of("VHXCONA_CRIT", Map.of("conNo", "197")), true)`. |
| `domain` / `api` | What the UI calls, in the UI's own JSON shapes (`maris-ui/src/api/openapi.yaml`): sign-on (`MRS02A`), fleet (`VHSW01A`), screen buttons. |
| `engine` (screen engine) | **Runs the old screens' own logic on the server**, for every screen: the statement groups (`RUIFunctions.egl`, `CommonFunctions.egl`), each screen's connections (`<SCREEN>Connections.egl`) and widgets. An EGL interpreter (`engine.egl`, `engine.runtime`) works on record bytes, so overlapping fields behave as before; `engine.screen` re-implements the gt2 connection runtime (field events after a statement group returns, two-way attribute connections, child windows sharing pass records, message prompts that pause events). Every `call "PGM"` goes to the same REST service with the same record bytes. The bundle in `src/main/resources/egl-screens/` is generated from the EGL source. |
| `domain.screens` | (first pilot, kept for the API) One `ScreenAction` class per translated button (EGL logic from `MARIS_RUI/EGLSource/<module>/RUIFunctions.egl`). Untranslated buttons answer 501 `NOT_MIGRATED`; the middleware never runs a button's program list blindly, because the old screens call them conditionally and many write data. |

## Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/auth/csrf` | sets `XSRF-TOKEN` cookie; send it back as `X-XSRF-TOKEN` header on POSTs |
| POST | `/api/v1/auth/signon` | `{"groupId","password"}` → UI session `{user, role, group, update, signedAt, menu, yard}`. Upper-cases both, like screen MRS02I. Role: USER_TYPE, then start menu (`domain.auth.UiRoles`) |
| POST | `/api/v1/auth/signoff` | ends the session |
| GET | `/api/v1/auth/me` | the session (UI restores it after a reload) |
| GET | `/api/v1/fleet` | yards (from `maris.fleet.yards`), tracks and cars from `VHSW01A`, one call per yard. `capacityKnown: false` |
| POST | `/api/v1/screens/{screen}/actions/{control}` | a screen button. Connected today: `VHIE01I/Push_Button3` (Revenue Incident Log, List → `VHIE01A`) |
| GET | `/api/v1/screens/actions` | list of connected buttons |
| POST | `/api/v1/ui/open` | `{"screen":"VHIC30I"}`: open a screen live (its open logic runs, backend calls included). Returns the open windows with every widget's state, the prompt if one is open, and the backend calls made |
| POST | `/api/v1/ui/windows/{id}/events` | what the user did: `{"changes":[{"widget","attr":"object","value"}], "widget":"Push_Button3", "event":"clicked"}` (events: `clicked`, `select` with a 1-based index, `losingFocus`; `@window`/`close`) |
| POST | `/api/v1/ui/dialogs/{id}` | answer the open prompt: `{"button":"Yes"}` |
| GET | `/api/v1/ui/state`, `/api/v1/ui/screens` | current windows; the screens the engine can run (215) |
| GET/POST | `/api/v1/dev/programs[/{program}]` | **dev only** (`maris.dev-api.enabled`, on in `local`): describe or run any program with JSON. Off in production. |
| GET | `/actuator/health` | includes `legacyBackend` |

## Screen engine

* Regenerate the bundle after EGL changes (reads the EGL source only):
  ```
  cd tools/egl-catalog-generator && mvn -q compile dependency:build-classpath -Dmdep.outputFile=cp.txt
  java -cp target/classes:$(cat cp.txt) com.bart.maris.tools.ScreenBundleGenerator ~/Desktop/MARIS/MarisProd-1 ../../src/main/resources/egl-screens
  ```
* Logs: each backend call is logged as `legacy call screen=… program=… ms=… errorCode=…`; a screen whose logic fails is
  logged as `screen logic failed` with the stack trace, and the user sees a prompt naming the problem.
* Test guards (never in production): `maris.engine.allowed-programs` (only these programs may be called) and
  `maris.engine.blocked-programs`. Used to press every button of every screen against the real backend without writing.
* Screens not covered: the 14 `mf:` screens were never moved to the web in the old system (their EGL is marked TODO), and
  the `web:`/`rc:` pages are ColdFusion / report pages outside the EGL backend.

## Run

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run -Dspring-boot.run.profiles=local      # http://localhost:8090, backend on :8080
```

Production settings come only from environment variables: `MARIS_LEGACY_BASE_URL`, `PORT`,
`MARIS_SESSION_TIMEOUT`, `MARIS_LEGACY_READ_TIMEOUT`, profile `prod`.

## Test

```
mvn test                                                    # 88 tests, no backend needed (fake backend); opens all 215 screens
MARIS_LIVE_GROUP=... MARIS_LIVE_PASSWORD=... mvn test -Plive  # 5 read-only tests against the real local backend
```

Golden vectors in `src/test/resources/golden/` were produced by running the browser's own gt2 functions
(`tools/golden-vectors/extract-gt2-vectors.mjs`).

## Regenerate the catalog (after EGL source changes)

```
cd tools/egl-catalog-generator
mvn -q compile exec:java -Dexec.args="$HOME/Desktop/MARIS/MarisProd-1 ../../src/main/resources/legacy-catalog"
```

Reads the EGL source only; exits non-zero if any layout differs from the browser's.

## Connecting another screen button

1. Read the button's EGL: `<SCREEN>Connections.egl` (which statement group the button runs, which screen field is which
   record field) and `RUIFunctions.egl` (the checks, the `call` statements, the messages).
2. Add a `ScreenAction` bean in `domain/screens/<module>/` that does the same, with the same messages.
3. Test it in `MiddlewareApiTest` (fake backend) and, for read-only programs, in `LiveBackendTest` against values read from Oracle.

## Notes for deployment

- Sessions are in memory: run one instance, or use sticky sessions, until Spring Session (JDBC/Redis) is added.
- The middleware never retries calls automatically, because many programs update data.
