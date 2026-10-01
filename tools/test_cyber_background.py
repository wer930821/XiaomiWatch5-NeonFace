"""Regression check for the Xiaomi-compatible Cyber Neon City background."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

from PIL import Image


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
        background = DRAWABLE / "cyber_concept_bg.jpg"
        self.assertTrue(background.is_file())
        with Image.open(background) as image:
            image.load()
            self.assertEqual((480, 480), image.size)

    def test_ambient_fallback_does_not_cover_the_visible_background(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        fallback = root.find(".//PartDraw")
        self.assertIsNotNone(fallback)
        self.assertEqual("0", fallback.attrib.get("alpha"))

    def test_live_hud_uses_large_time_and_tight_tech_frames(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        time_fonts = root.findall(".//DigitalClock/TimeText/Font")
        self.assertEqual(["108", "108"], [font.attrib["size"] for font in time_fonts])

        frames = root.findall(".//RoundRectangle")
        self.assertTrue(frames)
        self.assertTrue(all(int(frame.attrib["cornerRadius"]) <= 8 for frame in frames))

    def test_live_scene_has_two_native_second_orbits(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        transforms = root.findall(".//Group/Transform[@target='angle']")
        values = {transform.attrib["value"] for transform in transforms}
        self.assertIn("[SECOND] * 6", values)
        self.assertIn("[SECOND] * -6", values)


if __name__ == "__main__":
    unittest.main()
