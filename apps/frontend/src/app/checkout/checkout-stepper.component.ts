import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-checkout-stepper',
  imports: [RouterLink],
  template: `
    <nav class="checkout-progress checkout-flow-progress" aria-label="Checkout progress">
      <ol>
        <li [class.active]="currentStep() === 1" [class.complete]="currentStep() > 1"
          [attr.aria-current]="currentStep() === 1 ? 'step' : null">
          @if (currentStep() > 1) {
            <a routerLink="/cart" class="step-icon" aria-label="Return to shopping bag">✓</a>
          } @else {
            <span class="step-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><path d="M5 8.5h14l-1 12H6l-1-12Z"/><path d="M9 9V6a3 3 0 0 1 6 0v3"/></svg>
            </span>
          }
          <span><small>Step 1</small>Bag</span>
        </li>
        <li [class.active]="currentStep() === 2" [class.complete]="currentStep() > 2"
          [attr.aria-current]="currentStep() === 2 ? 'step' : null">
          @if (currentStep() > 2) {
            <a routerLink="/checkout/account" class="step-icon" aria-label="Return to account step">✓</a>
          } @else {
            <span class="step-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.5"/><path d="M5.5 20a6.5 6.5 0 0 1 13 0"/></svg>
            </span>
          }
          <span><small>Step 2</small>Account</span>
        </li>
        <li [class.active]="currentStep() === 3" [class.complete]="currentStep() > 3"
          [attr.aria-current]="currentStep() === 3 ? 'step' : null">
          @if (currentStep() > 3) {
            <a routerLink="/checkout/delivery" [queryParams]="guest() ? { guest: 1 } : null"
              class="step-icon" aria-label="Return to delivery step">✓</a>
          } @else {
            <span class="step-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><path d="M3 6h11v11H3zM14 10h4l3 3v4h-7z"/><circle cx="7" cy="18" r="2"/><circle cx="18" cy="18" r="2"/></svg>
            </span>
          }
          <span><small>Step 3</small>Delivery</span>
        </li>
        <li [class.active]="currentStep() === 4" [attr.aria-current]="currentStep() === 4 ? 'step' : null">
          <span class="step-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="1"/><path d="M3 10h18"/></svg>
          </span>
          <span><small>Step 4</small>Payment</span>
        </li>
      </ol>
    </nav>
  `
})
export class CheckoutStepperComponent {
  readonly currentStep = input.required<number>();
  readonly guest = input(false);
}
