# MeshHop App — Guiding Principles

> **MeshHop exists so a stranger to mesh radio can install, connect, and
> message in minutes — offline, forever.**
>
> 1. Protocol in the app, never the firmware. Firmware stays dumb and durable.
> 2. Hide mechanics; expose outcomes. Users see "heard ✓", not "flood route ACK".
> 3. Offline-first: the app must always be useful with no radio and no internet.
> 4. No artificial limits: history, contacts, channels live in the app DB.
> 5. Layers stay separated (UI / domain / protocol / transport) and tested;
>    `:core-protocol` never imports Android.
> 6. Minimal diffs; match existing style; every change maps to a stated need.
> 7. Accessibility is a requirement: ≥48 dp targets, screen-reader labels,
>    large type support.
> 8. When features conflict, order: newcomer experience → reliability →
>    enthusiast features.

Concrete consequences, enforced in review:

- `:core-protocol` and `:core-domain` compile on the plain JVM with no
  Android SDK; their tests run everywhere (this is what keeps them ≥ 90 %
  covered).
- All on-air protocol, crypto, retry policy, and path logic live in the app.
  The radio (`meshhop-firmware`) holds raw packets and persisted settings —
  nothing else.
- Keys never leave the app: identity private keys are sealed at rest and
  never sent to a radio.
- The user-facing language rules (07 §brand voice): say "share my contact",
  "channel", "heard ✓" — never "advert", "flood route", "payload".
