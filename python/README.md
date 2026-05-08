# Python exercises

## Virtual environment

From this directory (`python/`):

**Windows (PowerShell)**

```powershell
.\scripts\init_venv.ps1
```

**macOS / Linux**

```bash
chmod +x scripts/init_venv.sh
./scripts/init_venv.sh
```

This creates `exercises/` (the virtual environment directory), upgrades `pip`, and installs the project in editable mode plus dev tools from `requirements-dev.txt`.
