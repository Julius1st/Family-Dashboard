import { Injectable, Signal, effect, signal } from '@angular/core';

/**
 * The two themes `tokens.css` defines (`:root` for dark, the default; and
 * `:root[data-theme="light"]`). See `frontend/src/styles/tokens.css`.
 */
export type Theme = 'dark' | 'light';

/** localStorage key the chosen theme is persisted under. */
const STORAGE_KEY = 'theme';

function readStoredTheme(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'light' || stored === 'dark' ? stored : 'dark';
}

/**
 * Manual light/dark theme state for the app shell, toggled by a button in
 * the header (never OS-preference or time-of-day driven — an explicit
 * decision, since the design handoff itself is "dark at all hours" and
 * defines no light palette on its own).
 *
 * Mirrors `PageNavigationService`'s shape: a private writable signal with
 * a readonly public view. The one addition is the `effect()` in the
 * constructor, which is the single place that reaches outside the signal
 * graph — it mirrors the current theme onto
 * `document.documentElement.dataset.theme` (which is what `tokens.css`'s
 * `:root[data-theme="light"]` selector matches) and into `localStorage`
 * (so the choice survives a reload), every time the signal changes,
 * including the very first run right after construction.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<Theme>(readStoredTheme());

  readonly theme: Signal<Theme> = this._theme.asReadonly();

  constructor() {
    effect(() => {
      const theme = this._theme();
      document.documentElement.setAttribute('data-theme', theme);
      localStorage.setItem(STORAGE_KEY, theme);
    });
  }

  /** Flips between 'dark' and 'light'. */
  toggle(): void {
    this._theme.set(this._theme() === 'dark' ? 'light' : 'dark');
  }

  /**
   * Sets the theme directly, mirroring `PageNavigationService.select(page)`'s
   * shape. Setting the already-active theme is a no-op (same signal value in,
   * same value out) — unlike `toggle()`, this never flips away from the
   * requested theme.
   */
  setTheme(theme: Theme): void {
    this._theme.set(theme);
  }
}
