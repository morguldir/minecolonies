package com.minecolonies.coremod.client.gui.modules;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.MateriallyTexturedBlockManager;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.client.gui.AbstractModuleWindow;
import com.minecolonies.coremod.client.gui.WindowSelectRes;
import com.minecolonies.coremod.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.coremod.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.util.constant.WindowConstants.*;

/**
 * Cook window class. Specifies the extras the composter has for its list.
 */
public class DOCraftingWindow extends AbstractModuleWindow
{
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = ":gui/layouthuts/layoutdocrafting.xml";

    /**
     * Resource scrolling list.
     */
    private final ScrollingList resourceList;

    /**
     * Input inv.
     */
    public final Container inputInventory = new SimpleContainer(MateriallyTexturedBlockManager.getInstance().getMaxTexturableComponentCount()) {};

    /**
     * The module view.
     */
    private final CraftingModuleView craftingModuleView;

    /**
     * Constructor for the minimum stock window view.
     *
     * @param building class extending
     */
    public DOCraftingWindow(final IBuildingView building, final CraftingModuleView view)
    {
        super(building, Constants.MOD_ID + RESOURCE_STRING);
        this.craftingModuleView = view;

        resourceList = this.window.findPaneOfTypeByID("resourcesstock", ScrollingList.class);

        registerButton(BLOCK1_ADD, this::addBlock1);
        registerButton(BLOCK2_ADD, this::addBlock2);
        registerButton(BLOCK3_ADD, this::addBlock3);
        registerButton(ADD, this::addRecipe);
    }

    private void addRecipe()
    {
        final List<ArchitectsCutterRecipe> list = Minecraft.getInstance().level.getRecipeManager().getRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER, inputInventory, Minecraft.getInstance().level);
        final Map<Integer, List<Integer>> map = new HashMap<>();

        if (inputInventory.isEmpty())
        {
            return;
        }

        for (final ArchitectsCutterRecipe recipe : list)
        {
            final ItemStack result = recipe.assemble(inputInventory).copy();
            if (result.getItem() instanceof BlockItem)
            {
                final Block block = ((BlockItem) result.getItem()).getBlock();
                if (block instanceof IMateriallyTexturedBlock)
                {
                    final int components = ((IMateriallyTexturedBlock) block).getComponents().size();
                    final List<Integer> inputList = map.getOrDefault(components, new ArrayList<>());
                    inputList.add(list.indexOf(recipe));
                    map.put(components, inputList);
                }
            }
        }

        int inputCount = 0;
        for (int i = 0; i < inputInventory.getContainerSize(); i++)
        {
            if (!inputInventory.getItem(i).isEmpty())
            {
                inputCount++;
            }
        }

        final List<Integer> inputIndizes = map.get(inputCount);
        final List<ItemStorage> input = new ArrayList<>();
        for (int i = 0; i < inputCount; i++)
        {
            final ItemStack atPos = inputInventory.getItem(i).copy();
            if (!ItemStackUtils.isEmpty(atPos))
            {
                atPos.setCount(1);
                input.add(new ItemStorage(atPos));
            }
        }

        final List<ItemStack> additionalOutput = new ArrayList<>();
        for (int i = 1; i < inputIndizes.size(); i++)
        {
            additionalOutput.add(list.get(inputIndizes.get(i)).assemble(inputInventory).copy());
        }

        final IRecipeStorage storage = StandardFactoryController.getInstance().getNewInstance(
          TypeConstants.RECIPE,
          StandardFactoryController.getInstance().getNewInstance(TypeConstants.ITOKEN),
          input,
          3,
          list.get(inputIndizes.get(0)).assemble(inputInventory).copy(),
          Blocks.AIR,
          null,
          com.minecolonies.api.crafting.ModRecipeTypes.MULTI_OUTPUT_ID,
          additionalOutput,
          new ArrayList<>());

        Network.getNetwork().sendToServer(new AddRemoveRecipeMessage(buildingView, false, storage, craftingModuleView.getId()));
    }

    /**
     * Add the stack.
     */
    private void addBlock1()
    {
        new WindowSelectRes(this, buildingView, (stack) -> MateriallyTexturedBlockManager.getInstance().doesItemStackContainsMaterialForSlot(0, stack), (stack, qty) -> {
            inputInventory.setItem(0, stack);
            findPaneOfTypeByID(BLOCK1, ItemIcon.class).setItem(stack);
            updateStockList();
        }, false).open();
    }

    /**
     * Add the stack.
     */
    private void addBlock2()
    {
        new WindowSelectRes(this, buildingView, (stack) -> MateriallyTexturedBlockManager.getInstance().doesItemStackContainsMaterialForSlot(1, stack), (stack, qty) -> {
            inputInventory.setItem(1, stack);
            findPaneOfTypeByID(BLOCK2, ItemIcon.class).setItem(stack);
            updateStockList();
        }, false).open();
    }

    /**
     * Add the stack.
     */
    private void addBlock3()
    {
        new WindowSelectRes(this, buildingView, (stack) -> MateriallyTexturedBlockManager.getInstance().doesItemStackContainsMaterialForSlot(2, stack), (stack, qty) -> {
            inputInventory.setItem(2, stack);
            updateStockList();
            findPaneOfTypeByID(BLOCK3, ItemIcon.class).setItem(stack);
        }, false).open();
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        updateStockList();
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updateStockList()
    {
        resourceList.enable();
        resourceList.show();

        final List<ArchitectsCutterRecipe> list = Minecraft.getInstance().level.getRecipeManager().getRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER, inputInventory, Minecraft.getInstance().level);
        int inputCount = 0;
        for (int i = 0; i < inputInventory.getContainerSize(); i++)
        {
            if (!inputInventory.getItem(i).isEmpty())
            {
                inputCount++;
            }
        }

        final List<ArchitectsCutterRecipe> filteredList = new ArrayList<>();
        for (final ArchitectsCutterRecipe recipe : list)
        {
            final ItemStack result = recipe.assemble(inputInventory).copy();
            if (result.getItem() instanceof BlockItem)
            {
                final Block block = ((BlockItem) result.getItem()).getBlock();
                if (block instanceof IMateriallyTexturedBlock)
                {
                    if (((IMateriallyTexturedBlock) block).getComponents().size() == inputCount)
                    {
                        filteredList.add(recipe);
                    }
                }
            }
        }

        //Creates a dataProvider for the unemployed resourceList.
        resourceList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return filteredList.size();
            }

            /**
             * Inserts the elements into each row.
             * @param index the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final ItemStack resource = filteredList.get(index).assemble(inputInventory).copy();

                rowPane.findPaneOfTypeByID(RESOURCE_NAME, Text.class).setText(resource.getHoverName());
                rowPane.findPaneOfTypeByID(QUANTITY_LABEL, Text.class).setText(new TextComponent(String.valueOf(resource.getCount())));

                rowPane.findPaneOfTypeByID(RESOURCE_ICON, ItemIcon.class).setItem(resource);
            }
        });
    }
}
