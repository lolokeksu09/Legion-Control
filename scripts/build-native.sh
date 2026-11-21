#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

command -v cargo >/dev/null || { echo 'Rust/Cargo required' >&2; exit 1; }
command -v rustup >/dev/null || { echo 'rustup required' >&2; exit 1; }
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${sdk}/ndk/28.2.13676358}}"
[[ -d "$ndk/toolchains/llvm/prebuilt" ]] || {
  echo "Android NDK missing: $ndk" >&2; exit 1;
}

bin_dir=""
for host in linux-x86_64 darwin-arm64 darwin-x86_64; do
  if [[ -x "$ndk/toolchains/llvm/prebuilt/$host/bin/aarch64-linux-android33-clang" ]]; then
    bin_dir="$ndk/toolchains/llvm/prebuilt/$host/bin"
    break
  fi
done
[[ -n "$bin_dir" ]] || { echo 'Unsupported NDK host toolchain' >&2; exit 1; }
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$bin_dir/aarch64-linux-android33-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$bin_dir/x86_64-linux-android33-clang"

cargo test --manifest-path rust-core/Cargo.toml --locked
for target in aarch64-linux-android x86_64-linux-android; do
  rustup target list --installed | grep -qx "$target" || {
    echo "Missing Rust target $target" >&2; exit 1;
  }
  cargo build --manifest-path rust-core/Cargo.toml --locked --release --target "$target"
done

install -d app/src/main/jniLibs/arm64-v8a app/src/main/jniLibs/x86_64
install -m 644 rust-core/target/aarch64-linux-android/release/liblegion_core.so app/src/main/jniLibs/arm64-v8a/liblegion_core.so
install -m 644 rust-core/target/x86_64-linux-android/release/liblegion_core.so app/src/main/jniLibs/x86_64/liblegion_core.so
for lib in app/src/main/jniLibs/*/liblegion_core.so; do
  [[ -s "$lib" ]] || { echo "Missing Rust library $lib" >&2; exit 1; }
  echo "Built Rust JNI: $lib"
done
