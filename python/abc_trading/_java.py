"""Internal JPype bootstrap shared by public Python value objects."""

from __future__ import annotations

import os
from pathlib import Path

import jpype


_PROJECT_ROOT = Path(__file__).resolve().parents[2]
_DEFAULT_JAVA_CLASSES = _PROJECT_ROOT / "java" / "target" / "classes"


def ensure_jvm() -> None:
    if jpype.isJVMStarted():
        return
    configured = os.environ.get("ABC_TRADING_JAVA_CLASSES")
    classes = Path(configured).expanduser().resolve() if configured else _DEFAULT_JAVA_CLASSES
    if not classes.exists():
        raise FileNotFoundError(
            f"Java classes not found at {classes}; run 'mvn -pl java test' first"
        )
    jpype.startJVM(classpath=[str(classes)], convertStrings=True)


def java_class(name: str):
    ensure_jvm()
    return jpype.JClass(name)
