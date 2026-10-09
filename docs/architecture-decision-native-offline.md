# Decision: Native Offline-First Android

This historical decision describes the original beta. The current app includes optional GPS, weather, and user-selected backups.

Withrow DeerDeck starts as a native Kotlin Android app because the core beta workflow depends on Android-native camera permissions, reliable offline storage, and fast field use.

The first architecture uses a small layered shape:

- Compose screens render state and collect user actions.
- ViewModels own screen state.
- A repository coordinates observation/session behavior.
- Room persists sessions, wildlife button definitions, observations, and photo links locally.

The beta intentionally avoids backend, account, cloud sync, GPS, and publishing work. Those can be added after the local capture loop proves useful on the deck.
