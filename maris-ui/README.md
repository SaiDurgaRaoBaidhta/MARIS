# maris-ui: MARIS frontend (React + TypeScript + Tailwind)

The new MARIS UI. It is a port of the single-file prototype `../maris-2-v4.html` to a Vite + React 19 + TypeScript + Tailwind CSS v4 app.

```
npm install
npm run dev        # http://localhost:5173, /api proxied to the middleware on http://localhost:8090
npm run build      # type-check + production build in dist/
npm run gen:api    # src/api/openapi.yaml -> src/api/generated/schema.ts
VITE_MARIS_API=mock npm run dev   # demo data only, no middleware (the screen tools use this)
```

By default the UI talks to `maris-middleware` (start it first: see its README). Sign on with a real MARIS
group ID and password (program MRS02A). The middleware returns the session (user group, role, Update or Inquiry
maps) and keeps it in a cookie, so a page reload stays signed on.

What comes from the real MARIS system through the middleware today:

| UI | Middleware | MARIS program |
|---|---|---|
| Sign on / sign off / session after reload | `/api/v1/auth/signon`, `/signoff`, `/me` | MRS02A |
| Home dashboard, Daily Yard Track Inventory | `GET /api/v1/fleet` | VHSW01A (per yard, read-only) |
| **Every EGL screen (215)**, and the redesigned pages whose title names one (incdlog → VHIE01I, statveh → VHSV01I …) | `POST /api/v1/ui/open`, `/ui/windows/{id}/events`, `/ui/dialogs/{id}` | whatever the old screen calls, when it calls it: the middleware's screen engine runs the screen's own EGL logic |
| Menus | stay tiles in the new UI; the screens they open run live | – |
| `mf:*` screens (14), `web:*` / `rc:*` pages | captured layouts only | none: the `mf:` screens were never moved to the web in the old system; `web:`/`rc:` are ColdFusion / report pages |

A live screen (`features/screens/LiveScreenPage.tsx`) draws the captured layout with the state the engine sends
(values, list items, table rows, selection, enabled / visible, colours), sends a field when it loses focus (as the old
widgets committed edits), sends clicks and selections, follows the windows the old logic opens and closes, and shows the
old screens' message prompts. Under the screen it lists the MARIS programs the last step called, with their times.

MARIS sends no track capacity through its services, so the dashboard shows tracks in use and the busiest yard
instead of track space used. The yard and shop maps still draw their built-in demo drawings.

## Layout

```
maris-ui/
├── package.json
├── vite.config.ts        # dev proxy /api, alias @ -> src
├── tailwind.config.ts    # BART colours and fonts (read through @config in src/styles/index.css)
├── Dockerfile            # build -> nginx
├── nginx.conf.template   # MIDDLEWARE_UPSTREAM
├── index.html
├── public/               # screens/*.json (layouts), data/ (mock fleet, map drawings), fonts, logo
├── tools/                # regenerate the JSON from the prototype; check-screens.mjs
└── src/
    ├── main.tsx
    ├── app/              # App.tsx (sign on -> workspace), router.tsx, shell/ (header, side bar, footer)
    ├── api/              # openapi.yaml (middleware contract), generated/schema.ts
    ├── shared/
    │   ├── ui/           # Icon + icon set, PageHead, Dialog, Toasts
    │   ├── api/          # client.ts: fetch client, mock / http switch, Schemas
    │   ├── hooks/        # useElementWidth
    │   └── lib/          # app-state.tsx (session, page stack, theme, toasts), tokens.ts
    ├── features/         # one per module, each with its api.ts and types.ts
    │   ├── auth/         # sign-on page, user groups
    │   ├── menu/         # application menus as tiles
    │   ├── home/         # fleet dashboard
    │   ├── screens/      # any MARIS screen (VHxxxxx): renderer, button wiring, screen registry
    │   ├── screen-index/ # MARIS Screen Index (AS-IS document)
    │   └── yard-tracking/# yard / shop maps, Daily Yard Track Inventory, fleet API
    └── styles/           # index.css
```

`features/vehicle-incident/` is not there yet. The incident screens are drawn by the generic `features/screens` renderer today. A hand-written module goes in its own feature folder when it is built against the middleware.

## Docker

