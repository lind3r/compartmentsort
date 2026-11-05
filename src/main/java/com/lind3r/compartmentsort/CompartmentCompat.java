package com.lind3r.compartmentsort;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.bogosorter.api.IBogoSortAPI;
import com.cleanroommc.bogosorter.api.ISortingContextBuilder;

import binnie.core.craftgui.minecraft.ContainerCraftGUI;
import binnie.core.craftgui.minecraft.CustomSlot;
import binnie.core.machines.Machine;

/**
 * Bogosorter compat for Binnie's container: all slots of a Compartment (every tab) form one slot group, so a sort
 * merges equal items across tabs and fills tab 1 first, then tab 2, ...
 * <p>
 * Runs on both sides: the client uses the group to decide what to sort, the server rebuilds it from its own container
 * and does the actual sorting. The group only depends on slot indices, so both sides get the same result.
 * <p>
 * Binnie's compartment inventory ({@code ComponentCompartmentInventory}) is package-private, so it is reached by
 * reflection. Slot layout (Binnie 2.6.51): tab t = inventory slots [t * pageSize, (t + 1) * pageSize), each tab shown
 * as pageSize / 5 columns by 5 rows.
 */
final class CompartmentCompat {

    private static final Logger LOG = LogManager.getLogger(CompartmentSort.MODID);
    private static final String COMPARTMENT_CLASS = "binnie.core.machines.storage.ComponentCompartmentInventory";

    private static Class<?> compartmentClass;
    private static Method getPageSize;
    private static Method getTabNumber;

    private CompartmentCompat() {}

    static void register() {
        try {
            compartmentClass = Class.forName(COMPARTMENT_CLASS);
            getPageSize = compartmentClass.getDeclaredMethod("getPageSize");
            getTabNumber = compartmentClass.getDeclaredMethod("getTabNumber");
            getPageSize.setAccessible(true);
            getTabNumber.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("Binnie compartment inventory not found, compartment sorting is disabled", e);
            return;
        }
        IBogoSortAPI api = IBogoSortAPI.getInstance();
        api.addCompat(ContainerCraftGUI.class, CompartmentCompat::buildGroups);
        // Binnie puts every slot at (0, 0), so Bogosorter's buttons would end up in odd places: no buttons, key only.
        api.addPlayerSortButtonPosition(ContainerCraftGUI.class, (group, pos) -> pos.setEnabled(false));
    }

    /** Slots per tab of a compartment inventory, or 0 if it isn't one / can't be read. */
    static int pageSize(IInventory inventory) {
        if (compartmentClass == null || inventory == null) return 0;
        try {
            Object compartment = Machine.getInterface(compartmentClass, inventory);
            return compartment == null ? 0 : (Integer) getPageSize.invoke(compartment);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Shift-click targets for a compartment: every slot of every tab, the open tab first, then the other tabs in
     * order. Binnie's transfer goes over this list twice (first topping up matching stacks, then filling empty
     * slots), so items join a matching stack in any tab before they take an empty slot. Returns null if the layout
     * can't be read; the caller then leaves Binnie's own (open tab only) list alone.
     */
    static int[] shiftClickTargets(IInventory inventory, int currentTab) {
        if (compartmentClass == null || inventory == null) return null;
        try {
            Object compartment = Machine.getInterface(compartmentClass, inventory);
            if (compartment == null) return null;
            int pageSize = (Integer) getPageSize.invoke(compartment);
            int tabCount = (Integer) getTabNumber.invoke(compartment);
            if (pageSize <= 0 || tabCount <= 0 || currentTab < 0 || currentTab >= tabCount) return null;
            if (pageSize * tabCount > inventory.getSizeInventory()) return null;
            int[] slots = new int[pageSize * tabCount];
            int n = 0;
            for (int i = 0; i < pageSize; i++) slots[n++] = currentTab * pageSize + i;
            for (int tab = 0; tab < tabCount; tab++) {
                if (tab == currentTab) continue;
                for (int i = 0; i < pageSize; i++) slots[n++] = tab * pageSize + i;
            }
            return slots;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("Could not read the compartment layout for shift-click", e);
            return null;
        }
    }

    private static void buildGroups(ContainerCraftGUI container, ISortingContextBuilder builder) {
        // LinkedHashMap keeps the order deterministic on both sides (tile entities use identity equals/hashCode)
        Map<IInventory, List<Slot>> slotsByInventory = new LinkedHashMap<>();
        Map<IInventory, Integer> sizes = new LinkedHashMap<>();
        Map<IInventory, Integer> pageSizes = new LinkedHashMap<>();

        for (Object o : container.inventorySlots) {
            if (!(o instanceof CustomSlot)) continue;
            Slot slot = (Slot) o;
            IInventory inventory = slot.inventory;
            if (inventory == null || inventory instanceof InventoryPlayer) continue;

            Object compartment = Machine.getInterface(compartmentClass, inventory);
            if (compartment == null) continue;
            int pageSize, tabCount;
            try {
                pageSize = (Integer) getPageSize.invoke(compartment);
                tabCount = (Integer) getTabNumber.invoke(compartment);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.error("Could not read the compartment layout", e);
                return; // add no groups at all: nothing gets sorted
            }
            int index = slot.getSlotIndex();
            if (pageSize <= 0 || tabCount <= 0 || index < 0 || index >= pageSize * tabCount) continue;

            sizes.put(inventory, pageSize * tabCount);
            pageSizes.put(inventory, pageSize);
            slotsByInventory.computeIfAbsent(inventory, k -> new ArrayList<>())
                .add(slot);
        }

        for (Map.Entry<IInventory, List<Slot>> entry : slotsByInventory.entrySet()) {
            List<Slot> slots = entry.getValue();
            if (!isComplete(slots, sizes.get(entry.getKey()))) continue;
            builder.addSlotGroupOf(slots, Math.max(1, pageSizes.get(entry.getKey()) / 5))
                .buttonPosSetter(null);
        }
    }

    /**
     * The compartment is only sortable if the container holds exactly one slot for each of its inventory slots,
     * 0 .. size-1. Two container slots on the same inventory slot would make Bogosorter count that stack twice
     * (duplication), and a missing slot would leave part of the compartment out, so in either case nothing is sorted.
     * Sorts {@code slots} by inventory index.
     */
    private static boolean isComplete(List<Slot> slots, int size) {
        if (slots.size() != size) return false;
        slots.sort(Comparator.comparingInt(Slot::getSlotIndex));
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i)
                .getSlotIndex() != i) return false;
        }
        return true;
    }
}
