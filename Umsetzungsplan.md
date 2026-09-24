# Lumen – Umsetzungsplan

> **Zweck:** Arbeitsplan für einen beliebigen KI-Coding-Agenten zur schrittweisen Umsetzung des Lumen Discord-Bot-MVP.
> **Projekt:** Illunium / Lumen
> **Scope:** Community-Grundfunktionen, Minecraft-/Kingdoms-Integration und Monitoring.
> **Grundsatz:** Erst ein stabiles, produktiv nutzbares MVP bauen. Keine unnötige Plattform- oder Framework-Komplexität vorziehen.

---

## 0. Verbindliche Regeln für den KI-Agenten

Diese Regeln gelten für die gesamte Umsetzung und haben Vorrang vor persönlichen Präferenzen des Agenten.

### 0.1 Vor jeder Änderung

1. Repository-Struktur vollständig prüfen.
2. `README`, bestehende Build-Dateien, CI-Konfiguration, `.gitignore`, vorhandene Source-Sets und Tests lesen.
3. Bestehende Architektur und Konventionen weiterverwenden, sofern sie dem MVP nicht widersprechen.
4. Keine bereits funktionierende Struktur ohne konkreten technischen Grund ersetzen.
5. Keine Secrets, Tokens, IDs oder produktiven Zugangsdaten in Git schreiben.
6. Vor größeren Änderungen prüfen, ob sie wirklich für das aktuelle MVP erforderlich sind.

### 0.2 Arbeitsweise

- Änderungen in kleinen, nachvollziehbaren Schritten umsetzen.
- Nach jedem sinnvollen Teilabschnitt Build und relevante Tests ausführen.
- Fehler nicht mit Workarounds verdecken, wenn eine kleine saubere Lösung möglich ist.
- Keine unnötigen Abstraktionsschichten für hypothetische spätere Anforderungen bauen.
- Keine Multi-Guild-/SaaS-Architektur vorbereiten.
- Keine Microservices einführen.
- Keine externe Message Queue einführen.
- Keine Weboberfläche bauen, solange sie nicht explizit später angefordert wird.
- Keine Minecraft-Gameplay-Logik in den Discord-Bot verschieben.
- Lumen spiegelt Kingdoms-Zustände; das Kingdoms-System bleibt Source of Truth für Gameplay und Matchzustände.

### 0.3 Git- und Commit-Regeln

**Alle Commits müssen immer:**

- auf **Englisch** geschrieben sein,
- **kurz** sein,
- genau die wesentliche Änderung beschreiben,
- ohne unnötige Erklärung im Commit-Titel auskommen.

Beispiele:

```text
add ticket lifecycle
add minecraft status command
implement account linking
fix kingdom role sync
add monitoring deduplication
```

Nicht verwenden:

```text
Implemented a complete and robust ticket management system with several improvements
```

Zusätzlich verbindlich:

- **Keine `Co-authored-by`-Zeilen.**
- Keine AI-/Agent-Autorenschaft in Commit Messages.
- Keine `Generated-by`, `Assisted-by` oder vergleichbare Trailer.
- Keine personenbezogenen Autorenhinweise ergänzen.
- Bestehende Git-Historie nicht umschreiben, außer dies wurde ausdrücklich verlangt.
- Keine Force-Pushes ausführen.

### 0.4 Sprache im Projekt

- Code, Klassen, Methoden, Variablen und technische Bezeichner: **Englisch**.
- Commit Messages: **Englisch**.
- Logs: vorzugsweise Englisch.
- Benutzertexte in Discord dürfen passend zur Illunium-Community auf Deutsch formuliert werden.
- Interne Eventtypen und API-Felder bleiben Englisch.

---

## 1. MVP-Ziel

Lumen soll die erste produktiv nutzbare eigene Discord-Bot-Basis für Illunium werden.

Der MVP soll insbesondere ermöglichen:

- Discord-Rollen und Self Roles zu verwalten,
- einfache Tickets ohne externen Ticket-Bot zu betreiben,
- grundlegende Moderationsbefehle bereitzustellen,
- Minecraft-Serverstatus anzuzeigen,
- Discord- und Minecraft-Accounts zu verknüpfen,
- Kingdom-Rollen automatisch zu synchronisieren,
- Kingdoms-Matchzustände und Ergebnisse in Discord zu spiegeln,
- Match-Erinnerungen zu senden,
- Minecraft-/Service-Ausfälle zu melden,
- technische und administrative Bot-Logs zentral zu sammeln.

Der MVP ist **keine vollständige Ablösung aller vorhandenen Drittanbieter-Bots**. Die Ablösung erfolgt Modul für Modul erst dann, wenn das jeweilige Lumen-Modul im echten Betrieb stabil funktioniert.

---

## 2. Nicht-Ziele des MVP

Folgendes wird bewusst **nicht** in Version 1 gebaut:

