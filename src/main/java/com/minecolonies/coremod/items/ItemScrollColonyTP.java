package com.minecolonies.coremod.items;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.network.messages.client.VanillaParticleMessage;
import com.minecolonies.coremod.util.TeleportHelper;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;

import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_DESC;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item.Properties;

/**
 * Teleport scroll to teleport you back to the set colony. Requires colony permissions
 */
public class ItemScrollColonyTP extends AbstractItemScroll
{
    /**
     * Sets the name, creative tab, and registers the item.
     *
     * @param properties the properties.
     */
    public ItemScrollColonyTP(final Properties properties)
    {
        super("scroll_tp", properties);
    }

    @Override
    protected ItemStack onItemUseSuccess(final ItemStack itemStack, final Level world, final ServerPlayer player)
    {
        if (world.random.nextInt(10) == 0)
        {
            // Fail
            player.displayClientMessage(new TranslatableComponent("minecolonies.scroll.failed" + (world.random.nextInt(FAIL_RESPONSES_TOTAL) + 1)).setStyle(Style.EMPTY.withColor(
              ChatFormatting.GOLD)), true);

            BlockPos pos = null;
            for (final Direction dir : Direction.Plane.HORIZONTAL)
            {
                pos = BlockPosUtil.findAround(world,
                  player.blockPosition().relative(dir, 10),
                  5,
                  5,
                  (predWorld, predPos) -> predWorld.getBlockState(predPos).getMaterial() == Material.AIR && predWorld.getBlockState(predPos.above()).getMaterial() == Material.AIR);
                if (pos != null)
                {
                    break;
                }
            }

            if (pos != null)
            {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, TICKS_SECOND * 7));
                player.teleportTo((ServerLevel) world, pos.getX(), pos.getY(), pos.getZ(), player.getYRot(), player.getXRot());
            }

            SoundUtils.playSoundForPlayer(player, SoundEvents.BAT_TAKEOFF, 0.4f, 1.0f);
        }
        else
        {
            // Success
            doTeleport(player, getColony(itemStack), itemStack);
            SoundUtils.playSoundForPlayer(player, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6f, 1.0f);
        }

        itemStack.shrink(1);
        return itemStack;
    }

    @Override
    protected boolean needsColony()
    {
        return true;
    }

    /**
     * Does the teleport action
     *
     * @param player user of the item
     * @param colony colony to teleport to
     */
    protected void doTeleport(final ServerPlayer player, final IColony colony, final ItemStack stack)
    {
        TeleportHelper.colonyTeleport(player, colony);
    }

    @Override
    public void onUseTick(Level worldIn, LivingEntity entity, ItemStack stack, int count)
    {
        if (!worldIn.isClientSide && worldIn.getGameTime() % 5 == 0)
        {
            Network.getNetwork()
              .sendToTrackingEntity(new VanillaParticleMessage(entity.getX(), entity.getY(), entity.getZ(), ParticleTypes.INSTANT_EFFECT),
                entity);
            Network.getNetwork()
              .sendToPlayer(new VanillaParticleMessage(entity.getX(), entity.getY(), entity.getZ(), ParticleTypes.INSTANT_EFFECT),
                (ServerPlayer) entity);
        }
    }

    @Override
    public void appendHoverText(
      @NotNull final ItemStack stack, @Nullable final Level worldIn, @NotNull final List<Component> tooltip, @NotNull final TooltipFlag flagIn)
    {
        final MutableComponent guiHint = new TranslatableComponent(TOOL_COLONY_TELEPORT_SCROLL_DESCRIPTION);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GREEN));
        tooltip.add(guiHint);

        Component colonyDesc = new TranslatableComponent(TOOL_COLONY_TELEPORT_SCROLL_NO_COLONY);

        if (stack.getOrCreateTag().contains(TAG_DESC))
        {
            colonyDesc = new TextComponent(stack.getOrCreateTag().getString(TAG_DESC));
        }
        else
        {
            final IColony colony = getColonyView(stack);
            if (colony != null)
            {
                colonyDesc = new TextComponent(colony.getName());
                stack.getOrCreateTag().putString(TAG_DESC, colony.getName());
            }
        }

        final MutableComponent guiHint2 = new TranslatableComponent(TOOL_COLONY_TELEPORT_SCROLL_COLONY_NAME, colonyDesc);
        guiHint2.setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
        tooltip.add(guiHint2);
    }
}
