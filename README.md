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

### Import complete Bedrock model packs

MRPGNPC can import a ZIP containing Bedrock resource-pack assets. Copy the ZIP to:

```text
plugins/MRPGNPC/imports/
```

Then run:

```text
/mrn pack import <pack-id> <file.zip>
/mrn pack list
```

The importer:
- extracts JSON, geometry, textures, animation/controller definitions, render controllers, materials and supported audio files;
- rejects path traversal entries and enforces extraction limits (10,000 entries, 64 MiB per file, 512 MiB total);
- creates a resource-pack `manifest.json` when the ZIP does not include one;
- reads each `entity/*.entity.json` definition and tries to match its first geometry and texture to create a usable folder under `plugins/MRPGNPC/Skins/`;
- copies matching animation and animation-controller JSON into the generated skin folder;
- exports a Bedrock `.mcpack` at `plugins/MRPGNPC/exports/<pack-id>.mcpack`.

Use the generated skin name in the NPC config. For example, importing a pack with id `Mineville` and an entity file named `axolotl_pet.entity.json` creates `Mineville_axolotl_pet` when its geometry and texture can be resolved.

**Important:** importing a pack does not install or force it onto Bedrock clients. Install the exported `.mcpack` on clients or configure it as a server resource pack using the server's resource-pack mechanism. The client must have the same pack enabled for custom geometry, textures, animations and sounds to render/play.

### Animation controllers and sounds

Display NPC configurations now accept:

```yaml
Animation: "animation.axolotl_pet.idle"
AnimationInterval: 0
AnimationController: "__runtime_controller"
AnimationNextState: "default"
AnimationStopExpression: "query.any_animation_finished"
AnimationBlendOut: 0.0
Sound: ""
SoundVolume: 1.0
SoundPitch: 1.0
SoundInterval: 0
```

- `Animation`: exact Bedrock animation identifier.
- `AnimationController`: controller name placed in the Bedrock Animate Entity packet. Use a controller that is available to the client entity used by the NPC.
- `AnimationNextState`, `AnimationStopExpression` and `AnimationBlendOut`: packet playback controls.
- `Sound`: Bedrock sound event identifier declared by the enabled resource pack's `sounds/sound_definitions.json`.
- `SoundVolume` / `SoundPitch`: playback volume and pitch.
- `SoundInterval`: repeat interval in ticks; 0 disables automatic sound repetition.

You can trigger them on an already spawned NPC:

```text
/mrn npc animation <id> <animation> [controller]
/mrn npc sound <id> <sound-event> [volume] [pitch]
```

Sound events are sent to players in the same world within 64 blocks. The uploaded sample ZIP contains no `sounds/sound_definitions.json` or audio files, so it does not provide usable custom sound events by itself.

**Compatibility boundary:** Display NPCs still use the player-entity spawn protocol so the existing stable custom-skin/geometry behavior is preserved. The importer retains the original custom `minecraft:client_entity` definitions and render-controller files in the exported pack, but those definitions are not automatically registered as new server runtime entity types by this plugin. Likewise, ModelEngine controllers that depend on custom entity properties such as `query.property('modelengine:anim0')` need the matching client-entity/runtime-property integration; merely sending their controller name cannot create those properties. Direct animation packets and packet controller selection are supported, but a full ModelEngine property-driven controller runtime is not claimed.

## Existing combat mobs

Existing mob configurations in `plugins/MRPGNPC/Mobs/`, spawn points in `Points/`, skills in `Skills/` and `worldRandomSpawn.yml` remain separate from decorative NPC configurations. Existing commands and mob behavior are retained.

## Build

The `release` branch includes a GitHub Actions workflow that builds against NukkitPetteriM1Edition with Java 17 and runs server startup/spawn smoke tests.
