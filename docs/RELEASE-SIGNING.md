# Permanent Android release signing

This repository's **public** release certificate SHA-256 fingerprint:

`89:E7:29:D2:07:32:7D:68:2D:8B:4F:74:B7:EB:C6:63:A5:76:4D:DC:F1:51:55:A1:80:12:81:FC:6F:73:9F:FF`

Alias: `legion-control-release`. Keystore: PKCS12, RSA 4096-bit, generated 2026-11-21 and valid through 2059-09-29. The certificate fingerprint is public; **private key and passwords are never committed or printed in CI logs**.

## Initial setup — owner action required

1. Download the private signing-kit ZIP given in the ChatGPT conversation. Back it up securely **outside GitHub** with its keystore, passwords and recovery information. Never upload this ZIP, the `.p12`, or `GITHUB_SECRETS_PRIVATE.txt` to the repository, an Issue or a public release.
2. Open the repository's `Settings → Secrets and variables → Actions → New repository secret`. Create all four **repository secrets** from `GITHUB_SECRETS_PRIVATE.txt`:
   - `LEGION_RELEASE_KEYSTORE_B64`: the complete one-line base64-encoded PKCS12 keystore (not the file path).
   - `LEGION_RELEASE_STORE_PASSWORD`: the password.
   - `LEGION_RELEASE_KEY_ALIAS`: `legion-control-release`.
   - `LEGION_RELEASE_KEY_PASSWORD`: key password (PKCS12 uses same password).
3. After all four are saved, use `Actions → Legion Control permanently signed APK → Run workflow` **on main**. GitHub Actions restores the keystore to runner temporary storage, builds both Rust JNI targets and Android release APK, then calls `apksigner verify`, checking the pinned public certificate hash.
4. Download the resulting `Legion-Control-v0.1.0-alpha.4-permanent-release-apk` workflow artifact. Only distribute it after checking device installation and the real functionality; do not replace it with a debug APK in a public release.

**No release runs on pull requests.** Signed releases run for trusted `main` source pushes and may also be triggered manually on `main`. Ordinary Android pull request CI can build a debug APK with no secrets. If release secrets are not configured, the release workflow fails clearly; `:app:assembleRelease` itself refuses to run unsigned.

## Compatibility and recovery

The previous `0.1.0-alpha.1` APK was **debug signed**, not signed with the permanent certificate. Android does **not** permit updates over a differently signed package. The first release-signed build requires uninstalling the older debug package before installation, potentially deleting its app data. Do this only after preserving desired data; this alpha currently retains only an in-memory Rust history, which is lost at process exit anyway.

After the first permanent-release installation, subsequent releases must use **the same keystore and alias** and increasing `versionCode` (current: 4) for in-place updates. Recreating a new keystore—even using the same alias—changes the certificate and breaks update compatibility. A lost private key cannot be reconstructed from its public SHA-256 fingerprint.

If certificate pin verification fails, stop: **never** bypass the check, commit a different keystore or distribute an incompatible APK as an update. Recover the saved original key or explicitly plan a separate package migration.

## Security and limitations

- Signing secrets are not present in source, git history, release APK or diagnostic logs.
- Repo's GitHub app connector currently cannot set Actions secrets: the owner needs to add them in GitHub Settings.
- A successful CI build or signature check confirms archive/signature integrity, **not** Android installation, permissions or hardware behavior.
- The private signing kit from this conversation is a **sensitive one-time backup**. Avoid distributing it and store it in secure offline or password-manager storage.


## Version progression

- `v0.1.0-alpha.4` is `versionCode=4`. The pinned release certificate fingerprint remains the same as alpha.2; use the **same** privately backed-up PKCS12 key and alias.
- An APK signed by the debug key cannot update a release-signed APK (or vice versa). Never uninstall an installed release merely to install a debug build without checking data/export needs.
- Until all four Actions secrets are installed, the release workflow is configured but **no permanently signed artifact is produced**. Debug builds are development-only.

## Why alpha.4 is an actual update

The owner's alpha.3 release-signed APK was built with the pinned certificate and `versionCode=3`. The new alpha.4 release with `versionCode=4` uses the exact same pinned certificate and package `dev.lolokeksu.legioncontrol`, and is designed to update in-place without deleting installed app data. Any existing **debug-signed** copy remains incompatible with the release signer. Never uninstall an installed release just to test a debug APK.
