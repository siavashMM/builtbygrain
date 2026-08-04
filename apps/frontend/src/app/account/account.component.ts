import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';
import { AccountService, AddressInput, CustomerAddress, CustomerProfile } from './account.service';

type AccountView = 'overview' | 'details' | 'addresses' | 'security' | 'orders' | 'returns' | 'claims';

@Component({
  selector: 'app-account',
  imports: [ReactiveFormsModule, RouterLink, ShopNavigationComponent],
  templateUrl: './account.component.html',
  styleUrl: './account.css',
  encapsulation: ViewEncapsulation.None
})
export class AccountComponent implements OnInit {
  private readonly accounts = inject(AccountService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly forms = inject(FormBuilder);

  protected readonly view = this.route.snapshot.data['view'] as AccountView;
  protected readonly customer = signal<CustomerProfile | null>(null);
  protected readonly addresses = signal<CustomerAddress[]>([]);
  protected readonly billingAddresses = computed(() => this.addresses().filter(address => address.defaultBilling));
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly passwordSetupSent = signal(false);
  protected readonly editingAddressId = signal<number | null>(null);
  protected readonly showAddressForm = signal(false);

  protected readonly profileForm = this.forms.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    phone: ['', Validators.maxLength(40)],
    locale: ['en', [Validators.required, Validators.maxLength(20)]]
  });

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
    phone: ['', Validators.maxLength(40)],
    defaultShipping: [false],
    defaultBilling: [false]
  });

  protected readonly passwordForm = this.forms.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', Validators.required]
  });

  ngOnInit(): void {
    forkJoin({ customer: this.accounts.profile(), addresses: this.accounts.addresses() }).subscribe({
      next: ({ customer, addresses }) => {
        this.customer.set(customer);
        this.addresses.set(addresses);
        this.profileForm.patchValue({
          firstName: customer.firstName,
          lastName: customer.lastName,
          email: customer.email,
          phone: customer.phone ?? '',
          locale: customer.locale
        });
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('We could not load your account. Please refresh and try again.');
      }
    });
  }

  protected saveProfile(): void {
    this.resetMessages();
    this.profileForm.markAllAsTouched();
    if (this.profileForm.invalid) {
      this.errorMessage.set('Please review the highlighted fields.');
      return;
    }
    this.submitting.set(true);
    const value = this.profileForm.getRawValue();
    this.accounts.updateProfile({ ...value, phone: value.phone.trim() || null }).subscribe({
      next: customer => {
        this.customer.set(customer);
        this.submitting.set(false);
        this.successMessage.set('Your personal details have been saved.');
      },
      error: error => this.fail(error, 'We could not save your details.')
    });
  }

  protected startAddress(address?: CustomerAddress): void {
    this.resetMessages();
    this.editingAddressId.set(address?.id ?? null);
    this.addressForm.reset({
      recipientName: address?.recipientName ?? `${this.customer()?.firstName ?? ''} ${this.customer()?.lastName ?? ''}`.trim(),
      company: address?.company ?? '',
      street: address?.street ?? '',
      houseNumber: address?.houseNumber ?? '',
      addressLine2: address?.addressLine2 ?? '',
      postalCode: address?.postalCode ?? '',
      city: address?.city ?? '',
      region: address?.region ?? '',
      countryCode: address?.countryCode ?? 'DE',
      phone: address?.phone ?? '',
      defaultShipping: address?.defaultShipping ?? this.addresses().length === 0,
      defaultBilling: address?.defaultBilling ?? this.addresses().length === 0
    });
    this.showAddressForm.set(true);
    queueMicrotask(() => document.querySelector<HTMLElement>('#address-form-title')?.focus());
  }

  protected cancelAddress(): void {
    this.showAddressForm.set(false);
    this.editingAddressId.set(null);
    this.addressForm.reset();
  }

  protected saveAddress(): void {
    this.resetMessages();
    this.addressForm.markAllAsTouched();
    if (this.addressForm.invalid) {
      this.errorMessage.set('Please review the highlighted address fields.');
      return;
    }
    this.submitting.set(true);
    const value = this.addressForm.getRawValue();
    const input: AddressInput = {
      ...value,
      company: value.company.trim() || null,
      addressLine2: value.addressLine2.trim() || null,
      region: value.region.trim() || null,
      phone: value.phone.trim() || null,
      countryCode: value.countryCode.trim().toUpperCase()
    };
    const id = this.editingAddressId();
    const request = id === null ? this.accounts.addAddress(input) : this.accounts.updateAddress(id, input);
    request.subscribe({
      next: () => {
        this.submitting.set(false);
        this.showAddressForm.set(false);
        this.reloadAddresses('Your address has been saved.');
      },
      error: error => this.fail(error, 'We could not save this address.')
    });
  }

  protected removeAddress(address: CustomerAddress): void {
    if (!window.confirm(`Delete the address in ${address.city}?`)) return;
    this.resetMessages();
    this.accounts.deleteAddress(address.id).subscribe({
      next: () => this.reloadAddresses('The address has been deleted.'),
      error: error => this.fail(error, 'We could not delete this address.')
    });
  }

  protected chooseDefault(address: CustomerAddress, type: 'shipping' | 'billing'): void {
    this.resetMessages();
    this.accounts.chooseDefault(address.id, type).subscribe({
      next: () => this.reloadAddresses(`Default ${type} address updated.`),
      error: error => this.fail(error, `We could not update the default ${type} address.`)
    });
  }

  protected changePassword(): void {
    this.resetMessages();
    this.passwordForm.markAllAsTouched();
    const value = this.passwordForm.getRawValue();
    if (this.passwordForm.invalid) {
      this.errorMessage.set('Please review the highlighted fields.');
      return;
    }
    if (value.newPassword !== value.confirmPassword) {
      this.errorMessage.set('The new passwords do not match.');
      return;
    }
    this.submitting.set(true);
    this.accounts.changePassword(value.currentPassword, value.newPassword).subscribe({
      next: () => void this.router.navigate(['/account/sign-in'], { queryParams: { reason: 'password-changed' } }),
      error: error => this.fail(error, 'We could not change your password.')
    });
  }

  protected requestPasswordSetup(): void {
    const customer = this.customer();
    if (!customer || customer.passwordSet || this.passwordSetupSent()) return;
    this.resetMessages();
    this.submitting.set(true);
    this.accounts.forgotPassword(customer.email).subscribe({
      next: () => {
        this.submitting.set(false);
        this.passwordSetupSent.set(true);
        this.successMessage.set(`A secure password setup link was sent to ${customer.email}.`);
      },
      error: error => this.fail(error, 'We could not send the password setup email. Please try again.')
    });
  }

  protected logout(): void {
    this.accounts.logout().subscribe({
      next: () => void this.router.navigate(['/account/sign-in']),
      error: () => void this.router.navigate(['/account/sign-in'])
    });
  }

  private reloadAddresses(message: string): void {
    this.accounts.addresses().subscribe({
      next: addresses => {
        this.addresses.set(addresses);
        this.successMessage.set(message);
      },
      error: error => this.fail(error, 'The change was saved, but the address list could not be refreshed.')
    });
  }

  private resetMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  private fail(error: unknown, fallback: string): void {
    this.submitting.set(false);
    const response = error as HttpErrorResponse;
    this.errorMessage.set(response.error?.message || fallback);
  }
}
