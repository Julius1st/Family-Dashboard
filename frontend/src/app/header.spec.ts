import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Header } from './header';
import { PageNavigationService } from './page-navigation.service';
import { ThemeService } from './theme.service';

describe('Header', () => {
  beforeEach(() => {
    // Fake only `Date`/`setInterval`/`clearInterval` — the clock's own
    // timing primitives — and leave `setTimeout`/microtasks real, since
    // Angular's zoneless change-detection scheduler relies on those to
    // resolve `fixture.whenStable()`.
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(new Date('2026-09-10T07:42:00'));
    // ThemeService persists to real jsdom localStorage on every change
    // (see its constructor `effect()`); without clearing it here, a theme
    // change made by one test (e.g. clicking the light icon) leaks into
    // the next test's fresh ThemeService instance via `readStoredTheme()`,
    // silently changing which theme it starts in. Matches the convention
    // already used in theme.service.spec.ts.
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('renders the clock (mono, no seconds) and the German date', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.header__clock')?.textContent?.trim()).toBe('07:42');
    expect(compiled.querySelector('.header__date')?.textContent?.trim()).toBe('Donnerstag, 10. September');
  });

  it('updates the clock once a minute without a manual refresh', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    // Advance the shared fake clock (which also drives the faked `Date`)
    // by exactly one interval tick, rather than calling `setSystemTime`
    // again — jumping `Date` independently of the clock used to schedule
    // the interval would let the pending tick land mid-advance and fire
    // twice within the window.
    await vi.advanceTimersByTimeAsync(60_000);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.header__clock')?.textContent?.trim()).toBe('07:43');
  });

  it('does not update the clock before a full minute has elapsed', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    await vi.advanceTimersByTimeAsync(59_000);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.header__clock')?.textContent?.trim()).toBe('07:42');
  });

  it('defaults to the tasks page marked active in the nav pill', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const [tasksItem, transitItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'));
    expect(tasksItem.classList).toContain('header__nav-item--active');
    expect(tasksItem.getAttribute('aria-current')).toBe('page');
    expect(transitItem.classList).not.toContain('header__nav-item--active');
    expect(transitItem.getAttribute('aria-current')).toBeNull();
  });

  it('tapping the inactive nav item switches the shared active page', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const navigation = TestBed.inject(PageNavigationService);

    const compiled = fixture.nativeElement as HTMLElement;
    const [, transitItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'));
    transitItem.click();
    await fixture.whenStable();

    expect(navigation.activePage()).toBe('transit');
    const [tasksItem, transitItemAfter] = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'),
    );
    expect(tasksItem.classList).not.toContain('header__nav-item--active');
    expect(transitItemAfter.classList).toContain('header__nav-item--active');
  });

  it('meets the >=44px touch target baseline for both nav items', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    for (const item of Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'))) {
      const minHeight = getComputedStyle(item).minHeight;
      expect(minHeight).toBe('44px');
    }
  });

  it('the active nav pill text genuinely resolves via --ink-on-pill-active, not --ink-on-accent', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const activeItem = compiled.querySelector<HTMLButtonElement>('.header__nav-item--active');
    expect(activeItem).not.toBeNull();
    // jsdom doesn't substitute var() inside standard properties like
    // `color`, so getComputedStyle hands back the literal declaration —
    // which is exactly what lets this assert on the *token name* used,
    // not just a resolved color value that could match either token.
    const color = getComputedStyle(activeItem!).color;
    expect(color).toContain('var(--ink-on-pill-active)');
    expect(color).not.toContain('var(--ink-on-accent)');
  });

  it('renders a single icon-only theme toggle button (>=44px touch minimum), with no text label', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    expect(toggle).not.toBeNull();
    expect(toggle!.textContent?.trim()).toBe('');
    expect(toggle!.querySelectorAll('svg')).toHaveLength(1);

    // 54px, not just the 44px minimum — see the footprint-equality test
    // below for why: it matches the nav pill's own true rendered size.
    const style = getComputedStyle(toggle!);
    expect(style.minHeight).toBe('54px');
    expect(style.minWidth).toBe('54px');
  });

  it('the theme toggle is footprint-equal to the nav pill: 44px item + 5px+5px container padding = 54px', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    // jsdom doesn't perform real layout, so this can't measure actual
    // rendered pixels the way a browser would. What it CAN do is read back
    // each side's computed CSS declarations and add them up algebraically —
    // proving the two controls are sized to the same total footprint by
    // construction, which is the strongest check available without a real
    // browser to visually compare them in.
    const compiled = fixture.nativeElement as HTMLElement;
    const nav = compiled.querySelector<HTMLElement>('.header__nav');
    const navItem = compiled.querySelector<HTMLButtonElement>('.header__nav-item');
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    expect(nav).not.toBeNull();
    expect(navItem).not.toBeNull();
    expect(toggle).not.toBeNull();

    const navStyle = getComputedStyle(nav!);
    expect(navStyle.paddingTop).toBe('5px');
    expect(navStyle.paddingBottom).toBe('5px');
    expect(getComputedStyle(navItem!).minHeight).toBe('44px');

    const navPillTotalHeight = 44 + 5 + 5;
    const toggleStyle = getComputedStyle(toggle!);
    expect(toggleStyle.minHeight).toBe(`${navPillTotalHeight}px`);
    expect(toggleStyle.minWidth).toBe(`${navPillTotalHeight}px`);
  });

  it('shows the sun icon (tap-for-bright) while the default dark theme is active', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    // The sun icon is the only one with a <circle> (the moon is a single <path>).
    expect(toggle!.querySelector('svg circle')).not.toBeNull();
    expect(toggle!.querySelector('svg path')).toBeNull();
  });

  it('tapping the toggle calls ThemeService.toggle() and swaps the icon shown, in both directions', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const themeService = TestBed.inject(ThemeService);
    const toggleSpy = vi.spyOn(themeService, 'toggle');

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');

    toggle!.click();
    await fixture.whenStable();

    expect(toggleSpy).toHaveBeenCalledOnce();
    expect(themeService.theme()).toBe('light');
    // Now light: shows the moon icon (tap-for-dark) — a single <path>, no <circle>.
    expect(toggle!.querySelector('svg circle')).toBeNull();
    expect(toggle!.querySelector('svg path')).not.toBeNull();

    toggle!.click();
    await fixture.whenStable();

    expect(toggleSpy).toHaveBeenCalledTimes(2);
    expect(themeService.theme()).toBe('dark');
    expect(toggle!.querySelector('svg circle')).not.toBeNull();
    expect(toggle!.querySelector('svg path')).toBeNull();
  });

  it('the theme toggle icon genuinely resolves via --ink-primary on a --bg-inset background', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    expect(toggle).not.toBeNull();

    // As with the nav pill's own equivalent test above, jsdom hands back
    // the literal var() declaration rather than a resolved color, which is
    // what lets this assert on the token *name* actually used.
    const style = getComputedStyle(toggle!);
    expect(style.color).toContain('var(--ink-primary)');
    expect(style.background).toContain('var(--bg-inset)');
  });

  it('the icon uses currentColor, not a hardcoded fill/stroke, so it inherits the button color', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    const svg = toggle!.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg!.getAttribute('fill')).toBe('none');
    expect(svg!.getAttribute('stroke')).toBe('currentColor');

    // No child shape hardcodes its own color/fill either — each one either
    // omits `fill` entirely or explicitly sets it to 'none', relying on the
    // parent <svg>'s stroke="currentColor" for color.
    for (const shape of Array.from(svg!.querySelectorAll('circle, line, path'))) {
      const fill = shape.getAttribute('fill');
      expect(fill === null || fill === 'none').toBe(true);
      expect(shape.getAttribute('stroke')).toBeNull();
    }

    const icon = toggle!.querySelector<SVGElement>('.header__theme-icon');
    expect(getComputedStyle(icon!).color).toBe(getComputedStyle(toggle!).color);
  });
});
