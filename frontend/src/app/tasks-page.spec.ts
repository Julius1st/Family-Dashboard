import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { TasksPage } from './tasks-page';

describe('TasksPage', () => {
  it('renders a non-blank stub within the widget-card visual language', async () => {
    TestBed.configureTestingModule({ imports: [TasksPage] });
    const fixture = TestBed.createComponent(TasksPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent?.trim()).not.toBe('');
    expect(compiled.querySelector('.tasks-page__card')).toBeTruthy();
  });
});
