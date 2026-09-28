# 🌌 AetherWeb
### *The Invisible Offline Web — in your pocket.*

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Offline](https://img.shields.io/badge/Works-100%25_Offline-orange.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Mesh](https://img.shields.io/badge/Network-BLE_Mesh-blueviolet.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Zero Install](https://img.shields.io/badge/Guests-No_App_Needed-blue.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Privacy](https://img.shields.io/badge/Cloud-Zero-success.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)

**Chat, share files, host websites, and play with physics — with zero internet, zero cell towers, zero cloud.** AetherWeb turns nearby phones into their own private network using silent Bluetooth radio waves.

---

## 👀 Imagine this

> You're at a protest. The internet is shut down. Jammers are up. But your group chat still works — messages hopping phone-to-phone through the air.

> You're on a flight with friends. No Wi-Fi. You're still sharing photos, playing chess, and chatting — the whole cabin is your network.

> You're trekking in the mountains. No signal for miles. One tap broadcasts your location and an SOS siren across every phone in your group.

That's AetherWeb. Not a demo, not a concept — a working Android app.

---

## 📱 What is AetherWeb?

In old physics, the **Aether** was the invisible medium believed to connect everything in space. AetherWeb brings that idea to life: your phone becomes a node in an invisible, local web woven from Bluetooth radio.

No accounts. No servers. No internet. Just phones talking directly to phones.

It packs **two breakthroughs in one app**:

| 📡 **BLE Mesh Network** | ⚛️ **Live Physics Laboratory** |
|---|---|
| Phones link through the air using low-energy Bluetooth. Messages and files hop from phone to phone, so the network grows stronger with every person who joins. | A real-time interactive physics sandbox — gravity, collisions, springs, friction — that multiple people can play with together, live. |

---

## 🌍 Real life, real use cases

Small moments where AetherWeb changes everything:

- ✊ **Protests & internet shutdowns** — When authorities cut mobile data or deploy jammers, AetherWeb keeps working. Coordinate with your group, share updates and locations — entirely off the grid, with no central server to block.
- 🎓 **Classroom with no Wi-Fi** — A teacher shares notes, PDFs, and videos directly to every student's phone. Students collaborate on a shared canvas in real time.
- ✈️ **Flights & road trips** — Stuck without connectivity? Chat, share trip photos, and play chess or ludo with your travel buddies.
- 🏕️ **Trekking & camping** — Deep in the mountains with no signal: broadcast GPS pings and a loud SOS siren across all connected phones with one tap.
- 🎪 **Concerts & festivals** — 50,000 people crushing the cell network? Share photos and find your friends phone-to-phone instead.
- 🏠 **Hostel life** — Send movies, assignments, and music to roommates at high speed without burning mobile data.
- ⚡ **Blackouts & disasters** — When towers go down in a storm or earthquake, the mesh keeps neighborhoods communicating.
- ⚛️ **Physics students** — Drop objects, tweak gravity and friction live, and watch friends across the room launch objects into your shared simulation.
- 💻 **Builders & creators** — Host a website or demo straight from your phone. Friends open it in their browser via QR code — no app install, no App Store, no login.

---

## ✨ Features

- 💬 **Mesh chat** — group messaging that hops across phones, stored locally
- 🎙️ **Voice notes, calls & live voice/video** over the local mesh
- 📁 **High-speed file transfer** — share documents, photos, and media phone-to-phone
- 🎮 **Offline mini-games** — chess, ludo, polls, and minigames with live sync
- 🎨 **Shared canvas** — sketch and brainstorm together in real time
- 🌐 **Zero-install web portal** — your phone hosts web pages; guests join from any browser via QR code
- 📍 **Off-grid SOS** — location pings and acoustic siren beacons across the mesh
- 🎵 **Shared music** — listen together, synced across devices
- 🔒 **Private by design** — everything stays between devices; no cloud, no tracking

---

## 🔧 How it works (for the curious)

**The simple version:** Your phone constantly whispers to nearby phones over Bluetooth Low Energy. When you send a message, it hops from phone to phone until it reaches its destination — like a relay race. For heavy stuff (files, voice, video), phones spin up a fast local Wi-Fi link automatically. And your phone can act as a tiny web server, so anyone nearby can open your pages in their browser.

**The technical version:**
- **Mesh layer** — Custom BLE mesh protocol with packet framing, multi-hop routing, and a dispatcher (`protocol/`, `BleMeshManager`, `MeshRouter`)
- **Transport upgrade** — Automatic handoff to high-throughput Wi-Fi socket/hotspot links for files and media (`WifiSocketManager`, `HotspotManager`, `HighSpeedFileTransferManager`)
- **Local web server** — Embedded Ktor (Netty) server hosting SPAs and a developer portal; QR-code onboarding for browser guests (`WebServerManager`, `WebPortalTemplate`)
- **Real-time sync** — State synchronization for physics, canvas, and games across mesh nodes
- **Crypto** — Diffie-Hellman-derived keys for encrypted mesh traffic (`CryptoManager`)
- **Persistence** — Room database for chat history, DataStore for preferences
- **Background operation** — Foreground service keeps the mesh alive with minimal battery drain

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3), Navigation Compose |
| Mesh & networking | Custom BLE mesh protocol, Wi-Fi Direct/sockets, Ktor server (Netty) |
| Storage | Room, DataStore |
| Media | CameraX, Coil |
| Utilities | ZXing (QR), Play Services Location, Coroutines |
| Build | Gradle (Kotlin DSL), GitHub Actions CI |

---

## 🚀 Getting started

**Requirements:** Android Studio (latest), JDK 21, an Android device or emulator (API 24+).

```bash
git clone https://github.com/karanraj-ux/AetherWeb.git
cd AetherWeb
```

Then open the project in Android Studio — it will offer to generate the Gradle wrapper on first open — and hit **Run**. For a release build, CI handles signing via GitHub Actions (see `.github/workflows/android.yml`).

> 💡 **Try it with a friend:** install the app on two phones, keep them nearby, and watch them discover each other — no internet needed.

---

## 🔒 Privacy

- **Zero cloud** — messages, files, and simulations never leave the mesh
- **No accounts** — no phone numbers, no emails, no logins
- **No tracking** — no analytics, no cookies, no ads
- **Your network, your rules** — works even when the internet doesn't

---

*AetherWeb: creation and connection — anywhere on Earth, internet or not.* 🌌
