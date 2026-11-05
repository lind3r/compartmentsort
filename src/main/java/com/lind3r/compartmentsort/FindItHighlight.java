package com.lind3r.compartmentsort;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import binnie.core.craftgui.CraftGUI;
import binnie.core.craftgui.IWidget;
import binnie.core.craftgui.geometry.IArea;
import binnie.core.craftgui.minecraft.Window;
import binnie.core.machines.storage.WindowCompartment;

/**
 * Find It (key T) highlights matching slots through NEI's "slot underlay" draw hook, which vanilla's GuiContainer
 * calls while drawing its slots. Binnie's GUI draws its own slots and never calls it, so nothing shows. Find It still
 * works out the right slots; this draws them from Binnie's slot rendering instead, by calling Find It's own
 * highlighter (same texture and colour). Compartment tabs that hold a highlighted slot get an outline in the same
 * colour, so items in other tabs can be found too.
 * <p>
 * Find It is optional and reached by reflection (findit 1.4.8: {@code ClientItemFindService.slotHighlighter},
 * {@code SlotHighlighter.gui/slots/renderSlotUnderlay}). If anything is missing, this switches itself off.
 */
public final class FindItHighlight {

    private static final Logger LOG = LogManager.getLogger(CompartmentSort.MODID);

    private static boolean initDone;
    private static boolean available;
    private static Method getInstance;
    private static Field highlighterField;
    private static Field guiField;
    private static Field slotsField;
    private static Field red, green, blue, alpha;
    private static Method renderSlotUnderlay;

    private FindItHighlight() {}

    private static boolean init() {
        if (initDone) return available;
        initDone = true;
        try {
            Class<?> service = Class.forName("com.gtnh.findit.service.itemfinder.ClientItemFindService");
            Class<?> highlighter = Class.forName("com.gtnh.findit.fx.SlotHighlighter");
            getInstance = service.getMethod("getInstance");
            highlighterField = field(service, "slotHighlighter");
            guiField = field(highlighter, "gui");
            slotsField = field(highlighter, "slots");
            red = field(highlighter, "red");
            green = field(highlighter, "green");
            blue = field(highlighter, "blue");
            alpha = field(highlighter, "alpha");
            renderSlotUnderlay = highlighter.getMethod("renderSlotUnderlay", GuiContainer.class, Slot.class);
            available = true;
        } catch (ClassNotFoundException e) {
            LOG.info("Find It not found, no slot highlighting in compartments");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.error("Find It changed, no slot highlighting in compartments", e);
        }
        return available;
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void disable(Throwable e) {
        LOG.error("Find It highlighting failed, switching it off", e);
        available = false;
    }

    /** Find It's highlighter, only if it is currently highlighting slots of this GUI; else null. */
    private static Object activeHighlighter(GuiContainer gui) throws ReflectiveOperationException {
        Object service = getInstance.invoke(null);
        if (service == null) return null;
        Object h = highlighterField.get(service);
        if (h == null || guiField.get(h) != gui) return null;
        Set<?> slots = (Set<?>) slotsField.get(h);
        return slots == null || slots.isEmpty() ? null : h;
    }

    /** Called at the end of a Binnie slot's background, with GL translated to the slot widget (18 x 18 frame). */
    public static void drawSlot(IWidget widget, Slot slot) {
        if (slot == null || !init()) return;
        try {
            Window window = Window.get(widget);
            if (window == null || window.getGui() != Minecraft.getMinecraft().currentScreen) return;
            GuiContainer gui = window.getGui();
            Object h = activeHighlighter(gui);
            if (h == null || !((Set<?>) slotsField.get(h)).contains(slot)) return;
            // Find It draws at the slot's display position; Binnie's item sits at (1, 1) inside the widget
            GL11.glPushMatrix();
            GL11.glTranslatef(1 - slot.xDisplayPosition, 1 - slot.yDisplayPosition, 0);
            try {
                renderSlotUnderlay.invoke(h, gui, slot);
            } finally {
                GL11.glPopMatrix();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
    }

    /** Called at the end of a compartment tab's background: outline the tab if it holds a highlighted slot. */
    public static void drawTab(IWidget tab, Object value) {
        if (!(value instanceof Integer) || !init()) return;
        try {
            Window window = Window.get(tab);
            if (!(window instanceof WindowCompartment)
                || window.getGui() != Minecraft.getMinecraft().currentScreen) return;
            Object h = activeHighlighter(window.getGui());
            if (h == null) return;
            IInventory inventory = window.getInventory();
            int pageSize = CompartmentCompat.pageSize(inventory);
            if (pageSize <= 0) return;
            int t = (Integer) value;
            boolean found = false;
            for (Object o : (Set<?>) slotsField.get(h)) {
                Slot s = (Slot) o;
                if (s.inventory == inventory && s.getSlotIndex() / pageSize == t) {
                    found = true;
                    break;
                }
            }
            if (!found) return;
            int colour = argb(h);
            float w = tab.getSize()
                .x();
            float ht = tab.getSize()
                .y();
            CraftGUI.render.solid(new IArea(0, 0, w, 1), colour);
            CraftGUI.render.solid(new IArea(0, ht - 1, w, 1), colour);
            CraftGUI.render.solid(new IArea(0, 1, 1, ht - 2), colour);
            CraftGUI.render.solid(new IArea(w - 1, 1, 1, ht - 2), colour);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
    }

    private static int argb(Object h) throws IllegalAccessException {
        return c(alpha.getFloat(h)) << 24 | c(red.getFloat(h)) << 16 | c(green.getFloat(h)) << 8 | c(blue.getFloat(h));
    }

    private static int c(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255)));
    }
}
