#!/usr/bin/env python3
"""Validate the complete fast-jar layout rather than shipping only quarkus-run.jar."""
from pathlib import Path
import json
import sys
import zipfile


def check(archive):
    with zipfile.ZipFile(archive) as distribution:
        names = distribution.namelist()
        roots = {name.split("/")[0] for name in names}
        if len(roots) != 1:
            raise ValueError("Expected one top-level distribution directory")
        root = next(iter(roots)) + "/"
        for suffix in ("app/quarkus-run.jar", "run.sh", "README.md", "docs/configuration.md", "docs/operations.md", "sbom/bom.json"):
            if root + suffix not in names:
                raise ValueError(f"Missing {suffix}")
        for directory in ("app/lib/", "app/app/", "app/quarkus/"):
            if not any(name.startswith(root + directory) and name.endswith(".jar") for name in names):
                raise ValueError(f"Missing jars in {directory}")
        bom = json.loads(distribution.read(root + "sbom/bom.json"))
        if bom.get("bomFormat") != "CycloneDX" or not bom.get("components"):
            raise ValueError("Missing runtime components in CycloneDX SBOM")
        if any(name.startswith("/") or ".." in Path(name).parts for name in names):
            raise ValueError("Unsafe archive path")
    print(f"Distribution layout and SBOM verified: {archive}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 scripts/check-distribution.py <distribution.zip>")
    try:
        check(sys.argv[1])
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
