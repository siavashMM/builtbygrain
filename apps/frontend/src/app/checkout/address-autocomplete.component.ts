import {
  Component,
  OnDestroy,
  ViewEncapsulation,
  forwardRef,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import {
  AddressAutocompleteService,
  AddressAutocompleteSession,
  AddressPrediction,
  SuggestedAddress
} from './address-autocomplete.service';
import { CheckoutService } from './checkout.service';

@Component({
  selector: 'app-address-autocomplete',
  styleUrl: './address-autocomplete.css',
  encapsulation: ViewEncapsulation.None,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => AddressAutocompleteComponent),
    multi: true
  }],
  template: `
    <div class="address-autocomplete-control">
      <input
        type="text"
        [id]="inputId()"
        [value]="value()"
        [disabled]="disabled()"
        [attr.aria-controls]="suggestionsId()"
        [attr.aria-describedby]="status() === 'unavailable' && value().trim().length >= minimumQueryLength ? unavailableId() : null"
        [attr.aria-expanded]="suggestions().length > 0"
        [attr.aria-activedescendant]="activeOptionId()"
        [attr.aria-busy]="status() === 'loading'"
        role="combobox"
        aria-autocomplete="list"
        autocomplete="address-line1"
        autocapitalize="words"
        placeholder="Start typing a street or address"
        (input)="handleInput($event)"
        (focus)="handleFocus()"
        (blur)="handleBlur()"
        (keydown)="handleKeydown($event)"
      >

      @if (status() === 'loading') {
        <span class="address-autocomplete-spinner" role="status">
          <span class="address-autocomplete-visually-hidden">Loading address suggestions</span>
        </span>
      }

      @if (suggestions().length > 0) {
        <ul [id]="suggestionsId()" class="address-suggestion-list" role="listbox" aria-label="Address suggestions">
          @for (prediction of suggestions(); track prediction.id; let index = $index) {
            <li
              [id]="optionId(index)"
              class="address-suggestion-option"
              [class.active]="activeIndex() === index"
              [attr.aria-selected]="activeIndex() === index"
              role="option"
              (pointerdown)="keepInputFocus($event)"
              (mouseenter)="activeIndex.set(index)"
              (click)="selectPrediction(prediction)"
            >
              <strong>{{ prediction.primaryText }}</strong>
              @if (prediction.secondaryText) { <small>{{ prediction.secondaryText }}</small> }
            </li>
          }
          <li class="address-suggestion-attribution" role="presentation">
            <span translate="no">Google Maps</span>
          </li>
        </ul>
      }
    </div>

    @if (status() === 'unavailable' && value().trim().length >= minimumQueryLength) {
      <small [id]="unavailableId()" class="address-autocomplete-unavailable">
        Suggestions are unavailable. Enter the street manually.
      </small>
    }
  `
})
export class AddressAutocompleteComponent implements ControlValueAccessor, OnDestroy {
  private readonly autocomplete = inject(AddressAutocompleteService);
  private readonly checkout = inject(CheckoutService);
  private sessionPromise: Promise<AddressAutocompleteSession | null> | null = null;
  private suggestionTimer: ReturnType<typeof setTimeout> | null = null;
  private blurTimer: ReturnType<typeof setTimeout> | null = null;
  private requestVersion = 0;
  private destroyed = false;
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  readonly inputId = input('street-address');
  readonly addressSelected = output<SuggestedAddress>();
  protected readonly value = signal('');
  protected readonly disabled = signal(false);
  protected readonly suggestions = signal<AddressPrediction[]>([]);
  protected readonly activeIndex = signal(-1);
  protected readonly status = signal<'idle' | 'loading' | 'ready' | 'unavailable'>('idle');
  protected readonly minimumQueryLength = 2;

  protected suggestionsId(): string {
    return `${this.inputId()}-suggestions`;
  }

  protected unavailableId(): string {
    return `${this.inputId()}-suggestions-unavailable`;
  }

  protected optionId(index: number): string {
    return `${this.inputId()}-suggestion-${index}`;
  }

  protected activeOptionId(): string | null {
    return this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : null;
  }

