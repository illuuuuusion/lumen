# Lumen – Discord Bot MVP

> **Stand:** 24.09.2026  
> **Ziel:** erste produktiv nutzbare Version von Lumen  
> **Scope:** Community-Grundfunktionen + Kingdoms-/Minecraft-Integration + Monitoring

---

## 1. Ziel des MVP

Lumen soll langfristig mehrere heute getrennte Drittanbieter-Bots weitgehend ersetzen.

Das MVP versucht **nicht**, Carl-bot, Ticket Tool, Reaction Roles, MCStatus und sämtliche Moderationsbots sofort vollständig nachzubauen.

Stattdessen soll Version 1 eine belastbare modulare Grundlage schaffen und genau die Funktionen übernehmen, die für den ersten öffentlichen Minecraft-/Kingdoms-Betrieb den größten Nutzen bringen.

Der MVP ist erfolgreich, wenn Illunium damit:

- Discord-Rollen und Onboarding sauber verwalten kann
- einfache Tickets ohne externen Ticket-Bot betreiben kann
- den Status mehrerer Minecraft-Server anzeigen kann
- Kingdoms-Matches und wichtige Projektmeldungen automatisch in Discord spiegeln kann
- Minecraft-/Service-Ausfälle in Discord melden kann
- Minecraft-Accounts mit Discord-Accounts verknüpfen kann
- Kingdom-Rollen automatisch synchronisieren kann
- administrative Logs zentral sammeln kann
- Konfiguration und Botzustand über ein schmales Web UI mit Discord-Login pflegen kann

---

## 2. Nicht-Ziele des MVP

Noch nicht erforderlich:

- vollständiges Carl-bot-Automod-System
- komplexe Regex-/Spamfilter
- Musik
- Economy
- Level-/XP-System
- Giveaways
- umfangreiche Web-Dashboards über den Umfang aus Abschnitt 12 hinaus
- vollständige Website-Accounts
- KI-Moderation
- vollautomatisches Minecraft-Server-Management
- Start/Stop/Restart des Servers aus öffentlichen Discord-Commands
- komplexer Workflow-Builder
- Multi-Guild-/SaaS-Unterstützung

Ein schmales Web UI für Staff gehört dagegen zum MVP und ist in Abschnitt 12 beschrieben.

Der Bot wird zunächst ausschließlich für den eigenen Illunium-Discord entwickelt.

---

## 3. Technische Grundrichtung

Empfohlener Stack:

```text
Java 25
JDA
Gradle Kotlin DSL
SQLite für MVP
React + TypeScript + Vite für das Web UI
```

Begründung:

- gleicher JVM-/Gradle-Stack wie das Kingdoms-Plugin
- gemeinsame Entwicklungsumgebung
- gemeinsame DTOs/Utilities theoretisch später möglich
- kein zusätzlicher Produktionsstack notwendig: Node läuft nur im Build, ausgeliefert wird ein statisches Bundle im Bot-Artefakt

Eine spätere Migration von SQLite auf PostgreSQL bleibt möglich, wenn mehrere Botinstanzen oder größere Datenmengen entstehen.

---

## 4. Architektur

Der Bot wird modular aufgebaut:

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
├── storage
└── web
```

Nicht jedes Modul muss im MVP vollständig ausgebaut sein.

---

## 5. Core-Modul

Pflichtbasis für alle weiteren Module.

### Funktionen

- Bot Startup/Shutdown
- Config laden
- Secrets über Environment Variables
- Slash Commands registrieren
- Permission-Checks
- Guild-/Channel-/Role-IDs aus Config
- zentraler Error Handler
- strukturierte Logs
- Health State
- Datenbankinitialisierung/Migrations

### Config

Beispiel:

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

web:
  enabled: true
  bind: 127.0.0.1
  port: 8080
  base-url: "https://lumen.example.net"

servers:
  kingdoms:
    name: "Kingdoms"
    address: "kingdoms.example.net"
    port: 25565
    monitored: true
    kingdoms-events: true
    status-channel: 456
  forever:
    name: "Forever-World"
    address: "forever.example.net"
    port: 25565
    monitored: true
    kingdoms-events: false
```

Die Serverliste ist Konfiguration. Ein weiterer Server wird durch einen Eintrag angebunden, nicht durch Code.

Der Schlüssel eines Eintrags ist der Server Key. Er erscheint in Events, in gespeicherten Zuständen und in Discord-Ausgaben.

`kingdoms-events: true` trägt genau ein Server: Das Kingdoms-System existiert nur einmal, und nur dieser Server darf Kingdoms-Events senden.

