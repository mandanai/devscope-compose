# Visual verification checklist

No pixel-diff tooling is configured (and no reference screenshot was available in the repo), so verify manually with the sample app → **Open Inspector (demo data)**.

Unit tests (`./gradlew :devscope-ui-compose:testAndroidHostTest`) cover mapping, diagnostics, formatting and policies; attribution is tested in `devscope-diagnostics`.

- [ ] List shows 10 demo journeys (including Refresh Home with overlapping requests and Add to Cart (no checkpoint)); Add to Cart is "Slow Flow", Checkout "Failed", Sync ones "Running" (updating), Cold Start fully grey.
- [ ] Detail: title `Journey: Add to Cart`, subtitle `Trace #CART-1024 · Demo data`, total `1,265 ms`.
- [ ] Bar segments UI/VM blue, Network orange, Storage teal, Render purple; legend lists ms per category.
- [ ] 8 timeline rows in order: 0, 12, 34, 940 (orange), 4, 150, 15, 110 ms.
- [ ] Timing breakdown shows Network `940 ms · 74.3%` and a sequential critical path; Diagnostics shows "Network time represents approximately 74.3% of this sequential journey"; "Show API Request" scrolls to and expands the row.
- [ ] Refresh Home shows the overlap note and "Critical path unavailable"; Add to Cart (no checkpoint) shows a Warning "Expected checkpoint not recorded".
- [ ] Tap a row expands metadata (non-allowlisted keys hidden); filter chips filter rows; Expand all / Collapse all work.
- [ ] Back chevron / system back / swipe-back returns to the list; state survives rotation.
- [ ] Dark mode, font scale 2.0, tablet and landscape (two-pane ≥ 600 dp) render without clipping.
- [ ] Import Catalog (1,000 spans) scrolls smoothly.
- [ ] TalkBack/VoiceOver reads each row as title, detail, duration.

Captured Android screenshots: `docs/screenshots/`. iOS was not verified (no full Xcode on the build machine).

Note: `docs/screenshots/android-detail-insight-dark.png` predates the Phase 6 diagnostics UI and still shows the old single-insight card; recapture before publishing.
