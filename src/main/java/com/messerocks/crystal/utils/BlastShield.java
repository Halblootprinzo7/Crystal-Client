package com.messerocks.crystal.utils;

import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils.RaycastFactory;
import net.minecraft.class_1309;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;

public final class BlastShield {
   private static final class_310 mc = class_310.method_1551();
   public static final float ANCHOR_POWER = 10.0F;
   public static final float CRYSTAL_POWER = 12.0F;

   private BlastShield() {
   }

   public static RaycastFactory factory(class_2338 airAt, class_2338 shieldPos, class_2680 shieldState) {
      return (context, blockPos) -> {
         if (blockPos.equals(airAt)) {
            return null;
         } else {
            class_2680 state;
            if (blockPos.equals(shieldPos) && shieldState != null) {
               state = shieldState;
            } else {
               state = mc.field_1687.method_8320(blockPos);
               if (state.method_26204().method_9520() < 600.0F) {
                  return null;
               }
            }

            return state.method_26220(mc.field_1687, blockPos).method_1092(context.start(), context.end(), blockPos);
         }
      };
   }

   public static RaycastFactory vanillaFactory() {
      return factory(null, null, null);
   }

   public static float crystalDamage(class_1309 target, class_243 crystal) {
      return DamageUtils.explosionDamage(target, target.method_73189(), target.method_5829(), crystal, 12.0F, vanillaFactory());
   }

   public static float anchorDamage(class_1309 target, class_243 anchor) {
      return anchorDamageBehindShield(target, anchor, null, null);
   }

   public static float anchorDamageBehindShield(class_1309 target, class_243 anchor, class_2338 shieldPos, class_2680 shieldState) {
      return DamageUtils.explosionDamage(
         target, target.method_73189(), target.method_5829(), anchor, 10.0F, factory(class_2338.method_49638(anchor), shieldPos, shieldState)
      );
   }

   public static class_2350 shieldDirection(class_2338 spot) {
      if (mc.field_1724 == null) {
         return null;
      } else {
         class_243 away = spot.method_46558().method_1020(mc.field_1724.method_33571());
         double horizontal = away.field_1352 * away.field_1352 + away.field_1350 * away.field_1350;
         return horizontal < 0.25 ? null : class_2350.method_10142(away.field_1352, 0.0, away.field_1350);
      }
   }
}
