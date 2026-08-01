import { Component, ViewEncapsulation, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';
import { CheckoutStepperComponent } from './checkout-stepper.component';
import { CheckoutSummaryComponent } from './checkout-summary.component';

@Component({
  selector: 'app-checkout-payment',
  imports: [RouterLink, ShopNavigationComponent, CheckoutStepperComponent, CheckoutSummaryComponent],
  template: `
    <main id="main-content" class="checkout-page" tabindex="-1">
      <app-shop-navigation />
      <app-checkout-stepper [currentStep]="4" [guest]="guest" />
      <section class="checkout-heading">
        <div><p class="checkout-eyebrow">Final step</p><h1>Payment</h1></div>
        <p>Your account and delivery onboarding are complete.</p>
      </section>
      <div class="checkout-layout">
        <section class="checkout-main-card payment-placeholder">
          <span aria-hidden="true">◇</span>
          <p class="checkout-eyebrow">Secure payments</p>
          <h2>Payment processing is the next integration.</h2>
          <p>No payment details are collected yet. Server-validated pricing and a payment provider must be connected before orders can be placed safely.</p>
          <a routerLink="/checkout/delivery" class="checkout-secondary">Return to delivery</a>
        </section>
        <app-checkout-summary />
      </div>
    </main>
  `,
  styleUrl: './checkout.css',
  encapsulation: ViewEncapsulation.None
})
export class CheckoutPaymentComponent {
  private readonly route = inject(ActivatedRoute);
  protected readonly guest = this.route.snapshot.queryParamMap.get('guest') === '1';
}
