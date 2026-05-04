package com.akitain.minecartsoverhaul.registry.other;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static net.minecraft.world.level.block.DispenserBlock.DISPENSER_REGISTRY;

public class DispencerMinecartEntity extends AbstractMinecartContainer {

    private static final int INVENTORY_SIZE = 9;
    private static final int FIRE_COOLDOWN_TICKS = 8;
    private static final DefaultDispenseItemBehavior DEFAULT_BEHAVIOR = new DefaultDispenseItemBehavior();
    private static final EntityDataAccessor<Boolean> POWERED = SynchedEntityData.defineId(DispencerMinecartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLIPPED = SynchedEntityData.defineId(DispencerMinecartEntity.class, EntityDataSerializers.BOOLEAN);
    // NBT key kept lowercased-then-capitalised wrong for backwards-compat with worlds saved
    // before the typo was noticed; renaming would silently drop the flipped state on load.
    private static final String NBT_FLIPPED = "FLipped";
    private static final String NBT_COOLDOWN = "Cooldown";

    private int cooldown = 0;

    public DispencerMinecartEntity(EntityType<? extends DispencerMinecartEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POWERED, false);
        builder.define(FLIPPED, false);
    }

    protected boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    protected void setPowered(boolean powered) {
        this.entityData.set(POWERED, powered);
    }

    protected boolean isFlipped() {
        return this.entityData.get(FLIPPED);
    }

    protected void setFlipped(boolean flipped) {
        this.entityData.set(FLIPPED, flipped);
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.DISPENSER.defaultBlockState()
                .setValue(DispenserBlock.FACING, isFlipped() ? Direction.WEST : Direction.EAST)
                .setValue(DispenserBlock.TRIGGERED, isPowered());
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public void activateMinecart(ServerLevel world, int x, int y, int z, boolean powered) {
        if (cooldown != 0 || !powered) return;
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.getBlockState(pos).is(Blocks.ACTIVATOR_RAIL)) return;
        this.cooldown = FIRE_COOLDOWN_TICKS;
        BlockState facing = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, fireDirection());
        dispense(world, facing, pos);
    }

    private Direction fireDirection() {
        Direction dir = Direction.fromYRot(this.getYRot());
        // Yaw maps East to East but South to North on the Z axis, so flip Z-axis direction
        // to align with the visual "front" of the cart.
        if (dir.getAxis() == Direction.Axis.Z) dir = dir.getOpposite();
        if (isFlipped()) dir = dir.getOpposite();
        return dir;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) return super.interact(player, hand);
        setFlipped(!isFlipped());
        return InteractionResult.SUCCESS;
    }

    protected void dispense(ServerLevel world, BlockState state, BlockPos pos) {
        // Vanilla dispenser logic operates on a DispenserBlockEntity, so we mirror our inventory
        // into a temporary one, run the dispense, and copy any state changes back.
        DispenserBlockEntity proxy = new DispenserBlockEntity(pos, state);
        NonNullList<ItemStack> snapshot = snapshotInventory();
        proxy.setItems(snapshot);

        BlockSource pointer = new BlockSource(world, pos, state, proxy);
        int slot = proxy.getRandomSlot(world.random);
        if (slot < 0) {
            world.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
            world.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(proxy.getBlockState()));
        } else {
            ItemStack picked = proxy.getItem(slot);
            DispenseItemBehavior behavior = behaviorFor(world, picked);
            if (behavior != DispenseItemBehavior.NOOP) {
                proxy.setItem(slot, behavior.dispense(pointer, picked));
            }
        }

        writeBackInventory(snapshot);
    }

    private NonNullList<ItemStack> snapshotInventory() {
        NonNullList<ItemStack> copy = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        NonNullList<ItemStack> live = this.getItemStacks();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) copy.set(slot, live.get(slot));
        return copy;
    }

    private void writeBackInventory(NonNullList<ItemStack> snapshot) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) this.setChestVehicleItem(slot, snapshot.get(slot));
    }

    private DispenseItemBehavior behaviorFor(Level world, ItemStack stack) {
        if (!stack.isItemEnabled(world.enabledFeatures())) return DEFAULT_BEHAVIOR;
        DispenseItemBehavior registered = DISPENSER_REGISTRY.get(stack.getItem());
        if (registered != null) return registered;
        if (stack.has(DataComponents.EQUIPPABLE)) return EquipmentDispenseItemBehavior.INSTANCE;
        return DEFAULT_BEHAVIOR;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (cooldown > 0) cooldown--;
        setPowered(cooldown > 0);
    }

    @Override
    protected Item getDropItem() {
        return MinecartsOverhaul.DISPENSER_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(MinecartsOverhaul.DISPENSER_MINECART);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putShort(NBT_COOLDOWN, (short) this.cooldown);
        view.putBoolean(NBT_FLIPPED, isFlipped());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        this.cooldown = view.getShortOr(NBT_COOLDOWN, (short) 0);
        setFlipped(view.getBooleanOr(NBT_FLIPPED, false));
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
        return new DispenserMenu(syncId, playerInventory, this);
    }
}
