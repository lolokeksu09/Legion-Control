# Alpha 48 — Function Finder and Settings

Base: published `v0.1.0-alpha.47` / `5004071a`. This branch does not change the installed package, certificate, SQLite schema, Android permissions or published release.

## Scope and acceptance

- **AUD-10:** 26 specific function entries in eight modules. Russian and English keywords, token-wise matching and ё/е normalization. Each entry binds to an actual button or clickable tile; selecting it only navigates and scrolls. Existing user confirmation, permissions, enabled states and backend handlers are preserved.
- **AUD-10:** Settings centralizes Android permission links, notifications, backups, interrupted restore, clips and per-module settings. Package-scoped system settings use the system's documented action and fallback to the general access list when necessary.
- **AUD-08 partial:** both bottom-navigation implementations use minimum height and wrap-content with two-line labels, preserve the existing icons, and suppress duplicate child accessibility targets. Hub insets include cutout and IME. Actual 200% font scale, TalkBack and landscape verification remain distinct gates.
- **AUD-11 partial:** clearer screen descriptions and settings group names. Full module naming/glossary remains open.

## Verification

1. `FeatureFinderTest` covers known IDs, owner validation, multilingual matching, uniqueness and no-match.
2. `scripts/check_feature_finder.py` checks that all 26 anchors exist in real module sources and reveal does not invoke any action.
3. Android emulator smoke checks direct stereo/replay reveal and ZIP-export link from Settings, without initiating recording or export. Existing search, session and navigation tests remain.
4. `AUD-09` physical-device matrix and broader TalkBack testing remain NOT RUN.
5. PR #55 overlaps the released Alpha 47 and is not merged or overwritten by this work.

Current version in this PR remains 47 pending verified release preparation. CI result must be recorded for the exact head before merging or preparing a signed release.
