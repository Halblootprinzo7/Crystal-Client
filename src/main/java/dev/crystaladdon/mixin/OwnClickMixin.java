package dev.crystaladdon.mixin;

import dev.crystaladdon.utils.ClickGate;
import dev.crystaladdon.utils.TurnProgress;
import java.util.Arrays;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_315;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_310.class})
public abstract class OwnClickMixin {
   @Shadow
   @Final
   public class_315 field_1690;
   @Shadow
   public class_437 field_1755;
   @Unique
   private int crystal$heldAttacks;
   @Unique
   private int crystal$heldUses;
   @Unique
   private final int[] crystal$heldKeys = new int[11];

   @Inject(
      method = {"method_1508"},
      at = {@At("HEAD")}
   )
   private void crystal$holdPresses(CallbackInfo ci) {
      boolean mismatched = TurnProgress.SHARED.ownClickMismatched();
      KeyBindingAccessor attack = (KeyBindingAccessor)this.field_1690.field_1886;
      KeyBindingAccessor use = (KeyBindingAccessor)this.field_1690.field_1904;
      this.crystal$heldAttacks = 0;
      this.crystal$heldUses = 0;
      if (mismatched || !ClickGate.canAttack()) {
         this.crystal$heldAttacks = attack.meteor$getTimesPressed();
         attack.meteor$setTimesPressed(0);
      }

      if (mismatched || !ClickGate.canUse()) {
         this.crystal$heldUses = use.meteor$getTimesPressed();
         use.meteor$setTimesPressed(0);
      }

      Arrays.fill(this.crystal$heldKeys, 0);
      if (ClickGate.clickedThisTick()) {
         for (int i = 0; i < this.crystal$heldKeys.length; i++) {
            KeyBindingAccessor key = (KeyBindingAccessor)this.crystal$key(i);
            this.crystal$heldKeys[i] = key.meteor$getTimesPressed();
            key.meteor$setTimesPressed(0);
         }
      }
   }

   @Unique
   private class_304 crystal$key(int index) {
      if (index < 9) {
         return this.field_1690.field_1852[index];
      } else {
         return index == 9 ? this.field_1690.field_1831 : this.field_1690.field_1869;
      }
   }

   @Inject(
      method = {"method_1508"},
      at = {@At("TAIL")}
   )
   private void crystal$returnPresses(CallbackInfo ci) {
      if (this.field_1755 == null) {
         KeyBindingAccessor attack = (KeyBindingAccessor)this.field_1690.field_1886;
         KeyBindingAccessor use = (KeyBindingAccessor)this.field_1690.field_1904;
         if (this.crystal$heldAttacks > 0) {
            attack.meteor$setTimesPressed(attack.meteor$getTimesPressed() + this.crystal$heldAttacks);
         }

         if (this.crystal$heldUses > 0) {
            use.meteor$setTimesPressed(use.meteor$getTimesPressed() + this.crystal$heldUses);
         }

         for (int i = 0; i < this.crystal$heldKeys.length; i++) {
            if (this.crystal$heldKeys[i] > 0) {
               KeyBindingAccessor key = (KeyBindingAccessor)this.crystal$key(i);
               key.meteor$setTimesPressed(key.meteor$getTimesPressed() + this.crystal$heldKeys[i]);
            }
         }
      }

      this.crystal$heldAttacks = 0;
      this.crystal$heldUses = 0;
      Arrays.fill(this.crystal$heldKeys, 0);
   }

   @Inject(
      method = {"method_1536"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void crystal$waitForLookAttack(CallbackInfoReturnable<Boolean> cir) {
      if (TurnProgress.SHARED.ownClickMismatched() || !ClickGate.canAttack()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"method_1583"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void crystal$waitForLookUse(CallbackInfo ci) {
      if (TurnProgress.SHARED.ownClickMismatched() || !ClickGate.canUse()) {
         ci.cancel();
      }
   }

   @ModifyVariable(
      method = {"method_1590"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private boolean crystal$waitForLookMining(boolean breaking) {
      return breaking && !TurnProgress.SHARED.ownClickMismatched();
   }
}
