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

export interface AddressPrediction {
  id: string;
  primaryText: string;
  secondaryText: string;
}

export interface AddressAutocompleteSession {
  suggest(input: string): Promise<AddressPrediction[]>;
  select(prediction: AddressPrediction): Promise<SuggestedAddress>;
}

@Injectable({ providedIn: 'root' })
export class AddressAutocompleteService {
  private readonly document = inject(DOCUMENT);
  private loader: Promise<void> | null = null;

  async createSession(apiKey: string): Promise<AddressAutocompleteSession> {
    await this.load(apiKey);
    const maps = (this.document.defaultView as any)?.google?.maps;
    if (!maps) throw new Error('Google Maps did not load');
    const { AutocompleteSessionToken, AutocompleteSuggestion } = await maps.importLibrary('places');
    if (!AutocompleteSessionToken || !AutocompleteSuggestion) {
      throw new Error('Google address suggestions did not load');
    }

    let token = new AutocompleteSessionToken();
    const predictions = new WeakMap<AddressPrediction, any>();

    return {
      suggest: async input => {
        const response = await AutocompleteSuggestion.fetchAutocompleteSuggestions({
          input,
          includedPrimaryTypes: ['street_address', 'route'],
          includedRegionCodes: ['de'],
          language: 'de',
          region: 'de',
          sessionToken: token
        });

        return (response.suggestions ?? []).flatMap((suggestion: any, index: number) => {
          const placePrediction = suggestion.placePrediction;
          if (!placePrediction) return [];
          const primaryText = placePrediction.mainText?.toString()
            ?? placePrediction.text?.toString()
            ?? '';
          if (!primaryText) return [];
          const prediction: AddressPrediction = {
            id: `${placePrediction.placeId ?? primaryText}-${index}`,
            primaryText,
            secondaryText: placePrediction.secondaryText?.toString() ?? ''
          };
          predictions.set(prediction, placePrediction);
          return [prediction];
        });
      },
      select: async prediction => {
        const placePrediction = predictions.get(prediction);
        if (!placePrediction) throw new Error('The address suggestion is no longer available');
        const place = placePrediction.toPlace();
        await place.fetchFields({ fields: ['addressComponents'] });
        token = new AutocompleteSessionToken();
        return this.mapComponents(place.addressComponents ?? []);
      }
    };
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
