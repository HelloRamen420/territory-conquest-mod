package com.example.territory.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * TerritoryFlagBlock is placed to claim chunks.
 * It contains a "color" blockstate property (0 to 5) representing the player's team color:
 * 0: RED, 1: BLUE, 2: GREEN, 3: YELLOW, 4: PURPLE, 5: ORANGE.
 */
public class TerritoryFlagBlock extends Block {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 5);

    public TerritoryFlagBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }
}
