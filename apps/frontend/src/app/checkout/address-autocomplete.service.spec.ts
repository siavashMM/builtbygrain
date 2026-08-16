import { TestBed } from '@angular/core/testing';
import { AddressAutocompleteService } from './address-autocomplete.service';

describe('AddressAutocompleteService', () => {
  const browserWindow = window as typeof window & { google?: unknown };
  let originalGoogle: unknown;

  beforeEach(() => {
    originalGoogle = browserWindow.google;
  });

  afterEach(() => {
    if (originalGoogle === undefined) {
      delete browserWindow.google;
    } else {
      browserWindow.google = originalGoogle;
    }
  });

  it('gets German street suggestions and maps the selected address', async () => {
    class AutocompleteSessionToken {}
    const fetchFields = jasmine.createSpy('fetchFields').and.resolveTo(undefined);
    const place = {
      fetchFields,
      addressComponents: [
        component('route', 'Torstraße', 'Torstraße'),
        component('street_number', '12', '12'),
        component('postal_code', '10119', '10119'),
        component('locality', 'Berlin', 'Berlin'),
        component('administrative_area_level_1', 'Berlin', 'BE'),
        component('country', 'Germany', 'DE')
      ]
    };
    const placePrediction = {
      placeId: 'place-1',
      mainText: { toString: () => 'Torstraße 12' },
      secondaryText: { toString: () => '10119 Berlin, Germany' },
      toPlace: () => place
    };
    const fetchAutocompleteSuggestions = jasmine.createSpy('fetchAutocompleteSuggestions').and.resolveTo({
      suggestions: [{ placePrediction }]
    });
    browserWindow.google = {
      maps: {
        importLibrary: jasmine.createSpy('importLibrary').and.resolveTo({
          AutocompleteSessionToken,
          AutocompleteSuggestion: { fetchAutocompleteSuggestions }
        })
      }
    };
    const service = TestBed.inject(AddressAutocompleteService);

    const session = await service.createSession('browser-key');
    const suggestions = await session.suggest('BE');

    expect(fetchAutocompleteSuggestions).toHaveBeenCalledWith({
      input: 'BE',
      includedPrimaryTypes: ['street_address', 'route'],
      includedRegionCodes: ['de'],
      language: 'de',
      region: 'de',
      sessionToken: jasmine.any(AutocompleteSessionToken)
    });
    expect(suggestions).toEqual([{
      id: 'place-1-0',
      primaryText: 'Torstraße 12',
      secondaryText: '10119 Berlin, Germany'
    }]);

    const selected = await session.select(suggestions[0]);

    expect(fetchFields).toHaveBeenCalledWith({ fields: ['addressComponents'] });
    expect(selected).toEqual({
      street: 'Torstraße',
      houseNumber: '12',
      postalCode: '10119',
      city: 'Berlin',
      region: 'Berlin',
      countryCode: 'DE'
    });
  });
});

function component(type: string, longText: string, shortText: string) {
  return { types: [type], longText, shortText };
}
