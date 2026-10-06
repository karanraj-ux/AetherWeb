# AetherWeb Four-Door Experiments

Goal: prove one installed app carries all four doors of the mesh, and that
browser guests need no install. Run after installing the CI build.

Door tags: every web-door message carries its door in the sender name —
🌐 = WiFi portal guest, 🔵 = BLE browser guest.

## Experiment A — Two phones, all four doors, chat flowing

Setup: Phone 1 (host) installs the full APK and starts its hotspot.
Phone 2 installs the full APK (needed for doors 1–2; doors 3–4 need only its browser).

1. **Door 1 — BLE + app.** Both phones: WiFi OFF, Bluetooth ON. Open the app,
   send messages both ways. Expect delivery over the BLE mesh.
2. **Door 2 — WiFi + app.** Phone 2 joins Phone 1's hotspot. Send messages both ways.
3. **Door 3 — WiFi + browser.** Phone 2 opens the portal URL in Chrome
   (http://host-ip:8080), enters a name, waits for host approval, chats.
   Host sees the sender tagged 🌐.
4. **Door 4 — BLE + browser.** Phone 2 opens https://host:8443 (accept the
   one-time certificate warning: Advanced → Proceed), taps the 🔵 button,
   picks the host in the system Bluetooth picker, chats. Host sees the
   sender tagged 🔵.

Pass: messages arrive on all four doors; 🌐/🔵 tags are correct.

## Experiment B — Hotspot full, then 5 BLE guests

Setup: fill ~10 WiFi hotspot slots with devices on the portal (door 3),
then add 5 phones via 🔵 BLE (door 4) — the showpiece: 15 chatting, 10 on WiFi.

Pass: all five BLE guests connect and their messages arrive tagged 🔵,
proving BLE guests bypass the WiFi client limit.

## Experiment C — Zero-WiFi airplane mode

Setup: guest opens the portal once while on WiFi (service worker caches the
portal shell), then enables airplane mode, turns Bluetooth back on, taps 🔵
and reconnects, then chats.

Pass: message is delivered with WiFi off — page loads from cache, chat runs
over Bluetooth only.

## Known limits (by design)

- BLE doors are text-only; voice and files stay on WiFi.
- Chrome on Android only (Web Bluetooth); Safari/iOS and Firefox excluded.
- Browser guests are leaf nodes: they never relay, advertise, or host.
- First BLE contact needs the one-tap system picker; later reconnects use the
  remembered device.
- Voice-room HTTPS portal needs the one-time certificate bypass.