- vollständiger Carl-bot-Ersatz,
- komplexes AutoMod,
- Regex-/Spamfilter-Engine,
- KI-Moderation,
- Musik,
- Economy,
- Level-/XP-System,
- Giveaways,
- komplexes Web-Dashboard,
- Website-Accounts,
- Workflow-Builder,
- Multi-Guild-/SaaS-Unterstützung,
- vollautomatisches Minecraft-Server-Management,
- öffentliche Start-/Stop-/Restart-Befehle für Minecraft,
- direkte Minecraft-Konsole über Discord,
- Redis nur „für später“,
- Message Broker nur „für später“.

Wenn eine dieser Funktionen während der Umsetzung praktisch erscheint, kommt sie in den Post-MVP-Backlog statt in den aktuellen Scope.

---

## 3. Technische Zielrichtung

Wenn das Repository noch keinen abweichenden, bereits sinnvoll etablierten Stack enthält, gilt als Ziel:

```text
Java 25
JDA
Gradle Kotlin DSL
SQLite
```

### 3.1 Grundprinzipien

- Ein deploybares Bot-Artefakt.
- Modularer Monolith statt Microservices.
- SQLite für den MVP.
- Secrets ausschließlich über Environment Variables oder Secret Files.
- Discord-IDs aus Config/DB, niemals im Source Code hardcoden.
- Klare Modulgrenzen innerhalb desselben Projekts.
- Externe Systeme über kleine, klar definierte Interfaces anbinden.
- Strukturierte Logs über stdout/stderr; optional ergänzend Rolling File Logs.

### 3.2 Zielmodule

```text
Lumen
├── core
├── identity
├── roles
├── tickets
├── minecraft
├── kingdoms
├── monitoring
├── notifications
├── moderation
└── storage
```

Diese Modulnamen sind fachliche Grenzen. Sie müssen nicht zwingend als separate Gradle-Subprojects umgesetzt werden. Für das MVP ist ein einzelnes Gradle-Projekt mit sauberen Packages ausreichend.

---

## 4. Zielarchitektur

### 4.1 `core`

Verantwortlich für:

- Application Startup/Shutdown,
- JDA-Initialisierung,
- Config-Laden,
- Secret-Auflösung,
- Slash-Command-Registrierung,
- Permission Checks,
- Error Handling,
- Health State,
- gemeinsame Lifecycle-Hooks.

### 4.2 `storage`

Verantwortlich für:

- SQLite-Verbindung,
- Schema-/Migration-Management,
- Repository-Klassen,
- Transaktionsgrenzen,
- persistente Zustände.

Keine Fachlogik in SQL-Repositories ablegen.

### 4.3 `notifications`

Zentrale Discord-Ausgabeschicht.

Andere Module sollen nicht überall eigene Channel-Routing- und Embed-Logik duplizieren.

Mindestens folgende Notification-Typen vorsehen:

```text
INFO
SUCCESS
WARNING
CRITICAL
MATCH
STAFF
```

### 4.4 `identity`

Verantwortlich für:

- `/link`,
- `/unlink`,
- `/account`,
- temporäre Linking-Codes,
- Discord-ID ↔ Minecraft-UUID,
- Verified-Rolle nach erfolgreichem Link.

### 4.5 `roles`

Verantwortlich für:

- Self Roles,
- Button-/Select-Menü für Rollen,
- Kingdom-Rollensynchronisierung,
- Schutz vor Vergabe nicht freigegebener Rollen.

### 4.6 `tickets`

Verantwortlich für:

- Ticket erstellen,
- privaten Ticket-Channel anlegen,
- Claim,
- Add User,
- Remove User,
- Close,
- Statuspersistenz,
- optional einfachen Transcript-Export.

### 4.7 `minecraft`

Verantwortlich für:

- Minecraft-Server-Ping,
- Online-/Offline-Status,
- Spielerzahl,
- MOTD/Servername,
- Antwortzeit,
- `/server status`,
- `/server players`,
- optionale persistente Statusnachricht.

### 4.8 `kingdoms`

Verantwortlich für Discord-seitige Kingdoms-Integration.

Wichtig:

> Dieses Modul implementiert keine Matchlogik.

Es verarbeitet Events und synchronisierte Daten aus dem Kingdoms-System.

### 4.9 `monitoring`

Verantwortlich für:

- externe Health-/Status-Events,
- Zustände `ONLINE`, `DEGRADED`, `OFFLINE`,
- Zustandswechsel,
- Deduplication,
- Discord-Alerts.

### 4.10 `moderation`

MVP-Befehle:

```text
/warn
/warnings
/timeout
/kick
/ban
```

Jede Aktion wird geloggt.

---

## 5. Konfiguration und Secrets

### 5.1 Config

Eine normale Config darf nicht-sensitive IDs und Funktionskonfiguration enthalten.

Beispielstruktur:

```yaml
guild:
  id: 123

channels:
  announcements: 123
  status: 456
  bot-log: 789
  tickets-category: 999
  matches: 111

roles:
  verified: 1
  staff: 2
  north: 3
  east: 4
  south: 5
  west: 6
```

