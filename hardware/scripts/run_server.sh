#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ -x ".venv/bin/python" ]]; then
  PYTHON=".venv/bin/python"
elif [[ -x ".venv_tf/bin/python" ]]; then
  PYTHON=".venv_tf/bin/python"
else
  PYTHON="python3"
fi

exec "$PYTHON" -m uvicorn src.server.main:app --host 0.0.0.0 --port 8888
