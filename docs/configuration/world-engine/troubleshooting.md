# Troubleshooting

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

A: Set `world-engine.enabled` to `false` in `config.yml` and disable
the `jmanhunt_world_engine` datapack with
`/datapack disable jmanhunt_world_engine`.

**Q: Will this work in [specific Minecraft version]?**

A: This feature is tested to work on 26.2. If the plugin is marked to support
a newer version and you encounter issues, please open an issue on GitHub with
the relevant exception in server logs.
