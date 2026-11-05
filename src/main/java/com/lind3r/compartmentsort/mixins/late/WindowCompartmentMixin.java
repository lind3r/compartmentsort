package com.lind3r.compartmentsort.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lind3r.compartmentsort.CompartmentHooks;

import binnie.core.machines.storage.WindowCompartment;
import binnie.core.machines.transfer.TransferRequest;

/**
 * Binnie limits shift-click into a Compartment to the open tab ({@code alterRequest} sets the target slots to that
 * tab). After it ran, widen the targets to all tabs, open tab first.
 */
@Mixin(value = WindowCompartment.class, remap = false)
public abstract class WindowCompartmentMixin {

    @Shadow
    private int currentTab;

    @Inject(method = "alterRequest", at = @At("TAIL"))
    private void compartmentsort$allTabs(TransferRequest request, CallbackInfo ci) {
        CompartmentHooks.widenShiftClick((WindowCompartment) (Object) this, request, currentTab);
    }
}
