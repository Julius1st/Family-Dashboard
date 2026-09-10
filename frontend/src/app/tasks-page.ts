import { Component } from '@angular/core';

/**
 * Screen 1 — Aufgaben (design handoff). This ticket only wires the page
 * shell; the real per-member task board (columns, progress bars, add-task
 * flow) is Ticket 3's scope. For now this renders a minimal stub within
 * the widget-card visual language so the app shell is demonstrably wired
 * end-to-end.
 */
@Component({
  selector: 'app-tasks-page',
  templateUrl: './tasks-page.html',
  styleUrl: './tasks-page.css',
})
export class TasksPage {}
