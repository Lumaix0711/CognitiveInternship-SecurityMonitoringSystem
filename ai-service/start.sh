#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
source venv/bin/activate
exec python app.py
