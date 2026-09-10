/**
 * Per-member accent color, computed from a household member's position in
 * `TodoService.members()` rather than hardcoded to sample names.
 *
 * Matches the design handoff's own stated rule (design tokens & type
 * scale section, "Member colors"): fixed `L=0.74 C=0.13`, hue stepping by
 * ~45° per member, so any number of configured members (this project's
 * real `household.members` config currently has two) gets a distinct
 * color with zero code changes when the list grows.
 */
const MEMBER_COLOR_LIGHTNESS = 0.74;
const MEMBER_COLOR_CHROMA = 0.13;
const MEMBER_COLOR_HUE_STEP_DEGREES = 45;

export function memberColor(index: number): string {
  const hue = (index * MEMBER_COLOR_HUE_STEP_DEGREES) % 360;
  return `oklch(${MEMBER_COLOR_LIGHTNESS} ${MEMBER_COLOR_CHROMA} ${hue})`;
}
