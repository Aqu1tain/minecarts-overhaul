package com.akitain.minecartsoverhaul.registry.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Oxidizable;
import net.minecraft.block.enums.RailShape;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;

public class CopperRailBlock extends AbstractRailBlock {

    public static final EnumProperty<RailShape> SHAPE = Properties.STRAIGHT_RAIL_SHAPE;

    public static final MapCodec<CopperRailBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Oxidizable.OxidationLevel.CODEC.fieldOf("weathering_state").forGetter(CopperRailBlock::getDegradationLevel),
                    createSettingsCodec()
            ).apply(instance, CopperRailBlock::new)
    );

    public final Oxidizable.OxidationLevel oxidationLevel;

    public CopperRailBlock(Oxidizable.OxidationLevel oxidationLevel, AbstractBlock.Settings settings) {
        super(true, settings);
        this.oxidationLevel = oxidationLevel;
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(SHAPE, RailShape.NORTH_SOUTH)
                .with(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends AbstractRailBlock> getCodec() {
        return CODEC;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED);
    }

    public Oxidizable.OxidationLevel getDegradationLevel() {
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
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        RailShape rotated = rotateShape(state.get(SHAPE), rotation);
        return state.with(SHAPE, rotated);
    }

    private static RailShape rotateShape(RailShape shape, BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> rotate180(shape);
            case CLOCKWISE_90 -> rotate90Cw(shape);
            case COUNTERCLOCKWISE_90 -> rotate90Ccw(shape);
            case NONE -> shape;
        };
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
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        RailShape mirrored = mirrorShape(state.get(SHAPE), mirror);
        if (mirrored == null) return super.mirror(state, mirror);
        return state.with(SHAPE, mirrored);
    }

    private static RailShape mirrorShape(RailShape shape, BlockMirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> switch (shape) {
                case ASCENDING_NORTH -> RailShape.ASCENDING_SOUTH;
                case ASCENDING_SOUTH -> RailShape.ASCENDING_NORTH;
                case SOUTH_EAST -> RailShape.NORTH_EAST;
                case SOUTH_WEST -> RailShape.NORTH_WEST;
                case NORTH_WEST -> RailShape.SOUTH_WEST;
                case NORTH_EAST -> RailShape.SOUTH_EAST;
                default -> null;
            };
            case FRONT_BACK -> switch (shape) {
                case ASCENDING_EAST -> RailShape.ASCENDING_WEST;
                case ASCENDING_WEST -> RailShape.ASCENDING_EAST;
                case SOUTH_EAST -> RailShape.SOUTH_WEST;
                case SOUTH_WEST -> RailShape.SOUTH_EAST;
                case NORTH_WEST -> RailShape.NORTH_EAST;
                case NORTH_EAST -> RailShape.NORTH_WEST;
                default -> null;
            };
            default -> null;
        };
    }
}
