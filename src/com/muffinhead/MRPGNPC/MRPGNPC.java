package com.muffinhead.MRPGNPC;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import com.muffinhead.MRPGNPC.Events.MobNPCBeAttack;
import com.muffinhead.MRPGNPC.NPCs.DisplayNPC;
import com.muffinhead.MRPGNPC.NPCs.MobNPC;
import com.muffinhead.MRPGNPC.NPCs.NPC;
import com.muffinhead.MRPGNPC.Tasks.AutoSpawn;
import com.muffinhead.MRPGNPC.Tasks.worldRandomSpawn;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.nukkit.utils.Utils.readFile;

public class MRPGNPC extends PluginBase {
    public static MRPGNPC mrpgnpc;
    //public static Map<String, CompoundTag> tagMap = new HashMap<>();
    public static ConcurrentHashMap<String, Config> mobconfigs = new ConcurrentHashMap<String, Config>();
    public static ConcurrentHashMap<String, Config> pointconfigs = new ConcurrentHashMap<String, Config>();
    public static ConcurrentHashMap<String, Config> skillconfigs = new ConcurrentHashMap<String, Config>();
    public static ConcurrentHashMap<String, Config> displayNpcConfigs = new ConcurrentHashMap<>();
    public static Map<String, CompoundTag> skinTags = new HashMap<>();
    public static ConcurrentHashMap<String, Skin> skins = new ConcurrentHashMap<>();
    public static Config worldSpawnConfig;




    @Override
    public void onLoad() {
        Entity.registerEntity("MobNPC",MobNPC.class);
        Entity.registerEntity("DisplayNPC", DisplayNPC.class);
    }

    @Override
    public void onEnable() {
        getServer().getLogger().info("MRPGNPC is enable!The author is MuffinHead.");
        getServer().getPluginManager().registerEvents(new MobNPCBeAttack(),this);
        mrpgnpc = this;
        checkMobs();
        checkPoints();
        checkSkills();
        checkDisplayNPCs();
        checkWorldSpawnConfig();
        getServer().getScheduler().scheduleDelayedRepeatingTask(new AutoSpawn(),1,1);
        getServer().getScheduler().scheduleDelayedRepeatingTask(new worldRandomSpawn(),20,20);
        try {
            checkSkins();
        } catch (IOException e) {
            getServer().getLogger().alert("Skins check wrong！！");
        }
    }


    @Override
    public void onDisable() {
        for (Level level :getServer().getLevels().values()){
            for (Entity entity:level.getEntities()){
                if (entity instanceof MobNPC || entity instanceof DisplayNPC) {
                    entity.close();
                }
            }
        }
    }

