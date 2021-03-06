package io.github.cottonmc.witchcraft.mixin;

import io.github.cottonmc.witchcraft.effect.WitchcraftEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MilkBucketItem.class)
public abstract class MixinMilkBucket extends Item {

	public MixinMilkBucket(Settings settings) {
		super(settings);
	}

	@Inject(method = "finishUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;clearPotionEffects()Z"), cancellable = true)
	public void removeBadOmenClear(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
		//TODO: put in config
		if (user.hasStatusEffect(WitchcraftEffects.FIZZLE)) {
			StatusEffectInstance badOmen = user.getStatusEffect(WitchcraftEffects.FIZZLE);
			user.clearStatusEffects();
			user.addStatusEffect(badOmen);
			cir.setReturnValue(stack.isEmpty() ? new ItemStack(Items.BUCKET) : stack);
		}
	}
}
