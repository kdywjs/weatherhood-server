import json
import unittest
from unittest.mock import patch

from cleanup_images import manifest_dependencies


class ManifestRetentionTest(unittest.TestCase):
    def test_retained_index_protects_platform_manifest_and_attestation(self):
        responses = [json.dumps({'manifests': [{'digest': 'platform'}, {'digest': 'attestation'}]}), '{}', '{}']
        with patch('cleanup_images.subprocess.check_output', side_effect=responses):
            self.assertEqual(manifest_dependencies({'index'}, {}), {'index', 'platform', 'attestation'})


if __name__ == '__main__':
    unittest.main()
