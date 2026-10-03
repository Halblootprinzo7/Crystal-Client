package dev.crystaladdon.mixin;

import dev.crystaladdon.utils.TurnProgress;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_743;
import net.minecraft.class_744;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_743.class})
public abstract class KeyboardInputMixin extends class_744 {
   @Inject(
      method = {"method_3129"},
      at = {@At("TAIL")}
   )
   private void crystal$holdMovement(CallbackInfo ci) {
      if (TurnProgress.SHARED.holdsMovement()) {
         class_10185 keys = this.field_54155;
         this.field_54155 = new class_10185(false, false, false, false, keys.comp_3163(), keys.comp_3164(), keys.comp_3165());
         this.field_55868 = class_241.field_1340;
      }
   }
}
