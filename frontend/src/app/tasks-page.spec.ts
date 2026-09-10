import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { memberColor } from './member-color';
import { TasksPage } from './tasks-page';
import { TodoItem } from './todo-item';
import { TodoService } from './todo.service';

describe('TasksPage', () => {
  /**
   * `setDone` mutates the same `items` signal the real `TodoService`
   * would end up mutating (via its own `itemsState.set` after the PUT
   * response) — so tests that toggle a row exercise the same "component
   * reacts to a signal changing under it" path the real service produces,
   * without needing HTTP at all.
   */
  function configureWith(members: readonly string[] | undefined, items: readonly TodoItem[] | undefined) {
    const itemsSignal = signal(items);
    const fake = {
      members: signal(members),
      items: itemsSignal,
      create: vi.fn(),
      setDone: vi.fn((id: number, done: boolean) => {
        itemsSignal.update((current) => current?.map((item) => (item.id === id ? { ...item, done } : item)));
      }),
      updateDescription: vi.fn(),
      remove: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [TasksPage],
      providers: [{ provide: TodoService, useValue: fake }],
    });
    return fake;
  }

  async function createFixture(): Promise<ComponentFixture<TasksPage>> {
    const fixture = TestBed.createComponent(TasksPage);
    await fixture.whenStable();
    return fixture;
  }

  it('renders a loading state while members/items have not resolved yet', async () => {
    configureWith(undefined, undefined);

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.tasks-page__column').length).toBe(0);
    expect(compiled.textContent).toContain('Aufgaben werden geladen');
  });

  it('renders a non-blank empty state when no household members are configured', async () => {
    configureWith([], []);

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.tasks-page__column').length).toBe(0);
    expect(compiled.textContent?.trim()).not.toBe('');
    expect(compiled.textContent).toContain('Keine Haushaltsmitglieder konfiguriert.');
  });

  it('renders the header eyebrow/title and the "x von y erledigt" line, computed from all members’ items', async () => {
    configureWith(
      ['Alice', 'Bob'],
      [
        { id: 1, householdMember: 'Alice', description: 'Buy milk', done: true },
        { id: 2, householdMember: 'Alice', description: 'Walk dog', done: false },
        { id: 3, householdMember: 'Bob', description: 'Tidy room', done: true },
      ],
    );

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.tasks-page__eyebrow')?.textContent?.trim()).toBe('AUFGABEN');
    expect(compiled.querySelector('.tasks-page__title')?.textContent?.trim()).toBe('Haushalt');
    expect(compiled.querySelector('.tasks-page__done-line')?.textContent?.trim()).toBe('2 von 3 erledigt');
  });

  it('renders one column per configured member, including a member with zero items, colored by position', async () => {
    configureWith(
      ['Alice', 'Bob'],
      [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }],
    );

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    const columns = Array.from(compiled.querySelectorAll<HTMLElement>('.tasks-page__column'));
    expect(columns.length).toBe(2);

    const aliceColumn = columns.find((column) => column.textContent?.includes('Alice'));
    const bobColumn = columns.find((column) => column.textContent?.includes('Bob'));
    expect(aliceColumn).toBeTruthy();
    expect(bobColumn).toBeTruthy();

    expect(aliceColumn?.textContent).toContain('Buy milk');
    expect(aliceColumn?.querySelectorAll('.tasks-page__row').length).toBe(1);
    expect(aliceColumn?.querySelector('.tasks-page__member-counter')?.textContent?.trim()).toBe('0/1');
    expect(aliceColumn?.style.getPropertyValue('--member-color')).toBe(memberColor(0));

    // Bob has zero items today: still a visible, non-blank column, not
    // skipped or hidden.
    expect(bobColumn?.querySelectorAll('.tasks-page__row').length).toBe(0);
    expect(bobColumn?.textContent?.trim()).not.toBe('');
    expect(bobColumn?.textContent).toContain('Keine Aufgaben.');
    expect(bobColumn?.querySelector('.tasks-page__member-counter')?.textContent?.trim()).toBe('0/0');
    expect(bobColumn?.style.getPropertyValue('--member-color')).toBe(memberColor(1));
  });

  it('calls setDone with the toggled value when a task row is tapped', async () => {
    const fake = configureWith(
      ['Alice'],
      [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }],
    );

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const row = compiled.querySelector<HTMLButtonElement>('.tasks-page__row');
    expect(row).toBeTruthy();

    row?.click();

    expect(fake.setDone).toHaveBeenCalledExactlyOnceWith(1, true);
  });

  it(
    'updates the toggled row, that member’s progress bar/counter, and the page’s done line ' +
      'immediately after a toggle, with no manual re-render',
    async () => {
      configureWith(
        ['Alice', 'Bob'],
        [
          { id: 1, householdMember: 'Alice', description: 'Buy milk', done: false },
          { id: 2, householdMember: 'Alice', description: 'Walk dog', done: false },
        ],
      );

      const fixture = await createFixture();
      const compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.querySelector('.tasks-page__done-line')?.textContent?.trim()).toBe('0 von 2 erledigt');

      const aliceColumn = Array.from(compiled.querySelectorAll<HTMLElement>('.tasks-page__column')).find((column) =>
        column.textContent?.includes('Alice'),
      );
      expect(aliceColumn?.querySelector<HTMLElement>('.tasks-page__progress-fill')?.style.width).toBe('0%');

      const row = compiled.querySelector<HTMLButtonElement>('.tasks-page__row');
      row?.click();
      await fixture.whenStable();

      // Page-level "x von y erledigt" line.
      expect(compiled.querySelector('.tasks-page__done-line')?.textContent?.trim()).toBe('1 von 2 erledigt');

      // The toggled row's own appearance.
      expect(row?.getAttribute('aria-checked')).toBe('true');
      expect(row?.querySelector('.tasks-page__task-text')?.classList).toContain('tasks-page__task-text--done');
      expect(row?.querySelector('.tasks-page__checkbox')?.classList).toContain('tasks-page__checkbox--done');

      // That member's counter and progress-bar fill.
      expect(aliceColumn?.querySelector('.tasks-page__member-counter')?.textContent?.trim()).toBe('1/2');
      expect(aliceColumn?.querySelector<HTMLElement>('.tasks-page__progress-fill')?.style.width).toBe('50%');
    },
  );

  it('renders a real, clickable, >=44px add-task control per member', async () => {
    configureWith(['Alice', 'Bob'], []);

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const addButtons = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.tasks-page__add-button'));
    expect(addButtons.length).toBe(2);
    expect(addButtons[0].textContent).toContain('Neue Aufgabe');

    for (const button of addButtons) {
      expect(getComputedStyle(button).minHeight).toBe('52px');
    }
  });

  it("tapping a column's add-task button opens the add-task dialog scoped to that member, not another", async () => {
    configureWith(['Alice', 'Bob'], []);

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const addButtons = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.tasks-page__add-button'));

    addButtons[1].click();
    await fixture.whenStable();

    const dialog = compiled.querySelector('dialog') as HTMLDialogElement;
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('Bob');
    expect(dialog.textContent).not.toContain('Alice');
  });

  it('creating a task via the dialog appends it to that member’s column without a manual refresh', async () => {
    const fake = configureWith(['Alice', 'Bob'], [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }]);
    fake.create = vi.fn((member: string, description: string) => {
      const items = fake.items() ?? [];
      fake.items.set([...items, { id: 99, householdMember: member, description, done: false }]);
    });

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const addButtons = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.tasks-page__add-button'));

    addButtons[1].click();
    await fixture.whenStable();

    const dialog = compiled.querySelector('dialog') as HTMLDialogElement;
    const input = dialog.querySelector('input') as HTMLInputElement;
    input.value = 'Rasen mähen';
    (dialog.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(dialog.open).toBe(false);
    const bobColumn = Array.from(compiled.querySelectorAll<HTMLElement>('.tasks-page__column')).find((column) =>
      column.textContent?.includes('Bob'),
    );
    expect(bobColumn?.textContent).toContain('Rasen mähen');
  });

  it('meets the >=44px touch target baseline for task rows', async () => {
    configureWith(['Alice'], [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }]);

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const row = compiled.querySelector<HTMLButtonElement>('.tasks-page__row');

    expect(getComputedStyle(row!).minHeight).toBe('50px');
  });
});
