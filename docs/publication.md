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

The public gallery was later captured from the actual app UI under a host test
renderer using only invented records. Its fixture, capture method, and checksums
are documented in [the gallery](gallery/README.md). No private device screenshot
was reused. The preview APK is the original audited CI artifact, not a build
containing the fictional fixture; see [preview provenance](try-preview.md).

The weather request identifier uses a public website, as supported by the
[NWS API documentation](https://www.weather.gov/documentation/services-web-api).
