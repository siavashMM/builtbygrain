import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AccountService } from './account.service';

export const accountGuard: CanActivateFn = (_route, state) => {
  const accounts = inject(AccountService);
  const router = inject(Router);
  return accounts.validateSession().pipe(
    map(valid => valid ? true : router.createUrlTree(['/account/sign-in'], { queryParams: { returnUrl: state.url } })),
    catchError(() => {
      accounts.clear();
      return of(router.createUrlTree(['/account/sign-in'], { queryParams: { returnUrl: state.url } }));
    })
  );
};