IDs niemals direkt in Event Handlern oder Commands hardcoden.

### 5.2 Secrets

Nicht in Git:

- Discord Token,
- internes API-Secret,
- DB-Secrets bei späterer externer DB,
- Monitoring-Secrets,
- produktive Infrastruktur-Credentials.

Für lokale Entwicklung mindestens `.env.example` oder eine dokumentierte Liste benötigter Environment Variables bereitstellen, aber niemals echte Secrets committen.

Empfohlene Variablen:

```text
DISCORD_TOKEN
LUMEN_INTERNAL_API_SECRET
LUMEN_CONFIG_PATH
LUMEN_DATABASE_PATH
```

---

## 6. Datenmodell MVP

Mindestens folgende fachliche Tabellen vorsehen:

```text
users
minecraft_links
tickets
warnings
self_roles
server_status
kingdom_role_state
```

Je nach Repository-Stand dürfen Tabellen kombiniert oder leicht anders benannt werden, solange die fachlichen Anforderungen erhalten bleiben.

### 6.1 Mindestanforderungen

#### `minecraft_links`

- Discord User ID
- Minecraft UUID
- Minecraft Name
- created_at
- updated_at
- unique constraint auf Discord User ID
- unique constraint auf Minecraft UUID

#### `tickets`

- Ticket ID
- Discord Channel ID
- Creator User ID
- created_at
- claimed_by nullable
- closed_by nullable
- closed_at nullable
- status
- type (`SUPPORT`, `REPORT`)

#### `warnings`

- Warning ID
- Target User ID
- Moderator User ID
- Reason
- created_at

#### `self_roles`

- Discord Role ID
- Label
- Enabled

#### `server_status`

- Server Key
- Current State
- Last Change
- Last Successful Check

#### `kingdom_role_state`

- Discord User ID
- Minecraft UUID
- Kingdom ID
- Last Sync

---

## 7. Internal Event API

Für die Kommunikation zwischen Kingdoms und Lumen soll im MVP ein kleines internes HTTP API verwendet werden.

### 7.1 Endpoint

```text
POST /internal/minecraft/events
```

Beispiel:

```json
{
  "type": "MATCH_FINISHED",
  "server": "kingdoms",
  "timestamp": "2026-10-10T19:45:00+02:00",
  "data": {}
}
```

### 7.2 Authentifizierung

Mindestens:

```text
Authorization: Bearer <shared-secret>
```

Zusätzlich:

- möglichst nur auf privatem Interface / internen Netzwerk lauschen,
- Secret aus Environment Variable,
- Request Size Limit,
- JSON-/Payload-Validierung,
- Rate Limit,
- unbekannte Eventtypen sauber ablehnen,
- keine sensitiven Daten ungefiltert loggen.

### 7.3 Eventtypen MVP

```text
SERVER_STARTED
KINGDOM_MEMBER_UPDATED
MATCH_CHECKIN
MATCH_STARTED
MATCH_PAUSED
MATCH_RESUMED
MATCH_FINISHED
PLAYOFF_BRACKET_UPDATED
WEALTH_FINALIZED
RECOVERY_REQUIRED
```

Der Agent darf intern Enums verwenden.

### 7.4 Idempotenz

Wenn praktisch umsetzbar, Events mit einer Event-ID oder einem stabilen Deduplication-Key verarbeiten.

Ein doppeltes `MATCH_FINISHED`-Event darf nicht zwei Ergebnisposts erzeugen.

---

## 8. Slash Commands MVP

### Allgemein

```text
/help
/status
```

### Accounts

```text
/link
/unlink
/account
```

Staff:

```text
/account lookup <discord-user>
/account lookup-mc <minecraft-name>
```

### Rollen

```text
/roles
/roles panel create
```

### Tickets

```text
/ticket create
/ticket close
/ticket add
/ticket remove
```

Buttons sind für den normalen Ticketflow vorzuziehen.

### Minecraft

```text
/server status
/server players
```

### Kingdoms

```text
/kingdom info
/matches next
/matches today
/matches bracket
```

### Moderation

```text
/warn
/warnings
/timeout
/kick
/ban
```

---

# 9. Implementierungsphasen

Die Phasen werden in dieser Reihenfolge umgesetzt. Innerhalb einer Phase darf die genaue Commit-Aufteilung an den Repository-Stand angepasst werden.

---

## Phase 0 – Repository Baseline

### Ziel

Vorhandenes Repo verstehen und eine verlässliche Ausgangsbasis schaffen.

### Aufgaben

- [x] Projektstruktur analysieren.
- [x] Buildsystem prüfen.
- [x] Java-Version prüfen.
- [x] JDA-Abhängigkeit prüfen oder hinzufügen.
- [x] Gradle Wrapper prüfen/anlegen.
- [x] Test-Setup prüfen/anlegen.
- [x] `.gitignore` für Secrets, DB-Dateien, IDE-Dateien und Builds prüfen.
- [x] vorhandene CI prüfen.
- [x] lokale Startanleitung dokumentieren.
- [x] sicherstellen, dass ein sauberer Checkout gebaut werden kann.

