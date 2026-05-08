# Rust exercises (test dashboard)

A small **Axum + Tera** app that mirrors the Python dashboard: it reads **JUnit XML** from `reports/junit.xml`, shows pass/fail/skip in a table, runs **`cargo nextest`** from the UI, and can open a **source** modal (best-effort path mapping from the test’s Rust path).

## Prerequisites

- [Rust toolchain](https://rustup.rs/) (stable). This crate declares **`rust-version = "1.85"`** in `Cargo.toml` and includes **`rust-toolchain.toml`** (`channel = "stable"`) so **rustup** picks stable when you work under **`rust/`**. Use **rustup’s** `cargo` (~/.cargo/bin); distro packages (e.g. Ubuntu **1.75**) are too old for current dependencies. **First-time install:** see **[First-time Rust install (rustup)](#first-time-rust-install-rustup)**. A **recent** stable also avoids pinning when installing **cargo-nextest** (see below).
- **cargo-nextest** (the UI runs `cargo nextest`). Install either:
  - **Latest** (needs a current stable rustc, e.g. 1.91+ as required by recent nextest):  
    `cargo install cargo-nextest`
  - **Pinned** if `cargo install cargo-nextest` errors on MSRV (example when you are on **1.89**):  
    `cargo install cargo-nextest --version 0.9.128`  
    (Cargo often prints a concrete version that matches your toolchain.)
  - Or upgrade the compiler first: `rustup update stable`, then run `cargo install cargo-nextest` again.

Installing nextest **also compiles Rust code**, so you need a working linker (see Windows below) before `cargo install` will succeed.

### First-time Rust install (rustup)

On **Linux** (including **WSL**), **macOS**, or any environment where **`rustup`** is missing or **`cargo --version`** is very old (e.g. Ubuntu **1.75** from **`apt install cargo`**), use the official installer—not distro **`cargo`** alone.

**Non-interactive** (default stable, no prompts):

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
source "$HOME/.cargo/env"
rustup default stable
rustup update stable
```

**Interactive** (review prompts): drop the `-y` and run `... | sh` instead.

**Verify** you are using rustup’s Cargo (**not** `/usr/bin/cargo` on Debian/Ubuntu):

```bash
which cargo
cargo --version
```

You want **`$HOME/.cargo/bin/cargo`** and a **recent stable** matching this repo (**`rust-version = "1.85"`** in `Cargo.toml`; deps may need **`edition2024`**). If `which` shows **`/usr/bin/cargo`** or the version stays **1.75.x**, put rustup first and reload your shell:

```bash
echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
rustup update stable
```

**Linux / WSL (optional):** remove packages that shadow rustup (names may vary):

```bash
sudo apt remove cargo rustc
```

After this, **`rust-toolchain.toml`** in **`rust/`** will select **`stable`** via rustup when you run **`cargo`** from this directory (as long as **`~/.cargo/bin`** is the `cargo` you invoke).

### One-shot setup / reinstall

From this **`rust/`** directory:

- **Windows (PowerShell):** `.\scripts\resetup_rust_env.ps1`
- **macOS / Linux:** `chmod +x scripts/resetup_rust_env.sh && ./scripts/resetup_rust_env.sh`

Options: `-SkipNextest` / `--skip-nextest`, `-SkipBuild` / `--skip-build`.

### Windows: `link.exe` not found

The default Rust target is **MSVC** (`x86_64-pc-windows-msvc`). It needs the **Visual C++ build tools** (not VS Code).

1. Install [Build Tools for Visual Studio](https://visualstudio.microsoft.com/visual-cpp-build-tools/) (or full Visual Studio).
2. In the installer, select **“Desktop development with C++”** (or at least the **MSVC** compiler and **Windows SDK**).
3. Open a **new** terminal and run `cargo build` again.

If Build Tools are installed but you still see **`link.exe` not found**, you are probably in plain PowerShell. Use **Developer PowerShell for VS** or **x64 Native Tools Command Prompt** from the Start Menu (those run `VsDevCmd` / `vcvars64` so `link.exe` is on `PATH`). The `resetup_rust_env.ps1` script will also hint when `vswhere` finds an install but `link.exe` is missing from the current shell.

If you prefer **not** to install MSVC at all, use the **GNU / MinGW** host toolchain in the next section instead.

### Windows: GNU target (MinGW) instead of MSVC

Use this path if you do **not** want Visual Studio / MSVC and are fine using **GCC from MinGW-w64** as the linker. Rust’s default on Windows is still MSVC; the GNU option is a full **host triple** change (`x86_64-pc-windows-gnu`), not “MSVC + one extra flag”.

**1. Install a MinGW-w64 toolchain** and put its `bin` directory on `PATH` so Rust can invoke the linker (GCC). Typical options:

- [MSYS2](https://www.msys2.org/): in the **MINGW64** terminal, install the toolchain, then either keep using that terminal or add MinGW `bin` to `PATH` (often `C:\msys64\mingw64\bin`) for PowerShell:
  ```bash
  pacman -S --needed mingw-w64-x86_64-toolchain
  ```
- A standalone [MinGW-w64](https://www.mingw-w64.org/) build that ships `x86_64-w64-mingw32-gcc.exe` (or `gcc.exe`) in a `bin` folder you add to `PATH`.

**2. Install and select the matching Rust toolchain** (PowerShell):

```powershell
rustup toolchain install stable-x86_64-pc-windows-gnu
rustup default stable-x86_64-pc-windows-gnu
```

**3. Check that Cargo sees the GNU host and a working linker:**

In PowerShell, confirm the **same MinGW `bin` directory** is on `PATH` for **both** the compiler and DLL tooling (Rust’s GNU linker driver may invoke `gcc` *and* `dlltool.exe`). All of these should resolve to files under one `...\mingw64\bin` (or equivalent):

```powershell
where.exe gcc
where.exe x86_64-w64-mingw32-gcc
where.exe dlltool
```

If **`dlltool.exe` not found`** appears during `cargo build`, your `PATH` is incomplete or the toolchain install is partial. Fix: install **`mingw-w64-x86_64-toolchain`** (MSYS2) or a full MinGW-w64 `bin` layout, then **restart the terminal** so `dlltool.exe` sits next to `gcc.exe` on `PATH`.

Then:

```powershell
rustc -vV
```

Under `host:` you should see `x86_64-pc-windows-gnu`. Then from this `rust/` directory:

```powershell
cargo build
```

If the linker is missing, errors will mention `cc` / `gcc` / `linking with gcc failed`—fix `PATH` to the MinGW `bin` first. A GNU build can also fail with **`dlltool.exe`**: program not found; that is the same class of fix (full MinGW `bin` on `PATH`).

**4. Rest of this README** (`cargo install cargo-nextest`, `cargo run --bin exercises-web`, `cargo nextest`) works the same once `cargo build` succeeds.

**Switching back to MSVC** (after you install the C++ build tools):  
`rustup default stable-x86_64-pc-windows-msvc` (or `rustup default stable` if that is your MSVC toolchain name).

**Trade-offs:** Some Windows-only crates or prebuilt binaries assume MSVC; for a typical Axum app like this one, GNU is usually fine. Prefer MSVC if you already use Visual Studio or need maximum compatibility with Windows-native tooling.

### Windows: WSL (Linux in Windows)

If you want to avoid **MSVC**, **MinGW**, and **`link.exe` / `dlltool.exe`** on Windows entirely, run the project inside **WSL2** using the normal **Linux** Rust target (`x86_64-unknown-linux-gnu`).

1. Install [WSL](https://learn.microsoft.com/windows/wsl/install) and a distro (e.g. Ubuntu). Prefer **WSL2**.

2. In the WSL shell, install a C toolchain (linker for `rustc`):

   ```bash
   sudo apt update
   sudo apt install -y build-essential
   ```

3. Install Rust: follow **[First-time Rust install (rustup)](#first-time-rust-install-rustup)** above. **Do not stop at `sudo apt install cargo`:** that only gives **`/usr/bin/cargo` 1.75**, which cannot build this project (**`edition2024`** / lock file support). WSL uses the same rustup steps as native Linux.

4. **`cd` to this repo’s `rust/` folder**. Your Windows tree is usually under `/mnt/c/`, for example:

   ```bash
   cd /mnt/c/Users/owner/Documents/Git/exercises/exercises/rust
   ```

   *Optional:* Cloning or copying the repo under your WSL home (e.g. `~/projects/`) avoids `/mnt/c/...` file‑system overhead for large builds.

5. From **`rust/`**, you can use the same helper as on macOS/Linux:

   ```bash
   chmod +x scripts/resetup_rust_env.sh && ./scripts/resetup_rust_env.sh
   ```

   Or manually: `cargo install cargo-nextest`, then `cargo build` / `cargo run --bin exercises-web`.

6. Open the app in **Chrome or Edge on Windows** at [http://127.0.0.1:8082](http://127.0.0.1:8082). WSL2 forwards **localhost** to the Linux guest by default.

Use the **WSL terminal** for all `cargo` commands; a separate Rust install in PowerShell is unrelated. Optional env vars (`EXERCISES_WEB_PORT`, `EXERCISES_RUST_ROOT`) work the same as in “Run the server” below.

## Run the server

From this directory (`rust/`):

```bash
cargo run --bin exercises-web
```

Open [http://127.0.0.1:8082](http://127.0.0.1:8082) (Python uses 5000; this binary defaults to **8082**).

Optional:

- `EXERCISES_WEB_PORT` — listen port (default `8082`)
- `EXERCISES_RUST_ROOT` — workspace root if the binary is not running from this crate’s directory (must contain `Cargo.toml`)

## Reports

The **`dashboard`** nextest profile writes JUnit XML to **`reports/junit.xml`** (see `.config/nextest.toml`). After **`Run all tests`** in the UI (or `cargo nextest run --profile dashboard`), refresh the page to see rows.

## Re-run one test

**Re-run** submits a nextest filter (`test(^name$)`) for the test function name. Duplicate names may run more than one test.
