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

    def test_live_hud_uses_reference_proportions_and_translucent_tech_cards(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        time_fonts = root.findall(".//DigitalClock/TimeText/Font")
        self.assertEqual(["100", "100"], [font.attrib["size"] for font in time_fonts])

        self.assertEqual([], root.findall(".//RoundRectangle"))
        self.assertGreaterEqual(len(root.findall(".//Line")), 30)
        # The reference has dark glass inside the angular outlines, not empty boxes.
        glass_panels = [
            rectangle
            for rectangle in root.findall(".//Rectangle")
            if rectangle.find("Fill") is not None
            and rectangle.find("Fill").attrib.get("color") == "#B4090D20"
        ]
        self.assertGreaterEqual(len(glass_panels), 4)

        time_parts = root.findall(".//DigitalClock")
        self.assertEqual(["56", "272"], [part.attrib["x"] for part in time_parts])

    def test_live_scene_has_two_native_second_orbits(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        transforms = root.findall(".//Group/Transform[@target='angle']")
        values = {transform.attrib["value"] for transform in transforms}
        self.assertIn("[SECOND] * 6", values)
        self.assertIn("[SECOND] * -6", values)

    def test_live_scene_has_animated_background_over_static_fallback(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        animated = root.find(".//PartAnimatedImage")
        self.assertIsNotNone(animated)
        self.assertEqual(
            "cyber_exact_anim",
            animated.find("AnimatedImage").attrib["resource"],
        )
        self.assertEqual(
            "cyber_exact_thumb",
            animated.find("Thumbnail").attrib["resource"],
        )

    def test_weather_slot_has_a_system_default_and_empty_fallback(self) -> None:
        """Weather must come from WFF's native weather data source, not a slot."""
        root = ET.parse(WATCHFACE).getroot()
        self.assertIsNone(root.find(".//ComplicationSlot[@displayName='weather_slot']"))
        source = WATCHFACE.read_text()
        self.assertIn("[WEATHER.IS_AVAILABLE]", source)
        self.assertIn("[WEATHER.TEMPERATURE]", source)
        self.assertIn("[WEATHER.CONDITION_NAME]", source)

    def test_watch_face_uses_wff_v2_for_native_weather(self) -> None:
        manifest = ROOT / "cyberwatchface/src/main/AndroidManifest.xml"
        build = ROOT / "cyberwatchface/build.gradle.kts"
        self.assertIn('android:value="2"', manifest.read_text())
        self.assertIn("minSdk = 34", build.read_text())

    def test_date_is_large_enough_to_read_under_the_time(self) -> None:
        root = ET.parse(WATCHFACE).getroot()
        date_font = root.find(".//PartText[@x='105'][@y='246']/Text/Font")
        self.assertIsNotNone(date_font)
        self.assertEqual("24", date_font.attrib.get("size"))


if __name__ == "__main__":
    unittest.main()
