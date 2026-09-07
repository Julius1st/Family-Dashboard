import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { WidgetService } from './widget.service';

describe('App', () => {
  beforeEach(async () => {
    // App mounts DashboardShell, which depends on WidgetService. Stub it
    // out here so this smoke test doesn't make a real HTTP call (and
    // doesn't leave a pending request that would block `whenStable()`).
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: WidgetService, useValue: { widgets: signal([]) } }],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Hello, frontend');
  });
});
