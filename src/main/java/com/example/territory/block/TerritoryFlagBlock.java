package com.example.territory.block;

import net.minecraft.world.level.block.Block;

/**
 * TerritoryFlagBlock is a collision-less block (like a torch) placed to claim unclaimed chunks.
 * Its placing and breaking events are captured by TerritoryPlacementEventHandler to manage claims.
 */
public class TerritoryFlagBlock extends Block {
    public TerritoryFlagBlock(Properties properties) {
        super(properties);
    }
}
