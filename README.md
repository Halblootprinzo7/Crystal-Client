# Crystal Addon

Ein Addon für [Meteor Client](https://meteorclient.com) mit Modulen für Crystal- und Anchor-PvP sowie einem Schematic-Printer.
Alle Module liegen in Meteor in der Kategorie **Crystal**.

## Download

**[crystal-addon-1.0.0.jar herunterladen](https://github.com/Halblootprinzo7/Crystal-Client/raw/main/crystal-addon-1.0.0.jar)**

Voraussetzungen:

- Minecraft **1.21.11**
- Fabric Loader **0.18.2** oder neuer
- Meteor Client für 1.21.11
- Java **21**

## Installation

1. Fabric Loader für Minecraft 1.21.11 installieren.
2. Meteor Client und `crystal-addon-1.0.0.jar` in den Ordner `mods` legen.
3. Spiel starten und Meteor mit der rechten Shift-Taste öffnen. Die Module stehen in der Kategorie **Crystal**.

Am besten nur einen Crystal- bzw. Anchor-Optimizer (z. B. ClientSideCrystals, AnchorOptimizer) gleichzeitig verwenden. Mehrere davon können sich gegenseitig stören.

## Module

### Crystal

| Modul | Was es macht |
|---|---|
| **Auto Crystal** | Setzt und schlägt End Crystals auf das beste Ziel. Damage- und Selbstschaden-Grenzen, Bewegungsvorhersage, Face-Place, Smart-Delay für das Schadensfenster, Silent- oder Hotbar-Switch und Anti-Weakness. Obsidian, das du gerade selbst gesetzt hast oder anschaust, kommt zuerst dran. Optional **far-place**: Crystals bis 4,5 Blöcke weit setzen; sie explodieren, sobald du in Schlagreichweite (3 Blöcke) kommst. |
| **Double Tap** | Eine Taste: Ziel schlagen, Obsidian setzen, Crystal setzen, zünden. |
| **Sword Place** | Setzt Obsidian ans Fadenkreuz, ohne das Schwert aus der Hand zu nehmen. Funktioniert auch auf der normalen Use-Taste. |
| **Pop Window** | Senkt nach einem Totem-Pop des Ziels kurz die Damage-Grenze von Auto Crystal und Auto Anchor. |
| **Crystal Damage ESP** | Zeigt die Crystal-Plätze um ein Ziel und wie viel Schaden jeder machen würde. |
| **Suicide Prevent** | Pausiert die Auren, bevor ein eigener oder fremder Crystal dich töten würde. |

### Anchor

| Modul | Was es macht |
|---|---|
| **Crystal Anchor Macro** | Ein ganzer Anchor-Zyklus auf Tastendruck: Anchor setzen, laden, optional Schutzblock zwischen dich und den Anchor, zünden. Setzt Anchor und Schutzblock nicht in deinen Laufweg. Einstellbar: click-, place- und explode-speed, shield-wait, safe-anchor. Funktioniert auch mit Anchor-Optimizer-Mods. |
| **Auto Anchor** | Vollautomatische Respawn-Anchor-Aura mit bestätigten Schritten. |

### Verteidigung & Inventar

| Modul | Was es macht |
|---|---|
| **Smart Totem** | Wechselt den Offhand-Totem anhand des vorhergesagten Schadens. |
| **Inventory Totem** | Bewegt im offenen Inventar den Cursor auf einen Totem und drückt die Swap-Taste. |
| **Auto Block** | Hebt den Schild, wenn du gerade mit Crystals gecombot wirst. |
| **Auto Shield Break** | Schlägt einen blockenden Gegner mit der Axt, damit sein Schild in den Cooldown geht. |
| **Hotbar Refill** | Füllt die Hotbar aus dem Inventar wieder auf. |

### Info & HUD

| Modul | Was es macht |
|---|---|
| **Target Info** | Zeigt, ob das Ziel frei, umbaut, im Hole, getrappt oder burrowed ist. |
| **Fight Stats** | Zählt Schaden, Pops und Dauer eines Kampfes. |
| **Pop Counter** | Zählt die Totem-Pops jedes Spielers. |
| **Tier Spoof** | Zeigt in TierTagger einen beliebigen Tier auf deinem eigenen Namensschild. Nur lokal, sonst sieht es niemand. |
| **Crystal Info (HUD)** | HUD-Element: Ziel, Crystal-Schaden, Totems und eingehender Schaden. |

### Bauen

| Modul | Was es macht |
|---|---|
| **Schematic Builder** | Baut eine Litematica-Schematic an der Position deiner Litematica-Platzierung. **Automatic**: sucht den nächsten Block selbst und dreht sich dorthin. **Crosshair**: halbautomatischer Printer – dreht nie und setzt nur, was dein Fadenkreuz trifft. Nur auf echte Blockflächen (kein Air-Place) und nur mit der Ausrichtung, die die Schematic will. Nimmt den richtigen Block selbst aus der Hotbar. Bis 15 Blöcke pro Sekunde. Dazu Materialliste und Pause im Kampf. |

### Stealth

**Stealth** legt die Grenzen fest, an die sich alle Module halten, egal ob es an oder aus ist:

- **Reichweite:** Klicks nur in Vanilla-Reichweite und nur dort, wo der Blick landet. Nichts durch Wände.
- **Drehung:** maximale Drehung pro Tick, Glättung, Blickfeld.
- **Timing:** Reaktionszeit, Timing-Jitter und ein gemeinsames Klick-Budget pro Sekunde.
- **same-tick-switch:** erlaubt Slotwechsel und Klick im selben Tick, wie mit Zifferntaste und Maustaste.

Mit niedrigen Werten sind die Module am unauffälligsten. Höhere Werte machen sie schneller. Werte über 20 Klicks pro Sekunde, ein `replace-delay` von 0 und harte Snap-Drehungen schafft kein Mensch.

## Selbst bauen

```
./gradlew build
```

Die Jar liegt danach in `build/libs/`.

## Hinweis

Viele Server verbieten Clients wie Meteor und solche Addons. Benutze das Addon nur dort, wo die Server-Regeln es erlauben. Benutzung auf eigene Verantwortung.

## Lizenz

CC0 1.0, siehe [LICENSE](LICENSE).