### Akzeptanzkriterien

- `./gradlew build` läuft erfolgreich.
- Repository enthält keine Secrets.
- Ein Entwickler/Agent kann den Bot lokal starten, sobald ein Testtoken gesetzt ist.
- Aktuelle Architektur ist kurz im README oder einer Entwicklerdokumentation beschrieben.

---

## Phase 1 – Bot Core

### Ziel

Stabile technische Grundlage, auf der alle weiteren Module aufbauen.

### Aufgaben

- [x] Application Entry Point.
- [x] Discord/JDA Client initialisieren.
- [x] kontrollierten Shutdown implementieren.
- [x] Config Loader implementieren.
- [x] Environment-Secret Loader implementieren.
- [x] zentrale ID-/Config-Klassen statt Magic Numbers.
- [x] Slash-Command-Registrierung aufbauen.
- [x] Command Routing / Handler-Struktur definieren.
- [x] Permission Layer implementieren.
- [x] zentralen Error Handler implementieren.
- [x] strukturierte Logs einrichten.
- [x] Bot Health State bereitstellen.
- [x] `/help` und `/status` als Smoke-Test-Commands implementieren.

### Akzeptanzkriterien

- Bot startet stabil.
- Bot stoppt kontrolliert.
- Slash Commands registrieren sich reproduzierbar.
- Fehlende Pflichtkonfiguration führt zu verständlichem Startup-Fehler.
- Discord Token erscheint niemals im Log.
- Unauthorized Commands werden sauber abgelehnt.

### Mögliche Commits

```text
add bot bootstrap
add config loading
add command framework
add permission checks
add error handling
```

---

## Phase 2 – Storage / SQLite

### Ziel

Persistente MVP-Daten mit einfacher Migration.

### Aufgaben

- [ ] SQLite-Verbindung einrichten.
- [ ] DB-Dateipfad konfigurierbar machen.
- [ ] Migrationen einführen.
- [ ] erste Tabellen anlegen.
- [ ] Repository-Layer implementieren.
- [ ] Startup-Migration automatisch ausführen.
- [ ] Tests gegen temporäre SQLite-DB hinzufügen.

### Akzeptanzkriterien

- Neue leere DB wird automatisch initialisiert.
- Neustart zerstört keine Daten.
- Migrationen sind versionsbasiert und reproduzierbar.
- Fachmodule greifen nicht mit ad-hoc SQL quer durch das Projekt auf die DB zu.

---

## Phase 3 – Notification Layer

### Ziel

Einheitliche Discord-Ausgaben und Channel-Routing.

### Aufgaben

- [ ] Notification Service definieren.
- [ ] Channel Routing über Config.
- [ ] gemeinsame Embed-/Message-Helfer.
- [ ] Typen `INFO`, `SUCCESS`, `WARNING`, `CRITICAL`, `MATCH`, `STAFF`.
- [ ] Fehler bei fehlendem Zielchannel loggen, ohne den Bot zu crashen.

### Akzeptanzkriterien

- Fachmodule benötigen keine eigene Channel-ID-Logik.
- Channel-Routing ist zentral konfigurierbar.
- Fehlkonfigurationen erzeugen verständliche Logs.

---

## Phase 4 – Self Roles

### Ziel

Einfacher Reaction-Role-Ersatz ohne komplexen Editor.

### Aufgaben

- [ ] Self Roles in Config oder DB pflegen.
- [ ] `/roles` implementieren.
- [ ] `/roles panel create` für Admins.
- [ ] Button- oder Select-Menu-Panel erzeugen.
- [ ] Rolle hinzufügen/entfernen.
- [ ] nur explizit erlaubte Rollen akzeptieren.
- [ ] Hierarchie-/Permission-Fehler behandeln.

### Akzeptanzkriterien

- Nutzer können erlaubte Self Roles selbst setzen und entfernen.
- Nicht freigegebene Rollen können nicht über manipulierte Interactions vergeben werden.
- Staff-/Admin-Rollen sind nicht über Self Roles erreichbar.

---

## Phase 5 – Ticket System

### Ziel

Ticket Tool für einfache Support-/Report-Fälle im MVP ersetzen.

### Aufgaben

- [ ] Ticket Panel erstellen.
- [ ] `Support` und `Report` als Tickettypen unterstützen.
- [ ] privaten Ticket-Channel anlegen.
- [ ] korrekte Permission Overwrites setzen.
- [ ] Ticket-ID persistent erzeugen.
- [ ] `Claim` implementieren.
- [ ] `Add User` implementieren.
- [ ] `Remove User` implementieren.
- [ ] `Close` implementieren.
- [ ] Close-Metadaten speichern.
- [ ] optional: einfachen Text- oder HTML-Transcript erzeugen.
- [ ] geschlossene Tickets archivieren oder nach definierter Frist löschen.

