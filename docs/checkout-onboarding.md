# Checkout onboarding integrations

The checkout onboarding flow supports three paths without duplicating customer data:

- email/password registration and sign-in use the existing customer APIs;
- Google and Apple use OpenID Connect and link a provider subject to the same customer;
- guest checkout keeps the delivery address in memory and does not create an account.

Missing external credentials do not break checkout. The matching social button or address helper is shown as unavailable, while classic registration, sign-in, guest checkout, browser autofill, and manual address entry continue to work.

## Google account registration

1. In Google Cloud, configure the OAuth consent screen.
2. Create an OAuth 2.0 Client ID with application type **Web application**.
3. Add the exact redirect URI:
   - local: `http://localhost:4200/login/oauth2/code/google`
   - production: `https://YOUR-SHOP-DOMAIN/login/oauth2/code/google`
4. Set the credentials only in the runtime environment:

```dotenv
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...
```

The frontend starts the flow at `/api/account/auth/social/google`; Spring Security performs the authorization-code exchange, validates the OIDC ID token, and stores only the stable provider subject. OAuth tokens are not written to the customer table or browser storage.

Configured social providers are offered on both checkout onboarding and the regular account sign-in page. A social-only customer signs back in with the same provider and does not need a local password. Successful sign-in returns to the originally requested local page; a failed provider flow returns to the sign-in surface that started it.

## Sign in with Apple

Apple web login requires a paid Apple Developer account and production HTTPS:

1. Enable **Sign in with Apple** on a primary App ID.
2. Create a Services ID for the website and use it as `APPLE_OAUTH_CLIENT_ID`.
3. Associate the Services ID with the primary App ID and register:
   - domain: `YOUR-SHOP-DOMAIN`
   - return URL: `https://YOUR-SHOP-DOMAIN/login/oauth2/code/apple`
4. Create a Sign in with Apple private key.
5. Generate the Apple client-secret JWT with ES256. Its subject is the Services ID and its maximum lifetime is six months, so rotate it before expiry.
6. Configure production:

```dotenv
APPLE_OAUTH_CLIENT_ID=com.example.shop.web
APPLE_OAUTH_CLIENT_SECRET=eyJ...
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=None
```

Apple returns the authorization result with a cross-site form POST when name/email scopes are requested. `SameSite=None` and `Secure` are therefore required for the OAuth state session cookie in production. Do not use that setting over local plain HTTP; Apple web login should be tested on an HTTPS development domain or staging environment.

## Address recommendations

The Delivery step always uses semantic `autocomplete` attributes, so browser-saved addresses work without an external API. Optional richer recommendations use Google’s current Place Autocomplete widget:

1. Enable **Maps JavaScript API** and **Places API (New)** in Google Cloud.
2. Create a separate browser API key.
3. Restrict the key by exact HTTP referrers for local/staging/production.
4. Add API restrictions so the key can call only Maps JavaScript API and Places API.
5. Configure:

```dotenv
GOOGLE_MAPS_BROWSER_KEY=...
```

Browser API keys are public by design; referrer and API restrictions are the security boundary. The implementation requests only `addressComponents` after selection to limit returned data and cost. Users can always ignore suggestions and type every field manually.

## Current payment boundary

Account and Delivery onboarding are implemented. The Payment step intentionally does not collect card data or place orders yet. Before enabling payment, the backend must rebuild prices from product/variant IDs, create an authoritative order, and integrate a PCI-compliant payment provider. Never trust cart prices cached by the browser.
