# ashdod-prices

Static JSON API of supermarket prices in Ashdod, built nightly from the
price-transparency files that Israeli chains publish by law, and served by
GitHub Pages.

- `site/meta.json` – build time and per-chain status
- `site/stores.json` – stores
- `site/p/<last 3 digits of barcode>.json` – products and prices per store (agorot)
