package com.example.territory.client.gui;

import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketRequestCoreUpgrade;
import com.example.territory.network.PacketSyncCoreInfo;
import com.example.territory.network.PacketRequestAuctionAction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class CoreBlockScreen extends Screen {
    private final PacketSyncCoreInfo data;
    private int left, top, widthSize, heightSize;
    private int activeTab = 0; // 0: State & Revolution, 1: Debuff Evolution, 2: Nation Upgrades, 3: Foreign Trade

    // Buttons
    private Button tab0Button;
    private Button tab1Button;
    private Button tab2Button;
    private Button tab3Button;

    // Trade Tab Fields
    private EditBox priceField;
    private int currentPage = 0;

    public CoreBlockScreen(PacketSyncCoreInfo data) {
        super(new TextComponent(data.isMainCore ? "メインコア防衛・発展設定" : "サブコア取引・領土開拓設定"));
        this.data = data;
        this.widthSize = 340;
        this.heightSize = 230;
    }

    @Override
    protected void init() {
        this.left = (this.width - this.widthSize) / 2;
        this.top = (this.height - this.heightSize) / 2;

        boolean isOwner = Minecraft.getInstance().player.getName().getString().equals(data.ownerName);

        // Tab Buttons (Evenly spaced width=76, interval=81)
        if (data.isMainCore) {
            tab0Button = new Button(this.left + 10, this.top + 30, 76, 20, new TextComponent("国家・革命"), button -> {
                activeTab = 0;
                rebuildWidgets();
            });
            tab1Button = new Button(this.left + 91, this.top + 30, 76, 20, new TextComponent("デバフ進化"), button -> {
                activeTab = 1;
                rebuildWidgets();
            });
            tab2Button = new Button(this.left + 172, this.top + 30, 76, 20, new TextComponent("国家発展"), button -> {
                activeTab = 2;
                rebuildWidgets();
            });
            tab3Button = new Button(this.left + 253, this.top + 30, 76, 20, new TextComponent("外交交易"), button -> {
                activeTab = 3;
                rebuildWidgets();
            });

            tab0Button.active = activeTab != 0;
            tab1Button.active = activeTab != 1;
            tab2Button.active = activeTab != 2;
            tab3Button.active = activeTab != 3;

            this.addRenderableWidget(tab0Button);
            this.addRenderableWidget(tab1Button);
            this.addRenderableWidget(tab2Button);
            this.addRenderableWidget(tab3Button);
        } else {
            tab0Button = new Button(this.left + 15, this.top + 30, 150, 20, new TextComponent("サブコア設定"), button -> {
                activeTab = 0;
                rebuildWidgets();
            });
            tab3Button = new Button(this.left + 175, this.top + 30, 150, 20, new TextComponent("外交交易"), button -> {
                activeTab = 3;
                rebuildWidgets();
            });
            
            tab0Button.active = activeTab != 0;
            tab3Button.active = activeTab != 3;
            
            this.addRenderableWidget(tab0Button);
            this.addRenderableWidget(tab3Button);
        }

        // Tab Contents widgets
        if (activeTab == 0) {
            // General & Revolution Tab
            Button exchangeBtn = new Button(
                    this.left + 80, this.top + 100, 180, 20,
                    new TextComponent("総合取引所 (売買)"),
                    button -> {
                        Minecraft.getInstance().setScreen(new ExchangeScreen(
                                data.corePos, data.treasury,
                                data.totalIronSold, data.totalGoldSold, data.totalEmeraldSold,
                                data.doubleTradeLicense, this
                        ));
                    }
            );
            exchangeBtn.active = isOwner;
            this.addRenderableWidget(exchangeBtn);

            if (data.isMainCore) {
                // Peace Independence Button (Vassals only)
                if (data.isVassal) {
                    Button independenceBtn = new Button(
                            this.left + 80, this.top + 150, 180, 20,
                            new TextComponent("平和的独立 (500,000G)"),
                            button -> {
                                ModMessages.sendToServer(new PacketRequestCoreUpgrade(
                                        data.corePos,
                                        PacketRequestCoreUpgrade.UpgradeType.INDEPENDENCE,
                                        BlockPos.ZERO
                                ));
                            }
                    );
                    independenceBtn.active = isOwner && data.treasury >= 500000;
                    this.addRenderableWidget(independenceBtn);
                }
            } else {
                // SubCore passive income button
                Button passiveIncomeBtn = new Button(
                        this.left + 80, this.top + 150, 180, 20,
                        new TextComponent(data.isPassiveIncome ? "不労所得化 済" : "不労所得化 (100,000G)"),
                        button -> {
                            ModMessages.sendToServer(new PacketRequestCoreUpgrade(
                                    data.corePos,
                                    PacketRequestCoreUpgrade.UpgradeType.PASSIVE_INCOME,
                                    data.corePos
                            ));
                        }
                );
                passiveIncomeBtn.active = isOwner && !data.isPassiveIncome && data.treasury >= 100000;
                this.addRenderableWidget(passiveIncomeBtn);
            }
        } else if (activeTab == 1) {
            // Debuff Upgrades Tab (Items shifted down by 12px to resolve overlapping)
            // 1. Slowness Button
            int slownessCost = getSlownessCost(data.slownessLevel);
            Button slownessBtn = new Button(this.left + 15, this.top + 92, 145, 20,
                    new TextComponent(slownessCost > 0 ? "鈍化 " + (data.slownessLevel) + "➔" + (data.slownessLevel + 1) + " (" + slownessCost + "G)" : "鈍化 MAX"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.SLOWNESS)
            );
            slownessBtn.active = isOwner && slownessCost > 0 && data.treasury >= slownessCost;
            this.addRenderableWidget(slownessBtn);

            // 2. Weakness Button
            int weaknessCost = getWeaknessCost(data.weaknessLevel);
            Button weaknessBtn = new Button(this.left + 180, this.top + 92, 145, 20,
                    new TextComponent(weaknessCost > 0 ? "弱体 " + (data.weaknessLevel) + "➔" + (data.weaknessLevel + 1) + " (" + weaknessCost + "G)" : "弱体 MAX"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.WEAKNESS)
            );
            weaknessBtn.active = isOwner && weaknessCost > 0 && data.treasury >= weaknessCost;
            this.addRenderableWidget(weaknessBtn);

            // 3. Mining Fatigue Button
            int miningCost = getMiningCost(data.miningFatigueLevel);
            Button miningBtn = new Button(this.left + 15, this.top + 137, 145, 20,
                    new TextComponent(miningCost > 0 ? "疲労 " + (data.miningFatigueLevel) + "➔" + (data.miningFatigueLevel + 1) + " (" + miningCost + "G)" : "疲労 MAX"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.MINING_FATIGUE)
            );
            miningBtn.active = isOwner && miningCost > 0 && data.treasury >= miningCost;
            this.addRenderableWidget(miningBtn);

            // 4. Hunger Button
            int hungerCost = getHungerCost(data.hungerLevel);
            Button hungerBtn = new Button(this.left + 180, this.top + 137, 145, 20,
                    new TextComponent(hungerCost > 0 ? "空腹 " + (data.hungerLevel) + "➔" + (data.hungerLevel + 1) + " (" + hungerCost + "G)" : "空腹 MAX"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.HUNGER)
            );
            hungerBtn.active = isOwner && hungerCost > 0 && data.treasury >= hungerCost;
            this.addRenderableWidget(hungerBtn);

            // 5. Poison Button
            int poisonCost = data.poisonLevel == 0 ? 50000 : 0;
            Button poisonBtn = new Button(this.left + 95, this.top + 182, 150, 20,
                    new TextComponent(poisonCost > 0 ? "毒（虫食い）解除 (" + poisonCost + "G)" : "毒（虫食い） MAX"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.POISON)
            );
            poisonBtn.active = isOwner && poisonCost > 0 && data.treasury >= poisonCost;
            this.addRenderableWidget(poisonBtn);
        } else if (activeTab == 2) {
            // Nation Upgrades Tab (Items shifted down by 12px)
            // 1. Debuff Range Button
            int rangeCost = (data.debuffRangeUpgrade + 1) * 2000;
            Button rangeBtn = new Button(this.left + 15, this.top + 92, 145, 20,
                    new TextComponent("範囲拡張 (+1c: " + rangeCost + "G)"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.RANGE)
            );
            rangeBtn.active = isOwner && data.treasury >= rangeCost;
            this.addRenderableWidget(rangeBtn);

            // 2. Alert Button
            Button alertBtn = new Button(this.left + 180, this.top + 92, 145, 20,
                    new TextComponent(data.hasAlertUpgrade ? "アラート機能: 有効" : "アラート購入 (2万G)"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.ALERT)
            );
            alertBtn.active = isOwner && !data.hasAlertUpgrade && data.treasury >= 20000;
            this.addRenderableWidget(alertBtn);

            // 3. Double Trade Button
            Button doubleTradeBtn = new Button(this.left + 15, this.top + 137, 145, 20,
                    new TextComponent(data.doubleTradeLicense ? "売上2倍: 有効" : "売上2倍権 (5万G)"),
                    btn -> upgrade(PacketRequestCoreUpgrade.UpgradeType.DOUBLE_TRADE)
            );
            doubleTradeBtn.active = isOwner && !data.doubleTradeLicense && data.treasury >= 50000;
            this.addRenderableWidget(doubleTradeBtn);

            // 4. Sub-Core Passive Income Button
            Button incomeBtn = new Button(this.left + 180, this.top + 137, 145, 20,
                    new TextComponent("【サブコア画面で設定可能】"),
                    btn -> {}
            );
            incomeBtn.active = false;
            this.addRenderableWidget(incomeBtn);
        } else if (activeTab == 3) {
            // Foreign Trade (Auction Tab)
            // Price Input
            priceField = new EditBox(this.font, this.left + 135, this.top + 70, 75, 18, new TextComponent("希望価格"));
            priceField.setValue("1000");
            priceField.setFilter(s -> s.matches("\\d*"));
            this.addRenderableWidget(priceField);

            // Sell Button
            Button sellBtn = new Button(this.left + 220, this.top + 69, 105, 20, new TextComponent("手持ちを出品"), btn -> {
                try {
                    int price = Integer.parseInt(priceField.getValue());
                    ModMessages.sendToServer(new PacketRequestAuctionAction(data.corePos, PacketRequestAuctionAction.Action.SELL, price, 0));
                } catch (NumberFormatException e) {
                }
            });
            sellBtn.active = isOwner && !Minecraft.getInstance().player.getMainHandItem().isEmpty();
            this.addRenderableWidget(sellBtn);

            // Fetch listings
            List<com.example.territory.TerritorySavedData.AuctionEntry> list = com.example.territory.client.ClientAuctionData.getAuctionList();
            int totalPages = Math.max(1, (int) Math.ceil(list.size() / 4.0));
            if (currentPage >= totalPages) {
                currentPage = totalPages - 1;
            }
            if (currentPage < 0) {
                currentPage = 0;
            }

            // Prev Button
            Button prevBtn = new Button(this.left + 15, this.top + 185, 30, 18, new TextComponent("◀"), btn -> {
                if (currentPage > 0) {
                    currentPage--;
                    rebuildWidgets();
                }
            });
            prevBtn.active = currentPage > 0;
            this.addRenderableWidget(prevBtn);

            // Next Button
            Button nextBtn = new Button(this.left + 105, this.top + 185, 30, 18, new TextComponent("▶"), btn -> {
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    rebuildWidgets();
                }
            });
            nextBtn.active = currentPage < totalPages - 1;
            this.addRenderableWidget(nextBtn);

            // Dynamic Action Buttons
            int startIdx = currentPage * 4;
            int endIdx = Math.min(startIdx + 4, list.size());
            UUID myUuid = Minecraft.getInstance().player.getUUID();

            for (int i = startIdx; i < endIdx; i++) {
                com.example.territory.TerritorySavedData.AuctionEntry entry = list.get(i);
                int rowIdx = i - startIdx;
                int rowY = this.top + 98 + rowIdx * 21;

                boolean isSeller = entry.sellerUuid.equals(myUuid);
                Button actionBtn = new Button(this.left + 265, rowY + 1, 60, 18,
                        new TextComponent(isSeller ? "取消" : "購入"),
                        btn -> {
                            PacketRequestAuctionAction.Action act = isSeller
                                    ? PacketRequestAuctionAction.Action.CANCEL
                                    : PacketRequestAuctionAction.Action.BUY;
                            ModMessages.sendToServer(new PacketRequestAuctionAction(data.corePos, act, 0, entry.id));
                        }
                );
                actionBtn.active = isOwner;
                this.addRenderableWidget(actionBtn);
            }
        }

        // Beautiful Close button
        this.addRenderableWidget(new Button(
                this.left + (this.widthSize - 80) / 2,
                this.top + this.heightSize - 22,
                80, 18,
                new TextComponent("閉じる"),
                button -> this.onClose()
        ));
    }

    private void rebuildWidgets() {
        this.clearWidgets();
        this.init();
    }

    private void upgrade(PacketRequestCoreUpgrade.UpgradeType type) {
        ModMessages.sendToServer(new PacketRequestCoreUpgrade(data.corePos, type, BlockPos.ZERO));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (priceField != null && priceField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (priceField != null && priceField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);

        // Premium Dark-glassmorphic background card
        fill(poseStack, this.left - 2, this.top - 2, this.left + this.widthSize + 2, this.top + this.heightSize + 2, 0xFF444444);
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + this.heightSize, 0xDD111111);

        // Header highlight using team color
        int teamColorHex = getTeamColorHex(data.teamColor);
        fill(poseStack, this.left, this.top, this.left + this.widthSize, this.top + 25, teamColorHex | 0xFF000000);

        // Header Title
        drawCenteredString(poseStack, this.font, "§l" + this.title.getString(), this.left + this.widthSize / 2, this.top + 8, 0xFFFFFF);

        // Separator line
        fill(poseStack, this.left + 10, this.top + 55, this.left + this.widthSize - 10, this.top + 56, 0x22FFFFFF);

        // Render Tab contents text
        int currentY = this.top + 60;
        String teamColorCode = getTeamColorCode(data.teamColor);

        if (activeTab == 0) {
            // General Info & Revolution Tab (or SubCore Info)
            drawString(poseStack, this.font, (data.isMainCore ? "§eコア所有者: " : "§eサブコア所有者: ") + teamColorCode + data.ownerName, this.left + 20, currentY, 0xFFFFFF);
            drawString(poseStack, this.font, "§e国庫所持金: §6§l" + data.treasury + " §eG  §d⛃", this.left + 20, currentY + 16, 0xFFFFFF);

            if (data.isMainCore) {
                String statusStr = data.isVassal
                        ? "§c§l属国 §7(宗主への税率: §e" + (int) (data.vassalTaxRate * 100) + "%§7)"
                        : "§a§l独立国";
                if (data.isVassal && data.isRebelling) {
                    statusStr = "§e§l反乱中！ §c独立戦争の真っ最中 §7(上納金停止中)";
                }
                drawString(poseStack, this.font, "§e国家状態: " + statusStr, this.left + 20, currentY + 32, 0xFFFFFF);

                if (data.isVassal) {
                    if (data.isRebelling) {
                        drawCenteredString(poseStack, this.font, "§e宗主国のメインコアを破壊すれば独立を獲得できます！", this.left + this.widthSize / 2, this.top + 130, 0xFFFFFF);
                    } else {
                        drawCenteredString(poseStack, this.font, "§7平和的独立を行うか、チャットコマンド §c/territory rebel §7で反乱を起こせます。", this.left + this.widthSize / 2, this.top + 130, 0xCCCCCC);
                    }
                } else {
                    drawCenteredString(poseStack, this.font, "§7他プレイヤーのメインコアを完全に破壊すると属国にできます。", this.left + this.widthSize / 2, this.top + 135, 0xBBBBBB);
                }
            } else {
                drawCenteredString(poseStack, this.font, "§7自分のサブコアであれば不労所得化(金精製機化)が可能です。", this.left + this.widthSize / 2, this.top + 130, 0xCCCCCC);
            }
        } else if (activeTab == 1) {
            // Debuffs Tab text (Overlapping resolved: description at top+68, labels at top+82/top+127/top+172)
            drawCenteredString(poseStack, this.font, "§d§l【国家防衛個別進化ツリー - Plague Inc.風】", this.left + this.widthSize / 2, currentY, 0xFFFFFF);
            drawCenteredString(poseStack, this.font, "§7デバフは範囲内の侵入者にのみ適用されます。 (残高: §6" + data.treasury + "G§7)", this.left + this.widthSize / 2, this.top + 68, 0xCCCCCC);

            // Labels above buttons
            drawString(poseStack, this.font, "§e1. 移動速度低下 (Slowness)", this.left + 15, this.top + 82, 0xFFFFFF);
            drawString(poseStack, this.font, "§e2. 弱体化 (Weakness)", this.left + 180, this.top + 82, 0xFFFFFF);
            drawString(poseStack, this.font, "§e3. 採掘速度低下", this.left + 15, this.top + 127, 0xFFFFFF);
            drawString(poseStack, this.font, "§e4. 吐き気・空腹 (Hunger)", this.left + 180, this.top + 127, 0xFFFFFF);
            drawString(poseStack, this.font, "§e5. 虫食い (Poison)", this.left + 95, this.top + 172, 0xFFFFFF);
        } else if (activeTab == 2) {
            // Upgrades Tab text (Overlapping resolved: description at top+68, labels at top+82/top+127)
            drawCenteredString(poseStack, this.font, "§b§l【国家発展アップグレード項目】", this.left + this.widthSize / 2, currentY, 0xFFFFFF);
            drawCenteredString(poseStack, this.font, "§7ゴールドを使い領土の防衛能力や国力を強めます。 (残高: §6" + data.treasury + "G§7)", this.left + this.widthSize / 2, this.top + 68, 0xCCCCCC);

            // Display current states
            drawString(poseStack, this.font, "§e1. デバフ範囲: §a隣接 " + (3 + data.debuffRangeUpgrade) + " チャンク", this.left + 15, this.top + 82, 0xFFFFFF);
            drawString(poseStack, this.font, "§e2. 侵入警告アラーム", this.left + 180, this.top + 82, 0xFFFFFF);
            drawString(poseStack, this.font, "§e3. 取引売上2倍ライセンス", this.left + 15, this.top + 127, 0xFFFFFF);
            drawString(poseStack, this.font, "§e4. サブコア自動精製改造 (台数: " + data.passiveIncomeCount + ")", this.left + 180, this.top + 127, 0xFFFFFF);
        } else if (activeTab == 3) {
            // Foreign Trade (Auction Tab text)
            drawCenteredString(poseStack, this.font, "§d§l【外交オークション交易ボード】", this.left + this.widthSize / 2, currentY, 0xFFFFFF);
            drawCenteredString(poseStack, this.font, "§7全プレイヤーとアイテム売買が可能です。 (残高: §6" + data.treasury + "G§7)", this.left + this.widthSize / 2, this.top + 52, 0xCCCCCC);

            // Quick listing area details
            ItemStack handStack = Minecraft.getInstance().player.getMainHandItem();
            drawString(poseStack, this.font, "§e手持ち:", this.left + 15, this.top + 73, 0xFFFFFF);

            if (!handStack.isEmpty()) {
                this.itemRenderer.renderAndDecorateItem(handStack, this.left + 95, this.top + 69);
                this.itemRenderer.renderGuiItemDecorations(this.font, handStack, this.left + 95, this.top + 69);
            } else {
                drawString(poseStack, this.font, "§7[なし]", this.left + 95, this.top + 73, 0x888888);
            }

            // Separator between quick listing and listing list
            fill(poseStack, this.left + 10, this.top + 93, this.left + this.widthSize - 10, this.top + 94, 0x22FFFFFF);

            // Display listing list
            List<com.example.territory.TerritorySavedData.AuctionEntry> list = com.example.territory.client.ClientAuctionData.getAuctionList();
            int totalPages = Math.max(1, (int) Math.ceil(list.size() / 4.0));
            drawString(poseStack, this.font, "§fP. " + (currentPage + 1) + " / " + totalPages, this.left + 53, this.top + 190, 0xFFFFFF);

            int startIdx = currentPage * 4;
            int endIdx = Math.min(startIdx + 4, list.size());

            for (int i = startIdx; i < endIdx; i++) {
                com.example.territory.TerritorySavedData.AuctionEntry entry = list.get(i);
                int rowIdx = i - startIdx;
                int rowY = this.top + 98 + rowIdx * 21;

                // Alternate row backgrounds for better readability
                int rowBg = rowIdx % 2 == 0 ? 0x11FFFFFF : 0x05FFFFFF;
                fill(poseStack, this.left + 10, rowY, this.left + this.widthSize - 10, rowY + 20, rowBg);

                // Render item icon
                if (entry.itemStack != null && !entry.itemStack.isEmpty()) {
                    this.itemRenderer.renderAndDecorateItem(entry.itemStack, this.left + 12, rowY + 2);
                    this.itemRenderer.renderGuiItemDecorations(this.font, entry.itemStack, this.left + 12, rowY + 2);
                }

                // Description
                String itemName = entry.itemStack != null ? entry.itemStack.getHoverName().getString() : "不明";
                int count = entry.itemStack != null ? entry.itemStack.getCount() : 0;

                String sellerName = entry.sellerUsername;
                if (sellerName.length() > 12) {
                    sellerName = sellerName.substring(0, 11) + "..";
                }

                // Render text columns
                drawString(poseStack, this.font, "§a" + count + " §f" + itemName + " §7(" + sellerName + ")", this.left + 32, rowY + 6, 0xFFFFFF);
                drawString(poseStack, this.font, "§6§l" + entry.price + "G §d⛃", this.left + 185, rowY + 6, 0xFFFFFF);
            }
        }

        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getSlownessCost(int level) {
        if (level == 0) return 5000;
        if (level == 1) return 15000;
        if (level == 2) return 50000;
        return 0;
    }

    private int getWeaknessCost(int level) {
        if (level == 0) return 30000;
        if (level == 1) return 60000;
        return 0;
    }

    private int getMiningCost(int level) {
        if (level == 0) return 50000;
        if (level == 1) return 100000;
        if (level == 2) return 200000;
        return 0;
    }

    private int getHungerCost(int level) {
        if (level == 0) return 5000;
        if (level == 1) return 55000;
        return 0;
    }

    private int getTeamColorHex(String teamColor) {
        return switch (teamColor) {
            case "RED" -> 0xDD5555;
            case "BLUE" -> 0x5555FF;
            case "GREEN" -> 0x55FF55;
            case "YELLOW" -> 0xFFFF55;
            case "PURPLE" -> 0xAA00AA;
            case "ORANGE" -> 0xFFAA00;
            default -> 0xCCCCCC;
        };
    }

    private String getTeamColorCode(String teamColor) {
        return switch (teamColor) {
            case "RED" -> "§c";
            case "BLUE" -> "§9";
            case "GREEN" -> "§a";
            case "YELLOW" -> "§e";
            case "PURPLE" -> "§5";
            case "ORANGE" -> "§6";
            default -> "§f";
        };
    }
}
