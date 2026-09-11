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
  });

  afterEach(() => {
    vi.useRealTimers();
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

  it('renders two >=44x44px icon-only theme items, with no text labels', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const items = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'));
    expect(items).toHaveLength(2);

    for (const item of items) {
      expect(item.textContent?.trim()).toBe('');
      expect(item.querySelector('svg')).not.toBeNull();

      const style = getComputedStyle(item);
      expect(style.minHeight).toBe('44px');
      expect(style.minWidth).toBe('44px');
    }
  });

  it('defaults to the dark theme item marked active (default theme is dark)', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const [lightItem, darkItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'));
    expect(darkItem.classList).toContain('header__theme-item--active');
    expect(lightItem.classList).not.toContain('header__theme-item--active');
  });

  it('tapping the light icon calls ThemeService.setTheme("light") and marks it active', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const themeService = TestBed.inject(ThemeService);
    const setThemeSpy = vi.spyOn(themeService, 'setTheme');

    const compiled = fixture.nativeElement as HTMLElement;
    const [lightItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'));
    lightItem.click();
    await fixture.whenStable();

    expect(setThemeSpy).toHaveBeenCalledOnce();
    expect(setThemeSpy).toHaveBeenCalledWith('light');
    expect(themeService.theme()).toBe('light');
    const [lightItemAfter, darkItemAfter] = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'),
    );
    expect(lightItemAfter.classList).toContain('header__theme-item--active');
    expect(darkItemAfter.classList).not.toContain('header__theme-item--active');
  });

  it('is a direct selector, not a toggle: clicking the already-active item is a no-op', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const themeService = TestBed.inject(ThemeService);

    // Default theme is 'dark', so the dark item starts active.
    const compiled = fixture.nativeElement as HTMLElement;
    const [, darkItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'));
    darkItem.click();
    await fixture.whenStable();

    // A toggle() call here would have flipped to 'light'; setTheme('dark')
    // while already dark must leave it exactly as it was.
    expect(themeService.theme()).toBe('dark');
    const [, darkItemAfter] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'));
    expect(darkItemAfter.classList).toContain('header__theme-item--active');
  });

  it('the active theme icon resolves via --ink-on-pill-active on a --ink-primary background; the inactive one via --ink-nav-inactive on transparent', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const activeItem = compiled.querySelector<HTMLButtonElement>('.header__theme-item--active');
    const inactiveItem = compiled.querySelector<HTMLButtonElement>('.header__theme-item:not(.header__theme-item--active)');
    expect(activeItem).not.toBeNull();
    expect(inactiveItem).not.toBeNull();

    // As with the nav pill's own equivalent test above, jsdom hands back
    // the literal var() declaration rather than a resolved color, which is
    // what lets this assert on the token *name* actually used.
    const activeStyle = getComputedStyle(activeItem!);
    expect(activeStyle.color).toContain('var(--ink-on-pill-active)');
    expect(activeStyle.background).toContain('var(--ink-primary)');

    const inactiveStyle = getComputedStyle(inactiveItem!);
    expect(inactiveStyle.color).toContain('var(--ink-nav-inactive)');
    expect(inactiveStyle.background).toContain('rgba(0, 0, 0, 0)');
  });

  it('the icons use currentColor, not a hardcoded fill/stroke, so they inherit their container color', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const items = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__theme-item'));
    for (const item of items) {
      const svg = item.querySelector('svg');
      expect(svg).not.toBeNull();
      expect(svg!.getAttribute('fill')).toBe('none');
      expect(svg!.getAttribute('stroke')).toBe('currentColor');

      // No child shape hardcodes its own color/fill either — each one
      // either omits `fill` entirely or explicitly sets it to 'none',
      // relying on the parent <svg>'s stroke="currentColor" for color.
      for (const shape of Array.from(svg!.querySelectorAll('circle, line, path'))) {
        const fill = shape.getAttribute('fill');
        expect(fill === null || fill === 'none').toBe(true);
        expect(shape.getAttribute('stroke')).toBeNull();
      }

      const icon = item.querySelector<SVGElement>('.header__theme-icon');
      expect(getComputedStyle(icon!).color).toBe(getComputedStyle(item).color);
    }
  });
});
