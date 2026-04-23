package com.akitain.minecartsoverhaul.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class CopperRailBlock extends BaseRailBlock implements WeatheringCopper {

    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final MapCodec<CopperRailBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperRailBlock::getAge),
                    propertiesCodec()
            ).apply(instance, (s, p) -> new CopperRailBlock(s, false, p))
    );

    private final WeatherState weatherState;
    private final boolean oxidizing;

    public CopperRailBlock(WeatherState weatherState, boolean oxidizing, BlockBehaviour.Properties properties) {
        super(true, properties);
        this.weatherState = weatherState;
        this.oxidizing = oxidizing;
        registerDefaultState(stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false));
    }

    @Override
    public MapCodec<CopperRailBlock> codec() {
        return CODEC;
    }

    @Override
    public WeatherState getAge() {
        return weatherState;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return oxidizing;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        changeOverTime(state, level, pos, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(SHAPE, rotate(state.getValue(SHAPE), rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(SHAPE, mirror(state.getValue(SHAPE), mirror));
    }

    public static double getMaxSpeed(BlockState state) {
        return switch (((CopperRailBlock) state.getBlock()).weatherState) {
            case UNAFFECTED -> 40.0;
            case EXPOSED -> 20.0;
            case WEATHERED -> 10.0;
            case OXIDIZED -> 5.0;
        };
    }
}
