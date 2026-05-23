"""PySide6 visual shell for the headless OCR pipeline."""

from __future__ import annotations

import os
import sysconfig
from pathlib import Path


def ensure_qt_plugin_paths() -> None:
    qt_plugins = Path(sysconfig.get_path("purelib")) / "PySide6" / "Qt" / "plugins"
    if qt_plugins.exists():
        os.environ.setdefault("QT_PLUGIN_PATH", str(qt_plugins))
        os.environ.setdefault("QT_QPA_PLATFORM_PLUGIN_PATH", str(qt_plugins / "platforms"))


ensure_qt_plugin_paths()
