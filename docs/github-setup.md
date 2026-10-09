# GitHub Setup

Public source repository: <https://github.com/wildtwins/DeerDeck-public>.

```sh
git clone https://github.com/wildtwins/DeerDeck-public.git
cd DeerDeck-public
```

Follow the JDK 17 and Android SDK setup in the root README, then run
`scripts/validate.ps1` on Windows or the documented Gradle commands on Linux/macOS.

The Android CI workflow runs unit tests, release lint, an unsigned AAB build,
and a debug APK build for pushes to `main` and pull requests. Inspect the run
for the exact commit being tested. CI does not install the app or run device tests.

This repository starts with a reviewed source snapshot under the MIT license.
Use a GitHub noreply email address for public commits. Review staged files before
pushing; ignore rules cannot remove data already committed to history. Do not
copy private `.git` state, mirror-push another repository, or push local agent
checkpoint refs into this repository.
