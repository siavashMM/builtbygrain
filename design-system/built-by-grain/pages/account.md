# Account page override

This page uses a restrained Minimal / Swiss dashboard treatment while retaining the Built by Grain palette and serif display voice.

## Layout

- Keep the account banner short: 210px desktop and about 146px mobile.
- Place only the breadcrumb and page title in the banner.
- Use a thin, white navigation rail with underline-only active states.
- Keep sign out as a small text action with a conventional exit icon; never present it as a full-width alert button.
- Use three equal overview columns on desktop: purchases, help and contact, account details.
- Collapse to one column on mobile in this order: purchases, support, account details.
- Move sign out to a centered, underlined action after the mobile dashboard content.
- Divide the address book into delivery and billing sections with a rule extending from each section heading.
- Present addresses as full-width rows, followed by one centered add-address action; do not use a competing card grid.

## Visual treatment

- Page background: warm light gray (`#f3f3f1`).
- Surfaces: white, 1px neutral borders, 4px corners, no shadows.
- Use the forest brand color for the banner and quiet interaction accents.
- Account rows always inherit the page ink and muted tokens; visited links must never introduce browser-default purple or underlines.
- Use the serif display face only for the page title and customer name. Use the sans-serif UI face everywhere else.
- Icons are simple outline SVGs with consistent 1.7px strokes.
- Default-address badges use the primary ink background; edit and add actions use quiet neutral surfaces.

## Interaction and accessibility

- Interactive targets are at least 44px high.
- Tabs may scroll inside their own rail on narrow screens; the page itself must not overflow horizontally.
- Hover feedback uses border or background changes without movement.
- Respect reduced-motion preferences.
