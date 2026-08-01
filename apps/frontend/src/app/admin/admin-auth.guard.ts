import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AdminAuthService } from './admin-auth.service';

export const adminAuthGuard: CanActivateFn = () => {
  const authService = inject(AdminAuthService);
  const router = inject(Router);

  return authService.validateSession().pipe(
    map((valid) => {
      if (valid) return true;
      authService.clear();
      return router.createUrlTree(['/admin/login']);
    }),
    catchError(() => {
      authService.clear();
      return of(router.createUrlTree(['/admin/login']));
    })
  );
};
