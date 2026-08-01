import { Component, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AccountService, AddressInput, CustomerAddress } from '../account/account.service';
import { CartService } from '../cart/cart.service';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';
import { AddressAutocompleteService, SuggestedAddress } from './address-autocomplete.service';
import { CheckoutService } from './checkout.service';
import { CheckoutStepperComponent } from './checkout-stepper.component';
import { CheckoutSummaryComponent } from './checkout-summary.component';

@Component({
  selector: 'app-checkout-delivery',
  imports: [ReactiveFormsModule, ShopNavigationComponent, CheckoutStepperComponent, CheckoutSummaryComponent],
  templateUrl: './checkout-delivery.component.html',
  styleUrl: './checkout.css',
  encapsulation: ViewEncapsulation.None
})
export class CheckoutDeliveryComponent implements OnInit {
  private readonly accounts = inject(AccountService);
  private readonly checkout = inject(CheckoutService);
  private readonly autocomplete = inject(AddressAutocompleteService);
  private readonly cart = inject(CartService);
  private readonly forms = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly signedIn = signal(false);
  protected readonly addresses = signal<CustomerAddress[]>([]);
  protected readonly selectedAddressId = signal<number | 'new'>('new');
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly autocompleteStatus = signal<'hidden' | 'loading' | 'ready' | 'unavailable'>('hidden');

  protected readonly addressForm = this.forms.nonNullable.group({
    recipientName: ['', [Validators.required, Validators.maxLength(200)]],
    company: ['', Validators.maxLength(200)],
    street: ['', [Validators.required, Validators.maxLength(200)]],
    houseNumber: ['', [Validators.required, Validators.maxLength(30)]],
    addressLine2: ['', Validators.maxLength(200)],
    postalCode: ['', [Validators.required, Validators.maxLength(30)]],
    city: ['', [Validators.required, Validators.maxLength(120)]],
    region: ['', Validators.maxLength(120)],
    countryCode: ['DE', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    phone: ['', [Validators.required, Validators.maxLength(40)]],
    defaultShipping: [true],
    defaultBilling: [true]
  });

  ngOnInit(): void {
    if (this.cart.items().length === 0) {
      void this.router.navigate(['/cart']);
      return;
    }
    const guest = this.route.snapshot.queryParamMap.get('guest') === '1';
    this.accounts.restoreSession().pipe(catchError(() => of(false))).subscribe(valid => {
      this.signedIn.set(valid);
      if (!valid && !guest) {
        void this.router.navigate(['/checkout/account']);
        return;
      }
      if (valid) {
        this.addressForm.patchValue({
          recipientName: `${this.accounts.customer()?.firstName ?? ''} ${this.accounts.customer()?.lastName ?? ''}`.trim()
        });
        this.accounts.addresses().pipe(catchError(() => of([]))).subscribe(addresses => {
          this.addresses.set(addresses);
          const preferred = addresses.find(address => address.defaultShipping) ?? addresses[0];
          if (preferred) this.selectedAddressId.set(preferred.id);
          this.finishLoading();
        });
      } else {
        this.finishLoading();
      }
    });
  }

  protected chooseAddress(id: number | 'new'): void {
    this.selectedAddressId.set(id);
    this.errorMessage.set('');
    if (id === 'new') queueMicrotask(() => this.mountAutocomplete());
  }

  protected continue(): void {
    this.errorMessage.set('');
    if (this.selectedAddressId() !== 'new') {
      void this.router.navigate(['/checkout/payment']);
      return;
    }
    this.addressForm.markAllAsTouched();
    if (this.addressForm.invalid) {
      this.errorMessage.set('Please complete the required delivery details.');
      return;
    }
    const value = this.addressForm.getRawValue();
    const input: AddressInput = {
      ...value,
      company: value.company.trim() || null,
      addressLine2: value.addressLine2.trim() || null,
      region: value.region.trim() || null,
      phone: value.phone.trim(),
      countryCode: value.countryCode.trim().toUpperCase()
    };
    if (!this.signedIn()) {
      this.checkout.rememberGuestAddress(input);
      void this.router.navigate(['/checkout/payment'], { queryParams: { guest: 1 } });
      return;
    }
    this.submitting.set(true);
    this.accounts.addAddress(input).subscribe({
      next: () => void this.router.navigate(['/checkout/payment']),
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('We could not save this address. Please review it and try again.');
      }
    });
  }

  protected addressLine(address: CustomerAddress): string {
    return `${address.street} ${address.houseNumber}, ${address.postalCode} ${address.city}`;
  }

  private finishLoading(): void {
    this.loading.set(false);
    if (this.selectedAddressId() === 'new') queueMicrotask(() => this.mountAutocomplete());
  }

  private mountAutocomplete(): void {
    const container = document.querySelector<HTMLElement>('#checkout-address-autocomplete');
    if (!container || container.childElementCount > 0) return;
    this.checkout.config().pipe(catchError(() => of(null))).subscribe(config => {
      if (!config?.addressAutocompleteEnabled || !config.googleMapsBrowserKey) {
        this.autocompleteStatus.set('unavailable');
        return;
      }
      this.autocompleteStatus.set('loading');
      this.autocomplete.mount(container, config.googleMapsBrowserKey, address => this.applySuggestion(address))
        .then(() => this.autocompleteStatus.set('ready'))
        .catch(() => this.autocompleteStatus.set('unavailable'));
    });
  }

  private applySuggestion(address: SuggestedAddress): void {
    this.addressForm.patchValue(address);
    queueMicrotask(() => document.querySelector<HTMLInputElement>('#delivery-phone')?.focus());
  }
}
