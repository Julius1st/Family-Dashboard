import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset['theme'];
  });

  afterEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset['theme'];
  });

  it('defaults to dark when nothing is stored, and mirrors that onto the DOM and localStorage', () => {
    const service = TestBed.inject(ThemeService);
    TestBed.tick();

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('defaults to dark when localStorage holds a value other than "dark"/"light"', () => {
    localStorage.setItem('theme', 'sepia');

    const service = TestBed.inject(ThemeService);
    TestBed.tick();

    expect(service.theme()).toBe('dark');
  });

  it('toggle() flips the signal, the DOM attribute and localStorage to light', () => {
    const service = TestBed.inject(ThemeService);
    TestBed.tick();

    service.toggle();
    TestBed.tick();

    expect(service.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
    expect(localStorage.getItem('theme')).toBe('light');
  });

  it('toggle() flips back to dark on a second call', () => {
    const service = TestBed.inject(ThemeService);
    TestBed.tick();

    service.toggle();
    TestBed.tick();
    service.toggle();
    TestBed.tick();

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('persistence round-trips: a fresh instance picks up a previously stored "light" value', () => {
    localStorage.setItem('theme', 'light');

    const service = TestBed.inject(ThemeService);
    TestBed.tick();

    expect(service.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });
});
