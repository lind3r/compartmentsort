package com.lind3r.compartmentsort;

import net.minecraft.inventory.IInventory;

import binnie.core.machines.storage.WindowCompartment;
import binnie.core.machines.transfer.TransferRequest;

/** Called from the mixins; kept out of the mixin classes so they stay one-liners. */
public final class CompartmentHooks {

    private CompartmentHooks() {}

    /** Shift-click into a compartment: target every tab (open tab first) instead of the open tab only. */
    public static void widenShiftClick(WindowCompartment window, TransferRequest request, int currentTab) {
        IInventory inventory = window.getInventory();
        // same condition Binnie uses: only requests that go into the compartment, not out of it
        if (inventory == null || request.getDestination() != inventory) return;
        int[] slots = CompartmentCompat.shiftClickTargets(inventory, currentTab);
        if (slots != null) request.setTargetSlots(slots);
    }
}
