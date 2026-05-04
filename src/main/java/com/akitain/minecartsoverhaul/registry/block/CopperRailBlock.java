package com.akitain.minecartsoverhaul.registry.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

public class CopperRailBlock extends BaseRailBlock {

    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;

    public static final MapCodec<CopperRailBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperRailBlock::getDegradationLevel),
                    propertiesCodec()
            ).apply(instance, CopperRailBlock::new)
    );

    public final WeatheringCopper.WeatherState oxidationLevel;

    public CopperRailBlock(WeatheringCopper.WeatherState oxidationLevel, BlockBehaviour.Properties settings) {
        super(true, settings);
        this.oxidationLevel = oxidationLevel;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseRailBlock> codec() {
        return CODEC;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED);
    }

    public WeatheringCopper.WeatherState getDegradationLevel() {
        return oxidationLevel;
    }

    public static double getMaxVelocity(BlockState state) {
        if (!(state.getBlock() instanceof CopperRailBlock rail)) return 8.0;
        return switch (rail.oxidationLevel) {
            case UNAFFECTED -> 40.0;
            case EXPOSED -> 20.0;
            case WEATHERED -> 10.0;
            case OXIDIZED -> 5.0;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RailShape shape = state.getValue(SHAPE);
        RailShape rotated = switch (rotation) {
            case CLOCKWISE_180 -> rotate180(shape);
            case CLOCKWISE_90 -> rotate90Cw(shape);
            case COUNTERCLOCKWISE_90 -> rotate90Ccw(shape);
            case NONE -> shape;
        };
        return state.setValue(SHAPE, rotated);
    }

    private static RailShape rotate180(RailShape shape) {
        return switch (shape) {
            case ASCENDING_EAST -> RailShape.ASCENDING_WEST;
            case ASCENDING_WEST -> RailShape.ASCENDING_EAST;
            case ASCENDING_NORTH -> RailShape.ASCENDING_SOUTH;
            case ASCENDING_SOUTH -> RailShape.ASCENDING_NORTH;
            case SOUTH_EAST -> RailShape.NORTH_WEST;
            case SOUTH_WEST -> RailShape.NORTH_EAST;
            case NORTH_WEST -> RailShape.SOUTH_EAST;
            case NORTH_EAST -> RailShape.SOUTH_WEST;
            case NORTH_SOUTH, EAST_WEST -> shape;
        };
    }

    private static RailShape rotate90Cw(RailShape shape) {
        return switch (shape) {
            case ASCENDING_EAST -> RailShape.ASCENDING_SOUTH;
            case ASCENDING_WEST -> RailShape.ASCENDING_NORTH;
            case ASCENDING_NORTH -> RailShape.ASCENDING_EAST;
            case ASCENDING_SOUTH -> RailShape.ASCENDING_WEST;
            case SOUTH_EAST -> RailShape.SOUTH_WEST;
            case SOUTH_WEST -> RailShape.NORTH_WEST;
            case NORTH_WEST -> RailShape.NORTH_EAST;
            case NORTH_EAST -> RailShape.SOUTH_EAST;
            case NORTH_SOUTH -> RailShape.EAST_WEST;
            case EAST_WEST -> RailShape.NORTH_SOUTH;
        };
    }

    private static RailShape rotate90Ccw(RailShape shape) {
        return switch (shape) {
            case ASCENDING_EAST -> RailShape.ASCENDING_NORTH;
            case ASCENDING_WEST -> RailShape.ASCENDING_SOUTH;
            case ASCENDING_NORTH -> RailShape.ASCENDING_WEST;
            case ASCENDING_SOUTH -> RailShape.ASCENDING_EAST;
            case SOUTH_EAST -> RailShape.NORTH_EAST;
            case SOUTH_WEST -> RailShape.SOUTH_EAST;
            case NORTH_WEST -> RailShape.SOUTH_WEST;
            case NORTH_EAST -> RailShape.NORTH_WEST;
            case NORTH_SOUTH -> RailShape.EAST_WEST;
            case EAST_WEST -> RailShape.NORTH_SOUTH;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape shape = state.getValue(SHAPE);
        RailShape mirrored = switch (mirror) {
            case LEFT_RIGHT -> mirrorLeftRight(shape);
            case FRONT_BACK -> mirrorFrontBack(shape);
            default -> null;
        };
        if (mirrored == null) return super.mirror(state, mirror);
        return state.setValue(SHAPE, mirrored);
    }

    private static RailShape mirrorLeftRight(RailShape shape) {
        return switch (shape) {
            case ASCENDING_NORTH -> RailShape.ASCENDING_SOUTH;
            case ASCENDING_SOUTH -> RailShape.ASCENDING_NORTH;
            case SOUTH_EAST -> RailShape.NORTH_EAST;
            case SOUTH_WEST -> RailShape.NORTH_WEST;
            case NORTH_WEST -> RailShape.SOUTH_WEST;
            case NORTH_EAST -> RailShape.SOUTH_EAST;
            default -> null;
        };
    }

    private static RailShape mirrorFrontBack(RailShape shape) {
        return switch (shape) {
            case ASCENDING_EAST -> RailShape.ASCENDING_WEST;
            case ASCENDING_WEST -> RailShape.ASCENDING_EAST;
            case SOUTH_EAST -> RailShape.SOUTH_WEST;
            case SOUTH_WEST -> RailShape.SOUTH_EAST;
            case NORTH_WEST -> RailShape.NORTH_EAST;
            case NORTH_EAST -> RailShape.NORTH_WEST;
            default -> null;
        };
    }
}
