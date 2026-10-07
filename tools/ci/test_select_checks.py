"""Regression cases for CI path classification and report validation."""

import tempfile
import unittest
from pathlib import Path

from select_checks import FULL_SOURCES, GROUP_TESTS, SOURCE_GROUPS, select
from verify_android_results import verify


class SelectionTests(unittest.TestCase):
    def test_docs_and_ai_instructions_need_no_tests(self):
        result = select(["README.md", "AGENTS.md", ".agents/skills/demo/SKILL.md", ".codex/agents/scout.toml"])
        self.assertEqual("none", result["mode"])
        self.assertEqual("", result["android"])

    def test_app_groups_combine_and_add_smoke(self):
        result = select([
            "app/src/main/java/ru/itoltec/swypetris/GameMusic.kt",
            "app/src/main/java/ru/itoltec/swypetris/GameEngine.kt",
        ])
        self.assertEqual("selected", result["mode"])
        self.assertIn("ru.itoltec.swypetris.GameEngineTest", result["jvm"])
        self.assertIn("ru.itoltec.swypetris.MusicIntegrationTest", result["android"])
        self.assertIn("ru.itoltec.swypetris.SmokeTest", result["android"])

    def test_unknown_and_shared_inputs_get_full_regression(self):
        self.assertEqual("full", select(["app/src/main/java/ru/itoltec/swypetris/NewFeature.kt"])["mode"])
        self.assertEqual("full", select(["app/src/main/java/ru/itoltec/swypetris/new/GameEngine.kt"])["mode"])
        self.assertEqual("full", select(["app/build.gradle.kts"])["mode"])
        workflow = select([".github/workflows/ci.yml"])
        self.assertEqual("full", workflow["mode"])
        self.assertEqual("true", workflow["ci_scripts"])

    def test_digital_score_runs_layout_and_pixel_checks(self):
        result = select(["app/src/main/java/ru/itoltec/swypetris/DigitalScore.kt"])
        self.assertEqual("selected", result["mode"])
        for name in ("BorderlessHudTest", "HudLayeringTest", "GameUiTest", "SmokeTest"):
            self.assertIn("ru.itoltec.swypetris." + name, result["android"].split(","))

    def test_tools_use_own_checks(self):
        result = select(["tools/GitHub-Api.psm1", "tools/Sync-IssueProject.ps1", "tools/release/Build-Release.ps1", "tools/ci/select_checks.py"])
        self.assertEqual("none", result["mode"])
        self.assertEqual("true", result["github_scripts"])
        self.assertEqual("true", result["release_scripts"])
        self.assertEqual("true", result["ci_scripts"])

    def test_menu_sky_changes_run_motion_and_artwork_checks(self):
        for path in (
            "app/src/main/java/ru/itoltec/swypetris/MenuSkyMotion.kt",
            "app/src/main/java/ru/itoltec/swypetris/GameArt.kt",
            "app/src/main/res/drawable-nodpi/menu_cloud_day_0.png",
            "app/src/main/res/raw/menu_sky_registration.json",
        ):
            with self.subTest(path=path):
                result = select([path])
                self.assertEqual("selected", result["mode"])
                self.assertIn("ru.itoltec.swypetris.MenuSkyMotionTest", result["jvm"].split(","))
                self.assertIn("ru.itoltec.swypetris.MenuSkyTest", result["android"].split(","))

    def test_test_change_runs_itself_and_smoke(self):
        result = select(["app/src/test/java/ru/itoltec/swypetris/RewardsTest.kt"])
        self.assertEqual("selected", result["mode"])
        self.assertIn("RewardsTest", result["jvm"])
        self.assertIn("SmokeTest", result["android"])

    def test_all_current_sources_are_classified(self):
        source_dir = Path("app/src/main/java/ru/itoltec/swypetris")
        paths = {file.relative_to(source_dir).as_posix() for file in source_dir.rglob("*.kt")}
        self.assertFalse(paths - SOURCE_GROUPS.keys() - FULL_SOURCES)

    def test_mapped_test_classes_exist(self):
        unit_dir = Path("app/src/test/java/ru/itoltec/swypetris")
        device_dir = Path("app/src/androidTest/java/ru/itoltec/swypetris")
        for unit, device in GROUP_TESTS.values():
            for name in unit:
                self.assertTrue((unit_dir / f"{name}.kt").is_file(), name)
            for name in device:
                self.assertTrue((device_dir / f"{name}.kt").is_file(), name)


class AndroidReportTests(unittest.TestCase):
    def test_rejects_missing_selected_class(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / "TEST-device.xml"
            report.write_text('<testsuite><testcase classname="ru.itoltec.swypetris.SmokeTest" name="smoke"/></testsuite>')
            verify("selected", "ru.itoltec.swypetris.SmokeTest", Path(temporary))
            with self.assertRaises(ValueError):
                verify("selected", "ru.itoltec.swypetris.GameUiTest", Path(temporary))


if __name__ == "__main__":
    unittest.main()
