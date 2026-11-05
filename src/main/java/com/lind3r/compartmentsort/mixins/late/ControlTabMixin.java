package com.lind3r.compartmentsort.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lind3r.compartmentsort.FindItHighlight;

import binnie.core.craftgui.IWidget;
import binnie.core.craftgui.controls.tab.ControlTab;

/** Outlines compartment tabs that hold a slot Find It is highlighting (see {@link FindItHighlight}). Client only. */
@Mixin(value = ControlTab.class, remap = false)
public abstract class ControlTabMixin {

    @Shadow
    protected Object value;

    @Inject(method = "onRenderBackground", at = @At("TAIL"))
    private void compartmentsort$findIt(CallbackInfo ci) {
        FindItHighlight.drawTab((IWidget) this, value);
    }
}
