#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/../model"
source "$HERE/venv/bin/activate"
export PYTHONUNBUFFERED=1
exec python server.py
