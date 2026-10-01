"""Regression check for the Xiaomi-compatible Cyber Neon City background."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
WATCHFACE = ROOT / "cyberwatchface/src/main/res/raw/watchface.xml"
DRAWABLE = ROOT / "cyberwatchface/src/main/res/drawable"


class CyberBackgroundTest(unittest.TestCase):
    def test_visible_mode_uses_static_image_background(self) -> None:
        """The live scene must not depend solely on unsupported AGIF rendering."""
        root = ET.parse(WATCHFACE).getroot()
        image_parts = root.findall(".//PartImage")
        resources = {
            image.find("Image").attrib["resource"]
            for image in image_parts
            if image.find("Image") is not None
        }

        self.assertIn("cyber_concept_bg", resources)
        self.assertTrue((DRAWABLE / "cyber_concept_bg.jpg").is_file())


if __name__ == "__main__":
    unittest.main()
