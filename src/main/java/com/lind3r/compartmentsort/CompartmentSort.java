package com.lind3r.compartmentsort;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * Lets Bogosorter's in-GUI sort key (middle click by default) sort a Binnie Compartment from its own window: all tabs
 * together, so equal items from different tabs are merged.
 * <p>
 * Two pieces: {@link CompartmentCompat} tells Bogosorter which Binnie slots form the sortable group (runs on client and
 * server), and {@link ClientHooks} tells Bogosorter which slot is under the mouse, because Binnie's GUI never fills in
 * the vanilla "hovered slot" field Bogosorter reads.
 */
@Mod(
    modid = CompartmentSort.MODID,
    name = "Compartment Sorting",
    version = Tags.VERSION,
    dependencies = "required-after:bogosorter;required-after:BinnieCore",
    acceptableRemoteVersions = "*")
public class CompartmentSort {

    public static final String MODID = "compartmentsort";

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        CompartmentCompat.register();
        if (event.getSide()
            .isClient()) {
            ClientHooks.register();
        }
    }
}
