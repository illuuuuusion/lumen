# Lumen – Testprotokoll

> **Stand:** Phasen 0–6 umgesetzt (Baseline, Core, Storage, Notifications, Self Roles, Tickets, Moderation).
> **Zweck:** Abnahme des aktuellen Standes auf einer eigenen Test-Guild.
> **Nicht enthalten:** Phasen 7–16 (Minecraft-Status, Event-API, Linking, Kingdoms, Monitoring, Web UI).

Jede Zeile ist ein Testfall: abhaken, wenn das erwartete Ergebnis eintritt.
Abweichungen unter [Befunde](#befunde) notieren.

---

## 0. Vorbereitung

### 0.1 Test-Guild einrichten

- [ ] Eigene Discord-Guild anlegen (**nicht** die Produktiv-Guild).
- [ ] Rolle `Staff` anlegen.
- [ ] Rolle `Test-Farbe` anlegen (harmlos, keine Rechte) – für Self Roles.
- [ ] Rolle `Fake-Admin` anlegen **mit** Recht `Administrator` – für den Negativtest.
- [ ] Rolle `Über-Bot` anlegen und in der Rollenliste **über** die Bot-Rolle ziehen.
- [ ] Channels anlegen: `#announcements`, `#status`, `#bot-log`, `#matches`.
- [ ] Kategorie `Tickets` anlegen.
- [ ] Zweiten Testaccount bereithalten (ohne Staff-Rolle) – ohne den sind die
      Rechte-Negativtests nicht durchführbar.

### 0.2 Bot einladen

- [ ] Bot mit diesen Rechten einladen: `Manage Roles`, `Manage Channels`,
      `Kick Members`, `Ban Members`, `Moderate Members`, `View Channels`,
      `Send Messages`, `Attach Files`, `Read Message History`, `Manage Permissions`.
- [ ] Bot-Rolle in der Rollenhierarchie **über** `Test-Farbe` und `Staff` ziehen,
      aber **unter** `Über-Bot` lassen.

### 0.3 Lokal starten

```bash
cp .env.example .env                # DISCORD_TOKEN eintragen
cp config.example.yml config.yml    # IDs eintragen (Rechtsklick → ID kopieren, Entwicklermodus an)
./gradlew build
set -a && . ./.env && set +a
./gradlew run
```

- [ ] `./gradlew build` läuft grün durch.
- [ ] Bot geht online.

---

## 1. Automatische Tests

```bash
./gradlew clean build
```

- [ ] `BUILD SUCCESSFUL`.
- [ ] 52 Tests, 0 Fehler, 0 übersprungen:

```bash
for f in build/test-results/test/TEST-*.xml; do
  grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1
done | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "tests="t" skipped="s" failures="f" errors="e}'
```

Abgedeckt: Config-Parsing, Permission-Entscheidungen, Subcommand-Pfade,
Migrationen, Repository-Roundtrips, Self-Role-Sicherheitsregeln, Ticket-Zustände,
Moderations-Guards, Embed-Kürzung.

---

## 2. Phase 0 – Repository Baseline

| Feature | Test |
|---|---|
| Sauberer Checkout baut | `git clone` in leeres Verzeichnis, `./gradlew build` |
| Keine Secrets im Repo | `git grep -iE "token|secret" -- '*.yml' '*.java'` |

- [ ] Frischer Klon baut ohne zusätzliche Handgriffe.
- [ ] `.env` und `data/` sind in `.gitignore`, `git status` zeigt sie nicht.
- [ ] `config.example.yml` enthält nur Nullen, keine echten IDs.

---

## 3. Phase 1 – Bot Core

### 3.1 Startup-Validierung

| Feature | Test | Erwartung |
|---|---|---|
| Pflicht-IDs geprüft | `guild.id` in `config.yml` auf `0` setzen, starten | Start bricht ab: `Fill in these IDs in config.yml: guild.id` |
| Fehlende Config | `LUMEN_CONFIG_PATH=/tmp/gibtsnicht.yml ./gradlew run` | `Config file not readable: ...` |
| Fehlendes Secret | Ohne `DISCORD_TOKEN` starten | `Missing required environment variable: DISCORD_TOKEN` |

- [ ] `guild.id: 0` → Start bricht ab und **nennt den Schlüssel**.
- [ ] `roles.staff: 0` → Start bricht ab und nennt `roles.staff`.
- [ ] Mehrere Nullen gleichzeitig → **alle** fehlenden Schlüssel werden genannt.
- [ ] Fehlende Config-Datei → verständlicher Fehler mit absolutem Pfad.
- [ ] Fehlendes `DISCORD_TOKEN` → verständlicher Fehler.
- [ ] Danach alles korrekt eintragen, Bot startet sauber.

### 3.2 Token-Sicherheit

- [ ] Log nach dem Start durchsuchen – der Token taucht **nirgends** auf:
      `./gradlew run 2>&1 | grep -c "$DISCORD_TOKEN"` → `0`.

### 3.3 Command-Registrierung

- [ ] Nach dem Start erscheint im Log `Registered 9 commands on guild <Name>`.
- [ ] In Discord `/` tippen: `help`, `status`, `roles`, `ticket`, `warn`,
      `warnings`, `timeout`, `kick`, `ban` sind sichtbar.
- [ ] Registrierung ist sofort da (guild-scoped, kein Stundenwarten).

### 3.4 `/help` und `/status`

- [ ] `/help` → **ephemere** Liste aller Befehle mit Beschreibung.
- [ ] `/status` → ephemer, zeigt `Status: verbunden`, `Laufzeit: 0d 0h Xm`,
      `Gateway: XX ms`.
- [ ] Laufzeit wächst nach ein paar Minuten sichtbar.

### 3.5 Permission Layer

- [ ] **Als Nicht-Staff**: `/roles allow` → `Dir fehlen die Rechte für diesen Befehl.`
- [ ] Im Log steht eine `Rejected /roles allow for user ...`-Zeile.
- [ ] **Als Nicht-Staff**: `/roles pick` funktioniert (öffentlicher Subcommand).
- [ ] **Als Staff**: `/roles allow` funktioniert.

### 3.6 Kontrollierter Shutdown

> Nicht über `./gradlew run` testen – Gradle leitet `SIGINT` nicht verlässlich an
> die gestartete JVM weiter, der Shutdown-Hook liefe dann gar nicht erst an.
> Stattdessen:
>
> ```bash
> ./gradlew installDist
> set -a && . ./.env && set +a
> ./build/install/lumen/bin/lumen
> ```

- [ ] Bot mit `Strg+C` beenden.
- [ ] Log: `Shutting down`, danach beendet sich der Prozess von selbst.
- [ ] In `#bot-log` erscheint **vor** dem Beenden noch `Lumen wird beendet`.
- [ ] Der Prozess hängt nicht: er ist spätestens nach 10 Sekunden weg
      (danach greift `shutdownNow()`).

---

## 4. Phase 2 – Storage / SQLite

| Feature | Test |
|---|---|
| Auto-Init | `rm -rf data/`, Bot starten |
| Versionierte Migration | `sqlite3 data/lumen.db "PRAGMA user_version;"` |
| Downgrade-Schutz | Version künstlich hochsetzen |
| Pfad konfigurierbar | `LUMEN_DATABASE_PATH=/tmp/x/y.db` |

- [ ] `rm -rf data/` und Start → `data/lumen.db` wird samt Verzeichnis angelegt.
- [ ] Log zeigt `Applied schema migration 1`.
- [ ] `sqlite3 data/lumen.db "PRAGMA user_version;"` → `1`.
- [ ] `sqlite3 data/lumen.db ".tables"` → `kingdom_role_state`, `minecraft_links`,
      `self_roles`, `server_status`, `sqlite_sequence`, `tickets`, `warnings`.
      (`sqlite_sequence` legt SQLite wegen `AUTOINCREMENT` selbst an, das ist korrekt.)
- [ ] `sqlite3 data/lumen.db "PRAGMA journal_mode;"` → `wal`.
- [ ] Zweiter Start → **keine** erneute Migration im Log, Daten unverändert.
- [ ] Downgrade-Schutz greift:
      ```bash
      sqlite3 data/lumen.db "PRAGMA user_version = 99;"
      ./gradlew run   # erwartet: Abbruch "Database is at schema version 99"
      sqlite3 data/lumen.db "PRAGMA user_version = 1;"   # zurücksetzen
      ```
- [ ] `LUMEN_DATABASE_PATH=/tmp/lumen-test/x.db ./gradlew run` legt dort an.

---

## 5. Phase 3 – Notification Layer

| Typ | Channel | Auslöser im MVP |
|---|---|---|
| `INFO` | `channels.announcements` | Ticket-Intro, Panel-Embeds |
| `SUCCESS` | `channels.announcements` | – (noch ungenutzt) |
| `WARNING` / `CRITICAL` | `channels.status` | – (Phase 13) |
| `MATCH` | `channels.matches` | – (Phase 11) |
| `STAFF` | `channels.bot-log` | Start/Stop, Self-Role-Freigabe, Tickets, Moderation |

- [ ] Start → `#bot-log` bekommt Embed `🛠️ Lumen gestartet` (graue Farbe, Zeitstempel).
- [ ] Shutdown → `#bot-log` bekommt `🛠️ Lumen wird beendet`.
- [ ] **Fehlkonfiguration:** `channels.bot-log` auf `0` setzen, Bot starten,
      `/roles allow` ausführen → Bot läuft weiter, Log zeigt
      `Dropped STAFF notification ... 'channels.bot-log' is not set in the config`.
- [ ] **Unerreichbarer Channel:** `channels.bot-log` auf eine erfundene ID setzen →
      Log zeigt `channel ... is not a text channel the bot can see`, **kein Crash**.
- [ ] **Fehlende Schreibrechte:** Bot in `#bot-log` `Nachrichten senden` entziehen →
      Log zeigt `missing permission to post in #bot-log`, **kein Crash**.
- [ ] Danach Konfiguration und Rechte wiederherstellen.

---

## 6. Phase 4 – Self Roles

### 6.1 Grundfluss

- [ ] **Staff:** `/roles allow role:Test-Farbe label:Farbe` →
      `` `Farbe` ist jetzt eine Self Role. ``
- [ ] `#bot-log` bekommt `Self Role freigegeben`.
- [ ] **Staff:** `/roles list` → zeigt `Farbe` mit Zustand `aktiv`.
- [ ] **Mitglied:** `/roles pick` → ephemeres Select-Menü mit `Farbe`.
- [ ] Rolle auswählen → `Hinzugefügt: Test-Farbe`, Rolle ist im Profil.
- [ ] `/roles pick` erneut → die eigene Rolle ist **vorausgewählt**.
- [ ] Auswahl abwählen und absenden → `Entfernt: Test-Farbe`, Rolle ist weg.
- [ ] Ohne Änderung absenden → `Nichts geändert.`

### 6.2 Panel

- [ ] **Staff:** `/roles panel create` in `#announcements` → öffentliches Embed
      `Self Roles` mit Menü, Antwort `Panel erstellt.`
- [ ] **Anderer Account** benutzt das Panel → bekommt seine eigene Rolle,
      **keine** Vorauswahl (öffentliches Panel ist geteilt).
- [ ] Bot neu starten, Panel erneut benutzen → funktioniert weiterhin
      (Component-IDs überleben den Neustart).

### 6.3 Sicherheit (die wichtigen Fälle)

- [ ] `/roles allow role:Fake-Admin` → `Abgelehnt: Die Rolle hat privilegierte
      Rechte: Administrator.`
- [ ] `/roles allow role:@everyone` → `Abgelehnt: @everyone ist keine Self Role.`
- [ ] `/roles allow role:Staff` → `Abgelehnt: Die Staff-Rolle kann keine Self Role sein.`
- [ ] `/roles allow role:Über-Bot` → `Abgelehnt: ... steht über meiner höchsten Rolle ...`
- [ ] `/roles allow role:<Bot-Integrationsrolle>` → `... von Discord oder einer
      Integration verwaltet.`
- [ ] **Nachträgliche Rechteerhöhung** (der entscheidende Test):
      1. `/roles allow role:Test-Farbe`
      2. `Test-Farbe` in den Servereinstellungen `Administrator` geben
      3. `/roles pick` → `Test-Farbe` ist **nicht** im Menü,
         Antwort enthält `Nicht verfügbar: Farbe.`
      4. Log zeigt `Refusing self role Farbe ...`
      5. Recht wieder entziehen
- [ ] **Deaktivieren statt löschen:** `/roles deny role:Test-Farbe` →
      `... Wer sie hat, behält sie.` Ein Mitglied mit der Rolle **behält** sie.
- [ ] `/roles list` zeigt sie danach als `deaktiviert`, Label bleibt erhalten.
- [ ] `/roles allow` erneut → sie ist wieder `aktiv` mit altem Label.

---

## 7. Phase 5 – Ticket System

### 7.1 Panel und Eröffnung

- [ ] **Staff:** `/ticket panel create` in `#announcements` → Embed `Tickets`
      mit Buttons `Support` und `Report`.
- [ ] **Mitglied** klickt `Support` → privater Channel `ticket-0001` in der
      Kategorie `Tickets`, ephemere Antwort mit Link.
- [ ] Im Channel: Begrüßungs-Embed, Erwähnung des Erstellers, Buttons
      `Übernehmen` und `Schließen`.
- [ ] `#bot-log` bekommt `Ticket eröffnet`.
- [ ] `/ticket create typ:Report` → `ticket-0002` (Nummer zählt hoch).

### 7.2 Sichtbarkeit (Akzeptanzkriterium)

- [ ] **Dritter Account ohne Staff** sieht `ticket-0001` **nicht** in der Kanalliste.
- [ ] Ersteller sieht den Channel.
- [ ] Staff sieht den Channel.
- [ ] In den Channel-Einstellungen: `@everyone` hat `Kanal ansehen` **verweigert**.

### 7.3 Claim

- [ ] **Nicht-Staff** klickt `Übernehmen` → `Nur das Team kann Tickets übernehmen.`
- [ ] **Staff** klickt `Übernehmen` → öffentliche Antwort
      `... kümmert sich um dieses Ticket.`
- [ ] **Zweiter Staff** klickt `Übernehmen` → `Das Ticket hat schon <@...> übernommen.`
- [ ] **Neustart-Test:** Bot neu starten, erneut `Übernehmen` klicken →
      immer noch `Das Ticket hat schon ... übernommen.` (Claim überlebt Neustart).
- [ ] `sqlite3 data/lumen.db "SELECT id,status,claimed_by FROM tickets;"` bestätigt `CLAIMED`.

### 7.4 Teilnehmer

- [ ] **Nicht-Staff:** `/ticket add` → `Dir fehlen die Rechte für diesen Befehl.`
- [ ] **Staff:** `/ticket add user:@Dritter` → Dritter sieht den Channel jetzt.
- [ ] **Staff:** `/ticket remove user:@Dritter` → Zugriff wieder weg.
- [ ] `/ticket remove user:<Ersteller>` → `Der Ersteller kann nicht entfernt werden.`
- [ ] `/ticket add` außerhalb eines Ticket-Channels → `Das hier ist kein Ticket-Channel.`

### 7.5 Schließen

- [ ] **Ersteller** klickt `Schließen` → `Ticket geschlossen. Der Channel wird in
      30 Sekunden gelöscht.`
- [ ] Sofort danach: Ersteller kann im Channel **nicht mehr schreiben** (gesperrt).
- [ ] `#bot-log` bekommt `Ticket geschlossen` **mit `.txt`-Anhang**.
- [ ] Nach ~30 s ist der Channel gelöscht.
- [ ] **Idempotenz:** neues Ticket öffnen, zweimal schnell `Schließen` klicken →
      zweiter Klick sagt `Dieses Ticket ist bereits geschlossen.`, nichts wird doppelt gelöscht.

### 7.6 Randfälle

- [ ] **Ein Ticket pro Person:** mit offenem Ticket erneut `Support` klicken →
      `Du hast schon ein offenes Ticket: #ticket-000X`.
- [ ] **Doppelklick:** Panel-Button zweimal sehr schnell → nur **ein** Channel entsteht.
- [ ] **Von Hand gelöschter Channel:** Ticket-Channel manuell löschen, dann erneut
      `Support` klicken → neues Ticket entsteht, Log zeigt
      `Ticket ticket-000X closed: its channel ... no longer exists`.
- [ ] **Fehlende Kategorie:** `channels.tickets-category` auf eine erfundene ID →
      `Die Ticket-Kategorie ist nicht eingerichtet.` statt Absturz.
- [ ] **Fehlende Botrechte:** Bot `Kanäle verwalten` in der Kategorie entziehen →
      `Mir fehlen die Rechte, einen Ticket-Channel anzulegen.`

> ⚠️ **Bekannter Vorbehalt Transcript:** Der Bot startet mit
> `JDABuilder.createDefault`, also **ohne** den privilegierten Intent
> `MESSAGE_CONTENT`. Im `.txt`-Transcript stehen daher Zeitstempel, Autoren und
> Anhang-Links korrekt, die **Nachrichtentexte anderer Mitglieder bleiben aber
> leer**. Siehe [Befunde](#befunde).

---

## 8. Phase 6 – Moderation Basics

Alle fünf Befehle sind Staff-only, alle Antworten sind **ephemer**, der
Audit-Trail steht in `#bot-log`.

### 8.1 Rechte

- [ ] **Als Nicht-Staff** je einmal `/warn`, `/warnings`, `/timeout`, `/kick`,
      `/ban` → jedes Mal `Dir fehlen die Rechte für diesen Befehl.`

### 8.2 `/warn` und `/warnings`

- [ ] `/warn user:@Testaccount grund:Spam` → `... wurde verwarnt. Grund: Spam.
      Das ist Verwarnung #1.`
- [ ] `#bot-log` bekommt `Verwarnt` mit Moderator, Ziel, ID und Grund.
- [ ] Erneut `/warn ... grund:Beleidigung` → `Das ist Verwarnung #2.`
- [ ] `/warnings user:@Testaccount` → ephemeres Embed `Insgesamt 2 Verwarnungen:`,
      **neueste zuerst**, mit `#id`, Datum, Moderator und Grund.
- [ ] `/warnings user:@Unbescholtener` → `Keine Verwarnungen.`
- [ ] **Persistenz:** Bot neu starten, `/warnings` erneut → beide Verwarnungen sind noch da.
- [ ] `sqlite3 data/lumen.db "SELECT * FROM warnings;"` bestätigt die Zeilen.
- [ ] `grund` ist bei `/warn` **Pflichtfeld** (Discord lässt den Befehl ohne nicht abschicken).

### 8.3 `/timeout`

- [ ] `/timeout user:@Testaccount minuten:2 grund:Abkühlen` →
      `... wurde für 2 Minuten stummgeschaltet.`
- [ ] Der Account hat in Discord sichtbar einen Timeout.
- [ ] `#bot-log` bekommt den Eintrag.
- [ ] Discord-Audit-Log zeigt als Grund `<Moderator>: Abkühlen`.
- [ ] **Grenzwerte:** `minuten:0` und `minuten:40321` lassen sich in Discord
      **gar nicht erst abschicken** (Client-Validierung durch `setRequiredRange`).
- [ ] `minuten:40320` (28 Tage) wird akzeptiert.
- [ ] Ohne `grund` → `Grund: Kein Grund angegeben`.

### 8.4 `/kick` und `/ban`

> Auf der Test-Guild mit dem Zweitaccount durchführen, nicht produktiv.

- [ ] `/kick user:@Testaccount grund:Test` → `... wurde gekickt.`, Account ist
      von der Guild entfernt, `#bot-log` hat den Eintrag.
- [ ] Account erneut einladen.
- [ ] `/ban user:@Testaccount grund:Test` → `... wurde gebannt.`, Account steht
      in der Bannliste.
- [ ] **Ban ohne Mitgliedschaft:** Account entbannen, Guild verlassen lassen,
      dann `/ban user:<ID des Accounts>` → funktioniert trotzdem.
- [ ] `/kick` auf jemanden, der nicht auf dem Server ist →
      `Diese Person ist nicht auf dem Server.`

### 8.5 Hierarchie und Guards (Akzeptanzkriterien)

- [ ] `/warn user:@DichSelbst` → `Dich selbst kannst du nicht moderieren.`
- [ ] `/kick user:@Lumen` (der Bot) → `Mich selbst kann ich nicht moderieren.`
- [ ] **Ziel über dem Moderator:** Testaccount die Rolle `Über-Bot` geben (die über
      der Staff-Rolle liegt), dann als Staff `/kick` → `Diese Person steht in der
      Rollenhierarchie nicht unter dir.`
- [ ] **Ziel über dem Bot:** Moderator zum Server-Admin machen, Ziel behält
      `Über-Bot` → `/kick` → `Diese Person steht in der Rollenhierarchie über mir.`
- [ ] **`/warn` ignoriert die Botposition:** im selben Aufbau `/warn` →
      **funktioniert**, weil eine Verwarnung nur eine Zeile schreibt.
- [ ] **Fehlendes Botrecht:** Bot das Recht `Mitglieder kicken` entziehen,
      `/kick` → `Mir fehlt das Recht „Kick Members" auf diesem Server.`
      (nicht: ein fehlgeschlagener Discord-Aufruf).
- [ ] Analog für `/ban` (`Ban Members`) und `/timeout` (`Timeout Members` – so
      nennt JDA das Recht, Discord zeigt es als „Mitglieder timeouten“).
- [ ] Alle Rechte wiederherstellen.

### 8.6 Audit-Vollständigkeit

- [ ] Nach allen obigen Tests enthält `#bot-log` für **jede erfolgreiche** Aktion
      genau einen Eintrag mit Moderator, Ziel, Ziel-ID und Grund.
- [ ] Für **abgelehnte** Aktionen steht **kein** Eintrag in `#bot-log`.
- [ ] Keine Moderationsmeldung ist in einem öffentlichen Channel gelandet.

---

## 9. Querschnitt

### 9.1 Zentraler Error Handler

- [ ] Bot läuft, aber Datenbankdatei zur Laufzeit unbrauchbar machen
      (`chmod 000 data/lumen.db`), dann `/warnings user:@X` →
      Nutzer bekommt `Da ist etwas schiefgelaufen. Bitte melde dich beim Team.`,
      **Bot läuft weiter**, Stacktrace steht im Log.
- [ ] `chmod 644 data/lumen.db` zurücksetzen.

### 9.2 Veraltete Komponenten

- [ ] Altes Ticket-Panel behalten, Bot mit geänderter Ticket-Logik neu starten,
      Button klicken → entweder funktioniert er oder es kommt eine verständliche
      Meldung, **nie** ein stiller Fehler.

### 9.3 Neustart-Gesamttest

- [ ] Zustand aufbauen: 1 Self Role freigegeben, 1 offenes geclaimtes Ticket,
      2 Verwarnungen.
- [ ] Bot neu starten.
- [ ] Alle drei Zustände sind unverändert vorhanden.

---

## Befunde

### Offen

| # | Phase | Befund | Auswirkung |
|---|---|---|---|
| 1 | 5 | Bot läuft ohne Intent `MESSAGE_CONTENT` (`JDABuilder.createDefault`). Ticket-Transcripts enthalten Zeitstempel, Autoren und Anhang-Links, aber **keine Nachrichtentexte** anderer Mitglieder. | Das Transcript archiviert das Gespräch nicht vollständig. Behebung: Intent im Discord Developer Portal aktivieren **und** in `Lumen.java` anfordern. |

### Neu gefunden

| # | Phase | Testfall | Beobachtet | Erwartet |
|---|---|---|---|---|
| | | | | |
