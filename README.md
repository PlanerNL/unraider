# Unraider

Android-app om je Unraid-server te beheren via Tailscale, met de nadruk op de status van je HDD's en SSD's.

**Talen:** Engels (standaard) en Nederlands. Kies de taal in de app onder Instellingen → Taal,
of op Android 13+ via Instellingen → Apps → Unraider → Taal. "Systeemtaal" volgt je telefoon.
Teksten staan in `res/values/strings.xml` (Engels) en `res/values-nl/strings.xml` (Nederlands);
een taal toevoegen is een extra `values-xx`-map plus een regel in `res/xml/locales_config.xml`.

## Wat zit erin (v0.1)

| Tab | Inhoud |
|---|---|
| **Schijven** | Arraystatus, totale capaciteit, parity-check (starten/pauzeren/stoppen), per schijf: status, temperatuur, slapend of draaiend, vulgraad, I/O-fouten. Tik op een schijf voor details. Array starten/stoppen. |
| **Overzicht** | Servernaam, versie, uptime, compacte schijvenlijst met statuslampjes, CPU, geheugen, ongelezen meldingen. |
| **Docker** | Containers starten, stoppen, herstarten, pauzeren; WebUI openen; update-indicator. |
| **VM's** | Starten, afsluiten, herstarten, pauzeren, geforceerd uitzetten. |
| **WebGUI** | De gewone Unraid-webinterface als vangnet voor alles wat nog niet native in de app zit. |

Statuskleuren: groen = in orde, grijs = slaapt, oranje = waarschuwing, rood = kritiek.
Een schijf krijgt een waarschuwing bij een afwijkende status, hoge temperatuur, I/O-fouten of ≥ 90% vol.

### Slapende schijven blijven slapen

Het schijvenoverzicht leest alleen de arraystatus van Unraid; dat wekt geen schijven.
SMART-details (merk, serienummer, firmware, SMART-status) haalt Unraid op met `smartctl`,
en dat kan slapende HDD's laten opspinnen. Daarom gebeurt dat alleen als je op
**SMART-details ophalen** drukt.

## Vereisten

- Unraid **7.2 of nieuwer** (API ingebouwd). Oudere versie: installeer de *Unraid Connect*-plugin.
- Tailscale op de server (in Unraid 7 ingebouwd of als plugin) en op je telefoon.
- Android 8.0 of nieuwer.

## Installeren

1. Open deze map in **Android Studio** (File → Open). Gradle synchroniseert vanzelf.
   Stelt Android Studio voor om AGP/Kotlin bij te werken, dan kan dat gerust.
2. Zet USB-debugging aan op je telefoon en druk op **Run**, of maak een APK via
   *Build → Generate App Bundles or APKs → Build APK(s)*.

### Bouwen op GitHub

De workflow in `.github/workflows/build.yml` bouwt bij elke push naar `main` een APK.
Die vind je onder **Actions → laatste run → Artifacts**. Push je een tag als `v0.1.0`,
dan komt de APK ook onder **Releases**.

Zonder extra instellingen krijg je een debug-APK. Die wordt elke keer met een andere
sleutel ondertekend, dus voor een update moet je de app eerst verwijderen. Met een eigen
sleutel heb je daar geen last van:

```bash
keytool -genkeypair -v -keystore release.jks -alias unraider \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64
```

Zet dan onder **Settings → Secrets and variables → Actions** deze vier secrets:

| Secret | Waarde |
|---|---|
| `KEYSTORE_BASE64` | inhoud van `release.jks.b64` |
| `KEYSTORE_PASSWORD` | wachtwoord van de keystore |
| `KEY_ALIAS` | `unraider` |
| `KEY_PASSWORD` | wachtwoord van de sleutel |

Bewaar `release.jks` goed en zet hem nooit in de repository.

## Eerste keer instellen

1. In Unraid: **Settings → Management Access → API → API Keys → Create**.
   - Rol **Admin**: alles kunnen bedienen.
   - Rol **Viewer**: alleen kijken (veiliger om mee te beginnen).
2. Open de app en vul in:
   - **Serveradres**: het Tailscale-IP (`100.x.y.z`) of de MagicDNS-naam (`tower.tailnet-xxxx.ts.net`).
     Zonder `http://` gebruikt de app gewoon http; Tailscale versleutelt de verbinding al.
   - **API key**: de sleutel uit stap 1.
3. Druk op **Verbinding testen** en daarna **Opslaan**.

## Problemen

| Melding | Oplossing |
|---|---|
| *Server niet gevonden* / *time-out* | Staat Tailscale aan en verbonden op je telefoon? |
| *Toegang geweigerd* | API key verkeerd of ingetrokken. |
| *Geen API gevonden* | Unraid ouder dan 7.2 en geen Unraid Connect-plugin. |
| *HTTPS-fout* | Gebruik `http://` met het Tailscale-adres. |
| Leeg Docker- of VM-tabblad | Docker/VM-manager staat uit, of de API key heeft daar geen rechten voor. |

## Opbouw

```
app/src/main/java/nl/planernl/unraider/
├── MainActivity.kt          navigatie (tabs + instellingen)
├── data/
│   ├── UnraidClient.kt      GraphQL over HTTP met x-api-key, foutvertaling
│   ├── UnraidRepository.kt  alle queries en acties
│   ├── Models.kt            datamodellen, gezondheidsregels, opmaak
│   └── SettingsStore.kt     adres + API key (DataStore)
└── ui/
    ├── MainViewModel.kt     laden, acties, meldingen
    ├── Components.kt        kaarten, balken, dialogen, auto-verversen
    ├── theme/Theme.kt
    └── screens/             Schijven, Overzicht, Docker, VM's, WebGUI, Instellingen
```

De client gebruikt geen codegeneratie maar leest de JSON direct. Vraagt de app een veld op
dat jouw Unraid-versie niet kent, dan valt hij automatisch terug op een basisquery.

Ideeën voor later: pushmeldingen bij schijfproblemen (WorkManager), widget op het startscherm,
Docker-logs, shares en meerdere servers.

---
PlanerNL
