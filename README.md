# MRPGNPC — NukkitPetteriM1Edition

MRPGNPC adds configurable custom mobs to NukkitPetteriM1Edition, including combat, skills, drops, spawn points and optional MDungeon integration. It also supports **decorative display NPCs** that do not run the mob AI.

## Decorative NPCs

Display NPCs are separate from combat mobs. They do not acquire targets, attack, run skills, drop items or execute death commands.

### Create and spawn

```text
/mrn npc create statue
```

Edit `plugins/MRPGNPC/NPCs/statue.yml`, then spawn it:

```text
/mrn npc spawn statue
```

You can also supply an explicit location:

```text
/mrn npc spawn statue <x> <y> <z> <world> <yaw> <pitch>
```

Other commands:

- `/mrn npc list` — list display NPC configurations.
- `/mrn npc delete <id>` — delete the configuration and close active instances created from it.
- `/mrn clear npcs` — remove all currently spawned decorative NPCs.
- `/mrn reload` — reload configurations and close current MRPGNPC entities.

### Example configuration

```yaml
DisplayName: "Display NPC"
ModelType: "PLAYER" # PLAYER or BLOCK
Skin: "Guide"
Scale: 1.0
HitboxWidth: -1 # -1 = default for ModelType
HitboxHeight: -1 # -1 = default for ModelType
Immobile: true
Collidable: false
NameTag: "§eServer Guide"
NameTagVisible: true
NameTagAlwaysVisible: true
Animation: "animation.guide.idle"
AnimationInterval: 0
```

| Setting | Description |
|---|---|
| `ModelType: PLAYER` | Humanoid-style display. A custom skin/geometry can still be used. |
| `ModelType: BLOCK` | Display preset for block-like 3D geometry. Requires a custom skin/model. Its default hitbox is 0.8 × 0.8. |
| `Skin` | Name of a folder inside `plugins/MRPGNPC/Skins/`. |
| `Scale` | Entity model scale. |
| `HitboxWidth` / `HitboxHeight` | Server-side collision/hitbox dimensions. Set to -1 to use the model preset defaults. |
| `Immobile` | Makes the display entity stationary. |
| `Collidable` | Whether other entities collide with the display. |
| `NameTag` | Text shown above the model. |
| `NameTagVisible` | Whether the name tag is visible. |
| `NameTagAlwaysVisible` | Whether the name tag stays visible at a distance. |
| `Animation` | Exact animation identifier defined by the Bedrock resource pack. Empty disables animation. |
| `AnimationInterval` | Repeat interval in ticks. 0 sends the animation once when each client receives the NPC; 20 = one second. |

### Resource-pack models and skins

Create one folder per model under:

```text
plugins/
└── MRPGNPC/
    └── Skins/
        └── Guide/
            ├── skin.png
            ├── geometry.json
            ├── animations.json       # optional
            └── cape.png               # optional
```

The geometry file may also use a Bedrock resource-pack filename ending in `.geo.json`. Animation data may be named `animations.json`, `animation.json` or end in `.animation.json`. The plugin reads the geometry identifier from the geometry JSON instead of assuming a fixed identifier.

- `skin.png`: texture image for the model.
- `geometry.json` / `*.geo.json`: Bedrock geometry definition. For a block-like model, define the desired 3D shape here.
- `animations.json` / `*.animation.json`: Bedrock animation definitions sent with the NPC skin data.
- `cape.png`: optional cape texture.

The resource pack itself must also be installed/enabled on the Bedrock clients and contain the matching geometry, texture, animation and entity/client-entity definitions. MRPGNPC does not distribute or force-install a resource pack. The `Animation` value must match an animation identifier supported by the client-side entity definition. Animation playback packets require Bedrock protocol 1.16.100 or newer.

**Important distinction:** `ModelType: BLOCK` is a player-protocol display entity rendered with custom Bedrock geometry, not a real world block or block entity. This allows a 3D resource-pack model to be positioned, scaled, named and animated without placing or replacing blocks in the world.

## Existing combat mobs

Existing mob configurations in `plugins/MRPGNPC/Mobs/`, spawn points in `Points/`, skills in `Skills/` and `worldRandomSpawn.yml` remain separate from decorative NPC configurations. Existing commands and mob behavior are retained.

## Build

The `release` branch includes a GitHub Actions workflow that builds against NukkitPetteriM1Edition with Java 17 and runs server startup/spawn smoke tests.
