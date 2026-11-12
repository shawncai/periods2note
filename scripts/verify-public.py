#!/usr/bin/env python3
"""Check tracked paths and, optionally, a release APK before publishing.

This is a file-boundary check, not a replacement for reviewing source and screenshots.
Usage: python3 scripts/verify-public.py [path/to/release.apk]
"""
from pathlib import PurePosixPath
import subprocess
import sys
import zipfile

paths = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
for name in filter(None, paths):
    path = PurePosixPath(name)
    assert path.suffix.lower() not in {".json", ".jsonl", ".db", ".sqlite", ".jks", ".keystore", ".apk", ".aab", ".p12", ".pem", ".key", ".log"}, name
    assert not name.startswith(("deliverables/", "private/", "app/src/main/assets/")), name
    assert path.name != "local.properties" and not path.name.startswith(("._", ".env")), name

if len(sys.argv) > 1:
    with zipfile.ZipFile(sys.argv[1]) as apk:
        for name in apk.namelist():
            # Kotlin's dependency metadata is build information, not a record export.
            if name != "kotlin-tooling-metadata.json":
                assert not name.endswith((".json", ".db", ".sqlite", ".jks", ".keystore")), name
            if name.endswith(".dex"):
                content = apk.read(name)
                assert b"meiyou.json" not in content, "seed asset loader in APK"
                assert b"demo-0" not in content, "synthetic test fixture in APK"
print("PASS: tracked file boundary" + (" and APK data boundary" if len(sys.argv) > 1 else ""))
