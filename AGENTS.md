# DiaMind2 Agent Notes

## Projekt
- Android-App in Kotlin mit Jetpack Compose.
- Hauptmodul: `app`.
- Paket: `de.diamind.ai`.
- Zentrale Persistenz aktuell ueber `SharedPreferences` in `Preferences.kt`.

## Arbeitsweise
- Bestehende Struktur respektieren und nur gezielt aendern.
- Keine unaufgeforderten Refactorings.
- UI-Logik liegt hauptsaechlich in Compose-Screens unter `app/src/main/java/de/diamind/ai/ui/screens`.
- Lokale Chat- und Therapie-Logik liegt in `ai/DiaMindBrain.kt`.
- Insulin-, IOB-, SEA- und Lernlogik liegt in `insulin/InsulinAdvisor.kt`.
- xDrip- und Guardian-Logik liegt in `XDripReceiver.kt`.

## Build-Hinweise
- Projekt nutzt sehr neue Versionen: AGP/Kotlin/Compose/SDK.
- Vor Build-/Testlaeufen beachten, dass Gradle in `.gradle/` und `build/` schreibt.
- Bekannte Risiken: fehlender sichtbarer Kotlin-Android-Plugin-Alias, referenzierte `proguard-rules.pro`, Encoding/Mojibake in UI-Strings.

## DiaMind-Sicherheitsprinzip
- Keine automatische Therapieentscheidung.
- Bolus-, Korrektur- und SEA-Hinweise muessen transparent und vom Menschen bestaetigt bleiben.
- Online-KI nur mit aktiv gesetztem API-Key und klarer Nutzerentscheidung.
