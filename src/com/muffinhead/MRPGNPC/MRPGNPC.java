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

import static cn.nukkit.utils.Utils.readFile;

public class MRPGNPC extends PluginBase {
    public static MRPGNPC mrpgnpc;
    //public static Map<String, CompoundTag> tagMap = new HashMap<>();
    public static ConcurrentHashMap<String, Config> mobconfigs = new ConcurrentHashMap<String, Config>();
    public static ConcurrentHashMap<String, Config> pointconfigs = new ConcurrentHashMap<String, Config>();
    public static ConcurrentHashMap<String, Config> skillconfigs = new ConcurrentHashMap<String, Config>();
    public static Map<String, CompoundTag> skinTags = new HashMap<>();
    public static ConcurrentHashMap<String, Skin> skins = new ConcurrentHashMap<>();
    public static Config worldSpawnConfig;




    @Override
    public void onLoad() {
        Entity.registerEntity("MobNPC",MobNPC.class);
    }

    @Override
    public void onEnable() {
        getServer().getLogger().info("MRPGNPC is enable!The author is MuffinHead.");
        getServer().getPluginManager().registerEvents(new MobNPCBeAttack(),this);
        mrpgnpc = this;
        checkMobs();
        checkPoints();
        checkSkills();
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
                if (entity instanceof MobNPC){
                    entity.kill();
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
                if (!type.equals("mobs") && !type.equals("drops")) return false;
                for (Level level : getServer().getLevels().values()) {
                    for (Entity entity : level.getEntities()) {
                        if (type.equals("mobs") && entity instanceof MobNPC) {
                            entity.close();
                        } else if (type.equals("drops") && entity instanceof EntityItem) {
                            entity.close();
                        }
                    }
                }
                sender.sendMessage(type.equals("mobs")
                        ? "§aAll MRPGNPC mobs were cleared."
                        : "§aAll dropped items were cleared.");
                return true;
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
                        if (entity instanceof MobNPC) entity.close();
                    }
                }
                mobconfigs.clear();
                pointconfigs.clear();
                skillconfigs.clear();
                checkMobs();
                checkPoints();
                checkSkills();
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
        npc.setMobFeature(mobFeature);
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

    private List<String> getStringList(Config config, String key) {
        List<String> values = config.getList(key);
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    public Item getItemByString(String s){
        Item item = Item.get(Integer.parseInt(s.split(":")[0]));
        item.setDamage(Integer.parseInt(s.split(":")[1]));
        return item;
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
            Path geometryPath = skinFolder.toPath().resolve("geometry.json");
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
            skins.put(skinFolder.getName(), skin);
        }
    }

    public Skin newSkinOld(Path path) throws IOException {
        Skin skin = new Skin();
        Path skinfolder = getDataFolder().toPath().resolve("Skins");
        Path skinthings = skinfolder.resolve(path);
        Path skinpath = skinthings.resolve("skin.png");
        Path geometrypath = skinthings.resolve("geometry.json");
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
                System.out.println("皮肤模型出错");
            }
            skin.setGeometryName("geometry.mrpgnpc");
            skin.setSkinId(skinthings.toFile().getName());
        }
        return skin;
    }


    public Skin newSkinNew(Path path) throws IOException {
        Skin skin = new Skin();
        skin.generateSkinId("mrpgnpc");
        skin.setGeometryName("geometry.mrpgnpc");
        skin.setSkinResourcePatch("{\"geometry\":{\"default\":\"geometry.mrpgnpc\"}}");
        skin.setGeometryData(new String(Files.readAllBytes(Paths.get(path.toString() + "/geometry.json"))));
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
