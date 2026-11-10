import unittest
from check_claims import check

class ClaimsGuardTest(unittest.TestCase):
    def test_forbidden(self):
        for text in ("Close apps from Recents", "dismiss cards from recents", "2 GB freed", "98% boosted", "boost", "free up 32 MB"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                check(text, "test")

    def test_honest_copy(self):
        for text in ("Force stop apps using Settings", "5 apps closed", "Music is active", "Closing 25%"):
            check(text, "test")

if __name__ == "__main__":
    unittest.main()
