import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminAuthService } from './admin-auth.service';

type PasswordChangeState = 'idle' | 'submitting' | 'error';

@Component({
  selector: 'app-admin-settings',
  imports: [],
  template: `
    <section class="admin-route-page admin-settings-page">
      <header class="admin-page-heading">
        <div>
          <p class="admin-kicker">Workspace security</p>
          <h1>Settings</h1>
          <p>Manage access to the Built by Grain administration.</p>
        </div>
      </header>

      <article class="admin-card security-settings-card">
        <header class="card-heading">
          <div>
            <h2>Change password</h2>
            <p>Changing your password signs out every active session, including this one.</p>
          </div>
        </header>

        <form class="admin-form password-change-form" (submit)="changePassword($event)">
          <label>
            Current password
            <input
              name="currentPassword"
              type="password"
              autocomplete="current-password"
              required
              [disabled]="state() === 'submitting'"
              [value]="currentPassword()"
              (input)="currentPassword.set($any($event.target).value)"
            >
          </label>

          <label>
            New password
            <input
              name="newPassword"
              type="password"
              autocomplete="new-password"
              required
              minlength="12"
              [disabled]="state() === 'submitting'"
              [value]="newPassword()"
              (input)="newPassword.set($any($event.target).value)"
            >
          </label>
          <p class="field-help">Use at least 12 characters and do not reuse your current password.</p>

          <label>
            Confirm new password
            <input
              name="newPasswordConfirmation"
              type="password"
              autocomplete="new-password"
              required
              minlength="12"
              [disabled]="state() === 'submitting'"
              [value]="newPasswordConfirmation()"
              (input)="newPasswordConfirmation.set($any($event.target).value)"
            >
          </label>

          @if (errorMessage()) {
            <div class="admin-feedback error" role="alert">
              <span aria-hidden="true">!</span><p>{{ errorMessage() }}</p>
            </div>
          }

          <div class="admin-actions">
            <button class="admin-primary-button" type="submit" [disabled]="state() === 'submitting'">
              {{ state() === 'submitting' ? 'Changing password…' : 'Change password' }}
            </button>
          </div>
        </form>
      </article>
    </section>
  `
})
export class AdminSettingsComponent {
  private readonly auth = inject(AdminAuthService);
  private readonly router = inject(Router);

  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly newPasswordConfirmation = signal('');
  protected readonly state = signal<PasswordChangeState>('idle');
  protected readonly errorMessage = signal('');

  protected changePassword(event: SubmitEvent): void {
    event.preventDefault();
    this.errorMessage.set('');

    if (this.newPassword().length < 12) {
      this.fail('The new password must contain at least 12 characters.');
      return;
    }
    if (this.newPassword() !== this.newPasswordConfirmation()) {
      this.fail('The new passwords do not match.');
      return;
    }
    if (this.currentPassword() === this.newPassword()) {
      this.fail('The new password must be different from the current password.');
      return;
    }

    this.state.set('submitting');
    this.auth.changePassword(this.currentPassword(), this.newPassword()).subscribe({
      next: () => void this.router.navigate(['/admin/login'], {
        queryParams: { reason: 'password-changed' }
      }),
      error: error => this.fail(this.apiErrorMessage(error))
    });
  }

  private fail(message: string): void {
    this.state.set('error');
    this.errorMessage.set(message);
  }

  private apiErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
      return error.error.message;
    }
    return 'The password could not be changed. Please try again.';
  }
}
