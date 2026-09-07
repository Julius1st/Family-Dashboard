import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TodoItem } from './todo-item';
import { TodoService } from './todo.service';

describe('TodoService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(items: TodoItem[], members: string[]): void {
    httpMock.expectOne('/api/todos').flush(items);
    httpMock.expectOne('/api/todos/members').flush(members);
  }

  describe('initial fetch', () => {
    it('exposes a typed list of items and members as signals after a successful fetch', () => {
      const service = TestBed.inject(TodoService);
      const items: TodoItem[] = [
        { id: 1, householdMember: 'Alice', description: 'Buy milk', done: false },
        { id: 2, householdMember: 'Bob', description: 'Walk dog', done: true },
      ];
      const members = ['Alice', 'Bob'];

      flushInitialRequests(items, members);

      expect(service.items()).toEqual(items);
      expect(service.members()).toEqual(members);
    });

    it('treats an empty items array as a normal empty state, not an error', () => {
      const service = TestBed.inject(TodoService);

      flushInitialRequests([], ['Alice', 'Bob']);

      expect(service.items()).toEqual([]);
    });

    it('treats an empty members array as a normal empty state, not an error', () => {
      const service = TestBed.inject(TodoService);

      flushInitialRequests([{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }], []);

      expect(service.members()).toEqual([]);
    });

    it('starts undefined before either request resolves', () => {
      const service = TestBed.inject(TodoService);

      expect(service.items()).toBeUndefined();
      expect(service.members()).toBeUndefined();

      flushInitialRequests([], []);
    });
  });

  describe('create', () => {
    it('POSTs the new item and appends the response to items', () => {
      const service = TestBed.inject(TodoService);
      flushInitialRequests([{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: false }], ['Alice']);

      service.create('Alice', 'Walk dog');

      const req = httpMock.expectOne('/api/todos');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ householdMember: 'Alice', description: 'Walk dog' });

      const created: TodoItem = { id: 2, householdMember: 'Alice', description: 'Walk dog', done: false };
      req.flush(created);

      expect(service.items()).toEqual([
        { id: 1, householdMember: 'Alice', description: 'Buy milk', done: false },
        created,
      ]);
    });

    it('appends to an empty items list', () => {
      const service = TestBed.inject(TodoService);
      flushInitialRequests([], ['Alice']);

      service.create('Alice', 'Walk dog');

      const created: TodoItem = { id: 1, householdMember: 'Alice', description: 'Walk dog', done: false };
      httpMock.expectOne('/api/todos').flush(created);

      expect(service.items()).toEqual([created]);
    });
  });

  describe('setDone', () => {
    it('PUTs the full body (current description + new done) and updates items from the response', () => {
      const service = TestBed.inject(TodoService);
      flushInitialRequests(
        [
          { id: 1, householdMember: 'Alice', description: 'Buy milk', done: false },
          { id: 2, householdMember: 'Bob', description: 'Walk dog', done: false },
        ],
        ['Alice', 'Bob'],
      );

      service.setDone(1, true);

      const req = httpMock.expectOne('/api/todos/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ description: 'Buy milk', done: true });

      const updated: TodoItem = { id: 1, householdMember: 'Alice', description: 'Buy milk', done: true };
      req.flush(updated);

      expect(service.items()).toEqual([
        updated,
        { id: 2, householdMember: 'Bob', description: 'Walk dog', done: false },
      ]);
    });
  });

  describe('updateDescription', () => {
    it('PUTs the full body (new description + current done) and updates items from the response', () => {
      const service = TestBed.inject(TodoService);
      flushInitialRequests([{ id: 1, householdMember: 'Alice', description: 'Buy milk', done: true }], ['Alice']);

      service.updateDescription(1, 'Buy oat milk');

      const req = httpMock.expectOne('/api/todos/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ description: 'Buy oat milk', done: true });

      const updated: TodoItem = { id: 1, householdMember: 'Alice', description: 'Buy oat milk', done: true };
      req.flush(updated);

      expect(service.items()).toEqual([updated]);
    });
  });

  describe('remove', () => {
    it('DELETEs the item and removes it from items on success', () => {
      const service = TestBed.inject(TodoService);
      flushInitialRequests(
        [
          { id: 1, householdMember: 'Alice', description: 'Buy milk', done: false },
          { id: 2, householdMember: 'Bob', description: 'Walk dog', done: false },
        ],
        ['Alice', 'Bob'],
      );

      service.remove(1);

      const req = httpMock.expectOne('/api/todos/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(service.items()).toEqual([{ id: 2, householdMember: 'Bob', description: 'Walk dog', done: false }]);
    });
  });
});