Keine Discord-IDs hardcoden.

---

## 6. Identity / Minecraft Linking

Das Linking ist die Grundlage für automatische Minecraft-/Discord-Rollen.

### Ablauf

Discord:

```text
/link
```

Bot erzeugt einen einmaligen Code:

```text
A7K4P2
```

Gültigkeit:

```text
10 Minuten
```

Minecraft:

```text
/discord link A7K4P2
```

Das Kingdoms-Plugin übermittelt:

- Code
- Minecraft UUID
- Minecraft Name

an den Bot.

Der Bot speichert:

```text
discordUserId ↔ minecraftUuid
```

### Ergebnis

Nach erfolgreichem Link:

- Discord `Verified`-Rolle setzen
- Minecraft-Name optional im Profil/Log anzeigen
- Kingdom-Rolle synchronisieren, falls vorhanden

### Commands

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

---

## 7. Rollen-Modul

### 7.1 Kingdom-Rollen

Automatisch verwaltet:

```text
North
East
South
West
```

Quelle der Wahrheit bleibt der Minecraft-/Kingdoms-Projektstatus.

Wenn ein verknüpfter Spieler im Kingdoms-Plugin verschoben oder eingetragen wird:

```text
Kingdoms Plugin
→ Bot Event
→ alte Kingdom-Rolle entfernen
→ neue Kingdom-Rolle setzen
```

### 7.2 Self Roles / Reaction-Role-Ersatz

MVP benötigt keinen komplexen Reaction-Role-Editor.

Stattdessen ein einfaches Button-/Select-Menü:

```text
/roles panel create
```

Beispiele für Self Roles:

- Minecraft
- Dev
- Events
- Ping-Rollen

Admin konfiguriert die erlaubten Rollen in YAML/DB.

Der Bot darf ausschließlich explizit freigegebene Self Roles vergeben.

### 7.3 Keine freie Rollenverwaltung

Der Bot darf nicht beliebige Discord-Rollen vergeben, wenn diese nicht in seiner Config als verwaltbar markiert sind.

---

## 8. Ticket-Modul

Einfacher Ersatz für Ticket Tool.

### User Flow

Button:

```text
Ticket erstellen
```

Bot erstellt privaten Channel:

```text
ticket-0042
```

Sichtbar für:

- Ersteller
- Support-/Staff-Rolle
- Bot

### Commands / Buttons

```text
Close
Claim
Add User
Remove User
```

### Bei Close

- Ticket sperren
- Transcript als Text/HTML optional erzeugen
- Metadaten speichern
- Channel nach kurzer Frist löschen oder archivieren

### MVP-Daten

- Ticket ID
- Ersteller
- Erstellzeit
- Claimed by
- Closed by
- Status

Keine komplexen Ticketkategorien nötig; maximal `Support` und `Report`.

---

## 9. Minecraft-Status-Modul

Der Bot soll MCStatus-artige Grundfunktionen ersetzen.

### `/server status`

Anzeige pro Server:

- online/offline
- Spielerzahl
- MOTD/Servername
- Ping/Antwortzeit
- optional aktuelle Projektphase

Die Server kommen aus der Config, nicht aus dem Code. Beliebig viele Einträge sind möglich, im MVP:

```text
Kingdoms
Forever-World (sobald vorhanden)
```

Lobby/Testserver müssen nicht öffentlich angezeigt werden; sie bleiben auf `monitored: false` oder fehlen ganz.

`/server status` ohne Angabe zeigt alle Server, mit Angabe genau einen. Die Auswahl kommt aus der Config.

Alle Server werden parallel abgefragt. Ein langsamer oder toter Server darf die Anzeige der übrigen nicht verzögern.

### Status Message

Optional eine dauerhaft aktualisierte Embed-Nachricht je Server. Der Channel kommt aus `status-channel` des Servers, sonst aus `channels.status`.

Updateintervall:

```text
60 Sekunden
```

Nicht häufiger nötig.

---

## 10. Kingdoms-Integration

Das Kingdoms-Plugin bleibt Source of Truth für Matchzustände.

Der Discord-Bot spiegelt Informationen und übernimmt keine Matchlogik.

### 10.1 Events vom Minecraft-Plugin

Benötigte Eventtypen:

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

### 10.2 Discord-Ausgaben

Beispiele:

`#matches`

```text
North vs East startet um 19:30.
Check-in ist geöffnet.
```

```text
North vs East – Ergebnis
North: 5 Seals
East: 3 Seals
Sieger: North
```

