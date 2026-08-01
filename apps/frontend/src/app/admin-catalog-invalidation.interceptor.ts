import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { CatalogRequestCache } from './catalog-request-cache.service';

const READ_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export const adminCatalogInvalidationInterceptor: HttpInterceptorFn = (request, next) => {
  const cache = inject(CatalogRequestCache);
  const invalidatesCatalog = request.url.startsWith('/api/admin/') && !READ_METHODS.has(request.method);
  return next(request).pipe(tap(event => {
    if (invalidatesCatalog && event instanceof HttpResponse && event.ok) cache.invalidate();
  }));
};
