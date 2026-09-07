import { Component, signal } from '@angular/core';

import { DashboardShell } from './dashboard-shell';

@Component({
  imports: [DashboardShell],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');
}
