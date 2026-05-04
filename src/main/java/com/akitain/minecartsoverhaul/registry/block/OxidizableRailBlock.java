package com.akitain.minecartsoverhaul.registry.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

public class OxidizableRailBlock extends CopperRailBlock implements WeatheringCopper {

    // Each random tick rolls vanilla degradation up to this many times for an effective ~Nx
    // weathering rate over a vanilla copper block in the same location.
    private static final int DEGRADATION_ROLLS_PER_TICK = 4;

    public static final MapCodec<OxidizableRailBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(OxidizableRailBlock::getDegradationLevel),
                    propertiesCodec()
            ).apply(instance, OxidizableRailBlock::new)
    );

    public OxidizableRailBlock(WeatherState oxidationLevel, Properties settings) {
        super(oxidationLevel, settings);
    }

    @Override
    public MapCodec<OxidizableRailBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        for (int i = 0; i < DEGRADATION_ROLLS_PER_TICK; i++) {
            this.changeOverTime(state, world, pos, random);
            // Stop early once the stage advanced; otherwise multiple rolls could skip a tier.
            if (world.getBlockState(pos) != state) return;
        }
    }
}
