#!/usr/bin/env python3
"""Dependency-free source preflight; NOT a replacement for Android compilation.
Run this when Android SDK/Docker downloads are unavailable.
"""
from pathlib import Path
import sys, re, xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
manifest = root / 'app/src/main/AndroidManifest.xml'
ET.parse(manifest)
for x in (root / 'app/src/main/java').rglob('*.kt'):
    s = x.read_text(encoding='utf-8')
    if not s.startswith('package '):
        raise SystemExit(f'Missing Kotlin package: {x.relative_to(root)}')
    if '\x00' in s:
        raise SystemExit(f'NUL character: {x.relative_to(root)}')
readme=(root/'README.md').read_text(encoding='utf-8')
required=['Dockerfile','compose.yaml','tools/docker-build.sh',
          'app/src/main/AndroidManifest.xml','docs/PRD_AR.md',
          'app/src/main/java/org/onevault/app/ui/MainActivity.kt',
          'app/src/test/java/org/onevault/app/core/CoreTest.kt']
missing=[p for p in required if not (root/p).is_file()]
if missing: raise SystemExit(f'Missing project source: {missing}')
workflow=(root/'.github/workflows/android.yml').read_text()
assert 'docker-build.sh all' in workflow
assert 'app-debug.apk.sha256' in workflow
assert 'versionName = "0.2.6-alpha"' in (root/'app/build.gradle.kts').read_text()
assert 'source project' in readme.lower() or 'source' in readme.lower()
print('PASS offline source preflight (XML, expected files, workflow, version).')
print('NOTE Android SDK/Gradle compilation, Android lint and device tests have NOT run.')
