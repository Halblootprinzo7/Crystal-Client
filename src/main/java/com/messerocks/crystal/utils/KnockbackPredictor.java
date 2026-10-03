package com.messerocks.crystal.utils;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1893;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_5134;
import net.minecraft.class_746;

public final class KnockbackPredictor {
   private static final class_310 mc = class_310.method_1551();
   static final float BASE_KNOCKBACK = 0.4F;
   private static final float SPRINT_BONUS = 0.5F;
   private static final float SPRINT_CHARGE = 0.9F;

   private KnockbackPredictor() {
   }

   public static float strength(double attackKnockback, int knockbackLevel, boolean sprintHit) {
      return ((float)attackKnockback + knockbackLevel) / 2.0F + (sprintHit ? 0.5F : 0.0F);
   }

   public static float strength(class_1799 weapon) {
      return mc.field_1724 == null
         ? 0.0F
         : strength(mc.field_1724.method_45325(class_5134.field_23722), Utils.getEnchantmentLevel(weapon, class_1893.field_9121), sprintHit(mc.field_1724));
   }

   public static boolean sprintHit(class_1657 attacker) {
      return attacker.method_5624() && attacker.method_7261(0.5F) > 0.9F;
   }

   public static class_243 velocityAfterHit(class_1309 target, float strength, double yaw) {
      return mc.field_1724 != null && target != null
         ? velocityAfterHit(
            target.method_18798(),
            target.method_24828(),
            target.method_45325(class_5134.field_23718),
            !DamageWindow.reduced(target),
            mc.field_1724.method_23317() - target.method_23317(),
            mc.field_1724.method_23321() - target.method_23321(),
            strength,
            (float)yaw
         )
         : class_243.field_1353;
   }

   static class_243 velocityAfterHit(
      class_243 velocity, boolean onGround, double resistance, boolean freshHit, double towardsAttackerX, double towardsAttackerZ, float strength, float yaw
   ) {
      class_243 result = velocity;
      if (freshHit) {
         result = takeKnockback(velocity, onGround, resistance, 0.4F, towardsAttackerX, towardsAttackerZ);
      }

      if (strength > 0.0F) {
         float radians = yaw * (float) (Math.PI / 180.0);
         result = takeKnockback(result, onGround, resistance, strength, class_3532.method_15374(radians), -class_3532.method_15362(radians));
      }

      return result;
   }

   static class_243 takeKnockback(class_243 velocity, boolean onGround, double resistance, double strength, double x, double z) {
      strength *= 1.0 - resistance;
      if (strength <= 0.0) {
         return velocity;
      } else {
         class_243 kick = x * x + z * z < 1.0E-5F ? class_243.field_1353 : new class_243(x, 0.0, z).method_1029().method_1021(strength);
         return new class_243(
            velocity.field_1352 / 2.0 - kick.field_1352,
            onGround ? Math.min(0.4, velocity.field_1351 / 2.0 + strength) : velocity.field_1351,
            velocity.field_1350 / 2.0 - kick.field_1350
         );
      }
   }

   public static void afterAttack() {
      class_746 player = mc.field_1724;
      if (player != null) {
         afterAttack(sprintHit(player));
      }
   }

   public static void afterAttack(boolean sprintHit) {
      class_746 player = mc.field_1724;
      if (player != null) {
         float clientStrength = (float)player.method_45325(class_5134.field_23722) / 2.0F + (sprintHit ? 0.5F : 0.0F);
         if (clientStrength > 0.0F) {
            player.method_18799(player.method_18798().method_18805(0.6, 1.0, 0.6));
            player.method_5728(false);
         }

         player.method_7350();
      }
   }
}
