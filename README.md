# Anshin 安心

<p align="center">
  <strong>A personal medication tracker that follows your daily rhythm.</strong><br>
  <em>安心 · 安心 · 안심 — Peace of mind, in every dose.</em>
</p>

<p align="center">
  <a href="https://github.com/Tinnci/anshin/actions/workflows/build.yml">
    <img src="https://github.com/Tinnci/anshin/actions/workflows/build.yml/badge.svg" alt="CI Build">
  </a>
  <a href="https://github.com/Tinnci/anshin/releases/latest">
    <img src="https://img.shields.io/github/v/release/Tinnci/anshin?label=Latest%20Release&color=4CAF50" alt="Latest Release">
  </a>
  <img src="https://img.shields.io/badge/Material%203-Expressive-6750A4?logo=material-design&logoColor=white" alt="Material 3">
  <img src="https://img.shields.io/badge/Min%20SDK-26-brightgreen?logo=android&logoColor=white" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
</p>

<p align="center">
  <img src="screenshots/today.png" width="170" alt="Today">
  <img src="screenshots/history.png" width="170" alt="History">
  <img src="screenshots/meds.png" width="170" alt="Medications">
  <img src="screenshots/journal.png" width="170" alt="Wellbeing journal">
  <img src="screenshots/health.png" width="170" alt="Health vitals">
</p>

A native Android app built with **Kotlin · Jetpack Compose · Material 3 Expressive** that helps you
take the right dose at the right time, keep stock in check, and track vitals in one place.

## Features

- **Today** — an "Up next" hero, grouped daily plan, one-tap Take / Skip / Undo, and streaks.
- **Precise reminders** — exact alarms with Taken / Skip actions in the notification, restored after reboot.
- **Flexible schedules** — exact times or fuzzy periods (after breakfast, bedtime…), PRN doses, travel-mode time zones.
- **History** — 30-day adherence, streaks, and a calendar heatmap.
- **Medications** — dose, form, category, stock and refill alerts, a 7,000+ drug catalog, and interaction checks.
- **Health vitals** — blood pressure, glucose, weight and more with trends and on-device smart insights.
- **Device readings** — scan monitor screens with OCR, or auto-sync a BPX1 blood pressure monitor over Bluetooth.
- **Wellbeing journal** — daily mood, symptom and side-effect notes.
- **Share & backup** — QR export/import of plans, homescreen widgets, adaptive phone/tablet layouts.
- **Languages** — English, 中文, 日本語, 한국어.

## Getting Started

Requires JDK 17+ (CI uses 21) and Android SDK 37.

```bash
git clone https://github.com/Tinnci/anshin.git
cd anshin
./gradlew installDebug      # build and install on a device/emulator
./gradlew test              # unit tests
./gradlew ktlintCheck lintDebug
```

Release signing and tagging are documented in [docs/releasing.md](docs/releasing.md).
Pushing a `vX.Y.Z` tag from `main` triggers the signed release workflow.

## Architecture

Modular monolith with MVVM + UDF and a single source of truth: Room and DataStore are durable truth;
alarms, notifications and widgets are rebuilt from them.

```
:app ─┬─ :feature:onboarding
      ├─ :capability:reminders
      ├─ :core:model · :core:database · :core:preferences · :core:ui
```

See [Architecture governance](docs/architecture-governance.md) for boundaries and verification gates, and
[Medication daily flow](docs/medication-daily-flow.md) for scheduling and dose records.

## Contributing

Fork → feature branch → PR. Run `ktlintCheck` and `lintDebug` first, follow the existing architecture,
and add tests for new domain or ViewModel logic. Translations are welcome via
`app/src/main/res/values-{lang}/`.

## License

Apache License 2.0 — see https://www.apache.org/licenses/LICENSE-2.0.
