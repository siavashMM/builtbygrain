import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';
import { AccountService } from './account.service';

type AuthMode = 'sign-in' | 'register' | 'forgot-password' | 'reset-password';

@Component({
  selector: 'app-account-auth',
  imports: [ReactiveFormsModule, RouterLink, ShopNavigationComponent],
  templateUrl: './account-auth.component.html',
  styleUrl: './account-auth.css',
  encapsulation: ViewEncapsulation.None
})
export class AccountAuthComponent implements OnInit {
  private readonly accounts = inject(AccountService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly forms = inject(FormBuilder);

  protected readonly mode = this.route.snapshot.data['mode'] as AuthMode;
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal(
    this.route.snapshot.queryParamMap.has('socialError')
      ? 'Social sign-in could not be completed. Please try the same provider again.'
      : ''
  );
  protected readonly socialProviders = signal({ google: false, apple: false });
  protected readonly successMessage = signal(
    this.route.snapshot.queryParamMap.get('reason') === 'password-changed'
      ? 'Your password was changed. Sign in again with your new password.'
      : ''
  );

  protected readonly signInForm = this.forms.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.maxLength(128)]]
  });

  protected readonly registerForm = this.forms.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', Validators.required]
  });

  protected readonly emailForm = this.forms.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]]
  });

  protected readonly resetForm = this.forms.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', Validators.required]
  });

  ngOnInit(): void {
    if (this.mode !== 'sign-in') return;
    this.accounts.availableSocialProviders().pipe(catchError(() => of(null))).subscribe(providers => {
      if (providers) this.socialProviders.set(providers);
    });
  }

  protected submit(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    if (this.mode === 'sign-in') this.signIn();
    if (this.mode === 'register') this.register();
    if (this.mode === 'forgot-password') this.forgotPassword();
    if (this.mode === 'reset-password') this.resetPassword();
  }

  private signIn(): void {
    if (this.invalid(this.signInForm)) return;
    this.submitting.set(true);
    const { email, password } = this.signInForm.getRawValue();
    this.accounts.login(email.trim(), password, this.returnUrl()).subscribe({
      next: response => void this.router.navigateByUrl(response.returnUrl),
      error: error => this.fail(error, 'We could not sign you in. Check your email and password.')
    });
  }

  private register(): void {
    if (this.invalid(this.registerForm)) return;
    const value = this.registerForm.getRawValue();
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
      returnUrl: this.returnUrl()
    }).subscribe({
      next: response => void this.router.navigateByUrl(response.returnUrl),
      error: error => this.fail(error, 'We could not create your account.')
    });
  }

  private forgotPassword(): void {
    if (this.invalid(this.emailForm)) return;
    this.submitting.set(true);
    this.accounts.forgotPassword(this.emailForm.getRawValue().email.trim()).subscribe({
      next: response => {
        this.submitting.set(false);
        this.successMessage.set(response.message);
      },
      error: error => this.fail(error, 'We could not process the request. Please try again.')
    });
  }

  private resetPassword(): void {
    if (this.invalid(this.resetForm)) return;
    const value = this.resetForm.getRawValue();
    if (value.newPassword !== value.confirmPassword) {
      this.errorMessage.set('The passwords do not match.');
      return;
    }
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.errorMessage.set('This reset link is incomplete. Request a new one.');
      return;
    }
    this.submitting.set(true);
    this.accounts.resetPassword(token, value.newPassword).subscribe({
      next: response => {
        this.submitting.set(false);
        this.successMessage.set(response.message);
        this.resetForm.reset();
      },
      error: error => this.fail(error, 'This reset link is invalid or has expired.')
    });
  }

  protected returnUrl(): string | null {
    return this.route.snapshot.queryParamMap.get('returnUrl');
  }

  protected socialUrl(provider: 'google' | 'apple'): string {
    const destination = this.returnUrl() || '/account';
    return `/api/account/auth/social/${provider}?returnUrl=${encodeURIComponent(destination)}`;
  }

  private invalid(form: typeof this.signInForm | typeof this.registerForm | typeof this.emailForm | typeof this.resetForm):
    boolean {
    form.markAllAsTouched();
    if (form.invalid) {
      this.errorMessage.set('Please review the highlighted fields.');
      return true;
    }
    return false;
  }

  private fail(error: unknown, fallback: string): void {
    this.submitting.set(false);
    const response = error as HttpErrorResponse;
    this.errorMessage.set(response.error?.message || response.error?.error || fallback);
  }
}
