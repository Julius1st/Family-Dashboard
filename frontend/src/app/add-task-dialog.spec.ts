import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { AddTaskDialog } from './add-task-dialog';
import { TodoService } from './todo.service';

/**
 * jsdom (this project's test runner runs specs against jsdom via
 * `@angular/build`'s unit-test builder) does not implement
 * `HTMLDialogElement.prototype.showModal`/`.close()` — confirmed directly
 * against the jsdom version pinned in `package.json` before writing these
 * tests. `AddTaskDialog` feature-detects this (see its own doc comment)
 * and falls back to toggling the native `open` IDL attribute directly,
 * which jsdom does implement — so these tests exercise that fallback
 * branch, while a real browser always takes the `showModal`/`close`
 * branch instead. Both branches run the exact same open/close bookkeeping
 * (member scoping, input reset, `close` event), so this is genuine
 * coverage of the component's behavior, not a bypass of it.
 */
describe('AddTaskDialog', () => {
  function configure() {
    const fake = {
      members: vi.fn(),
      items: vi.fn(),
      create: vi.fn(),
      setDone: vi.fn(),
      updateDescription: vi.fn(),
      remove: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [AddTaskDialog],
      providers: [{ provide: TodoService, useValue: fake }],
    });
    return fake;
  }

  async function createFixture(): Promise<ComponentFixture<AddTaskDialog>> {
    const fixture = TestBed.createComponent(AddTaskDialog);
    await fixture.whenStable();
    return fixture;
  }

  function dialogEl(fixture: ComponentFixture<AddTaskDialog>): HTMLDialogElement {
    return (fixture.nativeElement as HTMLElement).querySelector('dialog') as HTMLDialogElement;
  }

  function inputEl(fixture: ComponentFixture<AddTaskDialog>): HTMLInputElement {
    return (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;
  }

  function submitButton(fixture: ComponentFixture<AddTaskDialog>): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  function cancelButton(fixture: ComponentFixture<AddTaskDialog>): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.add-task-dialog__cancel') as HTMLButtonElement;
  }

  it('is closed until opened', async () => {
    configure();
    const fixture = await createFixture();

    expect(dialogEl(fixture).open).toBe(false);
  });

  it('opens scoped to the given member, with no member picker rendered', async () => {
    configure();
    const fixture = await createFixture();

    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    expect(dialogEl(fixture).open).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Alice');
    expect((fixture.nativeElement as HTMLElement).querySelector('select')).toBeNull();
  });

  it('submitting a non-blank description calls TodoService.create for the scoped member and closes', async () => {
    const fake = configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Bob');
    await fixture.whenStable();

    inputEl(fixture).value = '  Rasen mähen  ';
    submitButton(fixture).click();
    await fixture.whenStable();

    expect(fake.create).toHaveBeenCalledExactlyOnceWith('Bob', 'Rasen mähen');
    expect(dialogEl(fixture).open).toBe(false);
  });

  it('resets the input after a successful submit so the next open starts blank', async () => {
    configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Bob');
    await fixture.whenStable();

    inputEl(fixture).value = 'Rasen mähen';
    submitButton(fixture).click();
    await fixture.whenStable();

    expect(inputEl(fixture).value).toBe('');
  });

  it('does not submit on blank input', async () => {
    const fake = configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    inputEl(fixture).value = '';
    submitButton(fixture).click();
    await fixture.whenStable();

    expect(fake.create).not.toHaveBeenCalled();
    expect(dialogEl(fixture).open).toBe(true);
  });

  it('does not submit on whitespace-only input', async () => {
    const fake = configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    inputEl(fixture).value = '   ';
    submitButton(fixture).click();
    await fixture.whenStable();

    expect(fake.create).not.toHaveBeenCalled();
    expect(dialogEl(fixture).open).toBe(true);
  });

  it('cancel button closes the dialog without creating anything', async () => {
    const fake = configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    inputEl(fixture).value = 'Something typed but not submitted';
    cancelButton(fixture).click();
    await fixture.whenStable();

    expect(fake.create).not.toHaveBeenCalled();
    expect(dialogEl(fixture).open).toBe(false);
    expect(inputEl(fixture).value).toBe('');
  });

  it('clicking the dialog element itself (outside the panel) dismisses it without creating anything', async () => {
    const fake = configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    const dialog = dialogEl(fixture);
    dialog.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    // jsdom's `Event.target` for a directly dispatched event on `dialog`
    // is `dialog` itself, matching a real "clicked the backdrop area"
    // click for the component's `event.target === dialogRef` check.
    await fixture.whenStable();

    expect(fake.create).not.toHaveBeenCalled();
    expect(dialog.open).toBe(false);
  });

  it('meets the >=44px touch target baseline for its own cancel/submit buttons', async () => {
    configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    expect(getComputedStyle(cancelButton(fixture)).minHeight).toBe('48px');
    expect(getComputedStyle(submitButton(fixture)).minHeight).toBe('48px');
  });

  it('the submit button text genuinely resolves via --ink-on-pill-active, not --ink-on-accent', async () => {
    // Regression test: the submit button fills its background with
    // `--ink-primary`, which inverts between themes (near-white in dark
    // mode, near-black in light mode). It used to pair that with
    // `--ink-on-accent`, a token deliberately fixed at a single dark value
    // in both themes (correct only for `.tasks-page__checkmark`'s
    // fixed-lightness member-color background) — so in light mode the
    // button's background flipped to near-black while the text stayed
    // fixed-dark, reading as illegible near-black-on-near-black. Same
    // pairing bug the header's active nav pill already had to solve (see
    // `header.spec.ts`'s equivalent test) — the fix is the same token,
    // `--ink-on-pill-active`, which does invert with the theme.
    configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    // jsdom doesn't substitute var() inside standard properties like
    // `color`, so getComputedStyle hands back the literal declaration —
    // which is exactly what lets this assert on the *token name* used,
    // not just a resolved color value that could match either token.
    const color = getComputedStyle(submitButton(fixture)).color;
    expect(color).toContain('var(--ink-on-pill-active)');
    expect(color).not.toContain('var(--ink-on-accent)');
  });

  it('leaves the cancel button unaffected: still --ink-secondary, untouched by the submit-button fix', async () => {
    configure();
    const fixture = await createFixture();
    fixture.componentInstance.open('Alice');
    await fixture.whenStable();

    const color = getComputedStyle(cancelButton(fixture)).color;
    expect(color).toContain('var(--ink-secondary)');
  });
});
