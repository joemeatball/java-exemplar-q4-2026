"""Tests release guards and artifact validation without a network or GitHub account."""
import importlib.util
import hashlib
import json
import os
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def load_script(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleasePreflightTest(unittest.TestCase):
    def run_guard(self, version="0.1.0", refs="", status="0"):
        with tempfile.TemporaryDirectory() as directory:
            fake_gh = Path(directory) / "gh"
            fake_gh.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$FAKE_REFS"\nexit "$FAKE_STATUS"\n')
            fake_gh.chmod(0o755)
            environment = dict(os.environ, PATH=directory + os.pathsep + os.environ["PATH"],
                               RELEASE_VERSION=version, GH_REPO="example/project",
                               FAKE_REFS=refs, FAKE_STATUS=status)
            return subprocess.run(["bash", str(ROOT / "scripts/release-preflight.sh")],
                                  env=environment, capture_output=True, text=True, check=False)

    def test_accepts_unused_semantic_version(self):
        self.assertEqual(self.run_guard().returncode, 0)

    def test_rejects_existing_tag(self):
        result = self.run_guard(refs="refs/tags/v0.1.0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("already exists", result.stderr)

    def test_api_failure_is_not_treated_as_a_missing_tag(self):
        self.assertNotEqual(self.run_guard(status="42").returncode, 0)

    def test_prefix_matches_do_not_reject_a_different_version(self):
        self.assertEqual(self.run_guard(refs="refs/tags/v0.1.00").returncode, 0)

    def test_invalid_versions_cannot_reach_the_api(self):
        for version in ("v1.0.0", "01.0.0", "1.0", "1.0.0-rc1", "1.0.0; echo unsafe", "$(id)"):
            with self.subTest(version=version):
                result = self.run_guard(version=version)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("semantic version", result.stderr)


class DevDependencyRefreshTest(unittest.TestCase):
    def run_refresh(self, directory, fail_at=0, write_output=True):
        root = Path(directory)
        (root / "scripts").mkdir()
        shutil.copyfile(ROOT / "scripts/refresh-dev-dependencies.sh", root / "scripts/refresh-dev-dependencies.sh")
        wrapper = root / "gradlew"
        wrapper.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
log = pathlib.Path("calls.json")
calls = json.loads(log.read_text()) if log.exists() else []
args = sys.argv[1:]
calls.append(args)
log.write_text(json.dumps(calls))
export = next((a.split("=", 1)[1] for a in args if a.startswith("-Dio.quarkus.devmode-args=")), None)
if not export or args[-1] != "quarkusDev":
    sys.exit("Refusing simulated dev launch without command export")
if len(calls) == int(os.environ["FAIL_AT"]):
    sys.exit(17)
if os.environ["WRITE_OUTPUT"] == "true":
    pathlib.Path(export).write_text("exported dev command")
''')
        wrapper.chmod(0o755)
        result = subprocess.run(["bash", str(root / "scripts/refresh-dev-dependencies.sh")],
                                env=dict(os.environ, FAIL_AT=str(fail_at), WRITE_OUTPUT=str(write_output).lower()),
                                capture_output=True, text=True, check=False)
        calls = json.loads((root / "calls.json").read_text())
        return result, calls

    def test_generates_then_strictly_verifies_without_launching_a_server(self):
        with tempfile.TemporaryDirectory() as directory:
            result, calls = self.run_refresh(directory)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(len(calls), 2)
            self.assertIn("--write-locks", calls[0])
            self.assertNotIn("--write-locks", calls[1])
            self.assertNotIn("--write-verification-metadata", calls[1])
            for args in calls:
                self.assertIn("--no-configuration-cache", args)
                self.assertTrue(any(a.startswith("-Dio.quarkus.devmode-args=") for a in args))

    def test_resolution_and_strict_verification_failures_are_propagated(self):
        for fail_at in (1, 2):
            with self.subTest(fail_at=fail_at), tempfile.TemporaryDirectory() as directory:
                result, calls = self.run_refresh(directory, fail_at=fail_at)
                self.assertEqual(result.returncode, 17)
                self.assertEqual(len(calls), fail_at)

    def test_missing_export_is_not_reported_as_success(self):
        with tempfile.TemporaryDirectory() as directory:
            result, calls = self.run_refresh(directory, write_output=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(len(calls), 1)


class DistributionTest(unittest.TestCase):
    def setUp(self):
        self.validator = load_script("check-distribution")

    def make_archive(self, path, omit=None, unsafe=False, empty_sbom=False):
        root = "2026-exemplar-0.1.0/"
        entries = {
            "app/quarkus-run.jar": b"runner",
            "app/lib/main/dependency.jar": b"dependency",
            "app/app/application.jar": b"application",
            "app/quarkus/generated-bytecode.jar": b"generated",
            "run.sh": b"launcher",
            "README.md": b"readme",
            "docs/configuration.md": b"configuration",
            "docs/operations.md": b"operations",
            "sbom/bom.json": json.dumps({"bomFormat": "CycloneDX", "components": [] if empty_sbom else [{"name": "dependency"}]}).encode(),
        }
        if omit:
            entries.pop(omit)
        if unsafe:
            entries["../escape"] = b"unsafe"
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(root + name, content)

    def test_accepts_complete_distribution(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "release.zip"
            self.make_archive(archive)
            self.validator.check(archive)

    def test_rejects_incomplete_runtime_empty_sbom_and_unsafe_paths(self):
        cases = ({"omit": "app/lib/main/dependency.jar"}, {"empty_sbom": True}, {"unsafe": True})
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as directory:
                archive = Path(directory) / "release.zip"
                self.make_archive(archive, **case)
                with self.assertRaises(ValueError):
                    self.validator.check(archive)


class CachedIdeChecksumsTest(unittest.TestCase):
    def setUp(self):
        self.recorder = load_script("record-cached-ide-checksums")

    def baseline(self, root):
        path = root / "verification-metadata.xml"
        path.write_text('''<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
   <components>
      <component group="example" name="library" version="1.0">
         <artifact name="library-1.0.jar"><sha256 value="existing-binary-checksum"/></artifact>
      </component>
   </components>
</verification-metadata>
''')
        return path

    def archive(self, cache, suffix="sources", content=b"source contents", folder="hash", name="library"):
        path = cache / "example" / name / "1.0" / folder / f"{name}-1.0-{suffix}.jar"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def test_streaming_hash_handles_empty_and_multiple_chunks(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "archive.jar"
            for contents in (b"", b"source bytes" * 20000):
                with self.subTest(size=len(contents)):
                    path.write_bytes(contents)
                    self.assertEqual(self.recorder.sha256_file(path), hashlib.sha256(contents).hexdigest())

    def test_records_exact_sources_and_javadocs_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = self.baseline(root)
            cache = root / "cache"
            self.archive(cache)
            self.archive(cache, suffix="javadoc", content=b"documentation")
            self.assertEqual(self.recorder.record(metadata, cache), 2)
            result = metadata.read_text()
            self.assertIn(hashlib.sha256(b"source contents").hexdigest(), result)
            self.assertIn("existing-binary-checksum", result)
            self.assertNotIn("trusted-artifacts", result)
            self.assertEqual(self.recorder.record(metadata, cache), 0)
            self.assertEqual(metadata.read_text(), result)

    def test_never_adds_unpinned_components_from_the_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = self.baseline(root)
            before = metadata.read_text()
            self.archive(root / "cache", name="unrelated")
            self.assertEqual(self.recorder.record(metadata, root / "cache"), 0)
            self.assertEqual(metadata.read_text(), before)

    def test_records_pom_and_module_metadata_alongside_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = self.baseline(root)
            archive = self.archive(root / "cache")
            for extension in ("pom", "module"):
                archive.with_name(f"library-1.0.{extension}").write_bytes(extension.encode())
            self.assertEqual(self.recorder.record(metadata, root / "cache"), 3)
            for extension in ("pom", "module"):
                self.assertIn(f'library-1.0.{extension}', metadata.read_text())
            approved = metadata.read_text()
            archive.with_name("library-1.0.pom").write_bytes(b"changed metadata")
            with self.assertRaisesRegex(ValueError, "refusing to replace"):
                self.recorder.record(metadata, root / "cache")
            self.assertEqual(metadata.read_text(), approved)

    def test_existing_checksum_mismatch_does_not_change_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = self.baseline(root)
            archive = self.archive(root / "cache")
            self.recorder.record(metadata, root / "cache")
            approved = metadata.read_text()
            archive.write_bytes(b"changed source archive")
            with self.assertRaisesRegex(ValueError, "refusing to replace"):
                self.recorder.record(metadata, root / "cache")
            self.assertEqual(metadata.read_text(), approved)

    def test_conflicting_cache_copies_do_not_change_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = self.baseline(root)
            before = metadata.read_text()
            self.archive(root / "cache", folder="first")
            self.archive(root / "cache", folder="second", content=b"different bytes")
            with self.assertRaisesRegex(ValueError, "Conflicting cached bytes"):
                self.recorder.record(metadata, root / "cache")
            self.assertEqual(metadata.read_text(), before)


if __name__ == "__main__":
    unittest.main()