### Akzeptanzkriterien

- Nicht berechtigte Nutzer sehen fremde Tickets nicht.
- Creator und Staff können Ticket nutzen.
- Claim-State bleibt nach Restart erhalten.
- Close ist idempotent.
- Channel-Löschung ohne DB-Update darf keinen kaputten Dauerzustand erzeugen.

### Nicht in dieser Phase

- keine komplexen Ticketkategorien,
- kein SLA-System,
- keine Automationsregeln,
- kein Dashboard.

---

## Phase 6 – Moderation Basics

### Ziel

Kleine, nachvollziehbare Grundmoderation.

### Aufgaben

- [ ] `/warn`.
- [ ] `/warnings`.
- [ ] `/timeout`.
- [ ] `/kick`.
- [ ] `/ban`.
- [ ] Discord Permission Checks.
- [ ] Bot-Rollenhierarchie prüfen.
- [ ] jede Aktion in `#bot-log` protokollieren.
- [ ] Warnings persistent speichern.

### Akzeptanzkriterien

- normale Nutzer können keine Moderationsbefehle ausführen.
- Bot kann keine User moderieren, die aufgrund Discord-Hierarchie nicht moderierbar sind.
- jede erfolgreiche Aktion ist auditierbar.
- Fehler werden verständlich an den Moderator zurückgegeben.

---

## Phase 7 – Minecraft Status

### Ziel

MCStatus-artige Grundfunktionen für Illunium-Server.

### Aufgaben

- [ ] Serverdefinitionen konfigurierbar machen.
- [ ] Minecraft-Ping implementieren.
- [ ] Statusdaten normalisieren.
- [ ] `/server status`.
- [ ] `/server players`.
- [ ] `Kingdoms` als MVP-Server aufnehmen.
- [ ] `Forever-World` vorbereiten, sobald vorhanden.
- [ ] optionale persistente Statusnachricht in `#status`.
- [ ] Updateintervall standardmäßig 60 Sekunden.
- [ ] Timeouts und Fehler sauber behandeln.

### Akzeptanzkriterien

- Offline-Server erzeugen keinen Command-Fehler.
- Online/Offline, Spielerzahl und Latenz werden sinnvoll angezeigt.
- Statusabfrage blockiert nicht unnötig JDA Event Threads.
- Polling kann kontrolliert gestoppt werden.

---

## Phase 8 – Internal Event API

### Ziel

Sichere minimale Schnittstelle für Kingdoms- und Monitoring-Events.

### Aufgaben

- [ ] kleinen HTTP-Server integrieren.
- [ ] nur benötigte Bind-Adresse/Port öffnen.
- [ ] Bearer Auth implementieren.
- [ ] Request Size Limit.
- [ ] Rate Limit.
- [ ] JSON Validation.
- [ ] Event Enum/Schema.
- [ ] unbekannte Events ablehnen.
- [ ] Event Dispatcher implementieren.
- [ ] relevante Requests strukturiert loggen, ohne Secret.
- [ ] Fehlerantworten konsistent gestalten.

### Akzeptanzkriterien

- Request ohne gültiges Secret wird abgelehnt.
- Ungültige Payload crasht den Bot nicht.
- große/unerwartete Requests werden begrenzt.
- Events erreichen genau das zuständige Modul.
- doppelte Events können für kritische Fälle dedupliziert werden.

---

## Phase 9 – Account Linking

### Ziel

Discord- und Minecraft-Identitäten sicher verknüpfen.

### Discord Flow

```text
/link
→ einmaliger Code
→ Gültigkeit 10 Minuten
```

### Minecraft Flow

```text
/discord link <code>
→ Kingdoms Plugin sendet Event/API Request
→ Lumen validiert Code
→ speichert Discord User ID ↔ Minecraft UUID
```

### Aufgaben

- [ ] `/link`.
- [ ] zufällige, nicht erratbare Kurz-Codes erzeugen.
- [ ] 10-Minuten-Ablauf.
- [ ] Single Use.
- [ ] persistente oder robuste temporäre Speicherung.
- [ ] API-Event für Link-Abschluss.
- [ ] Minecraft UUID und Name validieren.
- [ ] Mapping speichern.
- [ ] Verified-Rolle setzen.
- [ ] `/unlink`.
- [ ] `/account`.
- [ ] Staff Lookups.
- [ ] Konflikte bei bereits verknüpfter Discord-ID oder UUID sauber behandeln.

### Akzeptanzkriterien

- Code kann nur einmal verwendet werden.
- abgelaufener Code wird abgelehnt.
- zwei Discord-Konten können nicht dieselbe Minecraft-UUID beanspruchen.
- unlink entfernt/verändert nur Bot-eigene Zuordnungen.
- Linking ist nach Neustart konsistent.

---

## Phase 10 – Kingdom Role Sync

### Ziel

Kingdom-Zugehörigkeit automatisch in Discord spiegeln.

