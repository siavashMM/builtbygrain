import { Injectable } from '@angular/core';
import { Observable, defer, shareReplay, tap } from 'rxjs';

interface CacheEntry {
  expiresAt: number;
  value: Observable<unknown>;
}

@Injectable({ providedIn: 'root' })
export class CatalogRequestCache {
  private static readonly TTL_MS = 60_000;
  private readonly entries = new Map<string, CacheEntry>();

  get<T>(key: string, request: () => Observable<T>): Observable<T> {
    const current = this.entries.get(key);
    if (current && current.expiresAt > Date.now()) return current.value as Observable<T>;

    const value = defer(request).pipe(
      tap({ error: () => this.entries.delete(key) }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.entries.set(key, { expiresAt: Date.now() + CatalogRequestCache.TTL_MS, value });
    return value;
  }

  invalidate(): void {
    this.entries.clear();
  }
}
