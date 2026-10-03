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

public final class KnockbackPredictor {
   private static final class_310 mc = class_310.method_1551();
   private static final float SPRINT_BONUS = 0.5F;
   private static final float PER_ENCHANTMENT_LEVEL = 1.0F;

   private KnockbackPredictor() {
   }

   public static float strength(class_1799 weapon, boolean sprinting) {
      if (mc.field_1724 == null) {
         return 0.0F;
      } else {
         double base = mc.field_1724.method_45325(class_5134.field_23722);
         int level = Utils.getEnchantmentLevel(weapon, class_1893.field_9121);
         return (float)base + level * 1.0F + (sprinting ? 0.5F : 0.0F);
      }
   }

   public static float strength(class_1799 weapon) {
      return strength(weapon, mc.field_1724 != null && mc.field_1724.method_5624());
   }

   public static class_243 velocityAfterHit(class_1309 target, float strength) {
      if (mc.field_1724 != null && target != null) {
         class_243 velocity = target.method_18798();
         double resisted = strength * (1.0 - target.method_45325(class_5134.field_23718));
         if (resisted <= 0.0) {
            return velocity;
         } else {
            float yaw = mc.field_1724.method_36454() * (float) (Math.PI / 180.0);
            double x = class_3532.method_15374(yaw);
            double z = -class_3532.method_15362(yaw);
            class_243 kick = new class_243(x, 0.0, z).method_1029().method_1021(resisted);
            return new class_243(
               velocity.field_1352 / 2.0 - kick.field_1352,
               target.method_24828() ? Math.min(0.4, velocity.field_1351 / 2.0 + resisted) : velocity.field_1351,
               velocity.field_1350 / 2.0 - kick.field_1350
            );
         }
      } else {
         return class_243.field_1353;
      }
   }

   public static class_243 velocityAfterHit(class_1657 target, class_1799 weapon) {
      return velocityAfterHit(target, strength(weapon));
   }
}