### Source of Truth

Kingdoms/Minecraft bleibt authoritative.

### Ablauf

```text
KINGDOM_MEMBER_UPDATED
→ Discord Mapping suchen
→ alte verwaltete Kingdom-Rolle entfernen
→ neue Kingdom-Rolle setzen
→ Sync State speichern
```

### Aufgaben

- [ ] Kingdom IDs normalisieren (`north`, `east`, `south`, `west`).
- [ ] Discord Role IDs aus Config.
- [ ] ausschließlich verwaltete Kingdom-Rollen verändern.
- [ ] Retry bei temporären Discord-Fehlern.
- [ ] Sync Audit Log.
- [ ] Reconciliation Command oder Startup-Reconciliation vorsehen.

### Akzeptanzkriterien

- Ein Nutzer besitzt maximal eine verwaltete Kingdom-Rolle.
- andere Discord-Rollen bleiben unberührt.
- nicht verlinkte Minecraft-Spieler erzeugen keinen Fehlerzustand.
- erneutes identisches Event erzeugt keine unnötige Änderung.

---

## Phase 11 – Kingdoms Match Integration

### Ziel

Wichtige Matchinformationen automatisch in Discord spiegeln.

### Unterstützte Events

- `MATCH_CHECKIN`
- `MATCH_STARTED`
- `MATCH_PAUSED`
- `MATCH_RESUMED`
- `MATCH_FINISHED`
- `PLAYOFF_BRACKET_UPDATED`
- `WEALTH_FINALIZED`
- `RECOVERY_REQUIRED`

### Aufgaben

- [ ] Match Event DTOs definieren.
- [ ] `#matches`-Routing.
- [ ] Check-in Meldung.
- [ ] Match Start/Pause/Resume Meldung.
- [ ] Ergebnispost.
- [ ] Seals/Score sinnvoll darstellen.
- [ ] Playoff-Bracket-Update als Nachricht/Embed.
- [ ] Recovery Required an Staff eskalieren.
- [ ] `/matches next`.
- [ ] `/matches today`.
- [ ] `/matches bracket`.

### Akzeptanzkriterien

- Bot berechnet kein eigenes Matchresultat.
- Eventdaten werden validiert.
- doppelte Abschlussmeldungen werden verhindert.
- falscher/fehlender Channel crasht den Bot nicht.
- Staff-kritische Meldungen landen nicht nur in einem öffentlichen Channel.

---

## Phase 12 – Match Reminder

### Ziel

Geplante Kingdoms-Matches rechtzeitig ankündigen.

### Reminder

```text
60 Minuten vorher
15 Minuten vorher
Check-in geöffnet
```

### Aufgaben

- [ ] synchronisierten Matchplan speichern/lesen.
- [ ] Scheduler integrieren.
- [ ] Reminder nach Restart korrekt rekonstruieren.
- [ ] nur relevante Rollen/Teilnehmer erwähnen.
- [ ] keine Reminder für abgesagte/abgeschlossene Matches.
- [ ] doppelte Reminder verhindern.

### Akzeptanzkriterien

- Restart führt nicht zu mehrfachen Reminder-Posts.
- alte Matches werden ignoriert.
- Zeitzonen werden korrekt behandelt.
- Default-Zeitzone für Illunium: `Europe/Berlin`.

---

## Phase 13 – Monitoring

### Ziel

Ausfälle über einen externen Health Check in Discord melden.

### Hintergrund

Ein vollständig gecrashter Minecraft-Prozess kann selbst keine Discord-Meldung senden. Deshalb muss der eigentliche Ausfall von außen erkannt werden.

### Zustände

```text
ONLINE
DEGRADED
OFFLINE
```

### Aufgaben

- [ ] Monitoring Event Intake über internes API.
- [ ] Status pro Service speichern.
- [ ] Zustandswechsel erkennen.
- [ ] nur bei Zustandswechsel posten.
- [ ] Wiederherstellung melden.
- [ ] Ausfalldauer berechnen.
- [ ] Staff-only Eskalation bei kritischen Zuständen.
- [ ] Bot-interne Fehler separat behandeln.

### Akzeptanzkriterien

- Offline-Event jede Minute erzeugt nicht jede Minute eine neue Nachricht.
- `OFFLINE -> ONLINE` erzeugt Recovery-Meldung.
- Restart verliert den letzten relevanten Status nicht.
- Monitoring Events können nicht ohne Authentifizierung eingespeist werden.

---

## Phase 14 – Logging und Audit Finalisierung

### Ziel

Produktivbetrieb nachvollziehbar machen.

Mindestens loggen:

- Startup/Shutdown,
- Command Errors,
- Ticket create/claim/close,
- linking/unlinking,
- Kingdom Role Sync,
- Moderationsaktionen,
- Minecraft Statuswechsel,
- Monitoring Alerts,
- Kingdoms Match Events,
- API Authentication Failures in sinnvoll begrenzter Form.

