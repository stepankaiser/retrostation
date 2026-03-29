package com.retrohandheld.launcher;
import java.util.HashMap;
public class CoreMap {
    public static final HashMap<String, String> MAP = new HashMap<String, String>();
    static {
        MAP.put("GB", "gambatte_libretro_android.so");
        MAP.put("GBC", "gambatte_libretro_android.so");
        MAP.put("GBA", "mgba_libretro_android.so");
        MAP.put("NES", "fceumm_libretro_android.so");
        MAP.put("SNES", "snes9x_libretro_android.so");
        MAP.put("Genesis", "genesis_plus_gx_libretro_android.so");
        MAP.put("PS1", "pcsx_rearmed_libretro_android.so");
        MAP.put("N64", "mupen64plus_next_gles3_libretro_android.so");
        MAP.put("PSP", "ppsspp_libretro_android.so");
        MAP.put("Dreamcast", "flycast_libretro_android.so");
        MAP.put("Doom", "prboom_libretro_android.so");
        MAP.put("Arcade", "fbneo_libretro_android.so");
    }
}
