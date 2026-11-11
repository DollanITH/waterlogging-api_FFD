package dev.xy177.waterlogged.common;

import javax.annotation.Nullable;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;

public final class WaterloggedBucketHandler {
    private WaterloggedBucketHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFillBucket(FillBucketEvent event) {
        RayTraceResult target = event.getTarget();
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = containedSourceBeforeHit(event.getEntityPlayer(), event.getWorld(), target);
        if (pos == null) pos = target.getBlockPos();
        IBlockState state = event.getWorld().getBlockState(pos);
        IBlockState dryState = withWaterlogged(state, false);
        boolean stored = FFDStoredFluidStates.has(event.getWorld(), pos);
        if (stored && dryState == null) {
            dryState = state;
        }
        Fluid fluid = WaterloggedBlockApi.getContainedFluid(event.getWorld(), pos);
        if (dryState == null || fluid == null
                || WaterloggedBlockApi.getSourceFluid(event.getWorld(), pos) == null) {
            return;
        }

        FluidActionResult filled = FluidUtil.tryFillContainer(event.getEmptyBucket(),
                new FluidTank(new FluidStack(fluid, Fluid.BUCKET_VOLUME), Fluid.BUCKET_VOLUME),
                Fluid.BUCKET_VOLUME, null, true);
        if (!filled.isSuccess()) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        EnumFacing side = target.sideHit == null ? EnumFacing.UP : target.sideHit;
        if (!event.getWorld().isBlockModifiable(player, pos)
                || !player.canPlayerEdit(pos.offset(side), side, event.getEmptyBucket())) {
            event.setCanceled(true);
            return;
        }

        // Clear the host only after the state write (or stored-fluid removal for
        // an additional host) succeeds.  Consuming the bucket before this check
        // can lose the fluid when a client/server state update is rejected.
        if (!WaterloggedBlockApi.setDryState(event.getWorld(), pos, state, 11)) {
            return;
        }
        player.addStat(StatList.getObjectUseStats(Items.BUCKET));
        player.playSound(fluid.getFillSound(new FluidStack(fluid, Fluid.BUCKET_VOLUME)),
                1.0F, 1.0F);
        event.setFilledBucket(filled.getResult());
        event.setResult(Event.Result.ALLOW);
    }

    /** ItemBucket traces liquids independently of the block click.  If its ray
     * passes through the open part of a wet host, it can otherwise collect a
     * vanilla source behind the host and leave the contained source intact. */
    @Nullable
    private static BlockPos containedSourceBeforeHit(EntityPlayer player,
            net.minecraft.world.World world, RayTraceResult hit) {
        if (player == null || hit.hitVec == null) return null;
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d delta = hit.hitVec.subtract(eye);
        double length = Math.min(Math.sqrt(delta.x * delta.x + delta.y * delta.y
                + delta.z * delta.z), 6.0D);
        if (length <= 0.0D) return null;
        Vec3d step = delta.normalize();
        BlockPos previous = null;
        for (double distance = 0.0D; distance < length; distance += 1.0D / 32.0D) {
            BlockPos pos = new BlockPos(eye.x + step.x * distance,
                    eye.y + step.y * distance, eye.z + step.z * distance);
            if (pos.equals(previous)) continue;
            previous = pos;
            if (WaterloggedBlockApi.isWaterlogged(world, pos)
                    && WaterloggedBlockApi.getSourceFluid(world, pos) != null) {
                return pos;
            }
        }
        return null;
    }