```text
Upper Bracket Final:
North vs South
```

### 10.3 Match Reminder

Der Bot kann auf Basis des synchronisierten Schedules Reminder senden:

```text
60 Minuten vorher
15 Minuten vorher
Check-in geöffnet
```

Nur relevante Rollen/Teilnehmer pingen.

### 10.4 `/matches`

```text
/matches next
/matches today
/matches bracket
```

Die Daten stammen aus dem vom Kingdoms-Plugin synchronisierten Matchplan.

---

## 11. Kommunikation zwischen Kingdoms und Bot

MVP-Empfehlung:

```text
internes HTTP Event API
```

Der Bot stellt auf dem privaten internen Netzwerk einen kleinen Endpoint bereit:

```text
POST /internal/minecraft/events
```

Payload beispielsweise:

```json
{
  "type": "MATCH_FINISHED",
  "server": "kingdoms",
  "timestamp": "...",
  "data": {}
}
```

### Authentifizierung

Mindestens:

```text
Authorization: Bearer <shared-secret>
```

zusätzlich:

- nur internes Netzwerk
- Secret aus Environment Variable
- Rate Limit
- Payload Validation
- `server` muss ein konfigurierter Server Key sein, sonst wird das Event abgelehnt
- Kingdoms-Events nur von dem Server, der `kingdoms-events: true` trägt

Im MVP teilen sich alle eigenen Server ein gemeinsames Secret. Das `server`-Feld ordnet ein Event zu, es authentifiziert den Absender nicht. Sobald ein Server außerhalb des eigenen internen Netzes sendet, wird auf ein Secret pro Server umgestellt.

Später kann die Verbindung durch mTLS oder einen Message Broker ersetzt werden, falls überhaupt nötig.

### Warum HTTP für MVP

- kein Redis nötig
- keine Queue-Infrastruktur nötig
- einfach zu debuggen
- Plugin muss nur Events senden
- Bot bleibt unabhängig von Minecraft-Dateisystemen

---

## 12. Web UI

Ein bewusst schmales Web UI für Staff. Es ersetzt keine Discord-Funktion, sondern übernimmt die Aufgaben, die in Discord umständlich sind: Konfiguration pflegen und Botzustand einsehen.

### Umfang

Enthalten:

- Login über Discord
- Statusseite: Health, Uptime, Gateway-Latenz, Monitoring-Zustand
- Config-Editor für Guild-, Channel- und Rollen-IDs, mit Auswahllisten statt getippter Snowflakes
- letzte Audit-Log-Einträge

Nicht enthalten:

- eigene Benutzerverwaltung
- Moderationsaktionen
- Ticketbearbeitung
- öffentliche Seiten ohne Login
- Multi-Guild-Ansichten

Grundsatz: Was in Discord genauso schnell geht, bleibt in Discord.

### Technik

```text
React + TypeScript
Vite
kein UI-Framework, kein Router, kein State-Management-Paket
```

Das Frontend wird zu statischen Dateien gebaut und im Bot-Artefakt ausgeliefert. Der Gradle-Build erzeugt das Bundle, `./gradlew build` bleibt der einzige Buildbefehl. Node wird nur zur Buildzeit gebraucht.

Ausgeliefert wird vom selben HTTP-Server wie die Internal Event API (Abschnitt 11), unter getrennten Pfaden und mit getrennter Authentifizierung.

Optik ist zweitrangig. Wenige Seiten, wenige Komponenten, lesbar auf Anhieb.

### Login

Discord OAuth2 Authorization Code Flow:

```text
/auth/login     zufälliger state, Redirect zu Discord
/auth/callback  state prüfen und verbrauchen, Code gegen Token tauschen, User-ID lesen
/auth/logout    Session serverseitig löschen
```

Scope: nur `identify`. Das Access Token wird nach dem Callback verworfen.

Die Berechtigung kommt nicht aus OAuth-Scopes, sondern aus Discord selbst: Der Bot kennt die Guild bereits und prüft dort Mitgliedschaft und Staffrolle über JDA. Damit bleibt Discord die einzige Quelle der Berechtigung, und ein Rollenentzug wirkt sofort, ohne Logout.

### Session

- serverseitig im Speicher, mit Ablaufzeit
- Session-ID kryptografisch zufällig
- Cookie `HttpOnly`, `Secure`, `SameSite=Strict`
- nach Botneustart ist ein erneuter Login nötig; für ein Staff-Werkzeug akzeptabel
- keine Session ergibt `401`, fehlende Staffrolle ergibt `403`

