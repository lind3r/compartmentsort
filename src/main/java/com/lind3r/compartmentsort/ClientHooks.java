package com.lind3r.compartmentsort;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.cleanroommc.bogosorter.client.keybinds.control.BSKeybinds;
import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;

import binnie.core.craftgui.IWidget;
import binnie.core.craftgui.minecraft.GuiCraftGUI;
import binnie.core.craftgui.minecraft.Window;
import binnie.core.craftgui.minecraft.control.ControlSlot;
import binnie.core.machines.storage.WindowCompartment;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Bogosorter finds the slot to sort through {@code GuiContainer.theSlot}, which vanilla sets while drawing. Binnie's
 * GUI draws itself and never sets it, so Bogosorter sees no slot. This fills it in, but only:
 * <ul>
 * <li>inside a Compartment window,</li>
 * <li>while the input event is Bogosorter's in-GUI sort key,</li>
 * <li>for the duration of that one input event (set at HIGHEST priority, put back at LOWEST even if cancelled; also
 * put back before every frame as a fallback).</li>
 * </ul>
 * So Bogosorter's other slot features (move/drop shortcuts, pins) never see a Binnie slot, and Binnie's own input
 * handling, which doesn't use {@code theSlot}, is untouched.
 */
public final class ClientHooks {

    private static final Logger LOG = LogManager.getLogger(CompartmentSort.MODID);

    private final Field theSlot;
    private final Field window;

    private GuiContainer exposedGui;
    private Slot previousSlot;

    private ClientHooks(Field theSlot, Field window) {
        this.theSlot = theSlot;
        this.window = window;
    }

    static void register() {
        try {
            Field theSlot = ReflectionHelper.findField(GuiContainer.class, "theSlot", "field_147006_u");
            Field window = GuiCraftGUI.class.getDeclaredField("window");
            window.setAccessible(true);
            MinecraftForge.EVENT_BUS.register(new ClientHooks(theSlot, window));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("Could not hook the Binnie GUI, compartment sorting is disabled on this client", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(MouseInputEvent.Pre event) {
        expose(event.gui);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void afterMouseInput(MouseInputEvent.Pre event) {
        restore();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardInput(KeyboardInputEvent.Pre event) {
        expose(event.gui);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void afterKeyboardInput(KeyboardInputEvent.Pre event) {
        restore();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        restore();
    }

    private void expose(GuiScreen screen) {
        restore();
        if (!(screen instanceof GuiCraftGUI) || !isSortKeyEvent()) return;
        GuiContainer gui = (GuiContainer) screen;
        try {
            Object w = window.get(gui);
            if (!(w instanceof WindowCompartment)) return;
            Slot slot = hoveredSlot((Window) w);
            // only a slot that really is this container's slot under that number
            if (slot == null || gui.inventorySlots == null
                || slot.slotNumber < 0
                || slot.slotNumber >= gui.inventorySlots.inventorySlots.size()
                || gui.inventorySlots.getSlot(slot.slotNumber) != slot) {
                return;
            }
            previousSlot = (Slot) theSlot.get(gui);
            theSlot.set(gui, slot);
            exposedGui = gui;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("Could not expose the hovered compartment slot", e);
        }
    }

    private void restore() {
        if (exposedGui == null) return;
        try {
            theSlot.set(exposedGui, previousSlot);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("Could not reset the hovered slot", e);
        }
        exposedGui = null;
        previousSlot = null;
    }

    private static Slot hoveredSlot(Window window) {
        for (IWidget w = window.getMousedOverWidget(); w != null; w = w.getParent()) {
            if (w instanceof ControlSlot) return ((ControlSlot) w).slot;
        }
        return null;
    }

    /** Same test as Bogosorter's own key check, minus anything that consumes a key press. */
    private static boolean isSortKeyEvent() {
        KeyBinding key = BSKeybinds.sortKeyInGUI;
        int code = key.getKeyCode();
        if (code == 0) return false;
        if (key.getIsKeyPressed()) return true;
        if (code > 0) return Keyboard.getEventKeyState() && Keyboard.getEventKey() == code;
        return Mouse.getEventButtonState() && Mouse.getEventButton() == code + 100;
    }
}
