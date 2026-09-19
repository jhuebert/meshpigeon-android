# Troubleshooting

## My message shows ✓ but nobody replied

✓ only means your radio keyed up. **✓ heard** means a repeater relayed it,
and ✓✓ means the recipient confirmed. If you see ✓ for a while:

1. Check the conversation's region matches everyone else's (Settings →
   Region).
2. Try again from higher ground / near a window.
3. Long-press → Retry. Late confirmations still flip the message to ✓✓
   within a 30-second window.

## Radio not found

- USB: use a data cable (not charge-only), and accept the Android
  permission prompt.
- Bluetooth: make sure the radio is powered and within range; the app
  scans for MeshHop radios only.
- The radio was just powered on: it needs a few seconds to boot and start
  advertising.

## "Radio is set to EU-868. Your saved settings are US-915…"

Someone tuned the shared radio differently. Pick **Use radio's** to match
them, or **Apply mine** if you know the group agreed on your settings.

## Region questions

A region is just a bundle of radio settings (frequency, bandwidth,
spreading factor, power). Everyone who wants to talk to each other must use
the same one. It has nothing to do with your phone's language or locale.

## My message was "too long"

Messages travel over slow radio air. The composer counts characters for the
current channel; over the budget, split your message — or send a photo the
normal internet way when you're back online (MeshHop is off-grid first).
