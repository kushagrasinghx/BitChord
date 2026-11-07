"""Model replacement checks using real exports in an isolated temporary checkout."""
import contextlib
import importlib.util
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import automix_models as models


@unittest.skipUnless(importlib.util.find_spec("onnx"), "Install onnx to check candidate exports")
class ModelInstallerTest(unittest.TestCase):
    def test_installs_both_model_types_without_changing_the_other_weights(self):
        original = json.loads(models.MANIFEST.read_text(encoding="utf-8"))
        scratch = models.ROOT / ".tmp_verify"
        scratch.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="automix-install-test-", dir=scratch) as temporary:
            root = Path(temporary).resolve()
            self.assertTrue(root.is_relative_to(scratch.resolve()))
            manifest_path = root / "manifest.json"
            manifest_path.write_text(json.dumps(original), encoding="utf-8")
            directories = [root / "android", root / "web"]
            for directory in directories:
                directory.mkdir()
                for spec in original.values():
                    if isinstance(spec, dict):
                        shutil.copyfile(models.ASSET_DIRS[0] / spec["asset"], directory / spec["asset"])
            sources = {key: models.ASSET_DIRS[0] / original[key]["asset"] for key in ("beat", "vocal")}
            with patch.multiple(models, ROOT=root, MANIFEST=manifest_path, ASSET_DIRS=directories,
                                KOTLIN=root / "AutomixModels.kt", TYPESCRIPT=root / "modelManifest.ts"):
                with contextlib.redirect_stdout(io.StringIO()):
                    models.write_bindings(original)
                    for key in ("beat", "vocal"):
                        args = ["automix_models.py", f"install-{key}", str(sources[key]), "--checkpoint", original[key]["checkpoint"]]
                        with patch("sys.argv", args):
                            models.main()
                        installed = json.loads(manifest_path.read_text(encoding="utf-8"))
                        self.assertEqual(original[key]["sha256"], installed[key]["sha256"])
                        for directory in directories:
                            self.assertFalse((directory / original[key]["asset"]).exists())
                            self.assertTrue((directory / installed[key]["asset"]).exists())
                    with patch("sys.argv", ["automix_models.py", "check", "--onnx"]):
                        models.main()

    def test_invalid_export_is_rejected_before_any_bundled_file_is_touched(self):
        scratch = models.ROOT / ".tmp_verify"
        scratch.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="automix-invalid-test-", dir=scratch) as temporary:
            root = Path(temporary).resolve()
            self.assertTrue(root.is_relative_to(scratch.resolve()))
            candidate = root / "bad.onnx"
            candidate.write_bytes(b"not an ONNX model")
            original_manifest = models.MANIFEST.read_bytes()
            with patch.object(models, "ASSET_DIRS", [root / "absent-android", root / "absent-web"]):
                with patch("sys.argv", ["automix_models.py", "install-beat", str(candidate), "--checkpoint", "final1"]):
                    with self.assertRaises(Exception):
                        models.main()
            self.assertEqual(original_manifest, models.MANIFEST.read_bytes())
            self.assertFalse((root / "absent-android").exists())


if __name__ == "__main__":
    unittest.main()
