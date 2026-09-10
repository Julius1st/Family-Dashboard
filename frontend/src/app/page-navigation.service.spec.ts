import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { PageNavigationService } from './page-navigation.service';

describe('PageNavigationService', () => {
  it('starts on the tasks page', () => {
    const service = TestBed.inject(PageNavigationService);

    expect(service.activePage()).toBe('tasks');
  });

  it('switches to the requested page', () => {
    const service = TestBed.inject(PageNavigationService);

    service.select('transit');

    expect(service.activePage()).toBe('transit');
  });

  it('switches back and forth', () => {
    const service = TestBed.inject(PageNavigationService);

    service.select('transit');
    service.select('tasks');

    expect(service.activePage()).toBe('tasks');
  });

  it('selecting the already-active page is a harmless no-op', () => {
    const service = TestBed.inject(PageNavigationService);

    service.select('tasks');

    expect(service.activePage()).toBe('tasks');
  });
});
