package com.example.territory.client.gui;

import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketRequestDepositGold;

import com.example.territory.network.PacketSyncSubCoreInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public class SubCoreScreen extends Screen {
    private final PacketSyncSubCoreInfo data;
    private int left, top, widthSize, heightSize;
    private Button depositButton;
    private Button purchaseFlagButton;

    public SubCoreScreen(PacketSyncSubCoreInfo data) {
        super(new TextComponent("サブコア取引・領土開拓設定"));
        this.data = data;
        this.widthSize = 260;
        this.heightSize = 190;
    }

    @Override
    protected void init() {
        this.left = (this.width - this.widthSize) / 2;
        this.top = (this.height - this.heightSize) / 2;

        boolean isOwner = Minecraft.getInstance().player.getName().getString().equals(data.ownerName);
        boolean canAffordFlag = data.treasury >= 200;

        // Button to deposit gold items (Ingot, Nugget, Block)
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

        // Close button
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
        this.renderBackground(poseStack);

        // Render premium dark-glassmorphism background card
        fill(poseStack, this.left - 2, this.top - 2, this.left + this.widthSize + 2, this.top + this.heightSize + 2, 0xFF444444);
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + this.heightSize, 0xDD111111);

        // Header highlight using team color
        int teamColorHex = getTeamColorHex(data.teamColor);
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + 25, teamColorHex | 0xFF000000);

        // Header Title
        drawCenteredString(poseStack, this.font, "§l" + this.title.getString(), this.left + this.widthSize / 2, this.top + 8, 0xFFFFFF);

        // Stats Display
        int currentY = this.top + 38;
        String teamColorCode = getTeamColorCode(data.teamColor);

        // 1. Owner Info
        drawString(poseStack, this.font, "§eサブコア所有者: " + teamColorCode + data.ownerName, this.left + 15, currentY, 0xFFFFFF);

        // 2. Treasury Gold Balance
        drawString(poseStack, this.font, "§e国庫所持金: §6§l" + data.treasury + " §eGold  §d⛃", this.left + 15, currentY + 18, 0xFFFFFF);

        // Draw a separator line
        fill(poseStack, this.left + 10, currentY + 45, this.left + this.widthSize - 10, currentY + 46, 0x44FFFFFF);

        // Info / Warning message
        int helpY = currentY + 50;
        boolean isOwner = Minecraft.getInstance().player.getName().getString().equals(data.ownerName);
        boolean canAffordFlag = data.treasury >= 50;

        if (!isOwner) {
            drawCenteredString(poseStack, this.font, "§c※自分のサブコアでのみ取引可能です", this.left + this.widthSize / 2, helpY, 0xFFFFFF);
        } else {
            drawCenteredString(poseStack, this.font, "§7金（金塊/インゴット/ブロック）を預けて", this.left + this.widthSize / 2, helpY, 0xCCCCCC);
            drawCenteredString(poseStack, this.font, "§7ゴールドを貯め、領有旗を購入しましょう。", this.left + this.widthSize / 2, helpY + 10, 0xCCCCCC);
        }

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
