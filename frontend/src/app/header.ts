import { Component, DestroyRef, computed, inject, signal } from '@angular/core';

import { Page, PageNavigationService } from './page-navigation.service';
import { Theme, ThemeService } from './theme.service';

/** Once a minute — the handoff's own "State" section: "seconds are not shown". */
const CLOCK_UPDATE_INTERVAL_MS = 60_000;

/**
 * `Intl.DateTimeFormat` instances are reused across renders/instances
 * (module-level, not per-component) — deliberately not Angular's
 * `DatePipe`/locale-data registration machinery, per the plan's explicit
 * guidance, since hardcoded German with no i18n framework is the decision.
 */
const TIME_FORMATTER = new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit' });
const DATE_FORMATTER = new Intl.DateTimeFormat('de-DE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/**
 * The app shell's header band: live clock + German date on the left, the
 * two-page nav pill on the right (design handoff, Screen 1 "Header band" /
 * "Page switch"). Shared verbatim by both pages — only the active nav item
 * differs (Screen 2's header is "identical").
 */
@Component({
  selector: 'app-header',
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header {
  private readonly navigation = inject(PageNavigationService);
  private readonly themeService = inject(ThemeService);

  protected readonly activePage = this.navigation.activePage;
  protected readonly theme = this.themeService.theme;

  /** Ticks once a minute; see the `setInterval` below. */
  private readonly now = signal(new Date());

  protected readonly time = computed(() => TIME_FORMATTER.format(this.now()));
  protected readonly date = computed(() => DATE_FORMATTER.format(this.now()));

  constructor() {
    const intervalId = setInterval(() => this.now.set(new Date()), CLOCK_UPDATE_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(intervalId));
  }

  protected isActive(page: Page): boolean {
    return this.activePage() === page;
  }

  protected select(page: Page): void {
    this.navigation.select(page);
  }

  protected isTheme(theme: Theme): boolean {
    return this.theme() === theme;
  }

  protected selectTheme(theme: Theme): void {
    this.themeService.setTheme(theme);
  }
}
