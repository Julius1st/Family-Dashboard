import { Component, inject } from '@angular/core';

import { Header } from './header';
import { PageNavigationService } from './page-navigation.service';
import { TasksPage } from './tasks-page';
import { TransitWeatherPage } from './transit-weather-page';

/**
 * App root: mounts the shared header and whichever page is currently
 * active, per `PageNavigationService`'s in-memory `activePage` signal
 * (design handoff's "State" section) — replaces the Phase 2/3
 * generic-tile `DashboardShell` scaffolding (see
 * `docs/frontend-redesign-plan.md`, Ticket 2).
 */
@Component({
  imports: [Header, TasksPage, TransitWeatherPage],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly navigation = inject(PageNavigationService);

  protected readonly activePage = this.navigation.activePage;
}
