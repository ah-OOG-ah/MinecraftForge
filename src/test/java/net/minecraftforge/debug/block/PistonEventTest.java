/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.debug.block;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.data.DataGenerator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.level.PistonEvent.PistonMoveType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * This test mod blocks pistons from moving cobblestone at all except indirectly
 * This test mod adds a block that moves upwards when pushed by a piston
 * This test mod informs the user what will happen the piston and affected blocks when changes are made
 * This test mod makes black wool pushed by a piston drop after being pushed.
 */
@Mod.EventBusSubscriber(modid = PistonEventTest.MODID)
@Mod(value = PistonEventTest.MODID)
public class PistonEventTest
{
    public static final String MODID = "piston_event_test";
    public static String blockName = "shiftonmove";
    private static DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static DeferredRegister<Item>  ITEMS  = DeferredRegister.create(ForgeRegistries.ITEMS,  MODID);

    private static RegistryObject<Block> SHIFT_ON_MOVE = BLOCKS.register(blockName, () -> new Block(Block.Properties.of(Material.STONE)));
    private static RegistryObject<Item> SHIFT_ON_MOVE_ITEM = ITEMS.register(blockName, () -> new BlockItem(SHIFT_ON_MOVE.get(), new Item.Properties()));

    public PistonEventTest()
    {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::gatherData);

        modBus.addListener((CreativeModeTabEvent.BuildContents event) -> event.registerSimple(CreativeModeTabs.BUILDING_BLOCKS, SHIFT_ON_MOVE_ITEM.get()));
    }

    @SubscribeEvent
    public static void pistonPre(PistonEvent.Pre event)
    {
        if (event.getPistonMoveType() == PistonMoveType.EXTEND)
        {
            Level world = (Level) event.getLevel();
            PistonStructureResolver pistonHelper = event.getStructureHelper();
            Player player = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().player);
            if (world.isClientSide && player != null)
            {
                if (pistonHelper.resolve())
                {
                    player.sendSystemMessage(Component.literal(String.format(Locale.ENGLISH, "Piston will extend moving %d blocks and destroy %d blocks", pistonHelper.getToPush().size(), pistonHelper.getToDestroy().size())));
                }
                else
                {
                    player.sendSystemMessage(Component.literal("Piston won't extend"));
                }
            }

            if (pistonHelper.resolve())
            {
                List<BlockPos> posList = pistonHelper.getToPush();
                for (BlockPos newPos : posList)
                {
                    BlockState state = event.getLevel().getBlockState(newPos);
                    if (state.getBlock() == Blocks.BLACK_WOOL)
                    {
                        Block.dropResources(state, world, newPos);
                        world.setBlockAndUpdate(newPos, Blocks.AIR.defaultBlockState());
                    }
                }
            }

            // Make the block move up and out of the way so long as it won't replace the piston
            BlockPos pushedBlockPos = event.getFaceOffsetPos();
            if (world.getBlockState(pushedBlockPos).getBlock() == SHIFT_ON_MOVE.get() && event.getDirection() != Direction.DOWN)
            {
                world.setBlockAndUpdate(pushedBlockPos, Blocks.AIR.defaultBlockState());
                world.setBlockAndUpdate(pushedBlockPos.above(), SHIFT_ON_MOVE.get().defaultBlockState());
            }

            // Block pushing cobblestone (directly, indirectly works)
            event.setCanceled(event.getLevel().getBlockState(event.getFaceOffsetPos()).getBlock() == Blocks.COBBLESTONE);
        }
        else
        {
            boolean isSticky = event.getLevel().getBlockState(event.getPos()).getBlock() == Blocks.STICKY_PISTON;

            Player player = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().player);
            if (event.getLevel().isClientSide() && player != null)
            {
                if (isSticky)
                {
                    BlockPos targetPos = event.getFaceOffsetPos().relative(event.getDirection());
                    boolean canPush = PistonBaseBlock.isPushable(event.getLevel().getBlockState(targetPos), (Level) event.getLevel(), event.getFaceOffsetPos(), event.getDirection().getOpposite(), false, event.getDirection());
                    boolean isAir = event.getLevel().isEmptyBlock(targetPos);
                    player.sendSystemMessage(Component.literal(String.format(Locale.ENGLISH, "Piston will retract moving %d blocks", !isAir && canPush ? 1 : 0)));
                }
                else
                {
                    player.sendSystemMessage(Component.literal("Piston will retract"));
                }
            }
            // Offset twice to see if retraction will pull cobblestone
            event.setCanceled(event.getLevel().getBlockState(event.getFaceOffsetPos().relative(event.getDirection())).getBlock() == Blocks.COBBLESTONE && isSticky);
        }
    }

    public void gatherData(GatherDataEvent event)
    {
        DataGenerator gen = event.getGenerator();

        gen.addProvider(event.includeClient(), new BlockStates(gen, event.getExistingFileHelper()));
    }

    private class BlockStates extends BlockStateProvider
    {
        public BlockStates(DataGenerator gen, ExistingFileHelper exFileHelper)
        {
            super(gen, MODID, exFileHelper);
        }

        @Override
        protected void registerStatesAndModels()
        {
            ModelFile model = models().cubeAll(SHIFT_ON_MOVE.getId().getPath(), mcLoc("block/furnace_top"));
            simpleBlock(SHIFT_ON_MOVE.get(), model);
            simpleBlockItem(SHIFT_ON_MOVE.get(), model);
        }
    }

}
