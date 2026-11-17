#!/usr/bin/python3
"""Root-owned, fixed-command deployment entrypoint. Input is bounded JSON on stdin."""
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

ROOT = Path('/opt/weatherhood-dev')
REPOSITORY = 'ghcr.io/kdywjs/weatherhood-server'


def run(args, env, input_text=None, timeout=300):
    result = subprocess.run(args, input=input_text, text=True, capture_output=True,
                            env=env, timeout=timeout, cwd=ROOT)
    if result.returncode:
        # Docker output may contain runtime configuration; do not echo it to CI.
        raise RuntimeError('Command failed: ' + ' '.join(args[:3]))
    return result.stdout.strip()


def write_json(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.chmod(0o600)
    temporary.replace(path)


def deploy(request):
    digest, revision = request['digest'], request['revision']
    mode = request['mode']
    if not re.fullmatch(r'sha256:[a-f0-9]{64}', digest):
        raise ValueError('Invalid digest')
    if not re.fullmatch(r'[a-f0-9]{40}', revision):
        raise ValueError('Invalid revision')
    if mode not in ('auto', 'manual'):
        raise ValueError('Invalid deployment mode')
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}', request['actor']):
        raise ValueError('Invalid registry actor')
    token = request.pop('token')
    if not isinstance(token, str) or not 1 <= len(token) <= 2048:
        raise ValueError('Invalid registry token')
    image = REPOSITORY + '@' + digest
    state_path = ROOT / 'state.json'
    state = json.loads(state_path.read_text()) if state_path.exists() else {'history': [], 'current': None}
    previous = state['current']
    history = state['history']
    if mode == 'manual' and not any(item['image'] == image for item in history):
        raise ValueError('Manual deployment requires a retained successful image')
    with tempfile.TemporaryDirectory(prefix='registry-', dir=ROOT) as config:
        env = {'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root',
               'DOCKER_CONFIG': config, 'WEATHER_IMAGE': image}
        compose = ['docker', 'compose', '--env-file', str(ROOT / '.env'),
                   '-f', str(ROOT / 'compose.yaml')]

        def dc(*args, timeout=300):
            return run(compose + list(args), env, timeout=timeout)

        def migration_history():
            exists = dc('exec', '-T', 'postgres', 'psql', '-U', 'weatherhood', '-d', 'weatherhood',
                        '-Atc', "select to_regclass('public.flyway_schema_history') is not null")
            if exists == 'f':
                return []
            raw = dc('exec', '-T', 'postgres', 'psql', '-U', 'weatherhood', '-d', 'weatherhood',
                     '-Atc', 'select coalesce(json_agg(t order by installed_rank),\'[]\'::json) '
                     'from (select installed_rank, version, description, type, script, checksum, success '
                     'from flyway_schema_history) t')
            result = json.loads(raw)
            if any(not item['success'] for item in result):
                raise RuntimeError('Failed migration blocks rollback')
            return result

        def ready():
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                try:
                    dc('exec', '-T', 'server', 'curl', '--fail', '--silent', '--max-time', '3',
                       'http://localhost:8080/healthz', timeout=5)
                    return True
                except (RuntimeError, subprocess.TimeoutExpired):
                    time.sleep(min(5, max(0, deadline - time.monotonic())))
            return False

        run(['docker', 'login', 'ghcr.io', '-u', request['actor'], '--password-stdin'], env,
            input_text=token)
        token = None
        run(['docker', 'pull', image], env)
        label = run(['docker', 'image', 'inspect', '--format',
                     '{{index .Config.Labels "org.opencontainers.image.revision"}}', image], env)
        if label != revision:
            raise ValueError('Image revision does not match request')
        dc('up', '-d', '--wait', '--wait-timeout', '120', 'postgres')
        before = migration_history()
        if mode == 'manual':
            target = next(item for item in history if item['image'] == image)
            if target['migrations'] != before:
                raise ValueError('Schema history differs; deploy a fix instead of rolling back')
        started = time.time()
        write_json(ROOT / 'in-flight.json', {'image': image, 'revision': revision, 'started': started})
        try:
            dc('up', '-d', '--no-deps', 'server')
            if not ready():
                raise RuntimeError('Readiness failed within 120 seconds')
            migrations = migration_history()
        except (RuntimeError, subprocess.TimeoutExpired):
            # Stop the new app before comparing histories so a running migration cannot race rollback.
            dc('stop', 'server')
            after = migration_history()
            if previous and before == after == previous['migrations']:
                env['WEATHER_IMAGE'] = previous['image']
                dc('up', '-d', '--no-deps', 'server')
                if not ready():
                    raise RuntimeError('Deployment and rollback readiness failed')
                print('Previous image restored', flush=True)
            raise
        current = {'image': image, 'revision': revision, 'migrations': migrations,
                   'completed': time.time(), 'durationSeconds': round(time.time() - started, 2)}
        retained = ([current] + [item for item in history if item['image'] != image])[:3]
        write_json(state_path, {'current': current, 'history': retained})
        (ROOT / 'in-flight.json').unlink(missing_ok=True)
        # Only remove Weatherhood images previously recorded by this deployment tool.
        for item in history:
            if item['image'] not in {kept['image'] for kept in retained}:
                try:
                    run(['docker', 'image', 'rm', item['image']], env)
                except RuntimeError:
                    print('Retired local image cleanup deferred', flush=True)
        print(json.dumps({'status': 'deployed', 'revision': revision, 'image': image,
                          'durationSeconds': current['durationSeconds'],
                          'retained': [item['image'] for item in retained]}), flush=True)


def main():
    os.umask(0o077)
    raw = sys.stdin.buffer.read(8193)
    if len(raw) > 8192:
        raise ValueError('Request too large')
    with (ROOT / 'deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        deploy(json.loads(raw))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Deployment failed: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
