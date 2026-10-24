#!/usr/bin/env python3
"""Record cached IDE source/docs and metadata checksums for already-pinned components."""
import hashlib
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

NAMESPACE = "https://schema.gradle.org/dependency-verification"
NS = {"v": NAMESPACE}


def sha256_file(path):
    """Stream archives using APIs available in macOS's Python 3.9 as well as newer Python."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def record(metadata, cache):
    original = metadata.read_text()
    tree = ET.fromstring(original)
    additions = {}
    for component in tree.findall("v:components/v:component", NS):
        key = tuple(component.get(name) for name in ("group", "name", "version"))
        group, name, version = key
        # Only traverse exact coordinates already pinned by normal Gradle resolution.
        if any(not value or "/" in value or "\\" in value or value in (".", "..") for value in key):
            raise ValueError("Invalid component coordinates in verification metadata")
        directory = cache / group / name / version
        archives = {}
        for suffix in ("sources", "javadoc"):
            for archive in directory.glob(f"*/{name}-{version}-{suffix}.jar"):
                digest = sha256_file(archive)
                archives.setdefault(archive.name, set()).add(digest)
        for extension in ("pom", "module"):
            for artifact in directory.glob(f"*/{name}-{version}.{extension}"):
                digest = sha256_file(artifact)
                archives.setdefault(artifact.name, set()).add(digest)
        existing = {a.get("name"): a for a in component.findall("v:artifact", NS)}
        for filename, digests in sorted(archives.items()):
            if len(digests) != 1:
                raise ValueError(f"Conflicting cached bytes for {group}:{name}:{version}/{filename}")
            digest = next(iter(digests))
            if filename in existing:
                approved = {c.get("value") for c in existing[filename].findall("v:sha256", NS)}
                if digest not in approved:
                    raise ValueError(f"Existing checksum does not match {filename}; refusing to replace it")
                continue
            additions.setdefault(key, []).append((filename, digest))

    def append_artifacts(match):
        header, body, closing = match.groups()
        attributes = ET.fromstring(header + closing).attrib
        key = tuple(attributes.get(name) for name in ("group", "name", "version"))
        new = additions.get(key, [])
        if not new:
            return match.group(0)
        indentation = re.search(r"\n([ \t]*)$", body)
        indent = indentation.group(1) if indentation else "      "
        blocks = []
        for filename, digest in new:
            blocks.append(
                f"{indent}   <artifact name={quoteattr(filename)}>\n"
                f'{indent}      <sha256 value="{digest}" origin="Computed from cached IDE artifact; review required"/>\n'
                f"{indent}   </artifact>"
            )
        return header + body.rstrip() + "\n" + "\n".join(blocks) + "\n" + indent + closing

    count = sum(map(len, additions.values()))
    if count:
        updated = re.sub(r"(<component\b[^>]*>)(.*?)(</component>)", append_artifacts, original, flags=re.S)
        ET.fromstring(updated)
        metadata.write_text(updated)
    return count


def main():
    root = Path(__file__).resolve().parent.parent
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    try:
        count = record(root / "gradle/verification-metadata.xml", gradle_home / "caches/modules-2/files-2.1")
    except (OSError, ValueError, ET.ParseError) as error:
        sys.exit(str(error))
    print(f"Recorded {count} exact IDE artifact checksums for already-pinned components; review the diff.")


if __name__ == "__main__":
    main()
