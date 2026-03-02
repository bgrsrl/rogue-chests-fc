# Rogue Chests FC — RuneLite Plugin

A RuneLite sideloaded plugin for the **Rogue Chests** friends chat. It automatically checks the thieving level of every member in the chat and alerts you if anyone joins without the required **84 Thieving**.

---

## What It Does

- **Overlay** — Displays a red-titled box on screen listing the names of any FC members whose thieving level is below 84
- **Sound alert** — Plays a sound when a new member joins the FC with thieving under 84
- **In-chat highlighting** — Colors those players' names **red** directly in the in-game friends chat member list
- **Auto-scan** — When you join the FC (or enable the plugin while already in it), it silently checks everyone already in the chat via the OSRS hiscores API
- **Auto-clears** — The overlay and highlighting reset when you leave the FC or log out

---

## How It Works

The plugin uses the public **OSRS Hiscores API** to look up each member's thieving level. Lookups are queued one at a time to avoid hammering the API. Players whose accounts are not on the hiscores (e.g. very low level or private) are skipped to avoid false positives.

No data is collected, stored, or sent anywhere other than the standard OSRS hiscores API (`secure.runescape.com`).

---

## Installation (Sideloaded Plugin)

1. Download `rogue-chests-fc-1.0.jar` from the [Releases](../../releases) page
2. Create the folder `%USERPROFILE%\.runelite\sideloaded-plugins\` if it doesn't exist
3. Drop the JAR into that folder
4. Restart RuneLite
5. Search for **Rogue Chests FC** in the plugin list and enable it

> **Note:** The plugin only activates when you are in the **Rogue Chests** friends chat. It does nothing outside of that FC.

---

## Source Code

The full source is in this repository. The two main files are:

- [`RogueChestsPlugin.java`](src/main/java/com/roguechests/RogueChestsPlugin.java) — event handling, hiscore lookups, widget coloring
- [`RogueChestsOverlay.java`](src/main/java/com/roguechests/RogueChestsOverlay.java) — the on-screen overlay panel

Built with the standard RuneLite plugin API. No obfuscation, no external dependencies beyond RuneLite itself.
