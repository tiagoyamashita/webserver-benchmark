#!/usr/bin/env bash
# Rust-only: rustup stable, cargo-nextest, cargo build for this crate (rust/).
#
# Usage:
#   ./scripts/resetup_rust_env.sh
#   ./scripts/resetup_rust_env.sh --skip-nextest
#   ./scripts/resetup_rust_env.sh --skip-build
set -euo pipefail

RUST_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKIP_NEXTEST=0
SKIP_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-nextest) SKIP_NEXTEST=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--skip-nextest] [--skip-build]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

echo ""
echo "=== rust / resetup_rust_env.sh ==="
echo "Rust project: $RUST_ROOT"
echo ""

if [[ ! -f "$RUST_ROOT/Cargo.toml" ]]; then
  echo "No Cargo.toml in $RUST_ROOT" >&2
  exit 1
fi

(
  cd "$RUST_ROOT"
  echo "== rustup update stable =="
  rustup update stable || true

  if [[ "$SKIP_NEXTEST" -eq 0 ]]; then
    echo "== cargo-nextest (latest, else 0.9.128) =="
    if ! cargo install --locked cargo-nextest; then
      echo "Latest cargo-nextest failed; installing 0.9.128 (works with rustc 1.89)." >&2
      cargo install --locked cargo-nextest --version 0.9.128
    fi
  else
    echo "Skipping cargo-nextest (--skip-nextest)."
  fi

  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    echo "== cargo build =="
    if ! cargo build; then
      echo "cargo build failed. On Windows you need MSVC link.exe or GNU toolchain — see rust/README.md." >&2
    fi
  else
    echo "Skipping cargo build (--skip-build)."
  fi
)

echo ""
echo "Done."
echo ""
