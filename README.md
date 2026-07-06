# WhatsApp Clone - $0 Scaling Architecture (2026)

A production-grade, privacy-first messaging application built with Modern Android Development (MAD) standards.

## 🚀 Architecture Highlights
- **Language:** Kotlin 1.9+
- **UI:** Jetpack Compose (Single Activity)
- **Local DB (SSOT):** Room Database
- **Backend:** Supabase (Auth & Realtime WebSockets)
- **Networking:** Ktor
- **Security:** AES-256 GCM (Android KeyStore), Device Fingerprinting, Cloudflare Turnstile
- **Backups:** Google Drive REST API (Hidden AppData scope)
- **$0 Scaling:** Messages are transient in the cloud and stored permanently only on the device.

## 🛠 Tech Stack
- **Dagger Hilt** (DI)
- **Coroutines & Flow** (Async)
- **WorkManager** (Background Sync)
- **libphonenumber** (Phone Validation)
- **Jetpack DataStore** (Preferences)

## 📦 How to Build
1. Clone the repository.
2. Update `SUPABASE_URL` and `SUPABASE_KEY` in `NetworkModule.kt`.
3. Build and Run.

## 🛡 Security Shield
The registration flow is protected by Cloudflare Turnstile and hardware fingerprinting to prevent bot registrations and limit accounts per device.
