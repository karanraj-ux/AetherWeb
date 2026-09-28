# 🌌 AetherWeb
### *Your phone is the network. No internet needed.*

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Offline](https://img.shields.io/badge/Calls_Chats_Games-100%25_Offline-orange.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Mesh](https://img.shields.io/badge/Network-BLE_Mesh-blueviolet.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Zero Install](https://img.shields.io/badge/For_Friends-No_App_Needed-blue.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)
[![Privacy](https://img.shields.io/badge/Cloud-Zero-success.svg?style=for-the-badge)](https://github.com/karanraj-ux/AetherWeb)

**Video calls, music, games, and chat — with zero internet. And your friends don't even need the app.**

---

## 👀 Imagine this

> You're at a protest. The internet is shut down, jammers are up. Your group chat still works — messages hopping phone-to-phone through the air.

> Your friend doesn't have the app. Doesn't matter. They scan the QR code on your screen and join straight from their browser — no install, no App Store, no sign-up.

> You're on a flight with friends. No Wi-Fi. You're on a video call, vibing to the same music in perfect sync, mid chess game — the whole cabin is your network.

That's AetherWeb. A working Android app, not a concept.

---

## 📱 What is AetherWeb?

AetherWeb turns nearby phones into their own private network using Bluetooth radio. Your phone becomes the tower, the server, and the app — all at once.

- **No internet, no cell towers, no cloud.** Phones talk directly to phones.
- **No accounts, no sign-ups, no tracking.**
- **No app needed for guests.** Anyone nearby joins from their browser via QR code.

It stands on two simple ideas:

| 📡 **Phones become the network** | 📲 **Nobody needs to install anything** |
|---|---|
| Messages, calls, and files hop from phone to phone over Bluetooth mesh — automatically upgrading to fast local Wi-Fi for heavy stuff like video. The more people join, the stronger it gets. | Your phone is also a tiny web server. Friends on iPhone, laptop, anything — they scan, tap, and they're inside your world through Chrome or Safari. |

---

## 🤯 The "woah" moments

The features people don't believe until they see them:

- 📹 **Video calls with zero internet** — face-to-face calling, WhatsApp-style, with no carrier, no mobile data, and no server in the middle.
- 🎙️ **Audio calls & voice notes** — clear voice calls and voice notes traveling through thin air.
- 🎵 **Music in perfect sync** — everyone hears the same song at the exact same time. A silent disco with no internet, anywhere on Earth.
- 🎮 **Games together** — chess, ludo, polls, and mini-games with live multiplayer. Game night no longer needs Wi-Fi.
- 💬 **Chat that can't be shut down** — group messaging with no central server, so there is nothing to block, throttle, or switch off.
- 📲 **The other person needs no APK** — the magic trick. Show your QR code, they scan it, and they're in your call, game, or page from their browser.
- 💻 **Your own corner of the web** — host web pages, tools, and game packs straight from your pocket and share them locally.

---

## 🌍 Where it shines

- ✊ **Protests & internet shutdowns** — coordinate with your group when networks are cut or jammed. No server means nothing to block.
- 🎓 **Classrooms with no Wi-Fi** — push notes, PDFs, and videos to every student's phone; collaborate live.
- ✈️ **Flights & road trips** — video calls, synced music, and games with your travel crew, completely offline.
- 🏕️ **Trekking & camping** — no signal for miles? One tap sends location pings and a loud SOS siren across every connected phone.
- 🎪 **Concerts & festivals** — 50,000 people crushing the cell towers? Find your friends and share photos phone-to-phone instead.
- 🏠 **Hostels** — movies, assignments, and music to roommates at full speed, zero mobile data burned.
- ⚡ **Blackouts & disasters** — when the grid goes down, the mesh keeps the neighborhood talking.

---

## ✨ Everything inside

- 📹 Video calls & 🎙️ audio calls — with zero internet
- 🎵 Synced group music — everyone hears it together
- 🎮 Chess, ludo, polls & mini-games — live multiplayer, offline
- 💬 Mesh group chat + voice notes
- 📁 High-speed file & media transfer, phone-to-phone
- 🎨 Shared real-time canvas — sketch together live
- 🌐 Zero-install web portal — QR code → any browser → they're in
- 📍 Off-grid SOS beacons & location pings
- 🔒 Private by design — no cloud, no accounts, no tracking

---

## 🔧 How it works (for the curious)

**The simple version:** Your phone constantly whispers to nearby phones over Bluetooth Low Energy. Messages hop phone-to-phone like a relay race until they reach their destination. For heavy stuff — video, files, music — phones automatically spin up a fast local Wi-Fi link between themselves. And your phone doubles as a tiny web server, so anyone nearby can open your pages in their browser via QR code.

**The technical version:**
- **Mesh layer** — custom BLE mesh protocol with packet framing, multi-hop routing, and a dispatcher (`protocol/`, `BleMeshManager`, `MeshRouter`)
- **Transport upgrade** — automatic handoff to high-throughput Wi-Fi sockets / hotspot links for media (`WifiSocketManager`, `HotspotManager`, `HighSpeedFileTransferManager`)
- **Local web server** — embedded Ktor (Netty) server hosting pages, tools, and the guest portal; QR-code onboarding for browser guests (`WebServerManager`, `WebPortalTemplate`)
- **Real-time sync** — live state synchronization for canvas, games, music, and calls across mesh nodes
- **Crypto** — Diffie-Hellman-derived keys for encrypted mesh traffic (`CryptoManager`)
- **Persistence** — Room database for chat history, DataStore for preferences
- **Background operation** — foreground service keeps the mesh alive on minimal battery

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

Open the project in Android Studio (it will offer to generate the Gradle wrapper on first open) and hit **Run**. Release builds are handled by CI — see `.github/workflows/android.yml`.

> 💡 **Try it with a friend:** install it on two phones, keep them nearby, and watch them discover each other — no internet. Then get a third friend *without* the app to scan your QR code and join from their browser.

---

## 🔒 Privacy

- **Zero cloud** — calls, messages, files, and music never leave the mesh
- **No accounts** — no phone numbers, no emails, no logins
- **No tracking** — no analytics, no cookies, no ads
- **Your network, your rules** — it works even when the internet doesn't

---

*AetherWeb: creation and connection — anywhere on Earth, internet or not.* 🌌
