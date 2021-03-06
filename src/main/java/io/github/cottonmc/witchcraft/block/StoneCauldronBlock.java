package io.github.cottonmc.witchcraft.block;

import alexiil.mc.lib.attributes.AttributeList;
import alexiil.mc.lib.attributes.AttributeProvider;
import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.fluid.FluidAttributes;
import alexiil.mc.lib.attributes.fluid.amount.FluidAmount;
import alexiil.mc.lib.attributes.fluid.volume.FluidVolume;
import io.github.cottonmc.witchcraft.component.WitchcraftFluidVolume;
import io.github.cottonmc.witchcraft.item.WitchcraftItems;
import io.github.cottonmc.witchcraft.recipe.CauldronInventoryWrapper;
import io.github.cottonmc.witchcraft.recipe.CauldronRecipe;
import io.github.cottonmc.witchcraft.recipe.WitchcraftRecipes;
import io.github.cottonmc.witchcraft.block.entity.StoneCauldronEntity;
import io.github.cottonmc.cotton.cauldron.Cauldron;
import io.github.cottonmc.cotton.cauldron.CauldronBehavior;
import io.github.cottonmc.cotton.cauldron.CauldronContext;
import net.fabricmc.fabric.api.block.FabricBlockSettings;
import net.fabricmc.fabric.api.tools.FabricToolTags;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BooleanBiFunction;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Predicate;

public class StoneCauldronBlock extends BlockWithEntity implements AttributeProvider, Cauldron {

