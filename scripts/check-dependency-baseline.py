#!/usr/bin/env python3
"""Fail closed when the resolved dependency baseline has not been recorded."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


def main():
    root = Path(__file__).resolve().parent.parent
    lockfile = root / "gradle.lockfile"
    metadata = root / "gradle/verification-metadata.xml"
    problems = []
    if not lockfile.is_file() or not re.search(r"^[^#\s]+:[^#\s]+:[^=\s]+=", lockfile.read_text(), re.M):
        problems.append("resolved Gradle dependency locks are missing")
    try:
        tree = ET.parse(metadata)
        ns = {"v": "https://schema.gradle.org/dependency-verification"}
        components = tree.findall("v:components/v:component", ns)
        if not components:
            problems.append("artifact checksum baseline is empty")
        for artifact in tree.findall("v:components/v:component/v:artifact", ns):
            checksums = artifact.findall("v:sha256", ns)
            if not checksums or any(not re.fullmatch(r"[0-9a-f]{64}", c.get("value", "")) for c in checksums):
                problems.append(f"missing or invalid SHA-256 for {artifact.get('name')}")
        if tree.findall(".//v:trusted-artifact", ns):
            problems.append("broad artifact verification exemptions are not allowed")
        if tree.findtext("v:configuration/v:verify-metadata", namespaces=ns) != "true":
            problems.append("dependency metadata verification must be enabled")
    except (OSError, ET.ParseError) as error:
        problems.append(str(error))
    if problems:
        print("Dependency baseline is not ready:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print("Run bash scripts/refresh-dependencies.sh with network access and Docker, then review and commit the generated locks/checksums. See docs/dependencies.md.", file=sys.stderr)
        return 1
    print("Dependency locks and SHA-256 baseline are present.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