    //command part
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("mrn")) {
            return super.onCommand(sender, command, label, args);
        }
        if (args.length == 0) return false;

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "clear": {
                if (args.length < 2) return false;
                String type = args[1].toLowerCase(Locale.ROOT);
                if (!type.equals("mobs") && !type.equals("npcs") && !type.equals("drops")) return false;
                for (Level level : getServer().getLevels().values()) {
                    for (Entity entity : level.getEntities()) {
                        if (type.equals("mobs") && entity instanceof MobNPC) {
                            entity.close();
                        } else if (type.equals("npcs") && entity instanceof DisplayNPC) {
                            entity.close();
                        } else if (type.equals("drops") && entity instanceof EntityItem) {
                            entity.close();
                        }
                    }
                }
                sender.sendMessage(type.equals("mobs")
                        ? "§aAll MRPGNPC mobs were cleared."
                        : type.equals("npcs")
                        ? "§aAll decorative NPCs were cleared."
                        : "§aAll dropped items were cleared.");
                return true;
            }
            case "pack": {
                if (args.length < 2) return false;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "list": {
                        Path packs = getDataFolder().toPath().resolve("ResourcePacks");
                        if (!Files.isDirectory(packs)) {
                            sender.sendMessage("§eNo imported resource packs found.");
                            return true;
                        }
                        try (java.util.stream.Stream<Path> stream = Files.list(packs)) {
                            List<String> names = new ArrayList<>();
                            stream.filter(Files::isDirectory).forEach(path -> names.add(path.getFileName().toString()));
                            sender.sendMessage(names.isEmpty() ? "§eNo imported resource packs found."
                                    : "§aImported packs: §f" + String.join(", ", names));
                        } catch (IOException exception) {
                            sender.sendMessage("§cCould not list resource packs: " + exception.getMessage());
                        }
                        return true;
                    }
                    case "import": {
                        if (args.length != 4 || !isValidConfigId(args[2])
                                || !args[3].matches("[A-Za-z0-9_.-]{1,96}\\.zip")) {
                            sender.sendMessage("§cUsage: /mrn pack import <id> <zip-name>");
                            sender.sendMessage("§7Place the ZIP in plugins/MRPGNPC/imports/ first.");
                            return true;
                        }
                        try {
                            Path imports = getDataFolder().toPath().resolve("imports");
                            Files.createDirectories(imports);
                            Path archive = imports.resolve(args[3]).normalize();
                            if (!archive.startsWith(imports.normalize())) {
                                sender.sendMessage("§cInvalid ZIP path.");
                                return true;
                            }
                            ResourcePackImporter.ImportResult result = ResourcePackImporter.importPack(
                                    archive, getDataFolder().toPath(), args[2]);
                            checkSkins();
                            sender.sendMessage("§aResource pack imported: §f" + args[2]);
                            sender.sendMessage("§7Files: " + result.files + " | models converted to skins: " + result.models);
                            sender.sendMessage("§7Export: plugins/MRPGNPC/exports/" + args[2] + ".mcpack");
                            if (!result.hasSounds) {
                                sender.sendMessage("§eNo sounds/sound_definitions.json was found in this ZIP.");
                            }
                            sender.sendMessage("§eInstall the exported pack on Bedrock clients/server and restart as required.");
                        } catch (Exception exception) {
                            getServer().getLogger().error("Could not import MRPGNPC resource pack", exception);
                            sender.sendMessage("§cResource pack import failed: " + exception.getMessage());
                        }
                        return true;
                    }
                    default:
                        sender.sendMessage("§eUsage: /mrn pack <import|list> ...");
                        return true;
                }
            }
            case "npc": {
                if (args.length < 2) return false;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "create": {
                        if (args.length < 3 || !isValidConfigId(args[2])) {
                            sender.sendMessage("§cUsage: /mrn npc create <id> (letters, numbers, _ and - only)");
                            return true;
                        }
                        File file = getDisplayNPCFolder().resolve(args[2] + ".yml").toFile();
                        if (file.exists()) {
                            sender.sendMessage("§cThis NPC file already exists.");
                            return true;
                        }
                        Config config = createDisplayNPCConfig(file.getPath());
                        config.save();
                        displayNpcConfigs.put(args[2], config);
                        sender.sendMessage("§aDisplay NPC configuration created. Edit it, then use /mrn npc spawn " + args[2] + ".");
                        return true;
                    }
                    case "delete": {
                        if (args.length < 3 || !isValidConfigId(args[2])) return false;
                        File file = getDisplayNPCFolder().resolve(args[2] + ".yml").toFile();
                        if (!file.exists()) {
                            sender.sendMessage("§cDisplay NPC configuration not found.");
                            return true;
                        }
                        if (!file.delete()) {
                            sender.sendMessage("§cCould not delete the display NPC configuration.");
                            return true;
                        }
                        displayNpcConfigs.remove(args[2]);
                        for (Level level : getServer().getLevels().values()) {
                            for (Entity entity : level.getEntities()) {
                                if (entity instanceof DisplayNPC
                                        && ((DisplayNPC) entity).getConfigId().equalsIgnoreCase(args[2])) {
                                    entity.close();
                                }
                            }
                        }
                        sender.sendMessage("§aDisplay NPC configuration and its active instances were removed.");
                        return true;
                    }
                    case "spawn": {
                        DisplayNPC npc = spawnDisplayNPC(sender, args);
                        if (npc != null) {
                            npc.spawnToAll();
                            sender.sendMessage("§aDisplay NPC spawned.");
                        }
                        return true;
                    }
                    case "list": {
                        if (displayNpcConfigs.isEmpty()) {
                            sender.sendMessage("§eNo display NPC configurations found.");
                        } else {
                            sender.sendMessage("§aDisplay NPCs: §f" + String.join(", ", displayNpcConfigs.keySet()));
                        }
                        return true;
                    }
                    case "animation": {
                        if (args.length < 4 || args.length > 5) {
                            sender.sendMessage("§cUsage: /mrn npc animation <id> <animation> [controller]");
                            return true;
                        }
                        DisplayNPC target = findDisplayNPC(args[2]);
                        if (target == null) {
                            sender.sendMessage("§cNo active display NPC found with config id: " + args[2]);
                            return true;
                        }
                        if (args.length == 5) target.setAnimationController(args[4]);
                        target.setDisplayAnimation(args[3], 0);
                        sender.sendMessage("§aAnimation sent to display NPC: " + args[2]);
                        return true;
                    }
                    case "sound": {
                        if (args.length < 4 || args.length > 6) {
                            sender.sendMessage("§cUsage: /mrn npc sound <id> <sound-event> [volume] [pitch]");
                            return true;
                        }
                        DisplayNPC target = findDisplayNPC(args[2]);
                        if (target == null) {
                            sender.sendMessage("§cNo active display NPC found with config id: " + args[2]);
                            return true;
                        }
                        try {
                            float volume = args.length >= 5 ? Float.parseFloat(args[4]) : 1.0f;
                            float pitch = args.length >= 6 ? Float.parseFloat(args[5]) : 1.0f;
                            target.playDisplaySound(args[3], volume, pitch);
                            sender.sendMessage("§aSound event sent: " + args[3]);
                        } catch (NumberFormatException exception) {
                            sender.sendMessage("§cVolume and pitch must be numeric.");
                        }
                        return true;
                    }
                    default:
                        sender.sendMessage("§eUsage: /mrn npc <create|spawn|delete|list> ...");
                        return true;
                }
            }
            case "mob": {
                if (args.length < 2) return false;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "create": {
                        if (args.length < 3) return false;
                        File file = getMobFolder().resolve(args[2] + ".yml").toFile();
                        if (file.exists()) {
                            sender.sendMessage("§cThis mob file already exists.");
                            return true;
                        }
                        Config config = createMobConfig(file.getPath());
                        config.save();
                        mobconfigs.put(args[2], config);
                        sender.sendMessage("§aMob file created successfully.");
                        return true;
                    }
                    case "delete": {
                        if (args.length < 3) return false;
                        File file = getMobFolder().resolve(args[2] + ".yml").toFile();
                        if (!file.exists()) {
                            sender.sendMessage("§cMob file not found.");
                            return true;
                        }
                        if (file.delete()) {
                            mobconfigs.remove(args[2]);
                            sender.sendMessage("§aMob file deleted successfully.");
                        } else {
                            sender.sendMessage("§cCould not delete the mob file.");
                        }
                        return true;
                    }
                    case "spawn": {
                        if (args.length != 3 && args.length < 9) {
                            sender.sendMessage("§cUsage: /mrn mob spawn <mob> [x y z world yaw pitch]");
                            return true;
                        }
                        MobNPC npc = spawnNPC(sender, args);
                        if (npc != null) {
                            npc.spawnToAll();
                            sender.sendMessage("§aMob spawned.");
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
            case "point": {
                if (args.length < 2) return false;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "create": {
                        if (!(sender instanceof Player) || args.length < 3) return false;
                        File file = getPointFolder().resolve(args[2] + ".yml").toFile();
                        if (file.exists()) {
                            sender.sendMessage("§cThis point file already exists.");
                            return true;
                        }
                        Config config = createPointConfig(file.getPath(), (Player) sender);
                        config.save();
                        pointconfigs.put(args[2], config);
                        sender.sendMessage("§aPoint file created successfully.");
                        return true;
                    }
                    case "delete": {
                        if (args.length < 3) return false;
                        File file = getPointFolder().resolve(args[2] + ".yml").toFile();
                        if (!file.exists()) {
                            sender.sendMessage("§cPoint file not found.");
                            return true;
                        }
                        if (file.delete()) {
                            pointconfigs.remove(args[2]);
                            sender.sendMessage("§aPoint file deleted successfully.");
                        } else {
                            sender.sendMessage("§cCould not delete the point file.");
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
            case "skill": {
                if (args.length < 3 || !args[1].equalsIgnoreCase("create")) return false;
                File file = getSkillFolder().resolve(args[2] + ".yml").toFile();
                if (file.exists()) {
                    sender.sendMessage("§cThis skill file already exists.");
                    return true;
                }
                Config config = createSkillConfig(file.getPath());
                config.save();
                skillconfigs.put(args[2], config);
                sender.sendMessage("§aSkill file created successfully.");
                return true;
            }
            case "reload": {
                for (Level level : getServer().getLevels().values()) {
                    for (Entity entity : level.getEntities()) {
                        if (entity instanceof MobNPC || entity instanceof DisplayNPC) entity.close();
                    }
                }
                mobconfigs.clear();
                pointconfigs.clear();
                skillconfigs.clear();
                displayNpcConfigs.clear();
                checkMobs();
                checkPoints();
                checkSkills();
                checkDisplayNPCs();
                checkWorldSpawnConfig();
                try {
                    checkSkins();
                } catch (IOException e) {
                    getServer().getLogger().error("Could not reload MRPGNPC skins", e);
                    sender.sendMessage("§cConfigs reloaded, but one or more skins could not be loaded.");
                    return true;
                }
                sender.sendMessage("§aMRPGNPC configs reloaded. Other mobs and dropped items were left untouched.");
                return true;
            }
            case "test": {
                if (!(sender instanceof Player)) return false;
                Player player = (Player) sender;
                String mobfile = "测试A号";
                Location location = player.getLocation();
                MobNPC npc = spawnNPC(getServer().getConsoleSender(), mobfile, location,
                        "MDungeon:" + mobfile + "::0");
                if (npc == null) {
                    sender.sendMessage("§cTest mob configuration was not found.");
                    return true;
                }
                npc.getActiveattackcreature().add("MDungeon");
                npc.getUnattractivecreature().add("NotMDungeon");
                npc.spawnToAll();
                return true;
            }
            default:
                return false;
        }
    }

    public Path getMobFolder() {
        return getDataFolder().toPath().resolve("Mobs");
    }

    public Path getPointFolder() {
        return getDataFolder().toPath().resolve("Points");
    }

    public Path getSkillFolder() {
        return getDataFolder().toPath().resolve("Skills");
    }

    public Path getDisplayNPCFolder() {
        return getDataFolder().toPath().resolve("NPCs");
    }

    private boolean isValidConfigId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,48}");
    }

    public Config createDisplayNPCConfig(String configPath) {
        Config config = new Config(configPath, Config.YAML);
        config.set("DisplayName", "Display NPC");
        config.set("ModelType", "PLAYER"); // PLAYER or BLOCK (custom Bedrock geometry)
        config.set("Skin", "");
        config.set("Scale", 1.0);
        config.set("HitboxWidth", -1); // -1 uses the ModelType default
        config.set("HitboxHeight", -1); // -1 uses the ModelType default
        config.set("Immobile", true);
        config.set("Collidable", false);
        config.set("NameTag", "");
        config.set("NameTagVisible", false);
        config.set("NameTagAlwaysVisible", false);
        config.set("Animation", "");
        config.set("AnimationInterval", 0);
        config.set("AnimationController", "__runtime_controller");
        config.set("AnimationNextState", "default");
        config.set("AnimationStopExpression", "query.any_animation_finished");
        config.set("AnimationBlendOut", 0.0);
        config.set("Sound", "");
        config.set("SoundVolume", 1.0);
        config.set("SoundPitch", 1.0);
        config.set("SoundInterval", 0);
        return config;
    }

    public void checkDisplayNPCs() {
        File folder = getDisplayNPCFolder().toFile();
        if (!folder.exists() && !folder.mkdirs()) {
            getServer().getLogger().warning("Could not create MRPGNPC/NPCs folder.");
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            if (isValidConfigId(id)) {
                displayNpcConfigs.put(id, new Config(file.getPath(), Config.YAML));
            } else {
                getServer().getLogger().warning("Ignoring display NPC config with invalid file name: " + file.getName());
            }
        }
    }

    private DisplayNPC spawnDisplayNPC(CommandSender sender, String[] args) {
        if (args.length < 3 || !isValidConfigId(args[2])) {
            sender.sendMessage("§cUsage: /mrn npc spawn <id> [x y z world yaw pitch]");
            return null;
        }
        Config config = displayNpcConfigs.get(args[2]);
        if (config == null) {
            sender.sendMessage("§cDisplay NPC configuration not found: " + args[2]);
            return null;
        }

        Location location;
        if (args.length == 3) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cConsole spawning requires x y z world yaw pitch.");
                return null;
            }
            Player player = (Player) sender;
            Vector3 target = player.getTargetBlock(3);
            if (target == null) target = player.getLocation();
            location = new Location(target.x, target.y, target.z, player.getYaw(), player.getPitch(), player.getLevel());
        } else {
            if (args.length != 9) {
                sender.sendMessage("§cUsage: /mrn npc spawn <id> [x y z world yaw pitch]");
                return null;
            }
            String worldName = args[6];
            if (!getServer().isLevelLoaded(worldName) && !getServer().loadLevel(worldName)) {
                sender.sendMessage("§cWorld not found: " + worldName);
                return null;
            }
            Level level = getServer().getLevelByName(worldName);
            if (level == null) {
                sender.sendMessage("§cWorld not found: " + worldName);
                return null;
            }
            try {
                location = new Location(Double.parseDouble(args[3]), Double.parseDouble(args[4]),
                        Double.parseDouble(args[5]), Double.parseDouble(args[7]),
                        Double.parseDouble(args[8]), level);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cInvalid NPC coordinates or rotation.");
                return null;
            }
        }

        Level level = location.getLevel();
        if (level == null || !level.loadChunk(location.getChunkX(), location.getChunkZ(), true)
                || location.getChunk() == null) {
            sender.sendMessage("§cCould not load the display NPC spawn chunk.");
            return null;
        }

        DisplayNPC npc = new DisplayNPC(location.getChunk(), DisplayNPC.getDefaultNBT(location));
        String modelType = config.getString("ModelType", "PLAYER").trim().toUpperCase(Locale.ROOT);
        if (!modelType.equals("PLAYER") && !modelType.equals("BLOCK")) {
            getServer().getLogger().warning("Invalid ModelType '" + modelType + "' in NPC " + args[2] + "; using PLAYER.");
            modelType = "PLAYER";
        }
        double configuredScale = config.getDouble("Scale", 1.0);
        if (Double.isNaN(configuredScale)) configuredScale = 1.0;
        if (Double.isInfinite(configuredScale)) configuredScale = 1.0;
        npc.setScale((float) Math.max(0.01, Math.min(16.0, configuredScale)));
        double defaultHitboxWidth = modelType.equals("BLOCK") ? 0.8 : 0.6;
        double defaultHitboxHeight = modelType.equals("BLOCK") ? 0.8 : 1.8;
        double configuredHitboxWidth = config.getDouble("HitboxWidth", -1);
        double configuredHitboxHeight = config.getDouble("HitboxHeight", -1);
        if (configuredHitboxWidth <= 0) configuredHitboxWidth = defaultHitboxWidth;
        if (configuredHitboxHeight <= 0) configuredHitboxHeight = defaultHitboxHeight;
        npc.configure(args[2], config.getString("Animation", ""),
                Math.max(0, config.getInt("AnimationInterval", 0)),
                (float) configuredHitboxWidth, (float) configuredHitboxHeight);
        npc.configureAnimationControl(
                config.getString("AnimationController", "__runtime_controller"),
                config.getString("AnimationNextState", "default"),
                config.getString("AnimationStopExpression", "query.any_animation_finished"),
                (float) config.getDouble("AnimationBlendOut", 0.0));
        npc.configureSound(config.getString("Sound", ""),
                (float) config.getDouble("SoundVolume", 1.0),
                (float) config.getDouble("SoundPitch", 1.0),
                Math.max(0, config.getInt("SoundInterval", 0)));
        npc.setImmobile(config.getBoolean("Immobile", true));
        npc.setCollidable(config.getBoolean("Collidable", false));

        String skinName = config.getString("Skin", "").trim();
        if (modelType.equals("BLOCK") && skinName.isEmpty()) {
            sender.sendMessage("§cModelType BLOCK requires a custom Skin from the Skins folder.");
            npc.close();
            return null;
        }
        if (!skinName.isEmpty()) {
            Skin skin = skins.get(skinName);
            if (skin == null) {
                getServer().getLogger().warning("Display NPC '" + args[2] + "' references missing skin '" + skinName + "'.");
                sender.sendMessage("§cSkin not found: " + skinName + ". NPC was not spawned.");
                npc.close();
                return null;
            }
            npc.setSkin(skin);
            npc.setSkinname(skinName);
        }

        String nameTag = config.getString("NameTag", config.getString("DisplayName", ""));
        npc.setNameTag(nameTag);
        npc.setNameTagVisible(config.getBoolean("NameTagVisible", !nameTag.isEmpty()));
        npc.setNameTagAlwaysVisible(config.getBoolean("NameTagAlwaysVisible", false));
        return npc;
    }

    public Config createMobConfig(String configPath) {
        Config config = new Config(configPath, Config.YAML);
        config.set("DisplayName", "Mob");
        config.set("MaxHealth", 40);
        config.set("Size", 1.0);
        config.set("MovementSpeed", 1.0);
        config.set("Damage", 3.0);
        config.set("KnockBack", 0.1);
        config.set("DefenseFormula", "source.damage");
        config.set("AttackDelay", 30);
        config.set("DamageDelay", 0);
        config.set("BedamagedDelay", 0);
        config.set("AttackRange", 1.2);
        config.set("HitRange",0.15);
        config.set("HateRange", 15.0);
        config.set("NoHatesHeal", "200:1.0");
        config.set("CanBeKnockBack", false);
        config.set("BoundingBox",true);
        config.set("DeathCommands", new ArrayList<>());
        config.set("Skin", "GreenCross");
        config.set("ItemInHand", "267:0");
        config.set("BeDamagedBlockParticleID", "152:0");
        config.set("MoveLimitDistance", 15.0);
        config.set("ActiveAttackCreature",new ArrayList<>());
        config.set("UnattractiveCreature",new ArrayList<>());
        config.set("Drops", new ArrayList<>());
        config.set("Camp", "Example");
        config.set("Skills",new ArrayList<>());
        return config;
    }

    /*
    mobfilename-respawntick-1timespawnamount-maxamount-spawnlimit
     */
    public Config createPointConfig(String configPath,Player player) {
        Config config = new Config(configPath, Config.YAML);
        config.set("PointName", "A");
        config.set("PointPosition",player.getX()+":"+player.getY()+":"+player.getZ()+":"+player.getLevel().getName()+":"+player.getYaw()+":"+player.getPitch());
        config.set("SpawnList", new ArrayList<>());
        return config;
    }

    public Config createSkillConfig(String configPath) {
        Config config = new Config(configPath, Config.YAML);
        config.set("Skills", new ArrayList<>());
        return config;
    }
    public MobNPC spawnNPC(CommandSender sender, String mobfile, Location location, String mobFeature) {
        String[] args = new String[9];
        args[0] = "";
        args[1] = "";
        args[2] = mobfile;
        args[3] = String.valueOf(location.x);
        args[4] = String.valueOf(location.y);
        args[5] = String.valueOf(location.z);
        args[6] = location.getLevel().getName();
        args[7] = String.valueOf(location.getYaw());
        args[8] = String.valueOf(location.getPitch());
        MobNPC npc = spawnNPC(sender,args);
        if (npc != null) {
            npc.setMobFeature(mobFeature);
        }
        return npc;
    }

    public MobNPC spawnNPC(CommandSender sender, String[] args) {
        if (args == null || args.length < 3) {
            sender.sendMessage("§cUsage: /mrn mob spawn <mob> [x y z world yaw pitch]");
            return null;
        }

        String mobFile = args[2];
        Config config = mobconfigs.get(mobFile);
        if (config == null) {
            sender.sendMessage("§cMob configuration not found: " + mobFile);
            return null;
        }

        Location location;
        if (args.length == 3) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cConsole spawning requires x y z world yaw pitch.");
                return null;
            }
            Player player = (Player) sender;
            Vector3 target = player.getTargetBlock(3);
            if (target == null) target = player.getLocation();
            location = new Location(target.x, target.y, target.z,
                    player.getYaw(), player.getPitch(), player.getLevel());
        } else {
            if (args.length < 9) {
                sender.sendMessage("§cUsage: /mrn mob spawn <mob> [x y z world yaw pitch]");
                return null;
            }
            String levelName = args[6];
            if (!getServer().isLevelLoaded(levelName) && !getServer().loadLevel(levelName)) {
                sender.sendMessage("§cWorld not found: " + levelName);
                return null;
            }
            Level level = getServer().getLevelByName(levelName);
            if (level == null) {
                sender.sendMessage("§cWorld not found: " + levelName);
                return null;
            }
            try {
                location = new Location(
                        Double.parseDouble(args[3]),
                        Double.parseDouble(args[4]),
                        Double.parseDouble(args[5]),
                        Double.parseDouble(args[7]),
                        Double.parseDouble(args[8]),
                        level
                );
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cInvalid coordinates or rotation.");
                return null;
            }
        }

        Level level = location.getLevel();
        if (level == null || !level.loadChunk(location.getChunkX(), location.getChunkZ(), true)) {
            sender.sendMessage("§cCould not load the mob spawn chunk.");
            return null;
        }
        if (location.getChunk() == null) {
            sender.sendMessage("§cCould not access the mob spawn chunk.");
            return null;
        }

        MobNPC npc = new MobNPC(location.getChunk(), MobNPC.getDefaultNBT(location));
        npc.setDisplayName(config.getString("DisplayName", mobFile));
        npc.setMaxHealth(Math.max(1, config.getInt("MaxHealth", 40)));
        npc.setHealth(npc.getMaxHealth());
        npc.setScale((float) config.getDouble("Size", 1.0));
        npc.setSpeed(config.getDouble("MovementSpeed", 1.0));
        npc.setDamage(config.getDouble("Damage", 3.0));
        npc.setKnockback(config.getDouble("Knockback",
                config.getDouble("KnockBack", 0.1)));
        npc.setDefenseformula(config.getString("DefenseFormula", "source.damage"));
        npc.setAttackdelay(config.getInt("AttackDelay", 30));
        npc.setDamagedelay(config.getInt("DamageDelay", 0));
        npc.setBedamageddelay(config.getInt("BedamagedDelay", 0));
        npc.setAttackrange(config.getDouble("AttackRange", 1.2));
        npc.setHaterange(config.getDouble("HateRange", 15.0));
        npc.setNohatesheal(config.getString("NoHatesHeal", "200:1.0"));
        npc.setHitrange(config.getDouble("HitRange", 0.15));
        npc.setCanbeknockback(config.getBoolean("CanBeKnockBack", false));
        npc.setDeathcommands(getStringList(config, "DeathCommands"));
        npc.setCamp(config.getString("Camp", "Example"));
        npc.setSkills(getStringList(config, "Skills"));
        npc.getInventory().setItemInHand(getItemByString(config.getString("ItemInHand", "267:0")));
        npc.setBeDamagedblockparticle(config.getString("BeDamagedBlockParticleID", "152:0"));
        npc.setActiveattackcreature(getStringList(config, "ActiveAttackCreature"));
        npc.setUnattractivecreature(getStringList(config, "UnattractiveCreature"));
        npc.setDrops(getStringList(config, "Drops"));
        String skinName = config.getString("Skin", "");
        npc.setSkinname(skinName);
        Skin skin = skins.get(skinName);
        if (skin != null) npc.setSkin(skin);
        boolean boundingBox = config.getAll().containsKey("BoundingBox")
                ? config.getBoolean("BoundingBox")
                : config.getBoolean("BoudningBox", true);
        npc.setEnableBox(boundingBox);
        npc.moveLimitDistance = config.getDouble("MoveLimitDistance", 15.0);
        return npc;
    }

    private DisplayNPC findDisplayNPC(String configId) {
        if (configId == null) return null;
        for (Level level : getServer().getLevels().values()) {
            for (Entity entity : level.getEntities()) {
                if (entity instanceof DisplayNPC
                        && ((DisplayNPC) entity).getConfigId().equalsIgnoreCase(configId)) {
                    return (DisplayNPC) entity;
                }
            }
        }
        return null;
    }

    private List<String> getStringList(Config config, String key) {
        List<String> values = config.getList(key);
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    public Item getItemByString(String s){
        try {
            if (s == null) throw new IllegalArgumentException("Item definition is null");
            String[] parts = s.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Expected item format id:damage");
            int id = Integer.parseInt(parts[0].trim());
            int damage = Integer.parseInt(parts[1].trim());
            Item item = Item.get(id);
            if (item == null) throw new IllegalArgumentException("Unknown item id " + id);
            item.setDamage(damage);
            return item;
        } catch (RuntimeException exception) {
            getServer().getLogger().warning("Invalid MRPGNPC ItemInHand '" + s + "'. Using air instead.");
            return Item.get(0);
        }
    }
    private Path findGeometryFile(Path skinFolder) throws IOException {
        Path preferred = skinFolder.resolve("geometry.json");
        if (Files.isRegularFile(preferred)) return preferred;
        try (java.util.stream.Stream<Path> files = Files.list(skinFolder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".geo.json"))
                    .sorted()
                    .findFirst()
                    .orElse(preferred);
        }
    }

    private Path findAnimationFile(Path skinFolder) throws IOException {
        Path preferred = skinFolder.resolve("animations.json");
        if (Files.isRegularFile(preferred)) return preferred;
        Path legacy = skinFolder.resolve("animation.json");
        if (Files.isRegularFile(legacy)) return legacy;
        try (java.util.stream.Stream<Path> files = Files.list(skinFolder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".animation.json"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }
    }

    public void checkSkins() throws IOException {
        Path skinPath = getDataFolder().toPath().resolve("Skins");
        File skinsFolder = skinPath.toFile();
        if (!skinsFolder.exists() && !skinsFolder.mkdirs()) {
            throw new IOException("Could not create skins directory: " + skinsFolder);
        }

        skins.clear();
        File[] folders = skinsFolder.listFiles();
        if (folders == null) return;

        for (File skinFolder : folders) {
            if (!skinFolder.isDirectory()) continue;
            Path geometryPath = findGeometryFile(skinFolder.toPath());
            Path texturePath = skinFolder.toPath().resolve("skin.png");
            if (!Files.isRegularFile(geometryPath) || !Files.isRegularFile(texturePath)) {
                getServer().getLogger().warning("Skipping incomplete MRPGNPC skin folder: " + skinFolder.getName());
                continue;
            }

            Skin skin;
            Config geometryConfig = new Config(geometryPath.toString(), Config.JSON);
            if (geometryConfig.getAll().containsKey("format_version")) {
                skin = newSkinNew(skinFolder.toPath());
            } else {
                skin = newSkinOld(skinFolder.toPath());
            }
            if (skin == null || skin.getSkinData() == null) {
                getServer().getLogger().warning("Could not load MRPGNPC skin: " + skinFolder.getName());
                continue;
            }

            Path capePath = skinFolder.toPath().resolve("cape.png");
            if (Files.isRegularFile(capePath)) {
                try {
                    BufferedImage capeData = ImageIO.read(capePath.toFile());
                    if (capeData != null) {
                        skin.setCapeData(capeData);
                        skin.setCapeId(skinFolder.getName());
                    }
                } catch (IOException exception) {
                    getServer().getLogger().warning("Could not load cape for skin " + skinFolder.getName());
                }
            }
            Path animationPath = findAnimationFile(skinFolder.toPath());
            if (animationPath != null && Files.isRegularFile(animationPath)) {
                try {
                    String animationData = Files.readString(animationPath, StandardCharsets.UTF_8);
                    if (!animationData.trim().isEmpty()) skin.setAnimationData(animationData);
                } catch (IOException exception) {
                    getServer().getLogger().warning("Could not load animation data for skin " + skinFolder.getName()
                            + ": " + exception.getMessage());
                }
            }
            skins.put(skinFolder.getName(), skin);
        }
    }

    private String getGeometryIdentifier(String geometryData) {
        if (geometryData == null || geometryData.isEmpty()) return "geometry.mrpgnpc";
        Matcher matcher = Pattern.compile("\"identifier\"\\s*:\\s*\"([^\"]+)\"").matcher(geometryData);
        return matcher.find() ? matcher.group(1) : "geometry.mrpgnpc";
    }

    public Skin newSkinOld(Path path) throws IOException {
        Skin skin = new Skin();
        Path skinfolder = getDataFolder().toPath().resolve("Skins");
        Path skinthings = skinfolder.resolve(path);
        Path skinpath = skinthings.resolve("skin.png");
        Path geometrypath = findGeometryFile(skinthings);
        // Path animationpath = skinPath.resolve(skinthings.getName()).resolve("geometry.animation.json");
        BufferedImage skindata = null;
        String skingeometry = null;
        //  String skinanimation = null;
        try {
            skindata = ImageIO.read(skinpath.toFile());
            skingeometry = new String(Files.readAllBytes(geometrypath), StandardCharsets.UTF_8);
            //skinanimation = new String(Files.readAllBytes(animationpath),StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println(skinpath + ":" + geometrypath);
        }
        if (skindata != null) {
            skin.setSkinData(skindata);
            // if (skinanimation!=null) {

            //      skin.setAnimationData(skinanimation);
            // }
            if (skingeometry != null) {
                skin.setGeometryData(skingeometry);
            } else {
                getServer().getLogger().warning("Invalid geometry file for skin " + skinthings.getFileName());
            }
            String geometryIdentifier = getGeometryIdentifier(skingeometry);
            skin.setGeometryName(geometryIdentifier);
            skin.setSkinResourcePatch("{\"geometry\":{\"default\":\"" + geometryIdentifier + "\"}}");
            skin.setSkinId(skinthings.toFile().getName());
        }
        return skin;
    }


    public Skin newSkinNew(Path path) throws IOException {
        Skin skin = new Skin();
        skin.generateSkinId("mrpgnpc");
        String geometryData = Files.readString(findGeometryFile(path), StandardCharsets.UTF_8);
        String geometryIdentifier = getGeometryIdentifier(geometryData);
        skin.setGeometryName(geometryIdentifier);
        skin.setSkinResourcePatch("{\"geometry\":{\"default\":\"" + geometryIdentifier + "\"}}");
        skin.setGeometryData(geometryData);
        skin.setSkinData(ImageIO.read(Paths.get(path.toString() + "/skin.png").toFile()));
        skin.setTrusted(true);
        return skin;
    }
    public void checkWorldSpawnConfig(){
        worldSpawnConfig = new Config(getDataFolder().toPath().resolve("worldRandomSpawn.yml").toString(), Config.YAML);
    }
    public void checkMobs() {
        File folder = getMobFolder().toFile();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            mobconfigs.put(file.getName().substring(0, file.getName().length() - 4), new Config(file.getPath(), Config.YAML));
        }
    }

    public void checkPoints() {
        File folder = getPointFolder().toFile();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            pointconfigs.put(file.getName().substring(0, file.getName().length() - 4), new Config(file.getPath(), Config.YAML));
        }
    }

    public void checkSkills() {
        File folder = getSkillFolder().toFile();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            skillconfigs.put(file.getName().substring(0, file.getName().length() - 4), new Config(file.getPath(), Config.YAML));
        }
    }

}
