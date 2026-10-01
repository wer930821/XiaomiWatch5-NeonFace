"""Regression checks for foreground and background update discovery."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "phone-updater/src/main/java/com/agoose/neonupdater/MainActivity.kt"
WORKFLOW = ROOT / ".github/workflows/build-latest.yml"


class UpdateRefreshTest(unittest.TestCase):
    def test_checks_when_returning_to_foreground_and_every_fifteen_minutes(self) -> None:
        source = ACTIVITY.read_text()
        self.assertIn("override fun onResume()", source)
        self.assertIn("updaterViewModel.checkUpdaterUpdate(silent = true)", source)
        self.assertIn("PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES)", source)

    def test_release_bumps_the_updater_version_for_existing_users(self) -> None:
        workflow = WORKFLOW.read_text()
        self.assertIn("UPDATER_VERSION_CODE: 1001", workflow)
        self.assertIn('UPDATER_VERSION_NAME: "2.0.1"', workflow)


if __name__ == "__main__":
    unittest.main()
