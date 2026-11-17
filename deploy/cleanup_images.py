"""Remove only old Weatherhood GHCR versions outside the retained successful set."""
from datetime import datetime, timedelta, timezone
import json
import os
import subprocess


def main():
    retained = {image.split('@', 1)[1] for image in json.loads(os.environ['RETAINED_IMAGES'])}
    retired = {image.split('@', 1)[1] for image in json.loads(os.environ['RETIRED_IMAGES'])}
    if not retained or len(retained) > 3:
        raise ValueError('Refusing cleanup without a valid retained image set')
    endpoint = 'orgs/kdywjs/packages/container/weatherhood-server/versions'
    response = subprocess.check_output(['gh', 'api', '--paginate', '--slurp', endpoint], text=True)
    cutoff = datetime.now(timezone.utc) - timedelta(days=7)
    for page in json.loads(response):
        for version in page:
            if version['name'] in retained:
                continue
            created = datetime.fromisoformat(version['created_at'].replace('Z', '+00:00'))
            if version['name'] not in retired and created >= cutoff:
                continue
            subprocess.run(['gh', 'api', '--method', 'DELETE', endpoint + '/' + str(version['id'])], check=True)
            print('Removed expired unused image version', version['id'])


if __name__ == '__main__':
    main()
