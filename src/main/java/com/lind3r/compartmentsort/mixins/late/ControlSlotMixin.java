package com.lind3r.compartmentsort.mixins.late;

import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lind3r.compartmentsort.FindItHighlight;

import binnie.core.craftgui.IWidget;
import binnie.core.craftgui.minecraft.control.ControlSlot;

/** Draws Find It's slot highlight on Binnie slots (see {@link FindItHighlight}). Client only. */
@Mixin(value = ControlSlot.class, remap = false)
public abstract class ControlSlotMixin {

    @Shadow
    public Slot slot;

    @Inject(method = "onRenderBackground", at = @At("TAIL"))
    private void compartmentsort$findIt(CallbackInfo ci) {
        FindItHighlight.drawSlot((IWidget) this, slot);
    }
}
