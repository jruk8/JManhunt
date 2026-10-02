---
icon: material/wrench
---

# Troubleshooting

> For: everyone.

**Q: Do I need to restart after enabling the engine?**

A: You can start playing now, but restart soon so every new map has a
reachable End portal.

**Q: The datapack stays red and won't enable no matter what I do.**

A: Regenerate `JManhunt/settings/world-engine` by deleting it and restarting
the server. Open an issue on GitHub with the relevant exception in server
logs.

**Q: Strongholds are generating in non-vanilla places.**

A: This is a deliberate feature, not a bug. The world engine uses a custom
stronghold spread algorithm. You cannot switch to the vanilla stronghold
spread algorithm because this would make certain cells unbeatable after a
certain point due to the Vanilla 128-per-world limit.

**Q: I want to disable the world engine.**

A: Run `/manhunt config world-engine enabled false` (or set it in
`config.yml`) and restart. The plugin removes its datapack itself;
cells already generated stay as they were.

**Q: Will this work in [specific Minecraft version]?**

A: This feature is tested to work on 26.2-26.3. If the plugin is marked to support
a newer version and you encounter issues, please open an issue on GitHub with
the relevant exception in server logs.
