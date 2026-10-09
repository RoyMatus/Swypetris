"""Choose CI checks from changed paths; unknown build inputs get full regression."""

import argparse
import os
import subprocess
from pathlib import Path


PACKAGE = "ru.itoltec.swypetris."
SMOKE = "SmokeTest"

# Each entry is deliberately explicit. A new production file gets full regression
# until its dependencies are reviewed and recorded here.
SOURCE_GROUPS = {
    "AndroidGameFeedback.kt": ("feedback",),
    "ActionStyle.kt": ("navigation",),
    "DeveloperContact.kt": ("navigation", "legal"),
    "AppActionButton.kt": ("navigation",),
    "AppUpdates.kt": ("updates",),
    "ContactsScreen.kt": ("navigation", "legal"),
    "Difficulty.kt": ("engine", "settings"),
    "DigitalScore.kt": ("score",),
    "GameArt.kt": ("visual",),
    "GameBrand.kt": ("visual", "navigation"),
    "GameEngine.kt": ("engine", "line_clear"),
    "GameFeedback.kt": ("feedback",),
    "GameLayout.kt": ("visual",),
    "GameMusic.kt": ("music", "feedback"),
    "GamePalette.kt": ("visual", "settings"),
    "GameResults.kt": ("results", "storage"),
    "GameRules.kt": ("engine", "line_clear"),
    "GameStorage.kt": ("storage",),
    "GameTimer.kt": ("engine", "lifecycle"),
    "GestureController.kt": ("input",),
    "HapticPulse.kt": ("feedback",),
    "HelpScreen.kt": ("navigation", "visual"),
    "LaunchIntro.kt": ("intro",),
    "LaunchIntroMotion.kt": ("intro",),
    "LegalScreen.kt": ("legal",),
    "LineClearAnimation.kt": ("line_clear",),
    "LineClearEffects.kt": ("line_clear",),
    "LogoPieces.kt": ("intro", "visual"),
    "MusicPicker.kt": ("music", "settings"),
    "MusicSelection.kt": ("music", "settings"),
    "MenuTheme.kt": ("music",),
    "MenuSkyMotion.kt": ("visual",),
    "PixelFruit.kt": ("visual", "engine"),
    "PlaylistClock.kt": ("music",),
    "PrivacyScreen.kt": ("legal",),
    "ResultsScreen.kt": ("results",),
    "ScreenDecor.kt": ("visual",),
    "MenuTetrominoBackdrop.kt": ("visual",),
    "SettingsScreen.kt": ("settings",),
    "ShareAppDialog.kt": ("legal", "navigation"),
    "Srs.kt": ("engine",),
    "UpdateCheckPolicy.kt": ("updates",),
    "Placement.kt": ("engine",),
    "VictoryScreen.kt": ("results", "visual"),
    "VictoryMotion.kt": ("results", "visual"),
    "ui/theme/Color.kt": ("visual",),
    "ui/theme/Theme.kt": ("visual",),
    "ui/theme/Type.kt": ("visual",),
}

# Cross-cutting state and composition are intentionally full-regression inputs.
FULL_SOURCES = {
    "BackgroundCrop.kt", "BackgroundStore.kt", "BackgroundPicker.kt", "CustomBackground.kt",
    "AbsolutePostcard.kt", "AbsoluteVictoryScreen.kt",
    "GameContent.kt", "GameBoard.kt",
    "MainMenu.kt", "MenuAction.kt", "AppNavigation.kt",
    "GameSession.kt", "GameTimeline.kt", "GameViewModel.kt", "MainActivity.kt",
    "UpdateFailure.kt", "UpdateApk.kt", "UpdateDelivery.kt", "UpdateDialog.kt", "UpdateDownload.kt", "UpdateInstaller.kt",
}

GROUP_TESTS = {
    "score": ((), ("BorderlessHudTest", "HudLayeringTest", "GameUiTest")),
    "updates": (("UpdateCheckPolicyTest", "UpdateDownloadTest"), ("AppUpdatesTest", "UpdateCheckTest")),
    "engine": (("DifficultyTest", "GameEngineTest", "GameRulesTest", "SrsTest", "HiddenBufferTest", "LockDelayTest", "HoldTest", "PlacementTest", "ScoringTest", "ProgressionTest", "RewardsTest", "VictoryRulesTest", "SessionQueueBehaviorSpec"),
               ("GameTimerTest", "GameUiTest", "SessionLifecycleTest", "VictorySessionIntegrationTest", "VictoryThemeIntegrationTest")),
    "input": (("GestureControllerTest", "GestureHoldTest", "HoldTest", "PlacementTest", "ScoringTest", "ProgressionTest", "RewardsTest"), ("GestureDensityTest", "GameFeedbackIntegrationTest", "HoldIntegrationTest", "GameUiTest")),
    "line_clear": (("LineClearAnimationTest", "GameEngineTest", "GameRulesTest"), ("LineClearUiTest", "GameUiTest")),
    "feedback": (("GameFeedbackTest",), ("GameFeedbackIntegrationTest", "MusicIntegrationTest")),
    "music": (("PlaylistClockTest",), ("MusicIntegrationTest", "MusicSettingsFruitTest")),
    "storage": (("RecordHistoryTest", "SessionQueueBehaviorSpec"),
                ("MigrationTest", "SessionLifecycleTest", "SessionWriteTest", "ResultsIntegrationTest")),
    "lifecycle": (("SessionQueueBehaviorSpec",), ("ForegroundUiTest", "SessionLifecycleTest", "GameUiTest")),
    "navigation": ((), ("BrandNavigationTest", "GameUiTest", "ForegroundUiTest")),
    "intro": (("LaunchIntroMotionTest",), ("LaunchIntroTest", "LaunchIntroRecreationTest")),
    "visual": (("GameLayoutTest", "MenuSkyMotionTest", "VictoryMotionTest"),
               ("HelpHudTest", "GameUiTest", "VictoryThemeIntegrationTest", "MenuSkyTest")),
    "settings": (("DifficultyTest", "PlaylistClockTest"), ("GameUiTest", "MusicSettingsFruitTest", "StatisticsResetTest")),
    "legal": ((), ("LegalScreenTest", "PublicationTest", "ShareAppTest")),
    "results": (("RecordHistoryTest", "VictoryRulesTest", "VictoryMotionTest"),
                ("ResultsIntegrationTest", "VictorySessionIntegrationTest", "VictoryThemeIntegrationTest")),
}


