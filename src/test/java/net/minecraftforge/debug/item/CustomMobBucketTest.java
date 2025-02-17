/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.debug.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

@Mod(CustomMobBucketTest.MODID)
public class CustomMobBucketTest
{
    public static final String MODID = "custom_mob_bucket_test";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final boolean ENABLED = true;

    public static final RegistryObject<Item> COW_BUCKET = ITEMS.register("cow_bucket", () -> new MobBucketItem(
        () -> EntityType.COW,
        () -> Fluids.WATER,
        () -> SoundEvents.BUCKET_EMPTY_FISH,
        (new Item.Properties()).stacksTo(1)));

    public CustomMobBucketTest()
    {
        if (ENABLED)
        {
            IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
            ITEMS.register(modEventBus);
            FMLJavaModLoadingContext.get().getModEventBus().addListener((Consumer<CreativeModeTabEvent.BuildContents>) onBuildContents -> {
                if (onBuildContents.getTab() == CreativeModeTabs.INGREDIENTS) {
                    onBuildContents.register((flags, output, permissions) -> {
                        output.accept(COW_BUCKET.get());
                    });
                }
            });
        }
    }
}
