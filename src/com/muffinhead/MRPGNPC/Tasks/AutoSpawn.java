package com.muffinhead.MRPGNPC.Tasks;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;
import com.muffinhead.MRPGNPC.MRPGNPC;
import com.muffinhead.MRPGNPC.NPCs.MobNPC;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class AutoSpawn extends Task {
    protected ConcurrentHashMap<String,Integer> spawnTick = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String,Integer> mobAmount = new ConcurrentHashMap<>();
    @Override
    public void onRun(int currentTick) {
        spawnTick.replaceAll((key, value) -> value + 1);

        for (Config config : MRPGNPC.pointconfigs.values()) {
            try {
                String[] position = config.getString("PointPosition", "").split(":");
                if (position.length < 4) continue;

                String levelName = position[3];
                if (!MRPGNPC.mrpgnpc.getServer().isLevelLoaded(levelName)
                        && !MRPGNPC.mrpgnpc.getServer().loadLevel(levelName)) {
                    continue;
                }

                Level level = MRPGNPC.mrpgnpc.getServer().getLevelByName(levelName);
                if (level == null) continue;

                Location location = new Location(
                        Double.parseDouble(position[0]) - 0.1,
                        Double.parseDouble(position[1]),
                        Double.parseDouble(position[2]) - 0.1,
                        position.length > 4 ? Double.parseDouble(position[4]) : 0,
                        position.length > 5 ? Double.parseDouble(position[5]) : 0,
                        level
                );

                if (!level.loadChunk(location.getChunkX(), location.getChunkZ(), true)
                        || location.getChunk() == null) {
                    continue;
                }

                List<String> spawnList = config.getList("SpawnList");
                if (spawnList == null || spawnList.isEmpty()) continue;
                String pointName = config.getString("PointName", "unknown");

                for (String spawnEntry : spawnList) {
                    if (spawnEntry == null) continue;
                    String[] parts = spawnEntry.split(":");
                    if (parts.length < 4) {
                        MRPGNPC.mrpgnpc.getServer().getLogger().warning(
                                "Invalid SpawnList entry in point " + pointName + ": " + spawnEntry);
                        continue;
                    }

                    String mobFile = parts[0];
                    int respawnTicks = Integer.parseInt(parts[1]);
                    int amountOneTime = Integer.parseInt(parts[2]);
                    int maxAmount = Integer.parseInt(parts[3]);
                    if (respawnTicks < 0 || amountOneTime <= 0 || maxAmount <= 0) continue;

                    String mobFeature = spawnEntry + ":" + pointName;
                    boolean canSpawn = spawnTick.getOrDefault(mobFeature, 0) >= respawnTicks;

                    if (parts.length >= 5) {
                        for (String limit : parts[4].split("-")) {
                            canSpawn &= onCheckSpawnLimit(limit, location);
                        }
                    }

                    int currentAmount = 0;
                    for (Entity entity : level.getEntities()) {
                        if (entity instanceof MobNPC
                                && mobFeature.equals(((MobNPC) entity).getMobFeature())) {
                            currentAmount++;
                        }
                    }

                    if (currentAmount >= maxAmount) canSpawn = false;
                    if (!canSpawn) continue;

                    int spawnAmount = Math.min(amountOneTime, maxAmount - currentAmount);
                    boolean spawnedAtLeastOne = false;
                    for (int count = 0; count < spawnAmount; count++) {
                        MobNPC npc = MRPGNPC.mrpgnpc.spawnNPC(
                                MRPGNPC.mrpgnpc.getServer().getConsoleSender(),
                                mobFile, location, mobFeature
                        );
                        if (npc != null) {
                            npc.spawnToAll();
                            spawnedAtLeastOne = true;
                        }
                    }
                    spawnTick.put(mobFeature, 0);
                }
            } catch (RuntimeException exception) {
                MRPGNPC.mrpgnpc.getServer().getLogger().warning(
                        "Skipping invalid MRPGNPC spawn point: " + exception.getMessage());
            }
        }
    }

    public boolean onCheckSpawnLimit(String limit,Location location){
        boolean canSpawn = true;
        String condition = limit;
        String function = "";
        if (limit.contains("~")) {
            condition = limit.split("~")[0];
            function = limit.split("~")[1];
        }
        switch (condition) {
            case "atDay": {
                if (location.getLevel().getTime()>=13800&&location.getLevel().getTime()<24000) {
                    canSpawn = false;
                }
                break;
            }
            case "atNight": {
                if (location.getLevel().getTime()>=0&&location.getLevel().getTime()<13800) {
                    canSpawn = false;
                }
                break;
            }
            case "playersNearby": {
                boolean nearby = false;
                for (Player player : location.getLevel().getPlayers().values()) {
                    if (player.distance(location) <= Double.parseDouble(function)) {
                        nearby = true;
                    }
                }
                canSpawn = nearby;
                break;
            }
            case "noOneNearby": {
                boolean nearby = false;
                for (Player player : location.getLevel().getPlayers().values()) {
                    if (player.distance(location) <= Double.parseDouble(function)) {
                        nearby = true;
                    }
                }
                canSpawn = !nearby;
                break;
            }
            default:
        }
        return canSpawn;
    }
}
