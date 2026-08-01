import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { AddressInput } from '../account/account.service';

export interface CheckoutConfig {
  socialProviders: {
    google: boolean;
    apple: boolean;
  };
  addressAutocompleteEnabled: boolean;
  googleMapsBrowserKey: string | null;
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private readonly configRequest = this.http.get<CheckoutConfig>('/api/public/checkout/config').pipe(
    shareReplay({ bufferSize: 1, refCount: false })
  );
  private readonly guestAddress = signal<AddressInput | null>(null);

  readonly draftGuestAddress = this.guestAddress.asReadonly();

  config(): Observable<CheckoutConfig> {
    return this.configRequest;
  }

  rememberGuestAddress(address: AddressInput): void {
    this.guestAddress.set(address);
  }
}
