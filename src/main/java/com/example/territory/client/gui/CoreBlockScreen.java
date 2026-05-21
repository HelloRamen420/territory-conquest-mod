package com.example.territory.client.gui;

import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketRequestCoreUpgrade;
import com.example.territory.network.PacketRequestDepositGold;

import com.example.territory.network.PacketSyncCoreInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public class CoreBlockScreen extends Screen {
    private final PacketSyncCoreInfo data;
    private int left, top, widthSize, heightSize;
    private Button upgradeButton;
    private Button depositButton;
    private Button purchaseFlagButton;

    public CoreBlockScreen(PacketSyncCoreInfo data) {
        super(new TextComponent("メインコア防衛設定"));
        this.data = data;
        this.widthSize = 260;
        this.heightSize = 215; // Increased height to fit 3 buttons
    }

    @Override
    protected void init() {
        this.left = (this.width - this.widthSize) / 2;
        this.top = (this.height - this.heightSize) / 2;

        boolean isOwner = Minecraft.getInstance().player.getName().getString().equals(data.ownerName);
        boolean canAffordUpgrade = data.treasury >= 100;
        boolean canAffordFlag = data.treasury >= 200;

        depositButton = new Button(
                this.left + (this.widthSize - 180) / 2,
                this.top + 100,
                180, 20,
                new TextComponent("総合取引所 (アイテム売買)"),
                button -> {
                    Minecraft.getInstance().setScreen(new ExchangeScreen(
                            data.corePos, data.treasury,
                            data.totalIronSold, data.totalGoldSold, data.totalEmeraldSold,
                            this
                    ));
                }
        );
        depositButton.active = isOwner;
        this.addRenderableWidget(depositButton);

        // Flag purchase button has been moved to ExchangeScreen

        upgradeButton = new Button(
                this.left + (this.widthSize - 180) / 2,
                this.top + 150,
                180, 20,
                new TextComponent("防衛デバフ強化 (100G)"),
                button -> {
                    ModMessages.sendToServer(new PacketRequestCoreUpgrade(data.corePos));
                }
        );
        upgradeButton.active = isOwner && canAffordUpgrade;
        this.addRenderableWidget(upgradeButton);

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
        // Border frame (dark-grey outline)
        fill(poseStack, this.left - 2, this.top - 2, this.left + this.widthSize + 2, this.top + this.heightSize + 2, 0xFF444444);
        // Core card (dark grey glassmorphic)
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + this.heightSize, 0xDD111111);

        // Header highlight using team color
        int teamColorHex = getTeamColorHex(data.teamColor);
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + 25, teamColorHex | 0xFF000000);

        // Header Title
        drawCenteredString(poseStack, this.font, "§l" + this.title.getString(), this.left + this.widthSize / 2, this.top + 8, 0xFFFFFF);

        // Main Stats Layout
        int currentY = this.top + 38;
        String teamColorCode = getTeamColorCode(data.teamColor);

        // 1. Owner & Team Info
        drawString(poseStack, this.font, "§eコア所有者: " + teamColorCode + data.ownerName, this.left + 15, currentY, 0xFFFFFF);
        
        // 2. Debuff Level
        drawString(poseStack, this.font, "§e防衛デバフレベル: §a§l" + data.debuffLevel, this.left + 15, currentY + 18, 0xFFFFFF);

        // 3. Treasury (Gold Balance)
        drawString(poseStack, this.font, "§e国庫所持金: §6§l" + data.treasury + " §eGold  §d⛃", this.left + 15, currentY + 36, 0xFFFFFF);

        // Draw a separator line
        fill(poseStack, this.left + 10, currentY + 45, this.left + this.widthSize - 10, currentY + 46, 0x44FFFFFF);

        int helpY = currentY + 50;
        boolean isOwner = Minecraft.getInstance().player.getName().getString().equals(data.ownerName);

        if (!isOwner) {
            drawCenteredString(poseStack, this.font, "§c※自分のメインコアのみ操作可能です", this.left + this.widthSize / 2, helpY, 0xFFFFFF);
        } else {
            drawCenteredString(poseStack, this.font, "§7ゴールドを貯めて、強化やアイテム購入を行えます。", this.left + this.widthSize / 2, helpY, 0xCCCCCC);
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
