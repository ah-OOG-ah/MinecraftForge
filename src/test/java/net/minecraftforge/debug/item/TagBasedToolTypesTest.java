/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.debug.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mod(TagBasedToolTypesTest.MODID)
public class TagBasedToolTypesTest
{
    static final String MODID = "tag_based_tool_types";

    private static final TagKey<Block> MINEABLE_TAG = BlockTags.create(new ResourceLocation(MODID, "minable/my_tool"));
    private static final TagKey<Block> REQUIRES_TAG = BlockTags.create(new ResourceLocation(MODID, "needs_my_tier_tool"));

    private static final Tier MY_TIER = TierSortingRegistry.registerTier(
        new ForgeTier(5, 5000, 10, 100, 0, REQUIRES_TAG, () -> Ingredient.of(Items.BEDROCK)),
        new ResourceLocation(MODID, "my_tier"),
        List.of(Tiers.DIAMOND), List.of()
    );

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final RegistryObject<Block> STONE = BLOCKS.register("test_stone", () -> new Block(BlockBehaviour.Properties.of(Material.STONE, MaterialColor.DIRT).requiresCorrectToolForDrops().strength(1.5F, 6.0F)));

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    @SuppressWarnings("unused")
    private static final RegistryObject<Item> ORE_ITEM = ITEMS.register(STONE.getId().getPath(), () -> new BlockItem(STONE.get(), (new Item.Properties())));
    private static final RegistryObject<Item> TOOL = ITEMS.register("test_tool", () ->
    {
        return new DiggerItem(1, 1, MY_TIER, MINEABLE_TAG, new Item.Properties())
        {
            @Override
            public float getDestroySpeed(ItemStack stack, BlockState state)
            {
                if (state.is(BlockTags.MINEABLE_WITH_AXE)) return speed;
                if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return speed;
                return super.getDestroySpeed(stack, state);
            }

            @Override
            public boolean isCorrectToolForDrops(ItemStack stack, BlockState state)
            {
                if (state.is(BlockTags.MINEABLE_WITH_PICKAXE))
                    return TierSortingRegistry.isCorrectTierForDrops(Tiers.WOOD, state);
                if (state.is(BlockTags.MINEABLE_WITH_AXE))
                    return TierSortingRegistry.isCorrectTierForDrops(MY_TIER, state);
                if (state.is(MINEABLE_TAG))
                    return TierSortingRegistry.isCorrectTierForDrops(MY_TIER, state);
                return false;
            }
        };
    });

    public TagBasedToolTypesTest()
    {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::gatherData);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(new Consumer<CreativeModeTabEvent.BuildContents>() {
            @Override
            public void accept(CreativeModeTabEvent.BuildContents onBuildContents) {
                if (onBuildContents.getTab() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                    onBuildContents.register((flags, output, permission) -> {
                        output.accept(TOOL.get());
                    });
                } else if (onBuildContents.getTab() == CreativeModeTabs.BUILDING_BLOCKS) {
                    onBuildContents.register((flags, output, permission) -> {
                        output.accept(ORE_ITEM.get());
                    });
                }
            }

        });
    }

    @SubscribeEvent
    public void gatherData(GatherDataEvent event)
    {
        DataGenerator gen = event.getGenerator();
        ExistingFileHelper existing = event.getExistingFileHelper();

        gen.addProvider(event.includeServer(), new BlockTagsProvider(gen.getPackOutput(), event.getLookupProvider(), MODID, existing)
        {
            @Override
            protected void addTags(HolderLookup.Provider registry) {
                this.tag(MINEABLE_TAG).add(STONE.get());
                this.tag(REQUIRES_TAG).add(STONE.get());
            }
        });

        final class TestBlockLootProvider extends BlockLootSubProvider {
            public TestBlockLootProvider()
            {
                super(Set.of(), FeatureFlags.REGISTRY.allFlags());
            }

            @Override
            protected Iterable<Block> getKnownBlocks()
            {
                return BLOCKS.getEntries().stream().map(Supplier::get).collect(Collectors.toList());
            }

            @Override
            protected void generate()
            {
                this.dropSelf(STONE.get());
            }
        }

        gen.addProvider(event.includeServer(), new LootTableProvider(event.getGenerator().getPackOutput(), Set.of(), List.of(new LootTableProvider.SubProviderEntry(TestBlockLootProvider::new, LootContextParamSets.BLOCK))));

        gen.addProvider(event.includeClient(), new BlockStateProvider(gen, MODID, existing)
        {
            @Override
            protected void registerStatesAndModels()
            {
                ModelFile model = models().cubeAll(STONE.getId().getPath(), mcLoc("block/debug"));
                simpleBlock(STONE.get(), model);
                simpleBlockItem(STONE.get(), model);
            }
        });
        gen.addProvider(event.includeClient(), new ItemModelProvider(gen, MODID, existing)
        {
            @Override
            protected void registerModels()
            {
                getBuilder(TOOL.getId().getPath())
                    .parent(new UncheckedModelFile("item/generated"))
                    .texture("layer0", mcLoc("item/wooden_pickaxe"));
            }
        });
    }
}
