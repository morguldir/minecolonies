package com.minecolonies.api.inventory.container;

import com.minecolonies.api.inventory.ModContainers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.minecolonies.api.util.constant.InventoryConstants.*;

/**
 * Crafting container for the recipe teaching of normal crafting recipes.
 */
public class ContainerCrafting extends AbstractContainerMenu
{
    /**
     * The crafting matrix inventory (2x2).
     */
    public final CraftingContainer craftMatrix;

    /**
     * The crafting matrix inventory (2x2).
     */
    public final ResultContainer craftResult = new ResultContainer();

    /**
     * The crafting result slot.
     */
    private final Slot craftResultSlot;

    /**
     * The secondary outputs
     */
    private final List<ItemStack> remainingItems;

    /**
     * Boolean variable defining if complete grid or not 3x3(true), 2x2(false).
     */
    private final boolean complete;

    /**
     * World world
     */
    private final Level world;

    /**
     * The player inventory.
     */
    private final Inventory inv;

    /**
     * Position of container.
     */
    private final BlockPos pos;

    /**
     * The module id of the container.
     */
    private final String moduleId;

    /**
     * Deserialize packet buffer to container instance.
     *
     * @param windowId     the id of the window.
     * @param inv          the player inventory.
     * @param packetBuffer network buffer
     * @return new instance
     */
    public static ContainerCrafting fromFriendlyByteBuf(final int windowId, final Inventory inv, final FriendlyByteBuf packetBuffer)
    {
        final boolean complete = packetBuffer.readBoolean();
        final BlockPos tePos = packetBuffer.readBlockPos();
        final String moduleId = packetBuffer.readUtf(32767);
        return new ContainerCrafting(windowId, inv, complete, tePos, moduleId);
    }

    /**
     * Creates a crafting container.
     *
     * @param windowId the window id.
     * @param inv      the inventory.
     * @param moduleId the module id.
     */
    public ContainerCrafting(final int windowId, final Inventory inv, final boolean complete, final BlockPos pos, final String moduleId)
    {
        super(ModContainers.craftingGrid, windowId);
        this.moduleId = moduleId;
        this.world = inv.player.level;
        this.inv = inv;
        this.complete = complete;
        this.pos = pos;
        if (complete)
        {
            craftMatrix = new CraftingContainer(this, 3, 3);
        }
        else
        {
            craftMatrix = new CraftingContainer(this, 2, 2);
        }

        this.craftResultSlot = this.addSlot(new ResultSlot(inv.player, this.craftMatrix, craftResult, 0, X_CRAFT_RESULT, Y_CRAFT_RESULT)
        {
            @Override
            public boolean mayPickup(final Player playerIn)
            {
                return false;
            }
        });

        for (int i = 0; i < craftMatrix.getWidth(); ++i)
        {
            for (int j = 0; j < craftMatrix.getHeight(); ++j)
            {
                this.addSlot(new Slot(this.craftMatrix, j + i * (complete ? 3 : 2), X_OFFSET_CRAFTING + j * INVENTORY_OFFSET_EACH, Y_OFFSET_CRAFTING + i * INVENTORY_OFFSET_EACH)
                {
                    @Override
                    public int getMaxStackSize()
                    {
                        return 1;
                    }

                    @NotNull
                    @Override
                    public ItemStack remove(final int par1)
                    {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean mayPlace(final ItemStack par1ItemStack)
                    {
                        return true;
                    }

                    @Override
                    public boolean mayPickup(final Player par1PlayerEntity)
                    {
                        return false;
                    }
                });
            }
        }

        // Player inventory slots
        // Note: The slot numbers are within the player inventory and may be the same as the field inventory.
        int i;
        for (i = 0; i < INVENTORY_ROWS; i++)
        {
            for (int j = 0; j < INVENTORY_COLUMNS; j++)
            {
                addSlot(new Slot(
                  inv,
                  j + i * INVENTORY_COLUMNS + INVENTORY_COLUMNS,
                  PLAYER_INVENTORY_INITIAL_X_OFFSET + j * PLAYER_INVENTORY_OFFSET_EACH,
                  PLAYER_INVENTORY_INITIAL_Y_OFFSET_CRAFTING + i * PLAYER_INVENTORY_OFFSET_EACH
                ));
            }
        }

        for (i = 0; i < INVENTORY_COLUMNS; i++)
        {
            addSlot(new Slot(
              inv, i,
              PLAYER_INVENTORY_INITIAL_X_OFFSET + i * PLAYER_INVENTORY_OFFSET_EACH,
              PLAYER_INVENTORY_HOTBAR_OFFSET_CRAFTING
            ));
        }

        
        remainingItems = new ArrayList<>();

        this.slotsChanged(this.craftMatrix);
    }

    /**
     * Callback for when the crafting matrix is changed.
     */
    @Override
    public void slotsChanged(final Container inventoryIn)
    {
        if (!world.isClientSide)
        {
            final ServerPlayer player = (ServerPlayer) inv.player;
            final Optional<CraftingRecipe> iRecipe = ((ServerPlayer) inv.player).server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftMatrix, world);
            final ItemStack stack;
            if (iRecipe.isPresent() && (iRecipe.get().isSpecial()
                                          || !world.getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)
                                          || player.getRecipeBook().contains(iRecipe.get())
                                          || player.isCreative()))
            {
                stack = iRecipe.get().assemble(this.craftMatrix);
                this.craftResultSlot.set(stack);
                player.connection.send(new ClientboundContainerSetSlotPacket(this.containerId, 0, 0, stack));
            }
            else
            {
                this.craftResultSlot.set(ItemStack.EMPTY);
                player.connection.send(new ClientboundContainerSetSlotPacket(this.containerId, 0, 0, ItemStack.EMPTY));
            }
        }

