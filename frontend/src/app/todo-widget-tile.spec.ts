import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TodoItem } from './todo-item';
import { TodoService } from './todo.service';
import { TodoWidgetTile } from './todo-widget-tile';
import { WidgetDescriptor } from './widget-descriptor';

describe('TodoWidgetTile', () => {
  const descriptor: WidgetDescriptor = { id: 'todo', displayName: 'Todo Lists' };

  function configureWith(members: readonly string[] | undefined, items: readonly TodoItem[] | undefined) {
    const fake = {
      members: signal(members),
      items: signal(items),
      create: vi.fn(),
      setDone: vi.fn(),
      updateDescription: vi.fn(),
      remove: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [TodoWidgetTile],
      providers: [{ provide: TodoService, useValue: fake }],
    });
    return fake;
  }

  async function createFixture(): Promise<ComponentFixture<TodoWidgetTile>> {
    const fixture = TestBed.createComponent(TodoWidgetTile);
    fixture.componentRef.setInput('descriptor', descriptor);
    await fixture.whenStable();
    return fixture;
  }

  it('renders a loading state while members/items have not resolved yet', async () => {
    configureWith(undefined, undefined);

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.todo-widget-tile__member').length).toBe(0);
    expect(compiled.textContent).toContain('Loading to-dos');
  });

  it('renders a non-blank empty state when no household members are configured', async () => {
    configureWith([], []);

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.todo-widget-tile__member').length).toBe(0);
    expect(compiled.textContent?.trim()).not.toBe('');
    expect(compiled.textContent).toContain('No household members are configured.');
  });

  it('renders one section per configured member, including a member with zero items', async () => {
    configureWith(
      ['Alice', 'Bob'],
      [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }],
    );

    const fixture = await createFixture();

    const compiled = fixture.nativeElement as HTMLElement;
    const sections = Array.from(compiled.querySelectorAll<HTMLElement>('.todo-widget-tile__member'));
    expect(sections.length).toBe(2);

    const aliceSection = sections.find((section) => section.textContent?.includes('Alice'));
    const bobSection = sections.find((section) => section.textContent?.includes('Bob'));
    expect(aliceSection).toBeTruthy();
    expect(bobSection).toBeTruthy();

    expect(aliceSection?.textContent).toContain('Buy milk');
    expect(aliceSection?.querySelectorAll('.todo-widget-tile__item').length).toBe(1);

    // Bob has zero items today: still a visible, non-blank empty section, not
    // skipped or hidden.
    expect(bobSection?.querySelectorAll('.todo-widget-tile__item').length).toBe(0);
    expect(bobSection?.textContent?.trim()).not.toBe('');
    expect(bobSection?.textContent).toContain('No to-dos yet.');
  });

  it('calls setDone with the toggled value when the toggle control is clicked', async () => {
    const fake = configureWith(
      ['Alice'],
      [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }],
    );

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.todo-widget-tile__toggle');
    expect(toggle).toBeTruthy();

    toggle?.click();

    expect(fake.setDone).toHaveBeenCalledExactlyOnceWith(1, true);
  });

  it('calls remove with the item id when the delete control is clicked', async () => {
    const fake = configureWith(
      ['Alice'],
      [{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }],
    );

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const deleteButton = compiled.querySelector<HTMLButtonElement>('.todo-widget-tile__delete');
    expect(deleteButton).toBeTruthy();

    deleteButton?.click();

    expect(fake.remove).toHaveBeenCalledExactlyOnceWith(1);
  });

  it('calls create with the member and trimmed description, and clears the input, on submit', async () => {
    const fake = configureWith(['Alice'], []);

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector<HTMLInputElement>('.todo-widget-tile__add-input');
    const form = compiled.querySelector<HTMLFormElement>('.todo-widget-tile__add');
    expect(input).toBeTruthy();
    expect(form).toBeTruthy();

    input!.value = 'Walk dog';
    input!.dispatchEvent(new Event('input'));
    form!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();

    expect(fake.create).toHaveBeenCalledExactlyOnceWith('Alice', 'Walk dog');
    expect(input!.value).toBe('');
  });

  it('does not call create for a blank/whitespace-only description', async () => {
    const fake = configureWith(['Alice'], []);

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector<HTMLInputElement>('.todo-widget-tile__add-input');
    const form = compiled.querySelector<HTMLFormElement>('.todo-widget-tile__add');

    input!.value = '   ';
    input!.dispatchEvent(new Event('input'));
    form!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();

    expect(fake.create).not.toHaveBeenCalled();
  });
});
