import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { AddressAutocompleteComponent } from './address-autocomplete.component';
import {
  AddressAutocompleteService,
  AddressAutocompleteSession,
  AddressPrediction,
  SuggestedAddress
} from './address-autocomplete.service';
import { CheckoutService } from './checkout.service';

describe('AddressAutocompleteComponent', () => {
  it('shows suggestions in the Street field and supports keyboard selection', fakeAsync(() => {
    const prediction: AddressPrediction = {
      id: 'place-1',
      primaryText: 'Berliner Straße 12',
      secondaryText: '10715 Berlin, Germany'
    };
    const address: SuggestedAddress = {
      street: 'Berliner Straße',
      houseNumber: '12',
      postalCode: '10715',
      city: 'Berlin',
      region: 'Berlin',
      countryCode: 'DE'
    };
    const session: AddressAutocompleteSession = {
      suggest: jasmine.createSpy('suggest').and.resolveTo([prediction]),
      select: jasmine.createSpy('select').and.resolveTo(address)
    };
    const autocomplete = {
      createSession: jasmine.createSpy('createSession').and.resolveTo(session)
    };
    const fixture = createComponent(autocomplete, true);
    const changed = jasmine.createSpy('changed');
    const emitted = jasmine.createSpy('addressSelected');
    fixture.componentInstance.registerOnChange(changed);
    fixture.componentInstance.addressSelected.subscribe(emitted);
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;

    input.dispatchEvent(new Event('focus'));
    input.value = 'BE';
    input.dispatchEvent(new Event('input'));
    tick(250);
    flushMicrotasks();
    fixture.detectChanges();

    expect(autocomplete.createSession).toHaveBeenCalledWith('restricted-browser-key');
    expect(session.suggest).toHaveBeenCalledWith('BE');
    expect(fixture.nativeElement.querySelector('.address-suggestion-option')?.textContent)
      .toContain('Berliner Straße 12');
    expect(fixture.nativeElement.querySelector('.address-suggestion-attribution')?.textContent)
      .toContain('Google Maps');

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    flushMicrotasks();
    fixture.detectChanges();

    expect(session.select).toHaveBeenCalledWith(prediction);
    expect(changed).toHaveBeenCalledWith('BE');
    expect(changed).toHaveBeenCalledWith('Berliner Straße');
    expect(emitted).toHaveBeenCalledWith(address);
    expect(input.value).toBe('Berliner Straße');
    expect(fixture.nativeElement.querySelector('.address-suggestion-list')).toBeNull();
  }));

  it('keeps the Street field usable when suggestions are not configured', fakeAsync(() => {
    const autocomplete = { createSession: jasmine.createSpy('createSession') };
    const fixture = createComponent(autocomplete, false);
    const changed = jasmine.createSpy('changed');
    fixture.componentInstance.registerOnChange(changed);
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;

    input.value = 'BE';
    input.dispatchEvent(new Event('input'));
    tick(250);
    flushMicrotasks();
    fixture.detectChanges();

    expect(autocomplete.createSession).not.toHaveBeenCalled();
    expect(changed).toHaveBeenCalledWith('BE');
    expect(input.value).toBe('BE');
    expect(fixture.nativeElement.textContent).toContain('Enter the street manually.');
  }));

  function createComponent(
    autocomplete: Pick<AddressAutocompleteService, 'createSession'>,
    enabled: boolean
  ): ComponentFixture<AddressAutocompleteComponent> {
    TestBed.configureTestingModule({
      imports: [AddressAutocompleteComponent],
      providers: [
        { provide: AddressAutocompleteService, useValue: autocomplete },
        {
          provide: CheckoutService,
          useValue: {
            config: () => of({
              socialProviders: { google: false, apple: false },
              addressAutocompleteEnabled: enabled,
              googleMapsBrowserKey: enabled ? 'restricted-browser-key' : null
            })
          }
        }
      ]
    });
    const fixture = TestBed.createComponent(AddressAutocompleteComponent);
    fixture.detectChanges();
    return fixture;
  }
});
