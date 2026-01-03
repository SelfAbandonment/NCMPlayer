package org.selfabandonment.ncmplayer.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键映射
 *
 * @author SelfAbandonment
 */
public final class KeyMappings {

    /** 打开音乐界面快捷键 (默认 M 键) */
    public static final KeyMapping OPEN_MUSIC_UI = new KeyMapping(
            "key.ncmplayer.open_music",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.ncmplayer"
    );

    private KeyMappings() {
    }
}

