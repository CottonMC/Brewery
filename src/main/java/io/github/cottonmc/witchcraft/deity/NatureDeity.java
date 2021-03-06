package io.github.cottonmc.witchcraft.deity;

import io.github.cottonmc.cotton.commons.CommonTags;
import io.github.cottonmc.witchcraft.Witchcraft;
import io.github.cottonmc.witchcraft.effect.WitchcraftEffects;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.tag.ItemTags;
import net.minecraft.tag.Tag;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class NatureDeity extends Deity {
	public NatureDeity() {
		super(DeityCharacter.FORGIVING, Formatting.GREEN);
	}

	@Override
	public Map<Stat<?>, Float> getFavors() {
		Map<Stat<?>, Float> ret = new HashMap<>();
		List<Item> plants = new ArrayList<>(ItemTags.SAPLINGS.values());
		plants.addAll(CommonTags.PLANTABLES.values());
		for (Item item : plants) {
			Stat stat = Stats.USED.getOrCreateStat(item);
			ret.put(stat, 0.01f);
		}
		for (EntityType<?> type : Witchcraft.RARE_PASSIVES.values()) {
			Stat<?> stat = Stats.KILLED.getOrCreateStat(type);
			ret.put(stat, -1f);
		}
		return ret;
	}

	@Override
	public String getNameSubkey(PlayerEntity player) {
		return null;
	}

	@Nullable
	@Override
	public Text getFavorMessage(PlayerEntity player, float currentFavor, float changeAmount, boolean intRollover) {
		if (intRollover) {
			if (currentFavor <= -40) {
				return new TranslatableText("msg.witchcraft.nature.kill", getName(player).asFormattedString());
			}
			if (changeAmount > 0) {
				return new TranslatableText("msg.witchcraft.favor.gain", getName(player).asFormattedString());
			} else if (changeAmount < 0) {
				return new TranslatableText("msg.witchcraft.favor.lose", getName(player).asFormattedString());
			}
		}
		return null;
	}

	@Override
	public void affectPlayer(PlayerEntity player, float currentFavor, float changeAmount, boolean intRollover) {
		if (changeAmount < 0) {
			player.addStatusEffect(new StatusEffectInstance(WitchcraftEffects.DECAY_TOUCH, 640, 0, true, false, true));
		}
	}

}
