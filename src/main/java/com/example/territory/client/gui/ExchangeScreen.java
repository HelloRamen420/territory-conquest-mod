package com.example.territory.client.gui;

import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketRequestTrade;
import com.example.territory.network.PacketRequestPurchase;
import com.example.territory.util.TradeManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.example.territory.TerritorySavedData;
import net.minecraft.network.chat.Component;

public class ExchangeScreen extends Screen {
    private final BlockPos corePos;
    private int treasury;
    private final TerritorySavedData.PlayerState mockState;
    private final Screen parentScreen;
    private boolean hasDoubleSales;

    private int left, top;
    private final int widthSize = 250;
    private final int heightSize = 230;

    private boolean isBuyMode = false;
    private int selectedSellIndex = 0;
    private int selectedBuyIndex = 0;

    private Button tradeButton;
    private Button tabSellButton;
    private Button tabBuyButton;
    private TradeSlider quantitySlider;

    private static final TradeManager.TradeItem[] SELL_ITEMS = TradeManager.TradeItem.values();
    private static final TradeManager.PurchaseItem[] BUY_ITEMS = TradeManager.PurchaseItem.values();

    private static final int VISIBLE_ROWS = 6;
    private int scrollOffset = 0;
    private Button upScrollButton;
    private Button downScrollButton;
    private final Button[] listButtons = new Button[VISIBLE_ROWS];

    public ExchangeScreen(BlockPos corePos, int treasury, int ironSold, int goldSold, int emeraldSold, boolean hasDoubleSales, Screen parentScreen) {
        super(new TextComponent("総合取引所"));
        this.corePos = corePos;
        this.treasury = treasury;
        this.hasDoubleSales = hasDoubleSales;
        this.parentScreen = parentScreen;

        this.mockState = new TerritorySavedData.PlayerState();
        this.mockState.totalIronSold = ironSold;
        this.mockState.totalGoldSold = goldSold;
        this.mockState.totalEmeraldSold = emeraldSold;
    }

    public void updateData(int treasury, int ironSold, int goldSold, int emeraldSold, boolean hasDoubleSales) {
        this.treasury = treasury;
        this.mockState.totalIronSold = ironSold;
        this.mockState.totalGoldSold = goldSold;
        this.mockState.totalEmeraldSold = emeraldSold;
        this.hasDoubleSales = hasDoubleSales;
        updateControls();
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - this.widthSize) / 2;
        this.top = (this.height - this.heightSize) / 2;

        // Tabs
        tabSellButton = new Button(left + 10, top + 25, 110, 20, new TextComponent("買取 (アイテム売却)"), btn -> {
            isBuyMode = false;
            scrollOffset = 0;
            updateScrollLimits();
            updateControls();
        });
        tabBuyButton = new Button(left + 130, top + 25, 110, 20, new TextComponent("購入 (アイテム購入)"), btn -> {
            isBuyMode = true;
            scrollOffset = 0;
            updateScrollLimits();
            updateControls();
        });
        this.addRenderableWidget(tabSellButton);
        this.addRenderableWidget(tabBuyButton);

        // Slider
        quantitySlider = new TradeSlider(left + 120, top + 155, 110, 20, new TextComponent("数量: 1"), 0.0);
        this.addRenderableWidget(quantitySlider);

        // Trade Button
        tradeButton = new Button(left + 120, top + 180, 110, 20, new TextComponent("取引する"), btn -> executeTrade());
        this.addRenderableWidget(tradeButton);

        // Back Button
        this.addRenderableWidget(new Button(left + 10, top + 200, 80, 20, new TextComponent("戻る"), btn -> {
            Minecraft.getInstance().setScreen(parentScreen);
        }));

        // Scroll buttons
        upScrollButton = new Button(left + 110, top + 55, 10, 20, new TextComponent("▲"), btn -> {
            scrollUp();
        });
        downScrollButton = new Button(left + 110, top + 155, 10, 20, new TextComponent("▼"), btn -> {
            scrollDown();
        });
        this.addRenderableWidget(upScrollButton);
        this.addRenderableWidget(downScrollButton);

