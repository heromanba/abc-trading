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
    configured_classpath = os.environ.get("ABC_TRADING_JAVA_CLASSPATH")
    classpath = [str(classes)]
    if configured_classpath:
        classpath.extend(entry for entry in configured_classpath.split(os.pathsep) if entry)
    else:
        dependency_dir = classes.parent / "dependency"
        classpath.extend(str(path) for path in dependency_dir.glob("*.jar"))
        jackson_dir = Path.home() / ".m2" / "repository" / "com" / "fasterxml" / "jackson" / "core"
        jackson_version = os.environ.get("ABC_TRADING_JACKSON_VERSION", "2.15.2")
        for artifact in ("jackson-annotations", "jackson-core", "jackson-databind"):
            jar = jackson_dir / artifact / jackson_version / f"{artifact}-{jackson_version}.jar"
            if jar.exists():
                classpath.append(str(jar))
    jpype.startJVM(classpath=classpath, convertStrings=True)


def java_class(name: str):
    ensure_jvm()
    return jpype.JClass(name)
