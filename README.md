# OpenAgnes

Eine Android-App (Kotlin + Jetpack Compose), die die Funktionen der Agnes-App nachbildet
und dabei ausschließlich das **Agnes-AI-Gateway** (`https://apihub.agnes-ai.com/v1`) nutzt.

> Hinweis zur Abgrenzung: Dies ist eine **funktional gleichwertige Eigenentwicklung** mit
> eigenem Paketnamen (`com.openagnes.app`) und eigenem Branding. Es wurden keine Assets,
> Logos, Texte oder Code aus dem Original-APK übernommen.

## Funktionsumfang (Kern-MVP)

| Bereich | Umsetzung |
|---|---|
| Chat | Streaming über SSE, inkl. separater Anzeige des Reasoning-Streams |
| Vision | Bild anhängen und als Data-URL an `agnes-2.5-flash` senden |
| Bildgenerierung | `POST /v1/images/generations`, Größe und Modell wählbar |
| Videogenerierung | `POST /v1/videos` (`mode=text`) + Polling über `/agnesapi?video_id=` |
| Konversationen | Lokale Speicherung als JSON, anlegen/öffnen/löschen, Modellwechsel |
| Einstellungen | Basis-URL, API-Key (überschreibbar), Standardmodelle |

Modelle: `agnes-3.0-flash`, `agnes-2.5-flash`, `agnes-2.0-flash`, `agnes-2.5-pro-alpha`,
`agnes-2.5-pro-beta`, `agnes-image-2.5/2.1/2.0-flash`, `agnes-video-2.5(-flash)`.

## API-Key

Der Key wird zur Build-Zeit aus der **git-ignorierten** `.env` gelesen und in `BuildConfig`
injiziert. Ein Key in einem APK ist grundsätzlich auslesbar – für den Testbetrieb
akzeptabel, produktiv gehört der Zugriff hinter einen eigenen Proxy. In den Einstellungen
lässt sich der Key zur Laufzeit ersetzen, ohne neu zu bauen.

```
# .env  (nicht committen)
AGNES_API_KEY=sk-...
AGNES_BASE_URL=https://apihub.agnes-ai.com/v1
```

## Bauen

```bash
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.12+1-ms
export ANDROID_HOME=$PWD/.android-sdk

./gradlew :app:assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # Unit- + Live-Integrationstests
```

Toolchain: AGP 9.4.1, Gradle 9.7.1 (Wrapper ist eingecheckt), Kotlin 2.4.21
(AGP-eingebaut), Compose BOM 2026.09.00, compileSdk 37, minSdk 26, targetSdk 36, JDK 21.

## CI-Build (GitHub Actions)

`.github/workflows/build-apk.yml` baut bei Push auf `main`, bei Pull Requests und manuell
(`workflow_dispatch`) eine Debug-APK.

Einmalig im Repository einrichten:

| Name | Typ | Zweck |
|---|---|---|
| `AGNES_API_KEY` | Secret | Wird zur Build-Zeit eingebettet. Ohne Secret baut die App ohne Key. |
| `AGNES_BASE_URL` | Variable (optional) | Standard: `https://apihub.agnes-ai.com/v1` |

```bash
gh secret set AGNES_API_KEY
gh variable set AGNES_BASE_URL --body "https://apihub.agnes-ai.com/v1"
```

Der Lauf installiert JDK 21 und das Android-SDK (Versionen sind im Workflow fixiert und
werden zwischengespeichert), führt die Tests aus und lädt die Artefakte
`openagnes-debug-apk` (30 Tage) und `test-report` hoch.

> Ohne `AGNES_API_KEY`-Secret werden die fünf Live-Tests automatisch übersprungen
> (`skipped=5`) und die App erwartet den Key zur Laufzeit unter „Setup“. Mit Secret laufen
> sie tatsächlich (`skipped=0`) – dann steckt der Key aber im Artefakt, das für alle mit
> Repo-Zugriff sichtbar ist.

## Tests

* `ChatDeltaParserTest` – SSE-Parsing deterministisch, mit echten Payloads.
* `BaseUrlTest` – Regressionstest gegen doppeltes `/v1` in der Basis-URL.
* `AgnesClientLiveTest` – echte Aufrufe gegen das Gateway; überspringt sich selbst,
  wenn `AGNES_API_KEY` fehlt.

## Bekannte Grenzen

* **Nicht auf Gerät/Emulator ausgeführt.** Die Logik ist über Unit- und Live-Tests
  abgedeckt; ein Emulator ist in dieser Umgebung nicht zuverlässig lauffähig (2 CPUs,
  ~1 GB freier RAM).
* **Videogenerierung** ist serverseitig rate-limitiert; `video_queue_full` (HTTP 503)
  ist ein Dienstzustand, die App versucht es bis zu dreimal erneut.
* Der Debug-Build ist mit dem Debug-Keystore signiert, nicht release-tauglich.
* Nicht umgesetzt (bewusst außerhalb des Kern-MVP): News-Feed, Credits/Abos,
  Einladungscodes, Community/Games, Push, Tencent-IM, Widgets, LaTeX-Rendering.
