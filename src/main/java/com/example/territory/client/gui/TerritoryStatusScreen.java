package com.example.territory.client.gui;

import com.example.territory.network.PacketSyncTerritoryInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public class TerritoryStatusScreen extends Screen {
    private final PacketSyncTerritoryInfo data;
    private int left, top, widthSize, heightSize;

    public TerritoryStatusScreen(PacketSyncTerritoryInfo data) {
        super(new TextComponent("領土・国庫ステータス"));
        this.data = data;
        this.widthSize = 280;
        this.heightSize = 220;
    }

    @Override
    protected void init() {
        this.left = (this.width - this.widthSize) / 2;
        this.top = (this.height - this.heightSize) / 2;

        // Add a beautiful modern Close button
        this.addRenderableWidget(new Button(
                this.left + (this.widthSize - 100) / 2,
                this.top + this.heightSize - 30,
                100, 20,
                new TextComponent("閉じる"),
                button -> this.onClose()
        ));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        // Render semi-transparent background overlay
        this.renderBackground(poseStack);

        // Render premium dark-glassmorphism background card
        // Border frame
        fill(poseStack, this.left - 2, this.top - 2, this.left + this.widthSize + 2, this.top + this.heightSize + 2, 0xFF444444);
        // Core card (dark grey glassmorphic)
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + this.heightSize, 0xDD111111);

        // Header highlight using team color
        int teamColorHex = getTeamColorHex(data.teamColor);
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + 25, teamColorHex | 0xFF000000);

        // Header Title
        drawCenteredString(poseStack, this.font, "§l" + this.title.getString(), this.left + this.widthSize / 2, this.top + 8, 0xFFFFFF);

        // Main Stats Layout
        int currentY = this.top + 35;
        
        // 1. Player Info & Team Color
        drawString(poseStack, this.font, "§eプレイヤー名: §f" + Minecraft.getInstance().player.getName().getString(), this.left + 15, currentY, 0xFFFFFF);
        drawString(poseStack, this.font, "§eチームカラー: " + getTeamColorCode(data.teamColor) + data.teamColor, this.left + 15, currentY + 15, 0xFFFFFF);
        
        // 2. Vassal / Independence status
        String statusText = data.isVassal ? "§c属国 (宗主国: " + data.overlordName + ")" : "§a独立国";
        drawString(poseStack, this.font, "§e外交状態: " + statusText, this.left + 15, currentY + 30, 0xFFFFFF);

        // 3. Treasury (Gold Balance) with gorgeous styling
        drawString(poseStack, this.font, "§e国庫所持金: §6§l" + data.treasury + " §eGold  §d⛃", this.left + 15, currentY + 45, 0xFFFFFF);

        // Draw a separator line
        fill(poseStack, this.left + 10, currentY + 62, this.left + this.widthSize - 10, currentY + 63, 0x44FFFFFF);

        // 4. Claimed Chunks
        int chunkY = currentY + 70;
        drawString(poseStack, this.font, "§6§l■ 支配している領土: §f" + data.claimedCount + " チャンク", this.left + 15, chunkY, 0xFFFFFF);
        
        // Render coordinates list neatly
        int coordY = chunkY + 15;
        if (data.chunkList.isEmpty()) {
            drawString(poseStack, this.font, "§7支配している土地がありません", this.left + 25, coordY, 0xFFFFFF);
        } else {
            // Draw up to 3 coordinates and "+X more" if too many
            int maxShow = Math.min(3, data.chunkList.size());
            StringBuilder sb = new StringBuilder("§d");
            for (int i = 0; i < maxShow; i++) {
                sb.append(data.chunkList.get(i)).append("  ");
            }
            if (data.chunkList.size() > 3) {
                sb.append(" 他 ").append(data.chunkList.size() - 3).append(" 箇所");
            }
            drawString(poseStack, this.font, sb.toString(), this.left + 25, coordY, 0xFFFFFF);
        }

        // Draw a separator line
        fill(poseStack, this.left + 10, chunkY + 37, this.left + this.widthSize - 10, chunkY + 38, 0x44FFFFFF);

        // 5. Vassals List
        int vassalY = chunkY + 45;
        drawString(poseStack, this.font, "§b§l■ 支配下の属国: §f" + data.vassalCount + " 人", this.left + 15, vassalY, 0xFFFFFF);
        
        int listY = vassalY + 15;
        if (data.vassals.isEmpty()) {
            drawString(poseStack, this.font, "§7支配しているプレイヤーはいません", this.left + 25, listY, 0xFFFFFF);
        } else {
            StringBuilder sb = new StringBuilder("§a");
            for (int i = 0; i < data.vassals.size(); i++) {
                sb.append(data.vassals.get(i));
                if (i < data.vassals.size() - 1) sb.append(", ");
            }
            drawString(poseStack, this.font, sb.toString(), this.left + 25, listY, 0xFFFFFF);
        }

        // Render hover effects / animations for buttons natively
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getTeamColorHex(String teamColor) {
        return switch (teamColor) {
            case "RED"    -> 0xDD5555;
            case "BLUE"   -> 0x5555FF;
            case "GREEN"  -> 0x55FF55;
            case "YELLOW" -> 0xFFFF55;
            case "PURPLE" -> 0xAA00AA;
            case "ORANGE" -> 0xFFAA00;
            default       -> 0xCCCCCC;
        };
    }

    private String getTeamColorCode(String teamColor) {
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