        super.slotsChanged(inventoryIn);
    }

    @Override
    public boolean stillValid(@NotNull final Player playerIn)
    {
        return true;
    }

    @Override
    public void clicked(final int slotId, final int clickedButton, final @NotNull ClickType mode, final @NotNull Player playerIn)
    {
        if (slotId >= 1 && slotId < CRAFTING_SLOTS + (complete ? ADDITIONAL_SLOTS : 0))
        {
            // 1 is shift-click
            if (mode == ClickType.PICKUP
                  || mode == ClickType.PICKUP_ALL
                  || mode == ClickType.SWAP)
            {
                final Slot slot = this.slots.get(slotId);
                handleSlotClick(slot, this.getCarried());
                return;
            }

            return;
        }

        if (mode == ClickType.QUICK_MOVE)
        {
            return;
        }

        super.clicked(slotId, clickedButton, mode, playerIn);
    }

    /**
     * Handle a slot click.
     *
     * @param slot  the clicked slot.
     * @param stack the used stack.
     * @return the result.
     */
    public ItemStack handleSlotClick(final Slot slot, final ItemStack stack)
    {
        if (stack.getCount() > 0)
        {
            final ItemStack copy = stack.copy();
            copy.setCount(1);
            slot.set(copy);
        }
        else if (slot.getItem().getCount() > 0)
        {
            slot.set(ItemStack.EMPTY);
        }

        return slot.getItem().copy();
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(final Player playerIn, final int index)
    {
        final int total_crafting_slots = CRAFTING_SLOTS + (complete ? ADDITIONAL_SLOTS : 0);
        if (index <= total_crafting_slots)
        {
            return ItemStack.EMPTY;
        }

        final int total_slots = TOTAL_SLOTS + (complete ? ADDITIONAL_SLOTS : 0);

        ItemStack itemstack = ItemStack.EMPTY;
        final Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem())
        {
            final ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index == 0)
            {
                if (!this.moveItemStackTo(itemstack1, total_crafting_slots, total_slots, true))
                {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemstack1, itemstack);
            }
            else if (index < HOTBAR_START)
            {
                if (!this.moveItemStackTo(itemstack1, HOTBAR_START, total_slots, false))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if ((index < total_slots
                        && !this.moveItemStackTo(itemstack1, total_crafting_slots, HOTBAR_START, false))
                       || !this.moveItemStackTo(itemstack1, total_crafting_slots, total_slots, false))
            {
                return ItemStack.EMPTY;
            }
            if (itemstack1.getCount() == 0)
            {
                slot.set(ItemStack.EMPTY);
            }
            else
            {
                slot.setChanged();
            }
            if (itemstack1.getCount() == itemstack.getCount())
            {
                return ItemStack.EMPTY;
            }
        }
        return itemstack;
    }

    @Override
    public boolean canTakeItemForPickAll(final ItemStack stack, final Slot slotIn)
    {
        return slotIn != this.craftResultSlot && super.canTakeItemForPickAll(stack, slotIn);
    }

    /**
     * Getter for the world obj.
     *
     * @return the world obj.
     */
    public Level getWorldObj()
    {
        return world;
    }

    /**
     * Getter for the player.
     *
     * @return the player.
     */
    public Player getPlayer()
    {
        return inv.player;
    }

    /**
     * Getter for completeness.
     *
     * @return true if 3x3 and false for 2x2.
     */
    public boolean isComplete()
    {
        return complete;
    }

    /**
     * Get the craft matrix inv.
     *
     * @return the inv.
     */
    public CraftingContainer getInv()
    {
        return craftMatrix;
    }

    /**
     * Get for the container position.
     *
     * @return the position.
     */
    public BlockPos getPos()
    {
        return pos;
    }

    /**
     * Get for the remaining items. 
     * @return
     */
    public List<ItemStack> getRemainingItems()
    {
        final Optional<CraftingRecipe> iRecipe = this.world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftMatrix, world);
        if (iRecipe.isPresent())
        {
            List<ItemStack> ri = iRecipe.get().getRemainingItems(this.craftMatrix);
            remainingItems.clear();
            for(int i = 0; i< ri.size(); i++)
            {
                if(!ri.get(i).isEmpty())
                {
                    remainingItems.add(ri.get(i));
                }
            }
        }
        return remainingItems;
    }

    /**
     * Getter for the module id.
     * @return the id.
     */
    public String getModuleId()
    {
        return this.moduleId;
    }
}
