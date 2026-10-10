# Visual verification checklist

No pixel-diff tooling is configured (and no reference screenshot was available in the repo), so verify manually with the sample app → **Open Inspector (demo data)**.

Unit tests (`./gradlew :devscope-ui-compose:testAndroidHostTest`) cover aggregation, mapping, insights, formatting and policies.

- [ ] List shows 7 demo journeys; Add to Cart is "Slow Flow", Checkout "Failed", Sync ones "Running" (updating), Cold Start fully grey.
- [ ] Detail: title `Journey: Add to Cart`, subtitle `Trace #CART-1024 · Demo data`, total `1,265 ms`.
- [ ] Bar segments UI/VM blue, Network orange, Storage teal, Render purple; legend lists ms per category.
- [ ] 8 timeline rows in order: 0, 12, 34, 940 (orange), 4, 150, 15, 110 ms.
- [ ] Insight reads "API accounts for approximately 74% …"; "Show slowest step" scrolls to and expands API Request.
- [ ] Tap a row expands metadata (non-allowlisted keys hidden); filter chips filter rows; Expand all / Collapse all work.
- [ ] Back chevron / system back / swipe-back returns to the list; state survives rotation.
- [ ] Dark mode, font scale 2.0, tablet and landscape (two-pane ≥ 600 dp) render without clipping.
- [ ] Import Catalog (1,000 spans) scrolls smoothly.
- [ ] TalkBack/VoiceOver reads each row as title, detail, duration.

Captured Android screenshots: `docs/screenshots/`. iOS was not verified (no full Xcode on the build machine).
