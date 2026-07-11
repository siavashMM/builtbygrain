import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminAuthService } from './admin-auth.service';

export const adminAuthGuard: CanActivateFn = () => {
  const authService = inject(AdminAuthService);
  const router = inject(Router);

  return authService.isLoggedIn()
    ? true
    : router.createUrlTree(['/admin/login']);
};
