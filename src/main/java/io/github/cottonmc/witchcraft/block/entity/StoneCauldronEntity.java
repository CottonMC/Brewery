package io.github.cottonmc.witchcraft.block.entity;

import alexiil.mc.lib.attributes.fluid.FixedFluidInvView;
import alexiil.mc.lib.attributes.fluid.impl.SimpleFixedFluidInv;
import alexiil.mc.lib.attributes.fluid.volume.FluidVolume;
import io.github.cottonmc.witchcraft.block.WitchcraftBlocks;
import io.github.cottonmc.witchcraft.block.StoneCauldronBlock;
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.server.PlayerStream;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.packet.BlockEntityUpdateS2CPacket;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.Tickable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.List;
import java.util.stream.Collectors;

public class StoneCauldronEntity extends BlockEntity implements Tickable, BlockEntityClientSerializable {
	static VoxelShape ABOVE_SHAPE = Block.createCuboidShape(0.0D, 16.0D, 0.0D, 16.0D, 32.0D, 16.0D);

	public SimpleFixedFluidInv fluid = new SimpleFixedFluidInv(1, FluidVolume.BUCKET);
	public DefaultedList<ItemStack> previousItems = DefaultedList.create(8, ItemStack.EMPTY);

	public StoneCauldronEntity() {
		super(WitchcraftBlocks.STONE_CAULDRON_BE);
		fluid.addListener(this::listen, this::markDirty);
	}

	private void listen(FixedFluidInvView fixedFluidInvView, int i, FluidVolume fluidVolume, FluidVolume fluidVolume1) {
		markDirty();
	}

	public void craft() {
		for (ItemStack stack : previousItems) {
			stack.subtractAmount(1);
		}
		markDirty();
	}

	@Override
	public CompoundTag toTag(CompoundTag tag) {
		super.toTag(tag);
		tag.put("PreviousItems", Inventories.toTag(new CompoundTag(), previousItems));
		tag.put("Fluid", fluid.toTag());
		return tag;
	}

	@Override
	public void fromTag(CompoundTag tag) {
		super.fromTag(tag);
		Inventories.fromTag(tag.getCompound("PreviousItems"), previousItems);
		fluid.fromTag(tag.getCompound("Fluid"));
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (!this.world.isClient) {
			for (Object obj : PlayerStream.watching(this).toArray()) {
				ServerPlayerEntity player = (ServerPlayerEntity) obj;
				player.networkHandler.sendPacket(this.toUpdatePacket());
			}
		}
	}

	@Override
	public void tick() {
		if (world.isClient || fluid.getInvFluid(0).isEmpty()) return;
		List<ItemEntity> itemsAbove = getInputItemEntities();
		if (!itemsAbove.isEmpty()) {
			boolean soundPlayed = false;
			for (ItemEntity item : itemsAbove) {
				if (item.getScoreboardTags().contains("NoCauldronCollect")) continue;
				ItemStack stack = item.getStack();
				int index = StoneCauldronBlock.getLastFilledSlot(previousItems);
				if (index < 7) {
					if (!soundPlayed) {
						world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 1.0f, 1.0f);
						soundPlayed = true;
					}
					previousItems.set(index + 1, stack);
					item.remove();
				}
			}
			markDirty();
		}
	}

	public List<ItemEntity> getInputItemEntities() {
		VoxelShape inputShape = VoxelShapes.union(VoxelShapes.fullCube(), ABOVE_SHAPE);
		return inputShape.getBoundingBoxes().stream().flatMap((bb) -> world.getEntities(ItemEntity.class, bb.offset(pos.getX() - 0.5D, pos.getY() - 0.5D, pos.getZ() - 0.5D), EntityPredicates.VALID_ENTITY).stream()).collect(Collectors.toList());
	}

	@Override
	public void fromClientTag(CompoundTag tag) {
		this.fromTag(tag);
	}

	@Override
	public CompoundTag toClientTag(CompoundTag tag) {
		return this.toTag(tag);
	}
}
