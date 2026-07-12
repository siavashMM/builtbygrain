export function formatPrice(priceCents: number, currency: string): string {
  return new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency
  }).format(priceCents / 100);
}
