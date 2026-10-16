import unittest

from verify_release_metadata import (
    expected_release_version,
    parse_apk_badging,
    validate_release_version,
)


class ReleaseMetadataTests(unittest.TestCase):
    def test_parses_apk_package_and_version(self):
        output = (
            "package: name='com.mihirkumar.dayflow' versionCode='217' "
            "versionName='1.0.217' platformBuildVersionName='15'\n"
            "sdkVersion:'26'\n"
        )
        self.assertEqual(
            ("com.mihirkumar.dayflow", 217, "1.0.217"),
            parse_apk_badging(output),
        )

    def test_rejects_missing_manifest_version_fields(self):
        with self.assertRaisesRegex(ValueError, "Could not read package/version"):
            parse_apk_badging("package: name='com.mihirkumar.dayflow'")

    def test_expected_release_version_uses_positive_run_number(self):
        self.assertEqual((217, "1.0.217"), expected_release_version(217))
        with self.assertRaisesRegex(ValueError, "must be positive"):
            expected_release_version(0)

    def test_rejects_mismatch_between_apk_and_run_number(self):
        with self.assertRaisesRegex(ValueError, "APK version mismatch"):
            validate_release_version("com.mihirkumar.dayflow", 217, "1.0.217", 218)

    def test_rejects_an_unexpected_package(self):
        with self.assertRaisesRegex(ValueError, "unexpected package"):
            validate_release_version("com.example.other", 217, "1.0.217", 217)


if __name__ == "__main__":
    unittest.main()
