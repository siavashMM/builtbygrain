# Customer Accounts

## Design

Customer accounts extend the existing Spring Security setup rather than introducing
a second authentication framework. Passwords use Spring Security's delegating
password encoder and only the encoded value is stored. Authentication state is held
in the existing JDBC Spring Session tables and sent to the browser in an HttpOnly,
SameSite=Lax cookie. The cookie is Secure when `SESSION_COOKIE_SECURE=true`.

Customer sessions have a configurable 30-day inactivity lifetime. Admin sessions
continue to use the application's shorter default server-side lifetime. CSRF
protection uses the existing `XSRF-TOKEN` / `X-XSRF-TOKEN` cookie-and-header
contract.

Account APIs derive the customer from the authenticated principal. Address IDs are
always queried together with that customer ID, so changing an ID cannot read or
mutate another customer's address.

## Password reset mail

Reset tokens are generated with `SecureRandom`, stored only as SHA-256 hashes, expire
after 30 minutes by default, and are single use. A successful reset invalidates all
customer sessions. Forgot-password responses are identical for known and unknown
addresses.

Local development uses Mailpit:

```bash
just mail-up
```

Open `http://localhost:8025` to read development messages. The API never returns or
logs raw reset tokens. Production should point the standard Spring Mail settings at
the transactional email provider.

The authenticated profile includes `passwordSet` so the Security page can distinguish
password accounts from social-only accounts. Password accounts must provide their
current password to make a change. Social-only accounts receive the same single-use
email link to create an optional password; the browser session alone cannot create a
new login secret.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `CUSTOMER_SESSION_LIFETIME` | `30d` | Customer session inactivity lifetime |
| `PASSWORD_RESET_LIFETIME` | `30m` | Reset-token lifetime |
| `PASSWORD_RESET_URL` | `http://localhost:4200/account/reset-password` | Frontend reset page |
| `MAIL_FROM` | `no-reply@builtbygrain.local` | Sender address |
| `MAIL_HOST` | `localhost` | SMTP host |
| `MAIL_PORT` | `1025` | SMTP port |
| `SESSION_COOKIE_SECURE` | `false` | Must be `true` behind production HTTPS |

Flyway migration `V14__create_customer_accounts.sql` creates customers, saved
addresses, password-reset tokens, and sensitive-request throttling data. It is
non-destructive and runs with the existing migration process.

## Checkout integration contract

Registration and sign-in accept an optional `returnUrl`. The backend returns it only
when it is an internal path beginning with one `/`; external, protocol-relative,
backslash-containing, and control-character paths fall back to `/account`.

The reusable calls are:

- `POST /api/account/auth/register`
- `POST /api/account/auth/login`
- `GET /api/account/auth/session`

Successful registration and sign-in establish the customer session before returning.
The Angular `AccountService` keeps the returned profile in memory and navigates to
the server-sanitized path. It does not clear or rewrite the current guest cart, so a
future checkout can call the same APIs without losing the browser's current cart
presentation.

Stage 2 must replace the browser-only guest cart as the canonical source. It should
create an opaque anonymous cart ID in a secure cookie, store authoritative items and
totals on the server, and merge that cart with the authenticated customer's cart
using an explicit tested rule. No merge is performed in this stage.
