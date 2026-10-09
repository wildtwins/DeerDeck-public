# Google Play Assets

- `app-icon-512.png`: 512 x 512 store icon.
- `feature-graphic-1024x500.png`: 1024 x 500 store feature graphic.

These are derived from the app's bundled launcher artwork. Regenerate them with
`powershell -ExecutionPolicy Bypass -File .\scripts\generate-play-assets.ps1`.

Private phone screenshots are intentionally excluded, and the generator does
not import device captures. Before a store submission, create screenshots using
disposable synthetic observations and review every visible field and image.
