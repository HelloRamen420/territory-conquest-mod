package com.example.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class TerritorySavedData extends SavedData {
    private static final String DATA_NAME = TerritoryConquest.MOD_ID + "_data";

    public static class PlayerState {
        public UUID uuid;
        public String username;
        public String teamColor; // e.g., "RED", "BLUE", etc.
        public int spawnX;
        public int spawnY;
        public int spawnZ;
        public boolean isVassal;
        public UUID overlordUuid;
        public int treasury = 1000; // 1000G initially for testing
        public int debuffLevel = 0;
        public List<BlockPos> subCores = new ArrayList<>();
        public List<BlockPos> flags = new ArrayList<>();
        public int totalIronSold = 0;
        public int totalGoldSold = 0;
        public int totalEmeraldSold = 0;

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            tag.putString("username", username);
            tag.putString("teamColor", teamColor);
            tag.putInt("spawnX", spawnX);
            tag.putInt("spawnY", spawnY);
            tag.putInt("spawnZ", spawnZ);
            tag.putBoolean("isVassal", isVassal);
            tag.putInt("treasury", treasury);
            tag.putInt("debuffLevel", debuffLevel);
            tag.putInt("totalIronSold", totalIronSold);
            tag.putInt("totalGoldSold", totalGoldSold);
            tag.putInt("totalEmeraldSold", totalEmeraldSold);
            if (overlordUuid != null) {
                tag.putUUID("overlordUuid", overlordUuid);
            }
            
            // Serialize SubCores List
            ListTag subCoresTag = new ListTag();
            for (BlockPos pos : subCores) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                subCoresTag.add(posTag);
            }
            tag.put("subCores", subCoresTag);
            
            // Serialize Flags List
            ListTag flagsTag = new ListTag();
            for (BlockPos pos : flags) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                flagsTag.add(posTag);
            }
            tag.put("flags", flagsTag);
            
            return tag;
        }

        public static PlayerState fromNBT(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.uuid = tag.getUUID("uuid");
            state.username = tag.getString("username");
            state.teamColor = tag.getString("teamColor");
            state.spawnX = tag.getInt("spawnX");
            state.spawnY = tag.getInt("spawnY");
            state.spawnZ = tag.getInt("spawnZ");
            state.isVassal = tag.getBoolean("isVassal");
            state.treasury = tag.contains("treasury") ? tag.getInt("treasury") : 1000;
            state.debuffLevel = tag.getInt("debuffLevel");
            state.totalIronSold = tag.getInt("totalIronSold");
            state.totalGoldSold = tag.getInt("totalGoldSold");
            state.totalEmeraldSold = tag.getInt("totalEmeraldSold");
            if (tag.hasUUID("overlordUuid")) {
                state.overlordUuid = tag.getUUID("overlordUuid");
            }
            
            // Deserialize SubCores List
            if (tag.contains("subCores", Tag.TAG_LIST)) {
                ListTag subCoresList = tag.getList("subCores", Tag.TAG_COMPOUND);
                state.subCores = new ArrayList<>();
                for (int i = 0; i < subCoresList.size(); i++) {
                    CompoundTag posTag = subCoresList.getCompound(i);
                    state.subCores.add(new BlockPos(
                            posTag.getInt("x"),
                            posTag.getInt("y"),
                            posTag.getInt("z")
                    ));
                }
            }
            
            // Deserialize Flags List
            if (tag.contains("flags", Tag.TAG_LIST)) {
                ListTag flagsList = tag.getList("flags", Tag.TAG_COMPOUND);
                state.flags = new ArrayList<>();
                for (int i = 0; i < flagsList.size(); i++) {
                    CompoundTag posTag = flagsList.getCompound(i);
                    state.flags.add(new BlockPos(
                            posTag.getInt("x"),
                            posTag.getInt("y"),
                            posTag.getInt("z")
                    ));
                }
            }
            return state;
        }
    }


    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final Map<Long, UUID> claimedChunks = new HashMap<>(); // chunkPos long -> playerUuid
    // Stores core block positions -> ownerUuid (key = BlockPos.asLong())
    private final Map<Long, UUID> corePositions = new HashMap<>();
    // 維持費を最後に処理したマインクラフトの日数（サーバー再起動をまたいで永続化）
    private long lastProcessedDay = -1L;
    // ゲームが開始されているかどうか（サーバー再起動をまたいで永続化）
    private boolean gameStarted = false;

    public TerritorySavedData() {}

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean started) {
        this.gameStarted = started;
        setDirty();
    }

    public Map<UUID, PlayerState> getPlayers() {
        return players;
    }

    public Map<Long, UUID> getClaimedChunks() {
        return claimedChunks;
    }

    public Map<Long, UUID> getCorePositions() {
        return corePositions;
    }

    public long getLastProcessedDay() {
        return lastProcessedDay;
    }

    public void setLastProcessedDay(long day) {
        this.lastProcessedDay = day;
        setDirty();
    }

    /** Add a core block position for a player. */
    public void addCore(BlockPos pos, UUID ownerUuid) {
        corePositions.put(pos.asLong(), ownerUuid);
        setDirty();
    }

    /** Remove a core block (when broken). */
    public void removeCore(BlockPos pos) {
        corePositions.remove(pos.asLong());
        setDirty();
    }

    /** Return owner UUID for the core at the given position, or null. */
    public UUID getCoreOwner(BlockPos pos) {
        return corePositions.get(pos.asLong());
    }

    public static TerritorySavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            overworld = level;
        }
        return overworld.getDataStorage().computeIfAbsent(
                TerritorySavedData::load,
                TerritorySavedData::new,
                DATA_NAME
        );
    }

    public static TerritorySavedData load(CompoundTag tag) {
        TerritorySavedData data = new TerritorySavedData();
        data.lastProcessedDay = tag.contains("lastProcessedDay") ? tag.getLong("lastProcessedDay") : -1L;
        data.gameStarted = tag.contains("gameStarted") && tag.getBoolean("gameStarted");
        
        // Load players
        if (tag.contains("players", Tag.TAG_LIST)) {
            ListTag playersList = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < playersList.size(); i++) {
                CompoundTag pTag = playersList.getCompound(i);
                PlayerState pState = PlayerState.fromNBT(pTag);
                data.players.put(pState.uuid, pState);
            }
        }

        // Load claimed chunks
        if (tag.contains("claimedChunks", Tag.TAG_COMPOUND)) {
            CompoundTag chunksTag = tag.getCompound("claimedChunks");
            for (String key : chunksTag.getAllKeys()) {
                try {
                    long chunkPos = Long.parseLong(key);
                    UUID playerUuid = chunksTag.getUUID(key);
                    data.claimedChunks.put(chunkPos, playerUuid);
                } catch (NumberFormatException e) {
                    // Ignore malformed keys
                }
            }
        }

        // Load core positions
        if (tag.contains("corePositions", Tag.TAG_COMPOUND)) {
            CompoundTag coresTag = tag.getCompound("corePositions");
            for (String key : coresTag.getAllKeys()) {
                try {
                    long blockPos = Long.parseLong(key);
                    UUID playerUuid = coresTag.getUUID(key);
                    data.corePositions.put(blockPos, playerUuid);
                } catch (NumberFormatException e) {
                    // Ignore malformed keys
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        // Save players
        ListTag playersList = new ListTag();
        for (PlayerState pState : players.values()) {
            playersList.add(pState.toNBT());
        }
        tag.put("players", playersList);

        // Save claimed chunks
        CompoundTag chunksTag = new CompoundTag();
        for (Map.Entry<Long, UUID> entry : claimedChunks.entrySet()) {
            chunksTag.putUUID(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("claimedChunks", chunksTag);

        // Save lastProcessedDay
        tag.putLong("lastProcessedDay", lastProcessedDay);

        // Save gameStarted
        tag.putBoolean("gameStarted", gameStarted);

        // Save core positions
        CompoundTag coresTag = new CompoundTag();
        for (Map.Entry<Long, UUID> entry : corePositions.entrySet()) {
            coresTag.putUUID(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("corePositions", coresTag);

        return tag;
    }
}
