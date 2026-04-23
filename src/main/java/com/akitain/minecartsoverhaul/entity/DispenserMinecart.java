package com.akitain.minecartsoverhaul.entity;

import com.akitain.minecartsoverhaul.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class DispenserMinecart extends net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer {

    private static final EntityDataAccessor<Boolean> DATA_FLIPPED =
            SynchedEntityData.defineId(DispenserMinecart.class, EntityDataSerializers.BOOLEAN);
    private static final int FIRE_COOLDOWN_TICKS = 8;
    private static final int CONTAINER_SIZE = 9;

    private int fireCooldown = 0;

    public DispenserMinecart(EntityType<? extends DispenserMinecart> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLIPPED, false);
    }

    public boolean isFlipped() {
        return this.entityData.get(DATA_FLIPPED);
    }

    public void setFlipped(boolean flipped) {
        this.entityData.set(DATA_FLIPPED, flipped);
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DispenserMenu(containerId, inventory, this);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.DISPENSER_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.DISPENSER_MINECART);
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.DISPENSER.defaultBlockState();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide()) {
                setFlipped(!isFlipped());
            }
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && fireCooldown > 0) {
            fireCooldown--;
        }
    }

    @Override
    public void activateMinecart(ServerLevel level, int x, int y, int z, boolean powered) {
        if (!powered || fireCooldown > 0) return;
        fireOneItem(level);
        fireCooldown = FIRE_COOLDOWN_TICKS;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Flipped", isFlipped());
        output.putInt("FireCooldown", fireCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setFlipped(input.getBooleanOr("Flipped", false));
        fireCooldown = input.getIntOr("FireCooldown", 0);
    }

    private void fireOneItem(ServerLevel level) {
        NonNullList<ItemStack> stacks = this.getItemStacks();
        int index = pickRandomNonEmptySlot(stacks);
        if (index < 0) return;

        ItemStack slot = stacks.get(index);
        ItemStack thrown = slot.split(1);

        float yawRadians = fireYawRadians();
        double dx = -Mth.sin(yawRadians);
        double dz = Mth.cos(yawRadians);
        ItemEntity projectile = new ItemEntity(
                level,
                getX() + dx * 0.6,
                getY() + 0.4,
                getZ() + dz * 0.6,
                thrown
        );
        projectile.setDeltaMovement(dx * 0.3, 0.1, dz * 0.3);
        level.addFreshEntity(projectile);
    }

    private int pickRandomNonEmptySlot(NonNullList<ItemStack> stacks) {
        int count = 0;
        int pick = -1;
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) continue;
            count++;
            if (this.random.nextInt(count) == 0) pick = i;
        }
        return pick;
    }

    private float fireYawRadians() {
        float yaw = getYRot() + (isFlipped() ? 180.0F : 0.0F);
        return yaw * (float) (Math.PI / 180.0);
    }
}