def changed_paths(base: str, head: str) -> list[str]:
    subprocess.run(["git", "cat-file", "-e", f"{base}^{{commit}}"], check=True, capture_output=True)
    result = subprocess.run(
        ["git", "diff", "--name-only", "-z", "--no-renames", base, head],
        check=True, capture_output=True,
    )
    paths = [os.fsdecode(path) for path in result.stdout.split(b"\0") if path]
    if not paths:
        raise ValueError("No changed paths could be determined")
    return paths


def select(paths: list[str]) -> dict[str, str]:
    jvm: set[str] = set()
    android: set[str] = set()
    app = False
    full = False
    github_scripts = False
    release_scripts = False
    ci_scripts = False
    for raw_path in paths:
        path = raw_path.replace("\\", "/")
        name = Path(path).name
        if path == "AGENTS.md" or path.startswith((".agents/", ".codex/")) or (
            path.endswith(".md") and not path.startswith("app/")
        ):
            continue
        if path.startswith("tools/"):
            if path.startswith("tools/ci/"):
                ci_scripts = True
                full = True
                continue
            if path in {"tools/GitHub-Api.psm1", "tools/Update-Issue.ps1", "tools/Manage-PullRequest.ps1", "tools/Sync-IssueProject.ps1"} or path.startswith("tools/tests/"):
                github_scripts = True
                continue
            if path.startswith("tools/release/"):
                release_scripts = True
                continue
            full = True
            continue
        if path.startswith(".github/workflows/"):
            ci_scripts = True
            full = True
            continue
        if path.startswith("app/src/test/java/ru/itoltec/swypetris/") and name.endswith(".kt"):
            app = True
            test = path.removeprefix("app/src/test/java/ru/itoltec/swypetris/")
            if "/" in test or not Path(path).is_file():
                full = True
            else:
                jvm.add(name.removesuffix(".kt"))
            continue
        if path.startswith("app/src/androidTest/java/ru/itoltec/swypetris/") and name.endswith(".kt"):
            app = True
            test = path.removeprefix("app/src/androidTest/java/ru/itoltec/swypetris/")
            if "/" in test or not Path(path).is_file() or name == "IsolatedStorageRule.kt":
                full = True
            elif name != "EnergyScenarioTest.kt":
                android.add(name.removesuffix(".kt"))
            else:
                # Opt-in measurement is not a release correctness test.
                pass
            continue
        if path.startswith("app/src/main/java/ru/itoltec/swypetris/") and name.endswith(".kt"):
            app = True
            source = path.removeprefix("app/src/main/java/ru/itoltec/swypetris/")
            if source in FULL_SOURCES or source not in SOURCE_GROUPS:
                full = True
            else:
                for group in SOURCE_GROUPS[source]:
                    unit, device = GROUP_TESTS[group]
                    jvm.update(unit)
                    android.update(device)
            continue
        if path.startswith("app/src/main/res/raw/"):
            app = True
            group = "visual" if name == "menu_sky_registration.json" else "music" if name.endswith(".ogg") else "feedback"
            unit, device = GROUP_TESTS[group]
            jvm.update(unit)
            android.update(device)
            continue
        if path.startswith("app/src/main/res/drawable"):
            app = True
            for group in ("visual", "intro"):
                unit, device = GROUP_TESTS[group]
                jvm.update(unit)
                android.update(device)
            continue
        if path.startswith("app/src/main/assets/"):
            app = True
            for group in (("legal",) if "privacy" in name or "license" in path or "legal" in name else ("music",) if "music" in name else ("visual",)):
                unit, device = GROUP_TESTS[group]
                jvm.update(unit)
                android.update(device)
            continue
        # Manifest, Gradle, CI workflow, certificates and unknown inputs can
        # affect the whole app or the validity of the release pipeline.
        full = True
    if full:
        mode = "full"
        # Full mode runs unfiltered instrumentation. Do not expose accumulated
        # selected classes as a filter in its environment or release evidence.
        android.clear()
    elif app:
        mode = "selected"
        android.add(SMOKE)
    else:
        mode = "none"
    return {
        "mode": mode,
        "jvm": ",".join(PACKAGE + item for item in sorted(jvm)),
        "android": ",".join(PACKAGE + item for item in sorted(android)),
        "github_scripts": str(github_scripts).lower(),
        "release_scripts": str(release_scripts).lower(),
        "ci_scripts": str(ci_scripts).lower(),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        result = select(changed_paths(args.base, args.head))
    except (OSError, subprocess.CalledProcessError, ValueError):
        result = select(["unresolved-change-set"])
    with open(args.output, "a", encoding="utf-8") as output:
        for key, value in result.items():
            output.write(f"{key}={value}\n")
    print(result)


if __name__ == "__main__":
    main()
