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

    public ExchangeScreen(BlockPos corePos, int treasury, int ironSold, int goldSold, int emeraldSold, Screen parentScreen) {
        super(new TextComponent("総合取引所"));
        this.corePos = corePos;
        this.treasury = treasury;
        this.parentScreen = parentScreen;

        this.mockState = new TerritorySavedData.PlayerState();
        this.mockState.totalIronSold = ironSold;
        this.mockState.totalGoldSold = goldSold;
        this.mockState.totalEmeraldSold = emeraldSold;
    }

    public void updateData(int treasury, int ironSold, int goldSold, int emeraldSold) {
        this.treasury = treasury;
        this.mockState.totalIronSold = ironSold;
        this.mockState.totalGoldSold = goldSold;
        this.mockState.totalEmeraldSold = emeraldSold;
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
            updateControls();
        });
        tabBuyButton = new Button(left + 130, top + 25, 110, 20, new TextComponent("購入 (アイテム購入)"), btn -> {
            isBuyMode = true;
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

        // Lists (we use invisible buttons for the list area and draw the text ourselves)
        // For simplicity in this mock-villager UI, we add buttons for all items and toggle their visibility
        for (int i = 0; i < Math.max(SELL_ITEMS.length, BUY_ITEMS.length); i++) {
            final int index = i;
            Button listBtn = new Button(left + 10, top + 55 + (i * 20), 100, 20, new TextComponent(""), btn -> {
                if (isBuyMode) {
                    if (index < BUY_ITEMS.length) selectedBuyIndex = index;
                } else {
                    if (index < SELL_ITEMS.length) selectedSellIndex = index;
                }
                updateControls();
            }) {
                @Override
                public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
                    if (isBuyMode && index >= BUY_ITEMS.length) return;
                    if (!isBuyMode && index >= SELL_ITEMS.length) return;
                    
                    String label = isBuyMode ? getBuyItemName(BUY_ITEMS[index]) : getSellItemName(SELL_ITEMS[index]);
                    this.setMessage(new TextComponent(label));
                    super.renderButton(poseStack, mouseX, mouseY, partialTicks);
                }
                
                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    if (isBuyMode && index >= BUY_ITEMS.length) return false;
                    if (!isBuyMode && index >= SELL_ITEMS.length) return false;
                    return super.mouseClicked(mouseX, mouseY, button);
                }
            };
            this.addRenderableWidget(listBtn);
        }

        updateControls();
    }

    private void updateControls() {
        tabSellButton.active = isBuyMode;
        tabBuyButton.active = !isBuyMode;

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

        int rightX = left + 120;
        int rightY = top + 55;

        int selectedQty = getSelectedQuantity();

        if (isBuyMode) {
            TradeManager.PurchaseItem selectedItem = BUY_ITEMS[selectedBuyIndex];
            int price = TradeManager.getPurchasePrice(selectedItem);
            
            fill(poseStack, left + 8, top + 53 + (selectedBuyIndex * 20), left + 112, top + 75 + (selectedBuyIndex * 20), 0x55FFFFFF);
            
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
            
            fill(poseStack, left + 8, top + 53 + (selectedSellIndex * 20), left + 112, top + 75 + (selectedSellIndex * 20), 0x55FFFFFF);
            
            font.draw(poseStack, "選択中: " + getSellItemName(selectedItem), rightX, rightY, 0xFFFFFF);
            int soldCount = getSoldCount(selectedItem);
            font.draw(poseStack, "累計売却数: " + soldCount + " 個", rightX, rightY + 15, 0xAAAAAA);
            font.draw(poseStack, "現在レート: " + rate.requiredItems + "個 → " + rate.goldReward + "G", rightX, rightY + 28, 0x55FF55);
            
            int invCount = countInInventory(selectedItem);
            font.draw(poseStack, "手持ち: " + invCount + " 個", rightX, rightY + 45, 0xFFFFFF);
            
            if (selectedQty > 0) {
                int consumeItems = rate.requiredItems * selectedQty;
                int earnGold = rate.goldReward * selectedQty;
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
