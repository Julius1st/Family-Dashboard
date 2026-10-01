/**
 * Per-line badge colors for KVV (Karlsruher Verkehrsverbund) Stadtbahn and
 * tram lines — used by `DeparturesPanel` to color `.departures-panel__line`
 * to match the line's real-world official color instead of the generic
 * `bg/inset-hi` neutral badge the design handoff specifies as the *default*
 * (see `docs/design_handoff_family_dashboard/README.md`'s "Departures
 * widget" section — it never anticipated per-line color, so there is no
 * handoff token for this; these are genuinely new, line-specific values).
 *
 * **Source — secondary, not KVV's own GTFS feed.** The authoritative source
 * for these colors would be KVV's own GTFS feed (`routes.txt`'s
 * `route_color`/`route_text_color` fields,
 * https://projekte.kvv-efa.de/GTFS/google_transit.zip) — but that zip isn't
 * reachable from this project's sandboxed environment (egress firewall) and
 * is too large to fetch through other available tooling, and no mirror or
 * third-party aggregator exposing the color fields was found either. The
 * hex values below are instead transcribed from a Karlsruhe community wiki
 * page, https://ka.stadtwiki.net/Stadtwiki:Projekt_KVV/Gestaltung, which
 * pairs each line with an official RAL color-manual code and its hex
 * approximation — the RAL pairing is a decent signal this reflects KVV's
 * real corporate design manual rather than a fabricated guess, but it is
 * still a secondary, unofficial source and NOT verified against KVV's own
 * data. If/when someone pulls the real `routes.txt` (e.g. once the feed is
 * reachable, or downloaded manually), cross-check and correct the values
 * here — every entry below carries its source RAL name for exactly that
 * purpose.
 *
 * **Buses are intentionally absent.** The wiki page above only documents
 * Stadtbahn (S-lines) and Straßenbahn (tram lines 1-9) — it has no bus
 * section at all. Rather than guess, bus lines (and any other line string
 * not listed below) simply have no entry here; `lineColor()` returns
 * `undefined` for them, and `DeparturesPanel` falls back to the existing
 * neutral `bg/inset-hi` badge for those rows, unchanged from before this
 * file existed.
 *
 * **The S41 conflict.** The source page lists S41 twice, with two different
 * colors: once grouped with S4 ("S4, S41 → himbeerrot, #760d37") and once on
 * its own dedicated row ("S41 → Kräftiges Gelb, #bed730"). This isn't
 * resolved by picking whichever "looks right" — it's a genuine ambiguity in
 * the source (possibly a transcription artifact, possibly two real
 * branch/historical colors for the same line number). This file resolves it
 * by preferring the dedicated single-line entry (#bed730) over the grouped
 * one: a row naming S41 on its own, with no other line attached, reads as
 * the more deliberate, specific statement about that exact line, whereas
 * the grouped "S4, S41" row reads more like S41 being swept in alongside S4
 * rather than being independently verified. This is a judgment call, not a
 * certainty — flagging it here so a future maintainer checking against real
 * GTFS data knows exactly which of the two to double-check first.
 *
 * **Text color.** The handoff gives no per-line-color text-contrast rule
 * (the one existing colored-background precedent, `member-color.ts`, always
 * uses dark `ink-on-accent` text because member colors are generated at a
 * fixed, always-light `L=0.74`; these KVV colors have no such fixed
 * lightness — they range from near-black navy to near-white yellow — so
 * that shortcut doesn't apply here). Instead each line's text color is
 * computed, not hand-picked: `pickTextColor()` below computes the standard
 * WCAG relative luminance of the background and picks whichever of pure
 * black/white yields the higher contrast ratio against it. This is
 * equivalent to (and replaces the need for) manually eyeballing each of the
 * 20 colors below.
 */

/** One line's resolved badge colors, ready to apply directly as CSS. */
export interface LineColor {
  readonly background: string;
  readonly text: string;
}

/**
 * Raw source data: line name -> `[hex, RAL name]`, transcribed verbatim
 * from https://ka.stadtwiki.net/Stadtwiki:Projekt_KVV/Gestaltung. The RAL
 * name is kept purely as a traceability/credibility aid (see file doc
 * comment) — it plays no role in the computed colors.
 *
 * Lines sharing one color in the source are listed together and expanded
 * below; see the S41 entry's own comment for why it's listed on its own
 * rather than grouped with S4.
 */
