# Radios

MeshPigeon works with a dumb, durable radio. The app holds all the
intelligence; the radio just receives, remembers, and transmits on demand.

## Supported boards (v1)

| Board | MCU | Radio | Connection |
|---|---|---|---|
| Seeed XIAO ESP32-S3 + Wio-SX1262 | ESP32-S3 | SX1262 | USB + Bluetooth |
| Heltec WiFi LoRa 32 V3 | ESP32-S3 | SX1262 | USB + Bluetooth |
| Seeed SenseCAP T114 | nRF52840 | SX1262 | USB (Bluetooth lands with the first bench bring-up) |

## Flashing firmware the first time

You need PlatformIO once. From the firmware repo:

```sh
pio run -e xiao_wio -t upload      # (or heltec_v3, t114)
```

After that, the app can offer firmware updates over USB (Settings →
Advanced → Update radio firmware) — no computer needed (phase 2 of the
roadmap).

## What the radio does (and doesn't)

- It **remembers**: every packet received is stored (thousands of them,
  with timestamps), so nothing is lost while your phone was away.
- It **persists its settings**: unplug it, move it, reboot it — it keeps
  listening on your region without the app.
- It **never reads your messages**. It stores raw bytes it cannot decode;
  all encryption and protocol live in the app. A stolen radio leaks nothing.
- It **never transmits on its own**. (Repeating while your phone is
  connected is an app feature — the radio only forwards what the app tells
  it to.)

## Sharing one radio between people

Several phones can connect to one radio over Bluetooth at the same time.
Whoever tunes the radio first owns the tuning for the first five minutes;
after that, if someone else's saved settings differ, everyone gets a calm
banner: *"Radio is set to EU-868. Your saved settings are US-915. [Use
radio's] [Apply mine]"*.
