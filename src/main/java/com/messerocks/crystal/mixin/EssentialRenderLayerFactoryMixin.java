package com.messerocks.crystal.mixin;

import net.minecraft.class_12247;
import net.minecraft.class_1921;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
   targets = {"gg/essential/model/backend/minecraft/RenderLayerFactory$Companion"},
   remap = false
)
public abstract class EssentialRenderLayerFactoryMixin {
   @Inject(
      method = {"createRenderLayer"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void crystal$callStaticFactory(String name, class_12247 setup, CallbackInfoReturnable<class_1921> cir) {
      cir.setReturnValue(RenderLayerInvoker.crystal$of(name, setup));
   }
}