const RAW_LINE_COLORS: ReadonlyArray<{ lines: readonly string[]; hex: string; ral: string }> = [
  // Stadtbahn (light rail)
  { lines: ['S1', 'S11', 'S12'], hex: '#008e50', ral: 'verkehrsgrün' },
  { lines: ['S2'], hex: '#804387', ral: 'signalviolett' },
  { lines: ['S31', 'S32', 'S34'], hex: '#00907a', ral: 'wasserblau' },
  { lines: ['S4'], hex: '#760d37', ral: 'himbeerrot' },
  // S41 conflict: see this file's top-of-file doc comment ("The S41
  // conflict"). The dedicated single-line source row (#bed730) is used
  // instead of the grouped "S4, S41" row's #760d37.
  { lines: ['S41'], hex: '#bed730', ral: 'Kräftiges Gelb' },
  { lines: ['S42'], hex: '#009ee0', ral: 'Reines Blau' },
  { lines: ['S5', 'S51', 'S52'], hex: '#e17b7c', ral: 'altrosa' },
  { lines: ['S6'], hex: '#3d2d7c', ral: 'nachtblau' },
  { lines: ['S7', 'S71'], hex: '#fff90a', ral: 'schwefelgelb' },
  { lines: ['S8', 'S81'], hex: '#4d4f23', ral: 'olivgrün' },
  { lines: ['S9'], hex: '#e9a085', ral: 'lachsorange' },
  // Straßenbahn (tram)
  { lines: ['1'], hex: '#cb0b27', ral: 'verkehrsrot' },
  { lines: ['2'], hex: '#004d95', ral: 'verkehrsblau' },
  { lines: ['3'], hex: '#70582c', ral: 'olivbraun' },
  { lines: ['4'], hex: '#f1c21f', ral: 'verkehrsgelb' },
  { lines: ['5'], hex: '#18a6d9', ral: 'lichtblau' },
  { lines: ['6'], hex: '#63b631', ral: 'gelbgrün' },
  { lines: ['7'], hex: '#626564', ral: 'anthrazitgrau' },
  { lines: ['8'], hex: '#dc6727', ral: 'tieforange' },
  { lines: ['9'], hex: '#626564', ral: 'anthrazitgrau' },
];

/**
 * sRGB -> linear-light channel conversion, the standard first step of the
 * WCAG relative luminance formula
 * (https://www.w3.org/TR/WCAG21/#dfn-relative-luminance).
 */
function linearizeChannel(channel8bit: number): number {
  const c = channel8bit / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** WCAG relative luminance (0 = black, 1 = white) of a `#rrggbb` hex color. */
function relativeLuminance(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return 0.2126 * linearizeChannel(r) + 0.7152 * linearizeChannel(g) + 0.0722 * linearizeChannel(b);
}

/**
 * Picks whichever of pure black/white has the higher WCAG contrast ratio
 * against `backgroundHex`, per the standard contrast-ratio formula
 * (https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio): `(lighter + 0.05) /
 * (darker + 0.05)`. Black and white are the only two candidates (matching
 * this design system's existing light/dark-text convention — e.g.
 * `ink-on-accent` vs. `ink-primary` — rather than inventing a third
 * in-between text color).
 */
function pickTextColor(backgroundHex: string): string {
  const bgLuminance = relativeLuminance(backgroundHex);
  const contrastWithWhite = (1 + 0.05) / (bgLuminance + 0.05);
  const contrastWithBlack = (bgLuminance + 0.05) / (0 + 0.05);
  return contrastWithWhite >= contrastWithBlack ? '#ffffff' : '#000000';
}

/** Flattened `line -> LineColor` lookup, built once from {@link RAW_LINE_COLORS}. */
const LINE_COLORS: ReadonlyMap<string, LineColor> = new Map(
  RAW_LINE_COLORS.flatMap(({ lines, hex }) => {
    const color: LineColor = { background: hex, text: pickTextColor(hex) };
    return lines.map((line) => [line, color] as const);
  }),
);

/**
 * Resolves `line` (e.g. `"4"`, `"S2"`) to its official KVV badge color, or
 * `undefined` if `line` isn't one of the Stadtbahn/tram lines this file
 * documents (in particular, every bus line) — callers should fall back to
 * the existing neutral badge styling in that case.
 */
export function kvvLineColor(line: string): LineColor | undefined {
  return LINE_COLORS.get(line);
}
