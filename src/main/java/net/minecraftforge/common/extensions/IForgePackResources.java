/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.extensions;

import net.minecraft.server.packs.PackType;

public interface IForgePackResources
{
    /**
     * {@return {@code true} if the pack should be hidden from any user interfaces}
     */
    default boolean isHidden()
    {
        return false;
    }

    default void initForNamespace(final String nameSpace) {}

    default void init(final PackType packType) {}
}