### Anforderungen

- keine Tokens,
- keine Secrets,
- keine vollständigen Authorization Header,
- keine unnötigen personenbezogenen Inhalte,
- technische Logs auf stdout/stderr,
- relevante Admin-Aktionen zusätzlich nach `#bot-log`.

---

## Phase 15 – Production Hardening

### Aufgaben

- [ ] Discord-Permissions minimieren.
- [ ] keine pauschale Administrator-Berechtigung, wenn vermeidbar.
- [ ] Config Validation vervollständigen.
- [ ] API Limits prüfen.
- [ ] graceful shutdown testen.
- [ ] DB Backup-Verhalten definieren.
- [ ] Restart-Verhalten testen.
- [ ] Scheduler-Recovery testen.
- [ ] Status-Recovery testen.
- [ ] Rate Limits und Discord API Fehler testen.
- [ ] relevante Commands mit falschen Permissions testen.
- [ ] Dependency-Versionen pinnen.
- [ ] CI Build/Test aktivieren.
- [ ] Deployment-Dokumentation erstellen.

### Akzeptanzkriterien

- Bot übersteht kontrollierten Restart ohne Verlust kritischer Zustände.
- temporäre Discord API Fehler führen nicht zu Prozessabbruch.
- interne API ist nicht öffentlich ungeschützt erreichbar.
- produktive Secrets liegen außerhalb des Repos.
- DB kann gesichert und wiederhergestellt werden.

---

# 10. Teststrategie

## 10.1 Unit Tests

Priorität auf Logik, die ohne Discord-Netzwerk getestet werden kann:

- Linking Code Lifecycle,
- Permission Decisions,
- Ticket State Transitions,
- Kingdom Role Diff,
- Monitoring State Transitions,
- Deduplication,
- Reminder Scheduling,
- Event Validation,
- Config Validation.

## 10.2 Integration Tests

Mindestens:

- SQLite Migrationen,
- Repository Roundtrips,
- API Authentication,
- API Payload Validation,
- Event Dispatch,
- Restart-relevante persistente Zustände.

## 10.3 Manueller Discord-Test

Eigene Test-Guild verwenden.

Testfälle:

- Commands registriert,
- Permission Denied,
- Self Role add/remove,
- manipulierte Role Interaction,
- Ticket Create/Claim/Add/Remove/Close,
- Warn/Timeout/Kick/Ban soweit sicher testbar,
- Minecraft Status online/offline,
- Linking Erfolg/Timeout/Doppelverwendung,
- Kingdom Role Sync,
- Match Event Posts,
- Monitoring Offline/Recovery,
- Restart zwischen geplanten Remindern.

---

# 11. Security Checkliste

Vor MVP-Abnahme prüfen:

- [ ] Discord Token nicht im Repo.
- [ ] Internal API Secret nicht im Repo.
- [ ] Secret-Werte nie in Logs.
- [ ] Config enthält nur nicht-sensitive IDs/Optionen.
- [ ] Bot besitzt nur notwendige Discord-Rechte.
- [ ] Self Roles sind explizit allowlisted.
- [ ] Moderationsbefehle prüfen Staff-Permissions.
- [ ] interne API prüft Auth.
- [ ] Request Body Limit aktiv.
- [ ] Payload Validation aktiv.
- [ ] Rate Limit aktiv.
- [ ] öffentliche Minecraft-Control-Kommandos existieren nicht.
- [ ] SQL-Zugriffe sind parametrisiert.
- [ ] keine ungeprüften User Strings in Dateipfade übernehmen.

---

# 12. CI

Mindestens bei Push/PR:

```text
checkout
→ setup Java 25
→ ./gradlew clean build
→ tests
```

Optional zusätzlich:

- static analysis,
- dependency vulnerability scan,
- test report artifacts.

Kein automatisches Production-Deployment allein aufgrund eines Commits.

---

# 13. Deployment-Zielbild

Der genaue Illunium-Infrastruktur-Stack wird separat festgelegt. Lumen soll jedoch so gebaut werden, dass es später als normaler langlebiger Service betrieben werden kann.

Mindestens:

- einzelnes JAR oder reproduzierbares Container-Image,
- Config außerhalb des Artefakts,
- Secrets außerhalb des Artefakts,
- persistenter SQLite-Pfad,
- stdout/stderr Logs,
- kontrollierter Shutdown,
- Health-Zustand.

Deployment darf später beispielsweise über systemd oder Container-Orchestrierung erfolgen. Das Repo soll keine unnötige harte Bindung an eine bestimmte Produktionsplattform erhalten.

---

# 14. MVP Definition of Done

Lumen MVP ist fertig, wenn alle folgenden Punkte erfüllt sind:

- [ ] Bot startet stabil als Service.
- [ ] Slash Commands sind registriert.
- [ ] Config und Secrets sind sauber getrennt.
- [ ] SQLite wird migrationsfähig initialisiert.
- [ ] Self Roles funktionieren.
- [ ] Tickets können erstellt, verwaltet und geschlossen werden.
- [ ] Moderation Basics funktionieren.
- [ ] Minecraft-Status ist abrufbar.
- [ ] Discord ↔ Minecraft Linking funktioniert.
- [ ] Verified-Rolle funktioniert.
- [ ] Kingdom-Rollen werden automatisch synchronisiert.
- [ ] Kingdoms Match Events erscheinen im richtigen Channel.
- [ ] Match Reminder funktionieren.
- [ ] externe Offline-/Online-Meldungen funktionieren.
- [ ] Deduplication verhindert Spam.
- [ ] Staff-Logs existieren.
- [ ] Bot stellt nach Restart relevante Zustände wieder her.
- [ ] interne API ist authentifiziert und validiert.
- [ ] keine Secrets befinden sich im Git-Repository.
- [ ] Build und Tests laufen in CI.
- [ ] ein frischer Checkout kann anhand der Dokumentation lokal gestartet werden.

Nicht für MVP erforderlich:

- vollständiger Carl-bot-Ersatz,
- komplexes AutoMod,
- Webdashboard,
- Musik,
- Economy,
- Minecraft Console Control,
- Multi-Guild.

---

# 15. Empfohlene Ablösung externer Bots

Externe Bots werden **nicht nach Kalender**, sondern nach Funktionsreife entfernt.

Empfohlene Reihenfolge:

```text
Self Roles stabil
→ bisherigen Reaction-Role-Bot für diese Rollen ablösen

Tickets stabil
→ Ticket Tool ablösen

Minecraft Status + Monitoring stabil
→ MCStatus-/Statusbot ablösen

Moderation stabil genug
→ relevante Carl-bot-Funktionen schrittweise ablösen
```

Grundsatz:

> Ein externer Bot wird erst entfernt, wenn das entsprechende Lumen-Modul im echten Betrieb zuverlässig funktioniert.

---

# 16. Post-MVP Backlog

Erst nach stabilem MVP bewerten:

1. Reaction-/Role-System erweitern.
2. Ticket-Kategorien und Transcripts ausbauen.
3. Moderation und AutoMod erweitern.
4. Logging und Audit-Funktionen erweitern.
5. Monitoring für alle Illunium-Services zentralisieren.
6. sichere administrative Minecraft-Funktionen integrieren.
7. Website/API anbinden.
8. verbleibende externe Bots Modul für Modul entfernen.
9. SQLite nur bei echtem Bedarf auf PostgreSQL migrieren.
10. Message Broker nur bei echtem Integrationsbedarf evaluieren.

---

# 17. Agenten-Checkliste pro Arbeitsschritt

Vor Änderung:

- [ ] Verstehe ich den vorhandenen Code?
- [ ] Gehört die Änderung wirklich zum aktuellen MVP?
- [ ] Kann ich vorhandene Infrastruktur wiederverwenden?
- [ ] Verändere ich keine fremde Source of Truth?

Nach Änderung:

- [ ] Build erfolgreich?
- [ ] relevante Tests erfolgreich?
- [ ] keine Secrets hinzugefügt?
- [ ] keine unnötige neue Dependency?
- [ ] Logs enthalten keine sensitiven Daten?
- [ ] Fehlerfall behandelt?
- [ ] Restart-Verhalten berücksichtigt, falls Zustand betroffen ist?
- [ ] Dokumentation aktualisiert, falls Bedienung/Config geändert wurde?

Vor Commit:

- [ ] nur zusammengehörige Änderungen staged?
- [ ] Commit Message auf Englisch?
- [ ] Commit Message kurz?
- [ ] keine `Co-authored-by`-Zeile?
- [ ] keine AI-/Agent-Signatur?

---

# 18. Prioritätsregel bei Zeitdruck

Wenn Zeit oder Komplexität aus dem Ruder läuft, gilt folgende Reihenfolge:

```text
1. Bot Core / Config / Storage
2. Rollen / Tickets
3. Minecraft Status
4. Account Linking
5. Kingdom Role Sync
6. Kingdoms Match Events
7. Match Reminder
8. Monitoring
9. Moderation-Komfort / optionale UX
```

Stabilität und sichere Integration haben Vorrang vor Komfortfeatures.

Ein einfaches funktionierendes Command-/Button-System ist besser als eine große abstrakte Plattform, die das MVP verzögert.

---

# 19. Abschlusskriterium für den KI-Agenten

Der Agent soll eine Phase erst als abgeschlossen betrachten, wenn:

1. die Funktion implementiert ist,
2. relevante Tests erfolgreich sind,
3. Fehlerfälle behandelt wurden,
4. keine Security-Grundregel verletzt wird,
5. die benötigte Config dokumentiert ist,
6. der aktuelle Stand buildbar ist,
7. der Commit kurz und auf Englisch erfolgt,
8. der Commit **keine Co-Author-Angabe** enthält.

Danach mit der nächsten Phase fortfahren.