    /** A liquid ray can hit flowing water in front of the host.  The item-use
     * path must still target the solid block under the crosshair when it is
     * itself waterlogged.  This also covers clicks whose block interaction
     * was skipped by the client because the liquid was hit first. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEmptyBucketItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != Items.BUCKET) return;
        EntityPlayer player = event.getEntityPlayer();
        if (player.capabilities.isCreativeMode || player.isSpectator()) return;
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE)
                .getAttributeValue();
        RayTraceResult hit = event.getWorld().rayTraceBlocks(eye,
                eye.add(look.x * reach, look.y * reach, look.z * reach),
                false, false, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (!WaterloggedBlockApi.isWaterlogged(event.getWorld(), pos)
                || WaterloggedBlockApi.getSourceFluid(event.getWorld(), pos) == null) return;
        IBlockState state = event.getWorld().getBlockState(pos);
        Fluid fluid = WaterloggedBlockApi.getSourceFluid(event.getWorld(), pos);
        FluidStack source = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
        FluidActionResult filled = FluidUtil.tryFillContainer(stack,
                new FluidTank(source, Fluid.BUCKET_VOLUME),
                Fluid.BUCKET_VOLUME, null, true);
        if (!filled.isSuccess()) return;
        EnumFacing side = hit.sideHit == null ? EnumFacing.UP : hit.sideHit;
        if (!event.getWorld().isBlockModifiable(player, pos)
                || !player.canPlayerEdit(pos.offset(side), side, stack)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (!WaterloggedBlockApi.setDryState(event.getWorld(), pos, state, 11)) {
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        if (!event.getWorld().isRemote) {
            player.addStat(StatList.getObjectUseStats(Items.BUCKET));
            player.playSound(fluid.getFillSound(source), 1.0F, 1.0F);
            consumeFluidContainer(player, event.getHand(), stack, filled.getResult());
        }
    }

    /** The block-click packet carries the actual host position.  ItemBucket's
     * later liquid ray trace can instead select adjacent water and leave the
     * contained source in the host. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEmptyBucketClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != Items.BUCKET) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!WaterloggedBlockApi.isWaterlogged(event.getWorld(), pos)) {
            return;
        }
        IBlockState state = event.getWorld().getBlockState(pos);
        Fluid fluid = WaterloggedBlockApi.getSourceFluid(event.getWorld(), pos);
        if (fluid == null) {
            return;
        }
        FluidStack source = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
        FluidActionResult filled = FluidUtil.tryFillContainer(stack,
                new FluidTank(source, Fluid.BUCKET_VOLUME),
                Fluid.BUCKET_VOLUME, null, true);
        if (!filled.isSuccess()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        EnumFacing side = event.getFace() == null ? EnumFacing.UP : event.getFace();
        if (!event.getWorld().isBlockModifiable(player, pos)
                || !player.canPlayerEdit(pos.offset(side), side, stack)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (!WaterloggedBlockApi.setDryState(event.getWorld(), pos, state, 11)) {
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        // ItemBucket predicts removal on the client as well.  The server's
        // block-change packet remains authoritative if the interaction fails.
        if (event.getWorld().isRemote) {
            return;
        }
        player.addStat(StatList.getObjectUseStats(Items.BUCKET));
        player.playSound(fluid.getFillSound(source), 1.0F, 1.0F);
        consumeFluidContainer(event, filled.getResult());
    }

    @SubscribeEvent
    public static void onWaterBucketUse(PlayerInteractEvent.RightClickBlock event) {
        FluidStack contained = FluidUtil.getFluidContained(event.getItemStack());
        if (contained == null || contained.amount < Fluid.BUCKET_VOLUME
                || !WaterloggedConfig.isFluidAllowed(event.getWorld(), contained.getFluid())) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        EnumFacing side = event.getFace() == null ? EnumFacing.UP : event.getFace();
        IBlockState state = event.getWorld().getBlockState(clickedPos);
        BlockPos targetPos = clickedPos;
        IBlockState wetState = withWaterlogged(state, true);
        if (wetState == null) {
            if (tryExtraFluidBucket(event, clickedPos, contained, side)) {
                return;
            }
            boolean replaceable = state.getBlock().isReplaceable(event.getWorld(), clickedPos);
            targetPos = replaceable && side == EnumFacing.UP
                    ? clickedPos : clickedPos.offset(side);
            wetState = withWaterlogged(
                    event.getWorld().getBlockState(targetPos), true);
            if (wetState == null && tryExtraFluidBucket(event, targetPos, contained, side)) {
                return;
            }
        }
        if (wetState == null) {
            return;
        }

        ItemStack drainedContainer = null;
        if (!event.getEntityPlayer().capabilities.isCreativeMode) {
            drainedContainer = drainFluidContainer(event.getItemStack(), contained);
            if (drainedContainer == null) {
                return;
            }
        }

        if (!event.getWorld().isBlockModifiable(event.getEntityPlayer(), targetPos)
                || !event.getEntityPlayer().canPlayerEdit(targetPos, side,
                        event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (event.getWorld().isRemote) {
            return;
        }
        if (!event.getWorld().setBlockState(targetPos, wetState, 11)) {
            return;
        }

        Fluid fluid = contained.getFluid();
        WaterloggedBlockApi.recordContainedFluid(event.getWorld(), targetPos, fluid);
        event.getWorld().scheduleUpdate(targetPos, wetState.getBlock(),
                WaterloggedBlockApi.fluidTickRate(event.getWorld(), fluid));
        event.getEntityPlayer().addStat(
                StatList.getObjectUseStats(event.getItemStack().getItem()));
        consumeFluidContainer(event, drainedContainer);
        event.getWorld().playSound(null, targetPos, fluid.getEmptySound(contained),
                SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /**
     * Preserve a source fluid when a normal block placement replaces it.  The
     * World mixin handles vanilla's setBlockState path; this event covers
     * placement implementations that write the chunk first and only then
     * publish PlaceEvent.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidloggedBlockPlace(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote
                || !WaterloggedBlockApi.isWaterlogged(event.getState())) {
            return;
        }
        IBlockState replaced = event.getBlockSnapshot().getReplacedBlock();
        Fluid fluid = WaterloggedBlockApi.getFluidForBlock(replaced.getBlock());
        if (fluid != null
                && WaterloggedConfig.isFluidAllowed(event.getWorld(), fluid)
                && dev.xy177.waterlogged.common.block.WaterloggedPlantFluid
                        .isSourceFluid(replaced)) {
            WaterloggedBlockApi.recordContainedFluid(event.getWorld(), event.getPos(), fluid);
        }
    }

    private static boolean tryExtraFluidBucket(PlayerInteractEvent.RightClickBlock event,
            BlockPos pos, FluidStack fluid, EnumFacing side) {
        IBlockState state = event.getWorld().getBlockState(pos);
        if (!WaterloggedConfig.isAdditionalWaterloggingBlock(
                event.getWorld(), state.getBlock())
                || WaterloggedBlockApi.isWaterlogged(event.getWorld(), pos)) {
            return false;
        }
        if (!event.getWorld().isBlockModifiable(event.getEntityPlayer(), pos)
                || !event.getEntityPlayer().canPlayerEdit(pos, side, event.getItemStack())) {
            return false;
        }

        ItemStack emptied = null;
        if (!event.getEntityPlayer().capabilities.isCreativeMode) {
            emptied = drainFluidContainer(event.getItemStack(), fluid);
            if (emptied == null) {
                return false;
            }
        }

        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (!event.getWorld().isRemote) {
            FFDStoredFluidStates.set(event.getWorld(), pos,
                    WaterloggedBlockApi.sourceState(fluid.getFluid()));
            event.getWorld().scheduleUpdate(pos, state.getBlock(),
                    WaterloggedBlockApi.fluidTickRate(event.getWorld(), fluid.getFluid()));
            event.getWorld().notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            consumeFluidContainer(event, emptied);
            event.getWorld().playSound(null, pos, fluid.getFluid().getEmptySound(fluid),
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    private static ItemStack drainFluidContainer(ItemStack stack, FluidStack fluid) {
        ItemStack single = stack.copy();
        single.setCount(1);
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(single);
        if (handler == null) {
            return null;
        }
        FluidStack requested = new FluidStack(fluid.getFluid(), Fluid.BUCKET_VOLUME);
        FluidStack simulated = handler.drain(requested, false);
        if (simulated == null || simulated.amount != Fluid.BUCKET_VOLUME
                || !simulated.isFluidEqual(requested)) {
            return null;
        }
        FluidStack drained = handler.drain(requested, true);
        return drained != null && drained.amount == Fluid.BUCKET_VOLUME
                && drained.isFluidEqual(requested) ? handler.getContainer() : null;
    }

    private static void consumeFluidContainer(PlayerInteractEvent.RightClickBlock event,
                                               @Nullable ItemStack result) {
        consumeFluidContainer(event.getEntityPlayer(), event.getHand(),
                event.getItemStack(), result);
    }

    private static void consumeFluidContainer(EntityPlayer player, EnumHand hand,
                                               ItemStack held, @Nullable ItemStack result) {
        if (player.capabilities.isCreativeMode) {
            return;
        }
        held.shrink(1);
        if (held.isEmpty()) {
            player.setHeldItem(hand, result == null ? ItemStack.EMPTY : result);
        } else if (result != null && !result.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, result);
        }
    }

    private static IBlockState withWaterlogged(IBlockState state, boolean waterlogged) {
        IBlockState result = WaterloggedBlockApi.withWaterlogged(state, waterlogged);
        return result == null || WaterloggedBlockApi.isWaterlogged(state) == waterlogged
                ? null : result;
    }
}
