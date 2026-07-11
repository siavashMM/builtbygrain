import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminAuthService } from './admin-auth.service';

type LoginState = 'idle' | 'submitting' | 'error';

@Component({
  selector: 'app-admin-login',
  imports: [],
  templateUrl: './admin-login.component.html'
})
export class AdminLoginComponent {
  private readonly authService = inject(AdminAuthService);
  private readonly router = inject(Router);

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly state = signal<LoginState>('idle');
  protected readonly errorMessage = signal('');

  protected login(event: SubmitEvent): void {
    event.preventDefault();

    this.state.set('submitting');
    this.errorMessage.set('');

    this.authService.login(this.username().trim(), this.password()).subscribe({
      next: () => {
        void this.router.navigate(['/admin/products']);
      },
      error: () => {
        this.state.set('error');
        this.errorMessage.set('Admin username or password is wrong.');
      }
    });
  }
}