	public static final VoxelShape RAY_TRACE_SHAPE = createCuboidShape(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D);
	public static final VoxelShape OUTLINE_SHAPE = VoxelShapes.combineAndSimplify(VoxelShapes.fullCube(),
			VoxelShapes.union(createCuboidShape(0.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D),
					createCuboidShape(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 16.0D),
					createCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D), RAY_TRACE_SHAPE), BooleanBiFunction.ONLY_FIRST);

	public StoneCauldronBlock() {
		super(FabricBlockSettings.of(Material.STONE).breakByTool(FabricToolTags.PICKAXES).strength(6.0f, 6.0f).build());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, EntityContext entityPos) {
		return OUTLINE_SHAPE;
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public VoxelShape getRayTraceShape(BlockState state, BlockView view, BlockPos pos) {
		return RAY_TRACE_SHAPE;
	}

	@Override
	public BlockEntity createBlockEntity(BlockView view) {
		return new StoneCauldronEntity();
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient() || !(world.getBlockEntity(pos) instanceof StoneCauldronEntity)) return ActionResult.SUCCESS;
		ItemStack stack = player.getStackInHand(hand);
		StoneCauldronEntity cauldron = (StoneCauldronEntity) world.getBlockEntity(pos);
		FluidVolume fluid = cauldron.fluid.getInvFluid(0);
		if (stack.isEmpty() && !(cauldron.previousItems.isEmpty()) && player.isSneaking()) {
			int index = getLastFilledSlot(cauldron.previousItems);
			if (index != -1) {
				player.setStackInHand(hand, cauldron.previousItems.get(index));
				cauldron.previousItems.set(index, ItemStack.EMPTY);
			}
			return ActionResult.SUCCESS;
		}
		Item item = stack.getItem();
		if (item instanceof BucketItem) {
			if (item == Items.BUCKET && fluid.getAmount_F().isGreaterThan(FluidAmount.BUCKET)) {
				if (!player.isCreative()) {
					FluidAttributes.INSERTABLE.get(stack).insert(fluid);
					player.setStackInHand(hand, stack);
				}
				drain(world, pos, state, fluid.getRawFluid(), 3);
				SoundEvent event = fluid.getRawFluid() == Fluids.LAVA? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL;
				world.playSound(null, pos, event, SoundCategory.BLOCKS, 1.0f, 1.0f);
				return ActionResult.SUCCESS;
			}
			else if (fluid.isEmpty()) {
				if (!player.isCreative()) player.setStackInHand(hand, new ItemStack(Items.BUCKET));
				cauldron.fluid.setInvFluid(0, FluidAttributes.EXTRACTABLE.get(stack).extract(FluidAmount.BUCKET), Simulation.ACTION);
				SoundEvent event = item == Items.LAVA_BUCKET? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY;
				world.playSound(null, pos, event, SoundCategory.BLOCKS, 1.0f, 1.0f);
				return ActionResult.SUCCESS;
			}
		}
		if (world.getBlockState(pos.down()).getBlock() == Blocks.CAMPFIRE && !fluid.isEmpty()) {
			if (fluid.getRawFluid() == Fluids.WATER) {
				if (stack.getItem() == WitchcraftItems.BROOMSTICK) {
					if (fluid.getAmount_F().isGreaterThan(FluidAmount.BOTTLE)) {
						CauldronInventoryWrapper wrapper = new CauldronInventoryWrapper(cauldron.previousItems, WitchcraftRecipes.isFireUnder(world, pos));
						Optional<CauldronRecipe> opt = world.getRecipeManager().getFirstMatch(WitchcraftRecipes.CAULDRON, wrapper, world);
						if (opt.isPresent()) {
							world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA, SoundCategory.BLOCKS, 1.0f, 1.0f);
							CauldronRecipe recipe = opt.get();
							ItemStack result = recipe.craft(wrapper);
							drain(world, pos, state, Fluids.WATER, 1);
							cauldron.craft();
							player.increaseStat(Stats.USE_CAULDRON, 1);
							if (!player.inventory.insertStack(result)) {
								ItemEntity entity = player.dropItem(result, false);
								if (entity != null) entity.addScoreboardTag("NoCauldronCollect");
								world.spawnEntity(entity);
							}
						}
					}
				} else if (!stack.isEmpty()) {
					if (cauldron.addItem(stack)) stack.setCount(0);
				}
			}
			return ActionResult.SUCCESS;
		}
		CauldronContext ctx = new CauldronContext(world, pos, state, fluid.getAmount_F().asInt(3), fluid.getRawFluid(), cauldron.previousItems, player, hand, player.getStackInHand(hand));
		for (Predicate<CauldronContext> pred : CauldronBehavior.BEHAVIORS.keySet()) {
			if (pred.test(ctx)) {
				CauldronBehavior behavior = CauldronBehavior.BEHAVIORS.get(pred);
				behavior.react(ctx);
				cauldron.craft();
				return ActionResult.SUCCESS;
			}
		}
		return super.onUse(state, world, pos, player, hand, hit);
	}

	@Override
	public boolean fill(World world, BlockPos pos, BlockState state, Fluid fluid, int bottles) {
		StoneCauldronEntity cauldron = (StoneCauldronEntity)world.getBlockEntity(pos);
		FluidVolume vol = cauldron.fluid.getInvFluid(0);
		//copy fluid volume because it's mutable
		FluidAmount sum = FluidAmount.of(vol.getAmount_F().whole, vol.getAmount_F().numerator, vol.getAmount_F().denominator);
		sum.add(FluidAmount.of(bottles, 3));
		if (vol.getRawFluid().equals(fluid) && sum.isLessThanOrEqual(FluidAmount.BUCKET)) {
			vol.merge(new WitchcraftFluidVolume(vol.getFluidKey(), FluidAmount.of(bottles, 3)), Simulation.ACTION);
			cauldron.markDirty();
			return true;
		}
		return false;
	}

	@Override
	public boolean drain(World world, BlockPos pos, BlockState state, Fluid fluid, int bottles) {
		int amount = FluidVolume.BOTTLE * bottles;
		StoneCauldronEntity cauldron = (StoneCauldronEntity)world.getBlockEntity(pos);
		FluidVolume vol = cauldron.fluid.getInvFluid(0);
			vol.split(amount);
			cauldron.markDirty();
			return true;
	}

	@Override
	public boolean canAcceptFluid(World world, BlockPos pos, BlockState state, Fluid fluid) {
		StoneCauldronEntity cauldron = (StoneCauldronEntity)world.getBlockEntity(pos);
		FluidVolume vol = cauldron.fluid.getInvFluid(0);
		if (vol.getRawFluid() == null) return false;
		return vol.getRawFluid().equals(fluid);
	}

	@Override
	public CauldronContext createContext(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
		BlockState state = world.getBlockState(pos);
		StoneCauldronEntity cauldron = (StoneCauldronEntity) world.getBlockEntity(pos);
		FluidVolume fluid = cauldron.fluid.getInvFluid(0);
		Hand hand;
		if (player == null) hand = null;
		else hand = player.getActiveHand();
		return new CauldronContext(world, pos, state, fluid.getAmount_F().asInt(3), fluid.getRawFluid(), cauldron.previousItems, player, hand, stack);
	}

	public static int getLastFilledSlot(DefaultedList<ItemStack> slots) {
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).isEmpty()) return i - 1;
		}
		return slots.size() - 1;
	}

	@Override
	public void addAllAttributes(World world, BlockPos pos, BlockState state, AttributeList<?> to) {
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof StoneCauldronEntity) {
			StoneCauldronEntity cauldron = (StoneCauldronEntity) be;
			to.offer(cauldron.fluid, OUTLINE_SHAPE);
			to.offer(cauldron.fluid.getInsertable(), OUTLINE_SHAPE);
			to.offer(cauldron.fluid.getExtractable(), OUTLINE_SHAPE);
			// cauldron.addAttributes(to);
		}
	}
}
