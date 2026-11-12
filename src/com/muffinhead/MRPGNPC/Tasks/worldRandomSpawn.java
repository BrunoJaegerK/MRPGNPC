package com.muffinhead.MRPGNPC.Tasks;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;
import com.muffinhead.MRPGNPC.MRPGNPC;
import com.muffinhead.MRPGNPC.NPCs.MobNPC;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class worldRandomSpawn extends Task {
    protected ConcurrentHashMap<String, Integer> spawnTick = new ConcurrentHashMap<>();
    @Override
    public void onRun(int currentTick) {
        if (MRPGNPC.worldSpawnConfig == null || MRPGNPC.mrpgnpc == null) return;

        for (String levelName : MRPGNPC.worldSpawnConfig.getAll().keySet()) {
            Level level = Server.getInstance().getLevelByName(levelName);
            if (level == null) continue;

            List<String> spawnList = MRPGNPC.worldSpawnConfig.getList(levelName);
            if (spawnList == null) continue;

            for (String spawnEntry : spawnList) {
                if (spawnEntry == null) continue;
                String[] parts = spawnEntry.split(":");
                if (parts.length < 7) {
                    Server.getInstance().getLogger().warning(
                            "Invalid worldRandomSpawn entry in " + levelName + ": " + spawnEntry);
                    continue;
                }

                try {
                    String mobFile = parts[0];
                    int maxAmount = Integer.parseInt(parts[1]);
                    int maxAmountDistance = Integer.parseInt(parts[2]);
                    int spawnDistance = Integer.parseInt(parts[3]);
                    double disappearDistance = Double.parseDouble(parts[4]);
                    int cooldown = Integer.parseInt(parts[5]);
                    int probability = Math.max(0, Math.min(10000, Integer.parseInt(parts[6])));
                    if (maxAmount <= 0 || maxAmountDistance < 0 || spawnDistance <= 0
                            || disappearDistance < 0 || cooldown < 0) continue;

                    for (Player player : level.getPlayers().values()) {
                        if (!player.isAlive() || !player.isOnGround()) continue;

                        String key = levelName + ":" + mobFile + ":" + player.getName();
                        Location location = getSafeLoc(level, spawnDistance, player);
                        if (location == null || spawnTick.getOrDefault(key, 0) > 0) continue;

                        int amount = 0;
                        for (Entity entity : level.getEntities()) {
                            if (!(entity instanceof MobNPC)) continue;
                            String feature = ((MobNPC) entity).getMobFeature();
                            if (feature != null && feature.startsWith("Random:" + mobFile + ":")
                                    && entity.distance(player) <= maxAmountDistance) {
                                amount++;
                            }
                        }

                        if (amount >= maxAmount) continue;
                        if (ThreadLocalRandom.current().nextInt(10000) >= probability) {
                            spawnTick.put(key, cooldown);
                            continue;
                        }

                        String mobFeature = "Random:" + mobFile + ":" + disappearDistance;
                        MobNPC npc = MRPGNPC.mrpgnpc.spawnNPC(
                                Server.getInstance().getConsoleSender(), mobFile, location, mobFeature);
                        if (npc != null) npc.spawnToAll();
                        spawnTick.put(key, cooldown);
                    }
                } catch (RuntimeException exception) {
                    Server.getInstance().getLogger().warning(
                            "Skipping invalid worldRandomSpawn entry: " + spawnEntry
                                    + " (" + exception.getMessage() + ")");
                }
            }
        }

        for (String key : spawnTick.keySet()) {
            int remaining = spawnTick.getOrDefault(key, 0);
            if (remaining > 1) {
                spawnTick.put(key, remaining - 1);
            } else {
                spawnTick.remove(key);
            }
        }
    }

    public Location getSafeLoc(Level level, int distance, Player player) {
        if (level == null || player == null || distance <= 0) return null;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = player.getFloorX() + random.nextInt(-distance, distance + 1);
            int z = player.getFloorZ() + random.nextInt(-distance, distance + 1);
            if (Math.hypot(x - player.getX(), z - player.getZ()) > distance) continue;

            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!level.loadChunk(chunkX, chunkZ, true) || !level.isChunkLoaded(chunkX, chunkZ)) continue;

            int y = level.getHighestBlockAt(x, z) + 1;
            if (y <= level.getMinBlockY() || y + 1 >= level.getMaxBlockY()) continue;

            Block ground = level.getBlock(new Vector3(x, y - 1, z));
            Block feet = level.getBlock(new Vector3(x, y, z));
            Block head = level.getBlock(new Vector3(x, y + 1, z));
            if (ground.canPassThrough() || !feet.canPassThrough() || !head.canPassThrough()) continue;

            return new Location(x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch(), level);
        }
        return null;
    }

}
