package com.example.territory.command;

import com.example.territory.TerritorySavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.example.territory.TerritoryConquest;

import java.util.Map;
import java.util.UUID;

/**
 * Registers the /territory command.
 * Usage:
 *   /territory         - Show the current game state for all players
 *   /territory me      - Show your own territory info
 *   /territory chunk   - Show who owns the current chunk
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class TerritoryCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("territory")
                .executes(TerritoryCommand::executeStatus)
                .then(Commands.literal("me")
                    .executes(TerritoryCommand::executeMe))
                .then(Commands.literal("chunk")
                    .executes(TerritoryCommand::executeChunk))
        );
    }

    /** /territory - show all players' status */
    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel overworld = src.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        TerritorySavedData data = TerritorySavedData.get(overworld);
        Map<UUID, TerritorySavedData.PlayerState> players = data.getPlayers();

        src.sendSuccess(new TextComponent("§6━━━━━ Territory Conquest 状態 ━━━━━"), false);
        src.sendSuccess(new TextComponent("§e登録プレイヤー: §f" + players.size() + " / 6"), false);

        for (TerritorySavedData.PlayerState state : players.values()) {
            String colorCode = teamColorToCode(state.teamColor);
            long ownedChunks = data.getClaimedChunks().values().stream()
                    .filter(uid -> uid.equals(state.uuid)).count();
            String vassalInfo = state.isVassal ? " §7[属国]" : " §a[独立]";
            src.sendSuccess(new TextComponent(
                colorCode + "■ " + state.username +
                " §7| チャンク: §f" + ownedChunks +
                " §7| 聖域: §f" + state.spawnX + "," + state.spawnZ +
                vassalInfo
            ), false);
        }

        src.sendSuccess(new TextComponent("§6━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        return 1;
    }

    /** /territory me - show caller's own info */
    private static int executeMe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            UUID uuid = src.getPlayerOrException().getUUID();
            ServerLevel overworld = src.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) return 0;

            TerritorySavedData data = TerritorySavedData.get(overworld);
            TerritorySavedData.PlayerState state = data.getPlayers().get(uuid);

            if (state == null) {
                src.sendFailure(new TextComponent("§cあなたはまだ登録されていません。"));
                return 0;
            }

            long ownedChunks = data.getClaimedChunks().values().stream()
                    .filter(uid -> uid.equals(uuid)).count();
            String colorCode = teamColorToCode(state.teamColor);

            src.sendSuccess(new TextComponent("§6━━━━━ あなたの情報 ━━━━━"), false);
            src.sendSuccess(new TextComponent("§eチームカラー: " + colorCode + state.teamColor), false);
            src.sendSuccess(new TextComponent("§e聖域座標: §f" + state.spawnX + ", " + state.spawnY + ", " + state.spawnZ), false);
            src.sendSuccess(new TextComponent("§e所有チャンク数: §f" + ownedChunks), false);
            src.sendSuccess(new TextComponent("§e状態: " + (state.isVassal ? "§c属国" : "§a独立")), false);
            if (state.isVassal && state.overlordUuid != null) {
                TerritorySavedData.PlayerState overlord = data.getPlayers().get(state.overlordUuid);
                String overlordName = overlord != null ? overlord.username : "不明";
                src.sendSuccess(new TextComponent("§e宗主: §f" + overlordName), false);
            }
            src.sendSuccess(new TextComponent("§6━━━━━━━━━━━━━━━━━━"), false);
        } catch (Exception e) {
            src.sendFailure(new TextComponent("§cプレイヤーとして実行してください。"));
            return 0;
        }
        return 1;
    }

    /** /territory chunk - show who owns the current chunk */
    private static int executeChunk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            BlockPos pos = new BlockPos(src.getPlayerOrException().blockPosition());
            ChunkPos chunk = new ChunkPos(pos);
            ServerLevel overworld = src.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) return 0;

            TerritorySavedData data = TerritorySavedData.get(overworld);
            UUID ownerUuid = data.getClaimedChunks().get(chunk.toLong());

            if (ownerUuid == null) {
                src.sendSuccess(new TextComponent("§7このチャンク §f(" + chunk.x + ", " + chunk.z + ")§7 は未開拓です。"), false);
            } else {
                TerritorySavedData.PlayerState owner = data.getPlayers().get(ownerUuid);
                String name = owner != null ? owner.username : "不明";
                String colorCode = owner != null ? teamColorToCode(owner.teamColor) : "§f";
                src.sendSuccess(new TextComponent(
                    "§eチャンク §f(" + chunk.x + ", " + chunk.z + ")§e の所有者: " + colorCode + name
                ), false);
            }
        } catch (Exception e) {
            src.sendFailure(new TextComponent("§cプレイヤーとして実行してください。"));
            return 0;
        }
        return 1;
    }

    private static String teamColorToCode(String teamColor) {
        return switch (teamColor) {
            case "RED"    -> "§c";
            case "BLUE"   -> "§9";
            case "GREEN"  -> "§a";
            case "YELLOW" -> "§e";
            case "PURPLE" -> "§5";
            case "ORANGE" -> "§6";
            default       -> "§f";
        };
    }
}
