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
    const widgets: WidgetDescriptor[] = [
      { id: 'todo', displayName: 'Todo' },
      { id: 'weather', displayName: 'Weather' },
    ];
    configureWithWidgets(widgets);

    const fixture = TestBed.createComponent(DashboardShell);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const tiles = compiled.querySelectorAll('app-widget-fallback-tile');
    expect(tiles.length).toBe(2);
    expect(compiled.textContent).toContain('Todo');
    expect(compiled.textContent).toContain('Weather');
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
