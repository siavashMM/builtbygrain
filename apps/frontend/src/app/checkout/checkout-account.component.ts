import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AccountService } from '../account/account.service';
import { CartService } from '../cart/cart.service';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';
import { CheckoutConfig, CheckoutService } from './checkout.service';
import { CheckoutStepperComponent } from './checkout-stepper.component';
import { CheckoutSummaryComponent } from './checkout-summary.component';

type AccountMode = 'create' | 'sign-in';

@Component({
  selector: 'app-checkout-account',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ShopNavigationComponent,
    CheckoutStepperComponent,
    CheckoutSummaryComponent
  ],
  templateUrl: './checkout-account.component.html',
  styleUrl: './checkout.css',
  encapsulation: ViewEncapsulation.None
})
export class CheckoutAccountComponent implements OnInit {
  private readonly accounts = inject(AccountService);
  private readonly checkout = inject(CheckoutService);
  private readonly cart = inject(CartService);
  private readonly forms = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly mode = signal<AccountMode>('create');
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly config = signal<CheckoutConfig | null>(null);
  protected readonly customer = this.accounts.customer;

  protected readonly registerForm = this.forms.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', Validators.required]
  });

  protected readonly loginForm = this.forms.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.maxLength(128)]]
  });

  ngOnInit(): void {
    if (this.cart.items().length === 0) {
      void this.router.navigate(['/cart']);
      return;
    }
    if (this.route.snapshot.queryParamMap.has('socialError')) {
      this.errorMessage.set('Social sign-in could not be completed. Please try again or use email and password.');
    }
    this.accounts.restoreSession().pipe(catchError(() => of(false))).subscribe(() => {
      this.checkout.config().pipe(catchError(() => of(null))).subscribe(config => {
        this.config.set(config);
        this.loading.set(false);
      });
    });
  }

  protected selectMode(mode: AccountMode): void {
    this.mode.set(mode);
    this.errorMessage.set('');
  }

  protected register(): void {
    this.errorMessage.set('');
    this.registerForm.markAllAsTouched();
    const value = this.registerForm.getRawValue();
    if (this.registerForm.invalid) {
      this.errorMessage.set('Please review the highlighted fields.');
      return;
    }
    if (value.password !== value.confirmPassword) {
      this.errorMessage.set('The passwords do not match.');
      return;
    }
    this.submitting.set(true);
    this.accounts.register({
      email: value.email.trim(),
      password: value.password,
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      locale: 'en',
      returnUrl: '/checkout/delivery'
    }).subscribe({
      next: response => void this.router.navigateByUrl(response.returnUrl),
      error: error => this.fail(error, 'We could not create your account.')
    });
  }

  protected signIn(): void {
    this.errorMessage.set('');
    this.loginForm.markAllAsTouched();
    if (this.loginForm.invalid) {
      this.errorMessage.set('Enter your email address and password.');
      return;
    }
    this.submitting.set(true);
    const value = this.loginForm.getRawValue();
    this.accounts.login(value.email.trim(), value.password, '/checkout/delivery').subscribe({
      next: response => void this.router.navigateByUrl(response.returnUrl),
      error: error => this.fail(error, 'Email or password is incorrect.')
    });
  }

  protected continueSignedIn(): void {
    void this.router.navigate(['/checkout/delivery']);
  }

  protected continueAsGuest(): void {
    void this.router.navigate(['/checkout/delivery'], { queryParams: { guest: 1 } });
  }

  protected socialUrl(provider: 'google' | 'apple'): string {
    return `/api/account/auth/social/${provider}?returnUrl=${encodeURIComponent('/checkout/delivery')}`;
  }

  private fail(error: unknown, fallback: string): void {
    this.submitting.set(false);
    const response = error as HttpErrorResponse;
    this.errorMessage.set(response.error?.message || response.error?.error || fallback);
  }
}
