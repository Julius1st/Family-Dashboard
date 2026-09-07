import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { WidgetDescriptor } from './widget-descriptor';

/**
 * Fetches the registered widgets from `GET /api/widgets` and exposes them
 * to consumers as a signal, per this repo's "prefer signals over RxJS for
 * component state" convention (see `CLAUDE.md`).
 *
 * An empty array response is a normal, valid state (no widgets registered)
 * rather than an error, mirroring the backend's contract.
 */
@Injectable({ providedIn: 'root' })
export class WidgetService {
  private readonly http = inject(HttpClient);

  /**
   * The current list of widgets. `undefined` until the initial request
   * resolves; `[]` if the backend has no widgets registered.
   */
  readonly widgets: Signal<readonly WidgetDescriptor[] | undefined> = toSignal(
    this.http.get<WidgetDescriptor[]>('/api/widgets'),
  );
}
