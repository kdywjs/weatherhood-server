"""Remove only old Weatherhood GHCR versions outside the retained successful set."""
from datetime import datetime, timedelta, timezone
import json
import os
import subprocess
import tempfile


def manifest_dependencies(images, environment):
    """Keep platform manifests and attestations referenced by a retained image index."""
    protected = set(images)
    pending = list(images)
    while pending:
        digest = pending.pop()
        manifest = json.loads(subprocess.check_output(
            ['docker', 'manifest', 'inspect', 'ghcr.io/kdywjs/weatherhood-server@' + digest],
            env=environment, text=True))
        for child in manifest.get('manifests', []):
            if child['digest'] not in protected:
                protected.add(child['digest'])
                pending.append(child['digest'])
    return protected


def main():
    retained = {image.split('@', 1)[1] for image in json.loads(os.environ['RETAINED_IMAGES'])}
    retired = {image.split('@', 1)[1] for image in json.loads(os.environ['RETIRED_IMAGES'])}
    if not retained or len(retained) > 3:
        raise ValueError('Refusing cleanup without a valid retained image set')
    endpoint = 'orgs/kdywjs/packages/container/weatherhood-server/versions'
    response = subprocess.check_output(['gh', 'api', '--paginate', '--slurp', endpoint], text=True)
    cutoff = datetime.now(timezone.utc) - timedelta(days=7)
    versions = [version for page in json.loads(response) for version in page]
    candidates = [version for version in versions if version['name'] not in retained and
                  (version['name'] in retired or
                   datetime.fromisoformat(version['created_at'].replace('Z', '+00:00')) < cutoff)]
    if not candidates:
        return
    candidate_names = {version['name'] for version in candidates}
    roots = retained | {version['name'] for version in versions if version['name'] not in candidate_names}
    with tempfile.TemporaryDirectory(prefix='weatherhood-registry-') as directory:
        environment = dict(os.environ, DOCKER_CONFIG=directory)
        subprocess.run(['docker', 'login', 'ghcr.io', '-u', os.environ['GITHUB_ACTOR'], '--password-stdin'],
                       input=os.environ['GH_TOKEN'], text=True, env=environment, check=True,
                       stdout=subprocess.DEVNULL)
        protected = manifest_dependencies(roots, environment)
    for version in candidates:
        if version['name'] in protected:
            continue
        subprocess.run(['gh', 'api', '--method', 'DELETE', endpoint + '/' + str(version['id'])], check=True)
        print('Removed expired unused image version', version['id'])


if __name__ == '__main__':
    main()
