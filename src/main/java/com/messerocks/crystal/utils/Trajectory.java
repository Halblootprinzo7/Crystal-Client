package com.messerocks.crystal.utils;

import net.minecraft.class_1309;
import net.minecraft.class_243;

public final class Trajectory {
   private static final double VERTICAL_DRAG = 0.98;
   private static final double AIR_DRAG = 0.91;

   private Trajectory() {
   }

   public static class_243 predict(class_1309 entity, int ticks) {
      return predict(entity, entity.method_18798(), ticks);
   }

   public static class_243 predict(class_1309 entity, class_243 velocity, int ticks) {
      class_243 pos = entity.method_73189();
      if (ticks <= 0) {
         return pos;
      } else {
         double gravity = entity.method_56989();
         double x = pos.field_1352;
         double y = pos.field_1351;
         double z = pos.field_1350;
         double vx = velocity.field_1352;
         double vy = velocity.field_1351;
         double vz = velocity.field_1350;
         boolean airborne = !entity.method_24828() || vy > 0.0;

         for (int i = 0; i < ticks; i++) {
            x += vx;
            y += vy;
            z += vz;
            if (airborne) {
               vy = (vy - gravity) * 0.98;
               vx *= 0.91;
               vz *= 0.91;
            } else {
               vy = 0.0;
               vx *= 0.91;
               vz *= 0.91;
            }

            if (!airborne && vy <= 0.0) {
               y = Math.max(y, pos.field_1351);
               break;
            }
         }

         return new class_243(x, y, z);
      }
   }

   public static double heightAbove(class_1309 entity, class_243 from, int ticks) {
      return predict(entity, ticks).field_1351 - from.field_1351;
   }
}
