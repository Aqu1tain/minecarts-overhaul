package com.akitain.minecartsoverhaul.registry.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Oxidizable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public class OxidizableRailBlock extends CopperRailBlock implements Oxidizable {

    // Each random tick rolls vanilla degradation up to this many times for an effective ~Nx
    // weathering rate over a vanilla copper block in the same location.
    private static final int DEGRADATION_ROLLS_PER_TICK = 4;

    public static final MapCodec<OxidizableRailBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Oxidizable.OxidationLevel.CODEC.fieldOf("weathering_state").forGetter(OxidizableRailBlock::getDegradationLevel),
                    createSettingsCodec()
            ).apply(instance, OxidizableRailBlock::new)
    );

    public OxidizableRailBlock(OxidationLevel oxidationLevel, Settings settings) {
        super(oxidationLevel, settings);
    }

    @Override
    public MapCodec<OxidizableRailBlock> getCodec() {
        return CODEC;
    }

    @Override
    public boolean hasRandomTicks(BlockState state) {
        return Oxidizable.getIncreasedOxidationBlock(state.getBlock()).isPresent();
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        for (int i = 0; i < DEGRADATION_ROLLS_PER_TICK; i++) {
            this.tickDegradation(state, world, pos, random);
            // Stop early once the stage advanced; otherwise multiple rolls could skip a tier.
            if (world.getBlockState(pos) != state) return;
        }
    }
}
