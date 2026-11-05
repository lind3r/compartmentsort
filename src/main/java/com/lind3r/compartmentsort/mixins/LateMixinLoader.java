package com.lind3r.compartmentsort.mixins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Binnie is a normal mod, so its classes can only be mixed into once mods are loaded (late mixins). */
@LateMixin
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.compartmentsort.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!loadedMods.contains("BinnieCore")) return Collections.emptyList();
        List<String> mixins = new ArrayList<>();
        mixins.add("late.WindowCompartmentMixin"); // shift-click into all tabs
        if (FMLLaunchHandler.side()
            .isClient()) {
            // Find It highlighting
            mixins.add("late.ControlSlotMixin");
            mixins.add("late.ControlTabMixin");
        }
        return mixins;
    }
}
