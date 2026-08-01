import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

export interface SuggestedAddress {
  street: string;
  houseNumber: string;
  postalCode: string;
  city: string;
  region: string;
  countryCode: string;
}

@Injectable({ providedIn: 'root' })
export class AddressAutocompleteService {
  private readonly document = inject(DOCUMENT);
  private loader: Promise<void> | null = null;

  async mount(
    container: HTMLElement,
    apiKey: string,
    onAddress: (address: SuggestedAddress) => void
  ): Promise<void> {
    await this.load(apiKey);
    const maps = (this.document.defaultView as any)?.google?.maps;
    if (!maps) throw new Error('Google Maps did not load');
    const { PlaceAutocompleteElement } = await maps.importLibrary('places');
    const autocomplete = new PlaceAutocompleteElement({
      componentRestrictions: { country: ['de'] },
      includedPrimaryTypes: ['street_address', 'premise', 'subpremise']
    });
    autocomplete.setAttribute('aria-label', 'Search for your delivery address');
    autocomplete.setAttribute('placeholder', 'Start typing your street and house number');
    autocomplete.addEventListener('gmp-select', async (event: Event) => {
      const prediction = (event as CustomEvent).detail?.placePrediction
        ?? (event as any).placePrediction;
      if (!prediction) return;
      const place = prediction.toPlace();
      await place.fetchFields({ fields: ['addressComponents'] });
      onAddress(this.mapComponents(place.addressComponents ?? []));
    });
    container.replaceChildren(autocomplete);
  }

  private load(apiKey: string): Promise<void> {
    const view = this.document.defaultView as any;
    if (view?.google?.maps?.importLibrary) return Promise.resolve();
    if (this.loader) return this.loader;
    this.loader = new Promise<void>((resolve, reject) => {
      const callback = `bbgGoogleMapsReady${Date.now()}`;
      view[callback] = () => {
        delete view[callback];
        resolve();
      };
      const script = this.document.createElement('script');
      script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&loading=async&libraries=places&v=weekly&callback=${callback}`;
      script.async = true;
      script.onerror = () => {
        delete view[callback];
        this.loader = null;
        reject(new Error('Address suggestions could not be loaded'));
      };
      this.document.head.appendChild(script);
    });
    return this.loader;
  }

  private mapComponents(components: Array<{ longText: string; shortText: string; types: string[] }>): SuggestedAddress {
    const value = (type: string, short = false): string => {
      const component = components.find(candidate => candidate.types.includes(type));
      return component ? (short ? component.shortText : component.longText) : '';
    };
    return {
      street: value('route'),
      houseNumber: value('street_number'),
      postalCode: value('postal_code'),
      city: value('locality') || value('postal_town') || value('sublocality_level_1'),
      region: value('administrative_area_level_1'),
      countryCode: value('country', true).toUpperCase()
    };
  }
}