  protected handleInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.value.set(value);
    this.onChange(value);
    this.scheduleSuggestions(value);
  }

  protected handleFocus(): void {
    this.clearBlurTimer();
    void this.ensureSession();
    if (this.value().trim().length >= this.minimumQueryLength) {
      this.scheduleSuggestions(this.value());
    }
  }

  protected handleBlur(): void {
    this.onTouched();
    this.clearBlurTimer();
    this.blurTimer = setTimeout(() => this.closeSuggestions(), 150);
  }

  protected handleKeydown(event: KeyboardEvent): void {
    const count = this.suggestions().length;
    if (event.key === 'Escape') {
      if (count > 0) event.preventDefault();
      this.closeSuggestions();
      return;
    }
    if (count === 0) return;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex.set((this.activeIndex() + 1) % count);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex.set(this.activeIndex() <= 0 ? count - 1 : this.activeIndex() - 1);
      return;
    }
    if (event.key === 'Enter' && this.activeIndex() >= 0) {
      event.preventDefault();
      void this.selectPrediction(this.suggestions()[this.activeIndex()]);
    }
  }

  protected keepInputFocus(event: PointerEvent): void {
    event.preventDefault();
  }

  protected async selectPrediction(prediction: AddressPrediction): Promise<void> {
    const session = await this.ensureSession();
    if (!session || this.destroyed) return;
    this.requestVersion += 1;
    this.clearSuggestionTimer();
    this.status.set('loading');

    try {
      const selectedAddress = await session.select(prediction);
      if (this.destroyed) return;
      const address = {
        ...selectedAddress,
        street: selectedAddress.street || prediction.primaryText
      };
      this.value.set(address.street);
      this.onChange(address.street);
      this.closeSuggestions();
      this.status.set('ready');
      this.addressSelected.emit(address);
    } catch {
      if (!this.destroyed) {
        this.closeSuggestions();
        this.status.set('unavailable');
      }
    }
  }

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(callback: (value: string) => void): void {
    this.onChange = callback;
  }

  registerOnTouched(callback: () => void): void {
    this.onTouched = callback;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
    if (disabled) this.closeSuggestions();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.requestVersion += 1;
    this.clearSuggestionTimer();
    this.clearBlurTimer();
  }

  private scheduleSuggestions(value: string): void {
    this.requestVersion += 1;
    const version = this.requestVersion;
    this.clearSuggestionTimer();
    this.suggestions.set([]);
    this.activeIndex.set(-1);
    if (value.trim().length < this.minimumQueryLength) {
      if (this.status() !== 'unavailable') this.status.set('idle');
      return;
    }

    this.suggestionTimer = setTimeout(() => {
      void this.fetchSuggestions(value.trim(), version);
    }, 250);
  }

  private async fetchSuggestions(value: string, version: number): Promise<void> {
    this.status.set('loading');
    const session = await this.ensureSession();
    if (!session || this.destroyed || version !== this.requestVersion) return;

    try {
      const suggestions = await session.suggest(value);
      if (this.destroyed || version !== this.requestVersion) return;
      this.suggestions.set(suggestions);
      this.activeIndex.set(-1);
      this.status.set('ready');
    } catch {
      if (!this.destroyed && version === this.requestVersion) {
        this.suggestions.set([]);
        this.status.set('unavailable');
      }
    }
  }

  private ensureSession(): Promise<AddressAutocompleteSession | null> {
    if (this.sessionPromise) return this.sessionPromise;
    this.sessionPromise = firstValueFrom(this.checkout.config())
      .then(config => {
        if (!config.addressAutocompleteEnabled || !config.googleMapsBrowserKey) {
          this.status.set('unavailable');
          return null;
        }
        return this.autocomplete.createSession(config.googleMapsBrowserKey);
      })
      .catch(() => {
        if (!this.destroyed) this.status.set('unavailable');
        return null;
      });
    return this.sessionPromise;
  }

  private closeSuggestions(): void {
    this.suggestions.set([]);
    this.activeIndex.set(-1);
  }

  private clearSuggestionTimer(): void {
    if (this.suggestionTimer) clearTimeout(this.suggestionTimer);
    this.suggestionTimer = null;
  }

  private clearBlurTimer(): void {
    if (this.blurTimer) clearTimeout(this.blurTimer);
    this.blurTimer = null;
  }
}
