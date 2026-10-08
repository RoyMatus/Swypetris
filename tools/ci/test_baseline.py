import unittest

from verify_baseline import issue_ids, verify, verify_initial


def xml(*ids):
    return "<SmellBaseline><ManuallySuppressedIssues/><CurrentIssues>" + "".join(
        f"<ID>{item}</ID>" for item in ids
    ) + "</CurrentIssues></SmellBaseline>"


class BaselineTests(unittest.TestCase):
    def test_initial_snapshot_cannot_be_replaced(self):
        with self.assertRaisesRegex(ValueError, "reviewed legacy snapshot"):
            verify_initial(xml("new").encode())

    def test_unchanged_and_removed_debt_are_allowed(self):
        verify(xml("existing"), xml("existing"))
        verify(xml(), xml("existing"))

    def test_new_debt_cannot_be_baselined(self):
        with self.assertRaisesRegex(ValueError, "cannot grow"):
            verify(xml("existing", "new"), xml("existing"))

    def test_invalid_or_duplicate_ids_are_rejected(self):
        for value in ("<anything/>", xml(""), xml("same", "same")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                issue_ids(value)

    def test_manual_suppressions_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Manual"):
            issue_ids(xml().replace("<ManuallySuppressedIssues/>",
                                   "<ManuallySuppressedIssues><ID>new</ID></ManuallySuppressedIssues>"))
