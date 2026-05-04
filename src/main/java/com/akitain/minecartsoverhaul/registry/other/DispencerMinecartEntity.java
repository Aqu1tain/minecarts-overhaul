package com.akitain.minecartsoverhaul.registry.other;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.block.dispenser.EquippableDispenserBehavior;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.event.GameEvent;

import static net.minecraft.block.DispenserBlock.BEHAVIORS;

public class DispencerMinecartEntity extends StorageMinecartEntity {

    private static final int INVENTORY_SIZE = 9;
    private static final int FIRE_COOLDOWN_TICKS = 8;
    private static final ItemDispenserBehavior DEFAULT_BEHAVIOR = new ItemDispenserBehavior();
    private static final TrackedData<Boolean> POWERED = DataTracker.registerData(DispencerMinecartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> FLIPPED = DataTracker.registerData(DispencerMinecartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    // NBT key kept lowercased-then-capitalised wrong for backwards-compat with worlds saved
    // before the typo was noticed; renaming would silently drop the flipped state on load.
    private static final String NBT_FLIPPED = "FLipped";
    private static final String NBT_COOLDOWN = "Cooldown";

    private int cooldown = 0;

    public DispencerMinecartEntity(EntityType<? extends DispencerMinecartEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POWERED, false);
        builder.add(FLIPPED, false);
    }

    protected boolean isPowered() {
        return this.dataTracker.get(POWERED);
    }

    protected void setPowered(boolean powered) {
        this.dataTracker.set(POWERED, powered);
    }

    protected boolean isFlipped() {
        return this.dataTracker.get(FLIPPED);
    }

    protected void setFlipped(boolean flipped) {
        this.dataTracker.set(FLIPPED, flipped);
    }

    @Override
    public BlockState getDefaultContainedBlock() {
        return Blocks.DISPENSER.getDefaultState()
                .with(DispenserBlock.FACING, isFlipped() ? Direction.WEST : Direction.EAST)
                .with(DispenserBlock.TRIGGERED, isPowered());
    }

    @Override
    public int size() {
        return INVENTORY_SIZE;
    }

    @Override
    public void onActivatorRail(ServerWorld world, int x, int y, int z, boolean powered) {
        if (cooldown != 0 || !powered) return;
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.getBlockState(pos).isOf(Blocks.ACTIVATOR_RAIL)) return;
        this.cooldown = FIRE_COOLDOWN_TICKS;
        BlockState facing = Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, fireDirection());
        dispense(world, facing, pos);
    }

    private Direction fireDirection() {
        Direction dir = Direction.fromHorizontalDegrees(this.getYaw());
        // Yaw maps East to East but South to North on the Z axis, so flip Z-axis direction
        // to align with the visual "front" of the cart.
        if (dir.getAxis() == Direction.Axis.Z) dir = dir.getOpposite();
        if (isFlipped()) dir = dir.getOpposite();
        return dir;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!player.shouldCancelInteraction()) return super.interact(player, hand);
        setFlipped(!isFlipped());
        return ActionResult.SUCCESS;
    }

    protected void dispense(ServerWorld world, BlockState state, BlockPos pos) {
        // Vanilla dispenser logic operates on a DispenserBlockEntity, so we mirror our inventory
        // into a temporary one, run the dispense, and copy any state changes back.
        DispenserBlockEntity proxy = new DispenserBlockEntity(pos, state);
        DefaultedList<ItemStack> snapshot = snapshotInventory();
        proxy.setHeldStacks(snapshot);

        BlockPointer pointer = new BlockPointer(world, pos, state, proxy);
        int slot = proxy.chooseNonEmptySlot(world.random);
        if (slot < 0) {
            world.syncWorldEvent(WorldEvents.DISPENSER_FAILS, pos, 0);
            world.emitGameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Emitter.of(proxy.getCachedState()));
        } else {
            ItemStack picked = proxy.getStack(slot);
            DispenserBehavior behavior = behaviorFor(world, picked);
            if (behavior != DispenserBehavior.NOOP) {
                proxy.setStack(slot, behavior.dispense(pointer, picked));
            }
        }

        writeBackInventory(snapshot);
    }

    private DefaultedList<ItemStack> snapshotInventory() {
        DefaultedList<ItemStack> copy = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        DefaultedList<ItemStack> live = this.getInventory();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) copy.set(slot, live.get(slot));
        return copy;
    }

    private void writeBackInventory(DefaultedList<ItemStack> snapshot) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) this.setInventoryStack(slot, snapshot.get(slot));
    }

    private DispenserBehavior behaviorFor(World world, ItemStack stack) {
        if (!stack.isItemEnabled(world.getEnabledFeatures())) return DEFAULT_BEHAVIOR;
        DispenserBehavior registered = BEHAVIORS.get(stack.getItem());
        if (registered != null) return registered;
        if (stack.contains(DataComponentTypes.EQUIPPABLE)) return EquippableDispenserBehavior.INSTANCE;
        return DEFAULT_BEHAVIOR;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getEntityWorld().isClient()) return;
        if (cooldown > 0) cooldown--;
        setPowered(cooldown > 0);
    }

    @Override
    protected Item asItem() {
        return MinecartsOverhaul.DISPENSER_MINECART;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(MinecartsOverhaul.DISPENSER_MINECART);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putShort(NBT_COOLDOWN, (short) this.cooldown);
        view.putBoolean(NBT_FLIPPED, isFlipped());
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.cooldown = view.getShort(NBT_COOLDOWN, (short) 0);
        setFlipped(view.getBoolean(NBT_FLIPPED, false));
    }

    @Override
    public ScreenHandler getScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new Generic3x3ContainerScreenHandler(syncId, playerInventory, this);
    }
}
