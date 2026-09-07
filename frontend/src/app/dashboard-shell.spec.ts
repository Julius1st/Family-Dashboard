import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { DashboardShell } from './dashboard-shell';
import { WidgetDescriptor } from './widget-descriptor';
import { WidgetService } from './widget.service';

describe('DashboardShell', () => {
  function configureWithWidgets(widgets: readonly WidgetDescriptor[] | undefined): void {
    TestBed.configureTestingModule({
      imports: [DashboardShell],
      providers: [{ provide: WidgetService, useValue: { widgets: signal(widgets) } }],
    });
  }

  it('renders one tile per widget returned by the service', async () => {
    // Ids deliberately not registered in widget-tile-registry.ts (unlike
    // e.g. 'todo'), so both resolve to the generic fallback tile — this
    // test is about the shell's per-widget rendering loop, not any one
    // widget's dedicated component.
    const widgets: WidgetDescriptor[] = [
      { id: 'unregistered-widget-a', displayName: 'Widget A' },
      { id: 'unregistered-widget-b', displayName: 'Widget B' },
    ];
    configureWithWidgets(widgets);

    const fixture = TestBed.createComponent(DashboardShell);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const tiles = compiled.querySelectorAll('app-widget-fallback-tile');
    expect(tiles.length).toBe(2);
    expect(compiled.textContent).toContain('Widget A');
    expect(compiled.textContent).toContain('Widget B');
  });

  it('renders a non-blank empty state when there are no widgets', async () => {
    configureWithWidgets([]);

    const fixture = TestBed.createComponent(DashboardShell);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('app-widget-fallback-tile').length).toBe(0);
    expect(compiled.textContent?.trim()).not.toBe('');
    expect(compiled.textContent).toContain('No widgets are configured yet.');
  });

  it('renders a loading state while the request has not resolved yet', async () => {
    configureWithWidgets(undefined);

    const fixture = TestBed.createComponent(DashboardShell);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('app-widget-fallback-tile').length).toBe(0);
    expect(compiled.textContent).toContain('Loading widgets');
  });
});
