# Contributing to MeshPigeon App

Thanks for helping make mesh messaging approachable!

## Pull requests

1. Keep changes minimal and mapped to a stated need. No drive-by refactors.
2. Every behavior change adds or extends a test named for the requirement
   it protects (traceability in docs/testing-map.md as the suite grows).
3. Run the checks before pushing:

   ```sh
   ./gradlew test              # JVM unit tests (protocol/domain/transport)
   ./gradlew :app:assembleDebug
   ./gradlew lint              # Android lint
   ```

4. Kotlin style: official Kotlin code style, 4-space indent, match the
   existing voice in each layer. UI strings always go in
   `app/src/main/res/values/strings.xml` (i18n from day one, 06 §7).

## Layer rules (review gates)

- `:core-protocol` / `:core-domain` must compile on the plain JVM — never
  import Android there.
- The protocol layer never touches the DB; the domain layer translates
  protocol events into storage and UI state.
- Nothing above `:core-transport` knows whether the radio is BLE, USB, or
  Wi-Fi.
- User-facing wording follows GUIDING-PRINCIPLES.md: "share my contact",
  "heard ✓", "channel" — never jargon in primary UI.

## Conventional commits

`feat:`, `fix:`, `docs:`, `test:`, `chore:` — one logical change per commit.

## Reporting issues

Include app version, radio board + firmware version (`GET_INFO`), and what
you saw vs expected. For connectivity bugs: region preset and whether USB
or BLE.