### Config schreiben

- jede ID wird gegen die Guild validiert, bevor sie gespeichert wird
- `config.yml` wird atomar ersetzt
- der Bot lädt die Config danach ohne Neustart neu
- jede Änderung landet im Audit Log mit User, Feld, altem und neuem Wert
- Secrets werden über das Web UI weder gelesen noch geschrieben

### Betrieb

Der HTTP-Server bindet auf localhost und läuft hinter einem Reverse Proxy mit TLS. Ohne TLS kein produktiver Betrieb, das Session-Cookie ist `Secure`. Ist das Web UI in der Config deaktiviert, startet der Bot unverändert ohne OAuth-Konfiguration.

---

## 13. Monitoring-Modul

Wichtig ist die Trennung zwischen Minecraft-Plugin und echter Ausfallerkennung.

### Problem

Wenn Minecraft vollständig crasht, kann das Plugin keine Meldung mehr senden.

Deshalb:

```text
Host / Monitoring Service
        │
        ▼
Lumen
        │
        ▼
Discord
```

### MVP-Alerts

- Kingdoms offline
- Kingdoms wieder online
- Forever-World offline/online
- Bot-interner Fehler
- Recovery Required

### Deduplication

Nicht jede Minute dieselbe Offline-Meldung senden.

Status speichern:

```text
ONLINE
DEGRADED
OFFLINE
```

Nur bei Zustandswechsel posten.

---

## 14. Discord-Statusmeldungen

Channel:

```text
#status
```

Beispiel:

```text
🔴 Kingdoms ist nicht erreichbar.
Seit: 20:14
```

Nach Recovery:

```text
🟢 Kingdoms ist wieder erreichbar.
Ausfallzeit: 4m 32s
```

Bei Matchbetrieb kann zusätzlich Staff-only gewarnt werden, damit ein Ausfall nicht unnötig alle Mitglieder pingt.

---

## 15. Notifications

Zentrale Notification-Schicht statt in jedem Modul eigene Discord-Sendelogik.

Beispieltypen:

```text
INFO
SUCCESS
WARNING
CRITICAL
MATCH
STAFF
```

Das ermöglicht später einheitliche Embeds und Channel-Routing.

---

## 16. Moderation – MVP

Keine vollständige Carl-bot-Nachbildung.

MVP reicht:

```text
/warn
/warnings
/timeout
/kick
/ban
```

sofern Discord-Permissions dies erlauben.

Jede Bot-Moderationsaktion wird in `#bot-log` protokolliert.

### Keine automatische KI-/Regex-Moderation im MVP

Spam-/Automod-Funktionen können später ergänzt werden.

Discords eigene AutoMod-Funktionen können vorerst parallel genutzt werden.

---

## 17. Logging

Bot loggt mindestens:

- Startup/Shutdown
- Command Errors
- Ticket create/close
- Linking/unlinking
- Kingdom-Rollensync
- Moderationscommands
- Minecraft-Statuswechsel
- Monitoring Alerts
- Kingdoms Match Events

Staff Channel:

```text
#bot-log
```

Technische Logs zusätzlich in Datei/stdout für Container-/Service-Logs.

---

## 18. Datenhaltung

MVP:

```text
SQLite
```

Tabellen ungefähr:

```text
users
minecraft_links
tickets
warnings
self_roles
server_status
kingdom_role_state
```

Kingdoms-Matchresultate müssen nicht vollständig als zweites Primärsystem im Bot gespeichert werden.

Der Bot speichert nur so viel, wie für Discord-Anzeige/Reminder notwendig ist.

Source of Truth für Gameplay bleibt das Kingdoms-System.

---

## 19. Security

### Secrets

Nicht in Git:

- Discord Token
- Discord OAuth2 Client Secret
- internal API secret
- DB secrets
- Monitoring secrets

Environment Variables oder Secret Files.

### Discord Permissions

Bot erhält nur notwendige Rechte.

Nicht pauschal Administrator geben, wenn vermeidbar.

### Internal API

- private Bind-Adresse
- Shared Secret
- nur konfigurierte Server Keys werden akzeptiert
- Input Validation
- Request Size Limit
- Rate Limit

### Web UI

- Login ausschließlich über Discord OAuth2, Scope `identify`
- Autorisierung über Guild-Mitgliedschaft und Staffrolle, bei jedem Request neu geprüft
- `state`-Parameter gegen CSRF im Loginflow, einmalig verwendbar
- Session serverseitig, Cookie `HttpOnly`, `Secure`, `SameSite=Strict`
- kein Access Token und kein Client Secret im Frontend
- nur über TLS erreichbar, Bot bindet auf localhost hinter einem Reverse Proxy
- Rate Limit auf die Auth-Routen

