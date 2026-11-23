# Permanent Android release signing

This repository's **public** release certificate SHA-256 fingerprint:

`89:E7:29:D2:07:32:7D:68:2D:8B:4F:74:B7:EB:C6:63:A5:76:4D:DC:F1:51:55:A1:80:12:81:FC:6F:73:9F:FF`

Alias: `legion-control-release`. Keystore: PKCS12, RSA 4096-bit, generated 2026-11-21 and valid through 2059-09-29. The certificate fingerprint is public; **private key and passwords are never committed or printed in CI logs**.

## alpha.41 release identity

Текущая версия: `versionCode=41`, `versionName=0.1.0-alpha.41`. Тот же
`applicationId` и неизменный релизный сертификат. GitHub prerelease
`v0.1.0-alpha.41` может публиковаться только после зелёного Android CI
на точном коммите `main` и успешной проверки постоянной подписи.
Обновление устанавливать поверх существующего приложения без удаления.

## alpha.40 release identity

Current tested source candidate uses `applicationId=dev.lolokeksu.legioncontrol`,
`versionCode=40`, `versionName=0.1.0-alpha.40`. Its expected release artifact
is `Legion-Control-v0.1.0-alpha.40-permanent-release-apk` after an authorized
merge to `main` and a successful permanent-signing workflow. Never distribute
an APK signed with the disposable PR CI certificate as a user upgrade.

## Initial setup — owner action required

1. Download the private signing-kit ZIP given in the ChatGPT conversation. Back it up securely **outside GitHub** with its keystore, passwords and recovery information. Never upload this ZIP, the `.p12`, or `GITHUB_SECRETS_PRIVATE.txt` to the repository, an Issue or a public release.
2. Open the repository's `Settings → Secrets and variables → Actions → New repository secret`. Create all four **repository secrets** from `GITHUB_SECRETS_PRIVATE.txt`:
   - `LEGION_RELEASE_KEYSTORE_B64`: the complete one-line base64-encoded PKCS12 keystore (not the file path).
   - `LEGION_RELEASE_STORE_PASSWORD`: the password.
   - `LEGION_RELEASE_KEY_ALIAS`: `legion-control-release`.
   - `LEGION_RELEASE_KEY_PASSWORD`: key password (PKCS12 uses same password).
3. After all four are saved, use `Actions → Legion Control permanently signed APK → Run workflow` **on main**. GitHub Actions restores the keystore to runner temporary storage, builds both Rust JNI targets and Android release APK, then calls `apksigner verify`, checking the pinned public certificate hash.
4. Download the resulting `Legion-Control-v0.1.0-alpha.41-permanent-release-apk` workflow artifact. Only distribute it after checking device installation and the real functionality; do not replace it with a debug APK in a public release.

**No release runs on pull requests.** Signed releases run for trusted `main` source pushes and may also be triggered manually on `main`. Ordinary Android pull request CI can build a debug APK with no secrets. If release secrets are not configured, the release workflow fails clearly; `:app:assembleRelease` itself refuses to run unsigned.

## Compatibility and recovery

The previous `0.1.0-alpha.1` APK was **debug signed**, not signed with the permanent certificate. Android does **not** permit updates over a differently signed package. The first release-signed build cannot update over an existing debug-signed package. **Uninstalling deletes app-private SQLite history and SharedPreferences.** Export the local-data SAF ZIP (and separately copy any important MP4/PNG files) before considering a fresh installation. A currently installed release-signed alpha.27 can update **in place** to release-signed alpha.40 with the same original certificate and increasing versionCode; do not uninstall a compatible signed installation.

After the first permanent-release installation, subsequent releases must use **the same keystore and alias** and increasing `versionCode` (current: 40) for in-place updates. Recreating a new keystore—even using the same alias—changes the certificate and breaks update compatibility. A lost private key cannot be reconstructed from its public SHA-256 fingerprint.

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


## Alpha.5 in-place signed update

The `0.1.0-alpha.5` build uses `versionCode=5`, the **same `dev.lolokeksu.legioncontrol` package identity**, and the same pinned release certificate. SQLite remains schema v1; no destructive migration occurs. A signed alpha.5 APK may upgrade an installed alpha.4 **only when both APKs use this same signing identity**. Never uninstall an existing signed APK just to install a debug artifact. A successful GitHub test build is not itself a signed release; the protected `main` workflow must finish and verify `apksigner` before delivery.


## Alpha.6 signed update

`0.1.0-alpha.6` advances to `versionCode=6` and retains the original permanent signing certificate and application ID. The private SQLite schema stays unchanged. The mandatory protected `main` workflow must verify the certificate hash and produce the release APK before it may be called an in-place signed upgrade. Debug APKs carry an unrelated identity and cannot replace the owner's signed install without uninstallation/data loss. Public release and Y70 hardware acceptance remain separately gated.

## Alpha.7 signed update — schema migration

`0.1.0-alpha.7` has `versionCode=7` and keeps the existing release certificate and package ID. On first launch after an **in-place signed update**, SQLiteOpenHelper migrates database v1 to v2 by creating a new diagnostic table only. It does not delete old readings. The release workflow checks the pinned certificate; the v1-to-v2 schema test is mandatory. Before updating a phone that contains important data, export its current CSV (old schema) as an extra safety backup. Do not uninstall the existing signed application: uninstalling may erase the SQLite database.

## Alpha.8 signed upgrade — CSV extension only

The `0.1.0-alpha.8` build increments to `versionCode=8` and retains `dev.lolokeksu.legioncontrol` and the pinned permanent signing certificate. SQLite remains schema v2; no new migration or deletion is introduced. Export keeps prior 13 columns, appending seven optional diagnostic columns. Install the same-signer APK **over**, not in place of uninstalling, an installed signed alpha.7. The protected `main` release workflow must verify `apksigner` and package Rust JNI before distribution.

## Alpha.9 signed update

`0.1.0-alpha.9` increments `versionCode` from 8 to 9 with the existing `dev.lolokeksu.legioncontrol` package and same pinned permanent signing key. There is no SQLite schema change (v2), Rust ABI change, new Android permission, or change to saved data/CSV. The update must install **over** the earlier release-signed APK; do not uninstall and erase SQLite history to install a debug build. Protected `main` release CI must pass and verify `apksigner`/certificate pin before the APK is offered as an update.
