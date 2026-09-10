# Handoff: Family Dashboard — kiosk UI (Aufgaben / Abfahrten · Wetter)

## Overview
Wall-mounted touchscreen dashboard for a household, 1280×800 landscape, dark at all
hours, German UI. Two pages share one header; the header's right side holds the page
switch. Page 1 is a task board with one column per household member. Page 2 holds two
independent widgets: next departures from the local station, and today's weather.

## About the design files
`mockups.dc.html` in this folder is a **design reference created in HTML** — a prototype
showing the intended look, not production code to copy. Recreate these screens in the
target codebase (this project's frontend is Angular) using its existing components,
styling approach and data services. Ignore the prototype's own runtime: the two screens
are rendered from a hardcoded data array by a small helper class, purely so the mock can
show realistic content. `support.js` must sit next to the HTML for it to open in a browser.

The prototype renders both screens side by side on one canvas for review. In the real app
they are two routes/views, not two elements on a page.

## Fidelity
**High fidelity.** Colors, type, spacing and sizes are final and specified below. Recreate
pixel-accurately at 1280×800; the layout is fixed-size on purpose (a kiosk panel), so no
responsive behavior is required beyond filling that viewport.

## Design tokens

Colors are authored in `oklch()`. Hex equivalents are given for convenience; prefer the
oklch values if the stack supports them.

| Token | Value | Hex ≈ | Use |
|---|---|---|---|
| `bg/page` | `oklch(0.155 0.012 265)` | `#1a1d24` | page background |
| `bg/surface` | `oklch(0.205 0.014 265)` | `#252932` | header band, widget cards |
| `bg/inset` | `oklch(0.26 0.016 265)` | `#31353f` | nav pill track, line badges, bar tracks |
| `bg/inset-hi` | `oklch(0.3 0.018 265)` | `#3a3f4a` | line badge fill, progress track |
| `border/widget` | `oklch(0.28 0.016 265)` | `#353a44` | widget border, column dividers |
| `border/header` | `oklch(0.32 0.016 265)` | `#3d434e` | 1px rule under header |
| `border/hairline` | `oklch(0.26 0.014 265)` | `#31353d` | rules inside widgets |
| `border/control` | `oklch(0.34 0.018 265)` | `#414651` | add-task button border |
| `border/checkbox` | `oklch(0.42 0.02 265)` | `#535966` | unchecked checkbox |
| `ink/primary` | `oklch(0.96 0.005 265)` | `#f2f3f5` | headlines, task text, times |
| `ink/secondary` | `oklch(0.8 0.01 265)` | `#c3c6cd` | header date, button label |
| `ink/muted` | `oklch(0.64 0.01 265)` | `#9398a3` | labels, counters, meta — min contrast 4.5:1 |
| `ink/done` | `oklch(0.5 0.01 265)` | `#6f747f` | completed task text |
| `ink/on-accent` | `oklch(0.19 0.012 265)` | `#22262d` | text on the active nav pill / on member colors |
| `status/late` | `oklch(0.8 0.14 75)` | `#e0a442` | delay ("+3 Min") |
| `status/cancelled` | `oklch(0.72 0.17 25)` | `#e8615a` | "fällt aus" |
| `accent/rain` | `oklch(0.72 0.11 235)` | `#5c9fd6` | rain bar ≥ 50% |
| `accent/rain-low` | `oklch(0.45 0.02 265)` | `#5b6070` | rain bar < 50% |

**Member colors** — one hue ramp at fixed lightness/chroma so no member dominates:

| Member | Value | Hex ≈ |
|---|---|---|
| Mara | `oklch(0.74 0.13 300)` | `#c58ae0` |
| Jonas | `oklch(0.74 0.13 262)` | `#8fa5f0` |
| Lena | `oklch(0.74 0.13 214)` | `#5eb7e3` |
| Tobi | `oklch(0.74 0.13 168)` | `#4fc4a6` |

New members: keep L=0.74, C=0.13 and pick an unused hue (suggest steps of ~45°).

**Typography** — two families, both SIL OFL 1.1, both on Google Fonts:

- **Schibsted Grotesk** (400/500/600/700) — words: member names, task text, widget
  titles, nav labels, buttons, header date, weather condition.
- **IBM Plex Mono** (400/500) — numbers, times, and small uppercase labels: the clock,
  per-member counters, done line, task meta, departure times/platform/status, hourly
  temperatures and rain percentages, widget eyebrow labels.

Scale as used (px):

| Role | Family | Size / weight / line-height | Letterspacing |
|---|---|---|---|
| Clock | Mono | 58 / 500 / 0.9 | −0.035em |
| Header date | Sans | 22 / 600 / 1 | — |
| Nav pill label | Sans | 18 / 600 / 1 | — |
| Widget eyebrow (AUFGABEN etc.) | Mono | 13 / 500 / 1, uppercase | 0.18em |
| Widget title (Haushalt etc.) | Sans | 22 / 600 / 1 | −0.01em |
| Widget meta (right of title) | Mono | 14 / 400 / 1 | — |
| Member name | Sans | 24 / 600 / 1 | — |
| Member counter (1/4) | Mono | 15 / 500 / 1 | — |
| Task text | Sans | 19 / 500 / 1.3, `text-wrap: pretty` | — |
| Task meta | Mono | 14 / 400 / 1.3 | — |
| Add-task label | Sans | 17 / 600 / 1 | — |
| Departure destination | Sans | 22 / 500 / 1.2 | — |
| Departure line badge | Mono | 19 / 600 / 1 | — |
| Departure time | Mono | 24 / 500 / 1 | — |
| Departure countdown / platform | Mono | 14–15 / 400 / 1 | — |
| Current temperature | Mono | 88 / 500 / 0.85 | −0.045em |
| Weather condition | Sans | 22 / 600 / 1.15 | — |
| Hi/lo | Mono | 17 / 400 / 1 | — |
| Hour label / rain % | Mono | 14 / 400 / 1 and 13 / 400 / 1 | — |
| Hour temperature | Mono | 21 / 500 / 1 | — |
| Weather stat label | Mono | 12 / 500 / 1, uppercase | 0.16em |
| Weather stat value | Mono | 21 / 500 / 1 | — |

Nothing below 12px, and no body text below 19px — it is read from across a room.

**Radii:** widget card 18, nav pill 999 (both track and thumb), add-task button 12,
checkbox 7, departure line badge 8, rain bar track 3, progress bar 2, member dot 999.

**Spacing:** header padding 26/36; page body padding 30/36 (page 1) and 30/36 with a 20px
gap between widgets (page 2); widget padding 18/26/22 (page 1) and 24/26/20–22 (page 2);
member column padding 0/22; widget-internal gaps 16–22.

## Screen 1 — Aufgaben

**Purpose:** see who still has what to do today, tick items off, add a task for a member.

**Layout:** column flex, full 1280×800.

1. **Header band** — `flex: none`, `bg/surface`, 26px vertical / 36px horizontal padding,
   1px `border/header` bottom rule. Left: clock `07:42` (mono 58) baseline-aligned with the
   date `Dienstag, 9. September` (sans 22, `ink/secondary`), 18px gap. Right: page switch.
2. **Page switch** — pill on `bg/inset`, 5px padding, 4px gap between the two items. Each
   item: 13px vertical / 26px horizontal padding, radius 999, sans 18/600. Active item is
   filled `ink/primary` with `ink/on-accent` text; inactive is transparent with
   `oklch(0.72 0.01 265)` text. Labels: `Aufgaben`, `Abfahrten & Wetter`. The whole pill is
   the only navigation between the two pages — both items are tap targets (≈52px tall).
3. **Body** — 30/36 padding, holds exactly one widget that fills the remaining space.
4. **Tasks widget** — `bg/surface`, 1px `border/widget`, radius 18, padding 18/26/22,
   16px gap. Its header is a 3-column grid `1fr auto 1fr`: eyebrow `AUFGABEN` left, title
   `Haushalt` centered, `5 von 16 erledigt` right in `ink/muted`. Below it a 1px
   `border/hairline` rule spanning the widget's padding box.
5. **Member columns** — a 4-column grid (`repeat(4, minmax(0,1fr))`), `align-items:
   stretch`, no gap; the grid has a −22px horizontal margin so the dividers reach the
   widget's inner edges. Each column: 0/22 padding, 1px `border/widget` left divider
   except the first, 16px gap, column content in order:
   - **Name row** — 12px member dot (member color), name (sans 24/600, ellipsis on
     overflow), counter `1/4` right in `ink/muted`.
   - **Progress bar** — 4px tall, radius 2, track `oklch(0.3 0.015 265)`, fill the member
     color at `done/total` width.
   - **Task list** — 12px gap. Each row: 26px checkbox (radius 7, 2px `border/checkbox`)
     then the text block. Done rows: checkbox filled with the member color and a `✓` in
     `ink/on-accent`; text `ink/done` with `line-through`. Meta line (`überfällig`,
     `20 Min`, `Do`, `15:40`) sits 5px under the text in mono 14 `ink/muted`.
   - **Add-task button** — pinned to the bottom of the column (`margin-top: auto`), 52px
     tall, 1px `border/control`, radius 12, centered: a `+` (sans 24) in the member's color
     plus `Neue Aufgabe` (sans 17/600, `ink/secondary`). Hover: background
     `oklch(0.25 0.016 265)`. Per-member — the button carries the member identity, so the
     add flow needs no member picker.

**Exact copy in the mock** (sample data, replace with real): Mara — Sporttasche packen ✓,
Bücher zurückgeben (überfällig), Oma anrufen (nach der Schule), Zettel abgeben. Jonas —
Matheaufgaben ✓, Katze füttern ✓, Fahrradlicht reparieren, Schreibtisch aufräumen. Lena —
Klavier üben (20 Min), Ausflugszettel unterschreiben, Blumen gießen ✓, Schwimmzeug packen
(Do). Tobi — Altpapier rausbringen, Zahnarzt (15:40), Tablet laden ✓, Geschirrspüler
einräumen.

## Screen 2 — Abfahrten · Wetter

**Purpose:** leaving-the-house glance — next trains and today's weather.

**Layout:** identical header band (only the active nav item differs), then a body grid
`minmax(0,1fr) 452px` with a 20px gap and 30/36 padding. Two widgets, same card language
as the tasks widget: `bg/surface`, 1px `border/widget`, radius 18. Each has the same
3-column title row (eyebrow / centered title / meta).

**Departures widget** (left, fills remaining width)
- Title row: `ABFAHRTEN` · `Ostbahnhof` · `Stand 07:42`.
- Rows separated by 1px `border/hairline` (top rule on the container, bottom rule per row),
  18px vertical padding, 20px gap, columns in order:
  1. Line badge — min-width 64, 7/10 padding, radius 8, `bg/inset-hi`, mono 19/600.
  2. Destination — flex 1, sans 22/500, ellipsis.
  3. Platform — fixed 56px, right-aligned, mono 15 `ink/muted`, `Gl. 1`.
  4. Status — fixed 98px, right-aligned, mono 16. `pünktlich` in `ink/muted`, `+3 Min` in
     `status/late`, `fällt aus` in `status/cancelled`. Always present, never omitted, so
     the columns align.
  5. Time block — fixed 92px, right-aligned: time (mono 24) over countdown (mono 14
     `ink/muted`). A cancelled departure gets a struck-through, dimmed time and `—` instead
     of a countdown.
- Six rows fit; sample data S1/U4/RB58/S8/U6/S1 as above.

**Weather widget** (right, 452px fixed)
- Title row: `WETTER` · `Frankfurt` · `heute`.
- Current block (above a hairline): temperature `14°` (mono 88) beside condition
  `Wolkig, später sonnig` (sans 22/600) over `21° / 12°` (mono 17 `ink/secondary`).
- Hourly strip: eyebrow `TEMPERATUR / REGENRISIKO`, then a 6-column grid, each cell
  centered: hour (`09`…`19`), temperature, then a 22×30 rain bar — a `bg/hairline` track
  with a fill of `rain% × 0.3` px, colored `accent/rain` at ≥50% else `accent/rain-low` —
  then the percentage (mono 13; `accent/rain` at ≥50%, else `ink/muted`). The empty track
  is what makes 0% legible.
- Stats row above a hairline: 3 columns, uppercase mono label over mono 21 value —
  `Luftfeuchte 62%`, `Wind 11 km/h`, `Sonnenuntergang 19:42`.

## Interactions & behavior
- **Page switch** — tapping the inactive pill item navigates to the other page. No
  animation specified; a 150ms cross-fade is acceptable. This is the only navigation
  affordance, so it must stay visible on both pages in the same position.
- **Task row** — tapping anywhere on the row toggles done. Checked state is the filled
  checkbox + struck-through dimmed text; the member's progress bar and the widget's
  `x von y erledigt` line update immediately.
- **Add task** — tapping `Neue Aufgabe` opens task entry for that member (state not
  designed; an on-screen keyboard is needed since the panel has no hardware keyboard —
  ask before inventing it).
- **Departures / weather** — read-only. Both widgets show their own freshness (`Stand
  07:42`, `heute`); on a failed refresh keep the last values and mark them stale rather
  than blanking the widget.
- **No hover states** except the add-task button — this is a touch panel.
- Everything tappable is ≥44px: nav items ≈52px, add button 52px, task rows ≈50px.
  The 26px checkbox is a visual marker, not the hit target; the row is.

## State
- `activePage: 'tasks' | 'transit'`
- `members: { id, name, color, todos: { id, text, done, meta? }[] }` — counters, progress
  and the done line are all derived, never stored separately.
- `departures: { line, dest, time, countdown, track, status: 'ok' | 'late' | 'cancelled',
  delay? }[]` plus a `lastUpdated` timestamp for `Stand hh:mm`.
- `weather: { current, condition, high, low, hours: { hour, temp, rain }[], humidity,
  wind, sunset }` plus its own freshness.
- Clock ticks once a minute (seconds are not shown).

## Assets
None. No icons, images or logos are used — the weather is expressed by temperature and
rain bars, not glyphs, precisely so no icon set is needed. If icons are wanted later,
that is a new design decision.

## Files
- `mockups.dc.html` — both screens (Screen 1 Aufgaben, Screen 2 Abfahrten · Wetter),
  rendered side by side. Needs `support.js` beside it to open.
- `support.js` — runtime for the prototype only. Not part of the design.
