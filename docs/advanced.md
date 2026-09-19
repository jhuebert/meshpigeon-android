# Advanced

Everything here is optional. The main screens never require it.

## Routing override (per contact)

Auto (default): direct when a path is known, flood otherwise. You can pin
Direct or Flood per contact, and see learned paths with hop counts and
success rates. A failed path is cleared automatically — the next message
re-learns.

## Path hash size

3-byte path hashes by default (best reliability in dense meshes). Downgrade
to 2 or 1 byte for legacy interop — with a warning about false positives at
density.

## Identity export & backup

- **Export identity**: an encrypted `.meshhop-identity` file (passphrase —
  use a real one) containing your keys and name. Restores on a new phone.
- **Export full backup**: identity + contacts + channels + history.
- **Cloud backup** (off by default): encrypted blobs to your own Google
  Drive app folder. The mesh works with zero internet; this is convenience
  only.

## Share my location

Adds a static position to your adverts so others see you on the map. One
line of consequence: "Others will see you on the map." Off by default.

## Protocol log

A decoded packet inspector for debugging — what came in, on what path, at
what signal. Off by default.

## Radio settings editor

Frequency/bandwidth/spreading-factor/power beyond the region presets, for
experimental or private meshes.
