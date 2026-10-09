# Public Source

This source release preserves app version 2.4.4 and its existing functionality.
The original application checkout, installed phone app, records, and private
repository remain untouched.

Excluded from the public repository:

- All prior private Git history and commit author contact metadata.
- Real phone screenshots, their observation statistics, and backup details.
- Local attachments, generated presentations, logs, build outputs, and caches.
- Camera photos, recordings, exported records, backups, and runtime databases.
- Environment files, local SDK configuration, signing keys, and credentials.

Personal email references in support text and the weather User-Agent were
replaced with the developer's public GitHub profile. The app's launcher artwork,
store branding, database schema definitions, and synthetic unit test fixtures
are source assets, not copies of runtime observation data.

The ignore rules reduce accidental additions; they do not remove files from
history and do not substitute for reviewing files before publishing. Do not
copy historical Git objects into a new public repository.

The public repository is `wildtwins/DeerDeck-public`, with fresh history and the
[MIT license](../LICENSE). The original private repository is preserved separately.

The weather request identifier uses a public website, as supported by the
[NWS API documentation](https://www.weather.gov/documentation/services-web-api).