```
docker build -t maris-ui .                                   # call the middleware (default)
docker build -t maris-ui --build-arg VITE_MARIS_API=mock .   # demo data only
docker run -p 8080:80 -e MIDDLEWARE_UPSTREAM=http://middleware:8090 maris-ui
```

nginx serves the app (deep links fall back to `index.html`) and proxies `/api/` to `MIDDLEWARE_UPSTREAM`, keeping the `/api` prefix, the same as the Vite dev proxy.

## How screens work

The prototype had 211 MARIS desktop screens (from the EGL source) and about 50 hand-made ones. It laid each one out at run time with about 60 DOM-measuring passes. Those passes are not ported. Instead, `tools/capture-screens.mjs` opens every screen in the prototype in Chrome and saves its final layout as a JSON node tree (`ScreenDef` in `src/types/maris.ts`). `ScreenRenderer` turns the tree into React elements:

- Group boxes, labels, fields, drop-downs, check boxes, buttons, tables and 3D squares become Tailwind-styled elements at their captured positions, in light and dark theme.
- Every field is a controlled input. On the 211 EGL screens (`egl:*`), all 2,587 fields carry their **EGL field name** (`data-w`, for example `Text1`), so the middleware can fill and read a screen by the names the EGL programs use. The hand-made stand-in screens of the prototype (`mf:*`, `incd:*`, `changeyard` …) have 313 fields with no EGL name. They are not sent to the back end until they are given one.
- Values the prototype filled in at capture time (the signed-on user, today's date, the time) are stored as tokens (`{{user}}`, `{{date}}` …) and filled from the session and the clock.
- `wiring` (from the EGL `<SCREEN>Connections`) says what each button does: open a screen, close, ask, or call server programs (`p: ["VHCF01A", …]`).
- `meta` lists the screen's server programs, the tables it reads and writes, and its fields. This is the map for building the middleware.

## The middleware seam

The contract is `src/api/openapi.yaml`: `POST /api/signon`, `GET /api/fleet` and `POST /api/screens/{screen}/actions/{control}`. The UI's API types are generated from it (`npm run gen:api`), so change the YAML first and regenerate. Each feature calls the back end only through its `api.ts` (`features/auth/api.ts`, `features/yard-tracking/api.ts`, `features/screens/api.ts`), and those go through `shared/api/client.ts`.

By default every call is answered by a mock. Build or run with `VITE_MARIS_API=http` to call `/api` instead. Screen layouts, menus and map drawings are part of the UI and stay static.

A push button that calls server programs sends:

```json
POST /api/screens/VHCF01I/actions/Push_Button1
{ "screen": "VHCF01I", "control": "Push_Button1", "programs": ["VHCF01A", "VHCF01B", "VHCF01C"],
  "caption": "List / Refresh", "fields": { "Text1": "4117", "Drop-down_List1": "LIST DATA" } }
```

The middleware translates that into the EGL call (`XDCONTROL` + `*_CRIT` + `*_DATA` records), runs the program in MARIS_Server against Oracle, and answers:

```json
{ "control": { "URC": 0, "UMSGCODE": "", "UMSGINS": "" },
  "fields": { "Text11": "CY" }, "rows": { "<table>": [["…"]] }, "message": "Data retrieved." }
```

`fields` are written back into the screen by EGL field name. `URC != 0` shows the message as an error. `rows` is in the contract but not drawn yet: tables have no EGL name in the captured layout, so binding rows to a table is the first thing to add with the middleware.

## Regenerating the screen data

Only needed if `../maris-2-v4.html` changes. You need Google Chrome and playwright-core:

```
npm i --no-save playwright-core@1.48.2
tools/regenerate.sh
npm run dev &  node tools/check-screens.mjs http://localhost:5173   # every screen renders, every link resolves
```

## Not ported (yet)

- **The 3D yard view** (three.js) of the yard maps. The 2D map is ported.
- **Hand-made screen behaviour.** Screens such as the incident forms, consists and worksheets show their layout and fields. Their buttons go through `api.action` like every other button. In the prototype they ran their own mock logic, and that data now has to come from the middleware.
- **Detail / Summary table switching** on the few screens that stack two tables in one place. The second table stays hidden.
- **Radio sets** are not yet included in the `fields` sent with an action.
- **Web report pages** (MARIS Print, ColdFusion, NOGO, Train Summary, Availability) are drawn from their captured look. They stay light in the dark theme.
