"""Exercise deployment validation and rollback decisions without Docker or credentials."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import deploy


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.root_patch = patch.object(deploy, 'ROOT', self.root)
        self.root_patch.start()
        self.request = {'digest': 'sha256:' + 'a' * 64, 'revision': 'b' * 40,
                        'actor': 'test-user', 'token': 'test-token', 'mode': 'auto'}
        self.migrations = [{'installed_rank': 1, 'success': True}]
        self.calls = []

    def tearDown(self):
        self.root_patch.stop()
        self.directory.cleanup()

    def command(self, args, env, **kwargs):
        self.calls.append((args, env['WEATHER_IMAGE']))
        if args[:3] == ['docker', 'image', 'inspect']:
            return self.request['revision']
        if 'select to_regclass' in args[-1]:
            return 't'
        if 'json_agg' in args[-1]:
            return json.dumps(self.migrations)
        return ''

    def test_invalid_digest_never_calls_docker(self):
        self.request['digest'] = 'latest; touch /tmp/invalid'
        with patch.object(deploy, 'run') as run:
            with self.assertRaises(ValueError):
                deploy.deploy(self.request)
            run.assert_not_called()

    def test_unknown_manual_image_is_rejected(self):
        self.request['mode'] = 'manual'
        with patch.object(deploy, 'run') as run:
            with self.assertRaises(ValueError):
                deploy.deploy(self.request)
            run.assert_not_called()

    def test_success_records_three_unique_images_and_cleans_registry_credentials(self):
        history = [{'image': deploy.REPOSITORY + '@sha256:' + str(i) * 64,
                    'migrations': self.migrations} for i in range(1, 5)]
        deploy.write_json(self.root / 'state.json', {'current': history[0], 'history': history})
        with patch.object(deploy, 'run', side_effect=self.command):
            deploy.deploy(self.request)
        state = json.loads((self.root / 'state.json').read_text())
        self.assertEqual(len(state['history']), 3)
        self.assertEqual(state['current']['revision'], 'b' * 40)
        self.assertFalse(list(self.root.glob('registry-*')))
        self.assertFalse((self.root / 'in-flight.json').exists())

    def test_manual_schema_difference_never_replaces_server(self):
        self.request['mode'] = 'manual'
        image = deploy.REPOSITORY + '@' + self.request['digest']
        history = [{'image': image, 'migrations': []}]
        deploy.write_json(self.root / 'state.json', {'current': history[0], 'history': history})
        with patch.object(deploy, 'run', side_effect=self.command):
            with self.assertRaises(ValueError):
                deploy.deploy(self.request)
        self.assertFalse(any('--no-deps' in args for args, _ in self.calls))

    def test_failed_readiness_restores_previous_image_only_when_schema_matches(self):
        previous = {'image': deploy.REPOSITORY + '@sha256:' + 'c' * 64,
                    'migrations': self.migrations}
        deploy.write_json(self.root / 'state.json', {'current': previous, 'history': [previous]})
        with patch.object(deploy, 'run', side_effect=self.command), \
                patch.object(deploy.time, 'monotonic', side_effect=[0, 121, 200, 201]):
            with self.assertRaises(RuntimeError):
                deploy.deploy(self.request)
        starts = [image for args, image in self.calls if '--no-deps' in args]
        self.assertEqual(starts, [deploy.REPOSITORY + '@' + self.request['digest'], previous['image']])


if __name__ == '__main__':
    unittest.main()