        // Lists (we use invisible buttons for the list area and draw the text ourselves)
        // For simplicity in this mock-villager UI, we add buttons for all items and toggle their visibility
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int row = i;
            listButtons[row] = new Button(left + 10, top + 55 + (row * 20), 100, 20, new TextComponent(""), btn -> {
                int index = scrollOffset + row;
                if (isBuyMode) {
                    if (index < BUY_ITEMS.length) selectedBuyIndex = index;
                } else {
                    if (index < SELL_ITEMS.length) selectedSellIndex = index;
                }
                updateControls();
            }) {
                @Override
                public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
                    int index = scrollOffset + row;
                    int totalItems = isBuyMode ? BUY_ITEMS.length : SELL_ITEMS.length;
                    if (index < totalItems) {
                        String label = isBuyMode ? getBuyItemName(BUY_ITEMS[index]) : getSellItemName(SELL_ITEMS[index]);
                        this.setMessage(new TextComponent(label));
                        super.renderButton(poseStack, mouseX, mouseY, partialTicks);
                    }
                }
            };
            this.addRenderableWidget(listButtons[row]);
        }

        updateScrollLimits();
        updateControls();
    }

    private void updateControls() {
        tabSellButton.active = isBuyMode;
        tabBuyButton.active = !isBuyMode;

        int totalItems = isBuyMode ? BUY_ITEMS.length : SELL_ITEMS.length;

        if (listButtons != null) {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                if (listButtons[i] != null) {
                    int index = scrollOffset + i;
                    boolean isVisible = index < totalItems;
                    listButtons[i].visible = isVisible;
                    listButtons[i].active = isVisible;
                }
            }
        }

        if (upScrollButton != null && downScrollButton != null) {
            int maxOffset = Math.max(0, totalItems - VISIBLE_ROWS);
            upScrollButton.active = scrollOffset > 0;
            downScrollButton.active = scrollOffset < maxOffset;
            
            boolean needsScrolling = totalItems > VISIBLE_ROWS;
            upScrollButton.visible = needsScrolling;
            downScrollButton.visible = needsScrolling;
        }

        int maxTrades = getMaxTrades();
        if (maxTrades <= 0) {
            tradeButton.active = false;
            quantitySlider.active = false;
        } else {
            tradeButton.active = true;
            quantitySlider.active = true;
        }
        quantitySlider.updateMessage();
    }

    private int getMaxTrades() {
        if (isBuyMode) {
            TradeManager.PurchaseItem item = BUY_ITEMS[selectedBuyIndex];
            int price = TradeManager.getPurchasePrice(item);
            int maxBuyCount = treasury / price;
            return Math.max(0, Math.min(maxBuyCount, 64)); // Cap at 64
        } else {
            TradeManager.TradeItem item = SELL_ITEMS[selectedSellIndex];
            TradeManager.TradeRate rate = TradeManager.getCurrentRate(item, mockState);
            int invCount = countInInventory(item);
            int maxByInventory = invCount / rate.requiredItems;
            int maxByLimit = 64 / rate.requiredItems; // Cap at 64 items sold per click
            return Math.max(0, Math.min(maxByInventory, maxByLimit));
        }
    }

    private int getSelectedQuantity() {
        int max = getMaxTrades();
        if (max <= 0) return 0;
        return (int) Math.round(quantitySlider.getValue() * (max - 1)) + 1;
    }

    private void executeTrade() {
        int qty = getSelectedQuantity();
        if (qty <= 0) return;

        if (isBuyMode) {
            TradeManager.PurchaseItem item = BUY_ITEMS[selectedBuyIndex];
            ModMessages.sendToServer(new PacketRequestPurchase(corePos, item, qty));
        } else {
            TradeManager.TradeItem item = SELL_ITEMS[selectedSellIndex];
            ModMessages.sendToServer(new PacketRequestTrade(corePos, item, qty));
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);

        fill(poseStack, left, top, left + widthSize, top + heightSize, 0xCC000000);
        fill(poseStack, left - 1, top - 1, left + widthSize + 1, top, 0xFFDAA520);
        fill(poseStack, left - 1, top + heightSize, left + widthSize + 1, top + heightSize + 1, 0xFFDAA520);
        fill(poseStack, left - 1, top, left, top + heightSize, 0xFFDAA520);
        fill(poseStack, left + widthSize, top, left + widthSize + 1, top + heightSize, 0xFFDAA520);

        font.draw(poseStack, "国庫取引所 (総合)", left + 10, top + 10, 0xFFD700);
        font.draw(poseStack, "現在国庫: " + treasury + "G", left + 140, top + 10, 0xFFFFFF);

        // Draw scrollbar track and thumb
        int totalItems = isBuyMode ? BUY_ITEMS.length : SELL_ITEMS.length;
        if (totalItems > VISIBLE_ROWS) {
            int maxOffset = Math.max(0, totalItems - VISIBLE_ROWS);
            
            int trackX = left + 113;
            int trackY = top + 77;
            int trackWidth = 4;
            int trackHeight = 76;
            
            // Track background
            fill(poseStack, trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0xFF333333);
            
            int thumbHeight = Math.max(15, trackHeight * VISIBLE_ROWS / totalItems);
            if (thumbHeight > trackHeight) {
                thumbHeight = trackHeight;
            }
            int thumbYOffset = maxOffset > 0 ? (scrollOffset * (trackHeight - thumbHeight) / maxOffset) : 0;
            
            // Thumb
            fill(poseStack, trackX, trackY + thumbYOffset, trackX + trackWidth, trackY + thumbYOffset + thumbHeight, 0xFFDAA520);
        }

        int rightX = left + 120;
        int rightY = top + 55;

        int selectedQty = getSelectedQuantity();

        if (isBuyMode) {
            TradeManager.PurchaseItem selectedItem = BUY_ITEMS[selectedBuyIndex];
            int price = TradeManager.getPurchasePrice(selectedItem);
            
            int displayRow = selectedBuyIndex - scrollOffset;
            if (displayRow >= 0 && displayRow < VISIBLE_ROWS) {
                fill(poseStack, left + 8, top + 53 + (displayRow * 20), left + 112, top + 75 + (displayRow * 20), 0x55FFFFFF);
            }
            
            font.draw(poseStack, "選択中: " + getBuyItemName(selectedItem), rightX, rightY, 0xFFFFFF);
            font.draw(poseStack, "価格: " + price + " G / 1個", rightX, rightY + 20, 0xAAAAAA);
            
            if (selectedQty > 0) {
                font.draw(poseStack, "購入予定: " + selectedQty + " 個", rightX, rightY + 50, 0xFFFFFF);
                font.draw(poseStack, "消費G: " + (price * selectedQty) + " G", rightX, rightY + 65, 0xFF5555);
            } else {
                font.draw(poseStack, "ゴールドが不足しています", rightX, rightY + 50, 0xFF5555);
            }
        } else {
            TradeManager.TradeItem selectedItem = SELL_ITEMS[selectedSellIndex];
            TradeManager.TradeRate rate = TradeManager.getCurrentRate(selectedItem, mockState);
            
            int displayRow = selectedSellIndex - scrollOffset;
            if (displayRow >= 0 && displayRow < VISIBLE_ROWS) {
                fill(poseStack, left + 8, top + 53 + (displayRow * 20), left + 112, top + 75 + (displayRow * 20), 0x55FFFFFF);
            }
            
            font.draw(poseStack, "選択中: " + getSellItemName(selectedItem), rightX, rightY, 0xFFFFFF);
            int soldCount = getSoldCount(selectedItem);
            font.draw(poseStack, "累計売却数: " + soldCount + " 個", rightX, rightY + 15, 0xAAAAAA);
            int goldReward = rate.goldReward;
            if (hasDoubleSales) {
                goldReward *= 2;
            }
            font.draw(poseStack, "現在レート: " + rate.requiredItems + "個 → " + goldReward + "G", rightX, rightY + 28, 0x55FF55);
            
            int invCount = countInInventory(selectedItem);
            font.draw(poseStack, "手持ち: " + invCount + " 個", rightX, rightY + 45, 0xFFFFFF);
            
            if (selectedQty > 0) {
                int consumeItems = rate.requiredItems * selectedQty;
                int earnGold = rate.goldReward * selectedQty;
                if (hasDoubleSales) {
                    earnGold *= 2;
                }
                font.draw(poseStack, "消費予定: " + consumeItems + " 個", rightX, rightY + 65, 0xFF5555);
                font.draw(poseStack, "獲得予定: " + earnGold + " G", rightX, rightY + 80, 0x55FF55);
            } else {
                font.draw(poseStack, "売却可能なアイテムが", rightX, rightY + 65, 0xFF5555);
                font.draw(poseStack, "不足しています", rightX, rightY + 75, 0xFF5555);
            }
        }

        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private String getSellItemName(TradeManager.TradeItem item) {
        switch (item) {
            case IRON: return "鉄インゴット";
            case GOLD: return "金インゴット";
            case DIAMOND: return "ダイヤモンド";
            case EMERALD: return "エメラルド";
            case BOOK: return "本";
            case HAY_BLOCK: return "干草の俵";
            case NETHERITE_INGOT: return "ネザライト";
            case NETHER_STAR: return "ネザースター";
            default: return "不明";
        }
    }

    private String getBuyItemName(TradeManager.PurchaseItem item) {
        switch (item) {
            case TERRITORY_FLAG: return "開拓の旗";
            default: return "不明";
        }
    }

    private int getSoldCount(TradeManager.TradeItem item) {
        switch (item) {
            case IRON: return mockState.totalIronSold;
            case GOLD: return mockState.totalGoldSold;
            case EMERALD: return mockState.totalEmeraldSold;
            default: return 0;
        }
    }

    private int countInInventory(TradeManager.TradeItem item) {
        if (Minecraft.getInstance().player == null) return 0;
        net.minecraft.world.item.Item mcItem;
        switch (item) {
            case IRON: mcItem = Items.IRON_INGOT; break;
            case GOLD: mcItem = Items.GOLD_INGOT; break;
            case DIAMOND: mcItem = Items.DIAMOND; break;
            case EMERALD: mcItem = Items.EMERALD; break;
            case BOOK: mcItem = Items.BOOK; break;
            case HAY_BLOCK: mcItem = Items.HAY_BLOCK; break;
            case NETHERITE_INGOT: mcItem = Items.NETHERITE_INGOT; break;
            case NETHER_STAR: mcItem = Items.NETHER_STAR; break;
            default: return 0;
        }
        int count = 0;
        for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
            if (stack.getItem() == mcItem) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        quantitySlider.mouseReleased(mouseX, mouseY, button);
        updateControls();
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (quantitySlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            updateControls();
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean isHoveringList = mouseX >= (left + 10) && mouseX <= (left + 120) && mouseY >= (top + 55) && mouseY <= (top + 175);
        if (isHoveringList) {
            if (delta > 0) {
                scrollUp();
            } else if (delta < 0) {
                scrollDown();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void scrollUp() {
        scrollOffset--;
        updateScrollLimits();
        updateControls();
    }

    private void scrollDown() {
        scrollOffset++;
        updateScrollLimits();
        updateControls();
    }

    private void updateScrollLimits() {
        int totalItems = isBuyMode ? BUY_ITEMS.length : SELL_ITEMS.length;
        int maxOffset = Math.max(0, totalItems - VISIBLE_ROWS);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    class TradeSlider extends AbstractSliderButton {
        public TradeSlider(int x, int y, int width, int height, Component message, double value) {
            super(x, y, width, height, message, value);
        }

        @Override
        protected void updateMessage() {
            int qty = getSelectedQuantity();
            if (isBuyMode) {
                this.setMessage(new TextComponent("購入数: " + qty));
            } else {
                TradeManager.TradeItem item = SELL_ITEMS[selectedSellIndex];
                TradeManager.TradeRate rate = TradeManager.getCurrentRate(item, mockState);
                this.setMessage(new TextComponent("売却数: " + (qty * rate.requiredItems)));
            }
        }

        @Override
        protected void applyValue() {
            // Updated dynamically via getSelectedQuantity
        }
        
        public double getValue() {
            return this.value;
        }
    }
}
