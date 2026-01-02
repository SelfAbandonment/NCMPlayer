package org.demo.portalteleport.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class PortalTeleportKeyMappings {

    private PortalTeleportKeyMappings() {}

    public static final String CATEGORY = "key.categories.portalteleport";

    public static final KeyMapping OPEN_MUSIC_UI = new KeyMapping(
            "key.portalteleport.open_music_ui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );
}