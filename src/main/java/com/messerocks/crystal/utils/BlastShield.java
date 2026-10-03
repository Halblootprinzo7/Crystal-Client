package com.messerocks.crystal.utils;

import java.util.function.Predicate;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils.ExposureRaycastContext;
import meteordevelopment.meteorclient.utils.entity.DamageUtils.RaycastFactory;
import net.minecraft.class_1309;
import net.minecraft.class_1922;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
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
      if (mc.field_1687 == null) {
         return 0.0F;
      } else {
         double distance = Math.sqrt(target.method_5707(crystal));
         if (distance > 12.0) {
            return 0.0F;
         } else {
            RaycastFactory rays = vanillaFactory();
            float exposure = exposure(
               target.method_5829(),
               point -> class_1922.method_17744(point, crystal, new ExposureRaycastContext(point, crystal), rays, context -> null) == null
            );
            return DamageUtils.calculateReductions(rawDamage(distance, exposure, 12.0F), target, mc.field_1687.method_48963().method_48807(null));
         }
      }
   }

   public static float crystalRawDamage(class_1309 target, class_243 crystal) {
      return crystalRawDamage(target.method_73189(), target.method_5829(), crystal);
   }

   public static float crystalRawDamage(class_243 feet, class_238 box, class_243 crystal) {
      if (mc.field_1687 == null) {
         return 0.0F;
      } else {
         double distance = feet.method_1022(crystal);
         if (distance > 12.0) {
            return 0.0F;
         } else {
            float exposure = exposure(
               box,
               point -> class_1922.method_17744(point, crystal, new ExposureRaycastContext(point, crystal), DamageUtils.HIT_FACTORY, context -> null) == null
            );
            return rawDamage(distance, exposure, 12.0F);
         }
      }
   }

   public static float crystalMaxDamage(class_1309 target, class_243 feet, class_243 crystal) {
      if (mc.field_1687 == null) {
         return 0.0F;
      } else {
         double distance = feet.method_1022(crystal);
         return distance > 12.0
            ? 0.0F
            : DamageUtils.calculateReductions((float)Math.ceil(rawDamage(distance, 1.0, 12.0F)), target, mc.field_1687.method_48963().method_48807(null));
      }
   }

   public static float excessDamage(class_1309 target, float raw, float floor) {
      return mc.field_1687 != null && !(raw <= floor)
         ? DamageUtils.calculateReductions(raw - floor, target, mc.field_1687.method_48963().method_48807(null))
         : 0.0F;
   }

   public static float anchorDamage(class_1309 target, class_243 anchor) {
      return anchorDamageBehindShield(target, anchor, null, null);
   }

   public static float anchorDamageBehindShield(class_1309 target, class_243 anchor, class_2338 shieldPos, class_2680 shieldState) {
      if (mc.field_1687 == null) {
         return 0.0F;
      } else {
         double distance = Math.sqrt(target.method_5707(anchor));
         if (distance > 10.0) {
            return 0.0F;
         } else {
            RaycastFactory rays = factory(class_2338.method_49638(anchor), shieldPos, shieldState);
            float exposure = exposure(
               target.method_5829(), point -> class_1922.method_17744(point, anchor, new ExposureRaycastContext(point, anchor), rays, context -> null) == null
            );
            return DamageUtils.calculateReductions(rawDamage(distance, exposure, 10.0F), target, mc.field_1687.method_48963().method_48808(anchor));
         }
      }
   }

   static float rawDamage(double distance, double exposure, float power) {
      if (distance > power) {
         return 0.0F;
      } else {
         double impact = (1.0 - distance / power) * exposure;
         return (float)((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);
      }
   }

   static float exposure(class_238 box, Predicate<class_243> reaches) {
      double stepX = 1.0 / ((box.field_1320 - box.field_1323) * 2.0 + 1.0);
      double stepY = 1.0 / ((box.field_1325 - box.field_1322) * 2.0 + 1.0);
      double stepZ = 1.0 / ((box.field_1324 - box.field_1321) * 2.0 + 1.0);
      if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
         double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
         double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
         int clear = 0;
         int total = 0;

         for (double k = 0.0; k <= 1.0; k += stepX) {
            for (double l = 0.0; l <= 1.0; l += stepY) {
               for (double m = 0.0; m <= 1.0; m += stepZ) {
                  class_243 point = new class_243(
                     box.field_1323 + k * (box.field_1320 - box.field_1323) + offsetX,
                     box.field_1322 + l * (box.field_1325 - box.field_1322),
                     box.field_1321 + m * (box.field_1324 - box.field_1321) + offsetZ
                  );
                  if (reaches.test(point)) {
                     clear++;
                  }

                  total++;
               }
            }
         }

         return (float)clear / total;
      } else {
         return 0.0F;
      }
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