### Minecraft Server Control

Start/Stop/Console-Kommandos über Discord sind **nicht Teil des MVP**.

Das vermeidet einen unnötig mächtigen Angriffsweg.

---

## 20. Slash Commands – MVP

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

### Rollen

```text
/roles
/roles panel create   [admin]
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

## 21. MVP-Reihenfolge

### Phase 1 – Bot Core

1. JDA-Projekt
2. Config
3. Slash-Command-System
4. Permission Layer
5. SQLite
6. Logging

### Phase 2 – Community Basics

1. Self Roles
2. Ticket Panel
3. Ticket Lifecycle
4. Moderation Basics

### Phase 3 – Minecraft

1. Server Ping/Status
2. `/server status`
3. Status Channel
4. Internal Event API

### Phase 4 – Account Linking

1. `/link`
2. temporary codes
3. Minecraft `/discord link`
4. account mapping
5. Verified role

### Phase 5 – Kingdoms

1. Kingdom Role Sync
2. Match events
3. Match result posts
4. Schedule sync
5. Match reminders
6. Playoff bracket messages

### Phase 6 – Monitoring

1. External health event intake
2. Offline/online transition
3. Discord alerting
4. Deduplication

### Phase 7 – Web UI

1. React-Bundle im Gradle-Build
2. Discord OAuth2 Login
3. Session und Rollenprüfung
4. Statusseite
5. Config-Editor mit Reload

---

## 22. Definition of Done

Das Discord-Bot-MVP ist einsatzbereit, wenn:

- Bot stabil als Service startet
- Slash Commands registriert sind
- Config/Secrets sauber getrennt sind
- SQLite migrationsfähig initialisiert wird
- Self Roles funktionieren
- Tickets erstellt/geschlossen werden können
- der Status aller konfigurierten Minecraft-Server abrufbar ist
- Minecraft ↔ Discord Linking funktioniert
- Web UI ist nur für eingeloggte Staffmitglieder nutzbar
- Config kann über das Web UI gepflegt werden und wirkt ohne Neustart
- Kingdom-Rollen automatisch synchronisiert werden können
- Kingdoms Match Events im richtigen Channel erscheinen
- Matchreminder funktionieren
- externe Offline-/Online-Meldungen funktionieren
- Staff-Logs vorhanden sind
- Bot nach Restart seinen relevanten Zustand wiederherstellt

Nicht erforderlich für MVP:

- vollständiger Carl-bot-Ersatz
- komplexes AutoMod
- Webdashboard über den Umfang aus Abschnitt 12 hinaus
- Musik
- Economy
- Minecraft-Console-Control
- Multi-Guild

---

## 23. Unmittelbarer Nutzen für Kingdoms

Mit diesem MVP kann der erste Kingdoms-Durchlauf bereits folgende Abläufe automatisieren:

```text
Spieler joint Discord
→ Account verlinken
→ Kingdom-Rolle automatisch
→ private Kingdom-Channels werden sichtbar
```

```text
Match steht an
→ Bot erinnert Rollen/Teilnehmer
→ Check-in wird angekündigt
→ Kingdoms Plugin startet Match
→ Bot veröffentlicht Status
→ Match endet
→ Ergebnis automatisch in Discord
```

```text
Minecraft crasht
→ externes Monitoring erkennt Ausfall
→ Bot meldet Ausfall
→ Server kommt zurück
→ Bot meldet Recovery
```

Damit liefert der eigene Bot schon in Version 1 konkreten Mehrwert, ohne vor Kingdoms sämtliche Drittanbieter-Bot-Funktionen neu entwickeln zu müssen.

---

## 24. Danach – Post-MVP

Erst nach stabilem MVP schrittweise:

1. Reaction-/Role-System vollständig ausbauen
2. Ticket Tool vollständig ersetzen
3. Moderations-/AutoMod-Funktionen ausbauen
4. Logging erweitern
5. MCStatus-/Monitoring-Funktionen für alle Server zentralisieren
6. administrative Minecraft-Funktionen sicher integrieren
7. Web UI über Konfiguration und Status hinaus ausbauen
8. Website/API anbinden
9. verbleibende externe Discord-Bots Modul für Modul entfernen

Grundsatz:

> Ein externer Bot wird erst entfernt, wenn das eigene Ersatzmodul im echten Betrieb zuverlässig funktioniert.
