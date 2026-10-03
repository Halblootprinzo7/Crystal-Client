package dev.crystaladdon.utils;

import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_238;
import net.minecraft.class_243;

public final class Trajectory {
   private static final double VERTICAL_DRAG = 0.98;
   private static final double AIR_DRAG = 0.91;
   private static final double GROUND_DRAG = 0.546;
   private static final double EPSILON = 1.0E-5;

   private Trajectory() {
   }

   public static class_243 predict(class_1309 entity, int ticks) {
      return predict(entity, entity.method_18798(), ticks);
   }

   public static class_243 predict(class_1309 entity, class_243 velocity, int ticks) {
      if (ticks <= 0) {
         return entity.method_73189();
      } else {
         class_243[] path = path(entity, velocity, ticks);
         return path[path.length - 1];
      }
   }

   public static class_243[] path(class_1309 entity, class_243 velocity, int ticks) {
      if (ticks <= 0) {
         return new class_243[0];
      } else {
         Trajectory.Collider world = (box, movement) -> class_1297.method_20736(entity, movement, box, entity.method_73183(), List.of());
         return simulate(entity.method_5829(), velocity, entity.method_24828(), entity.method_56989(), ticks, world);
      }
   }

   public static class_243 walk(class_1309 entity, class_243 perTick, int ticks) {
      return walk(entity, entity.method_5829(), perTick, ticks);
   }

   public static class_243 walk(class_1309 entity, class_238 start, class_243 perTick, int ticks) {
      if (ticks <= 0) {
         return new class_243((start.field_1323 + start.field_1320) / 2.0, start.field_1322, (start.field_1321 + start.field_1324) / 2.0);
      } else {
         Trajectory.Collider world = (box, movement) -> class_1297.method_20736(entity, movement, box, entity.method_73183(), List.of());
         class_243[] path = walkPath(start, perTick, entity.method_24828(), entity.method_56989(), ticks, world);
         return path[path.length - 1];
      }
   }

   static class_243[] walkPath(class_238 box, class_243 perTick, boolean onGround, double gravity, int ticks, Trajectory.Collider collider) {
      class_243[] path = new class_243[Math.max(0, ticks)];
      double vx = perTick.field_1352;
      double vz = perTick.field_1350;
      double vy = onGround ? 0.0 : perTick.field_1351;

      for (int i = 0; i < path.length; i++) {
         class_243 wanted = new class_243(vx, vy, vz);
         class_243 moved = collider.move(box, wanted);
         box = box.method_997(moved);
         if (Math.abs(moved.field_1352 - vx) >= 1.0E-5) {
            vx = 0.0;
         }

         if (Math.abs(moved.field_1350 - vz) >= 1.0E-5) {
            vz = 0.0;
         }

         boolean vertical = moved.field_1351 != vy;
         onGround = vertical && vy < 0.0;
         vy = !onGround && (!vertical || !(vy > 0.0)) ? vy : 0.0;
         vy = (vy - gravity) * 0.98;
         path[i] = new class_243((box.field_1323 + box.field_1320) / 2.0, box.field_1322, (box.field_1321 + box.field_1324) / 2.0);
      }

      return path;
   }

   public static double heightAbove(class_1309 entity, class_243 from, int ticks) {
      return predict(entity, ticks).field_1351 - from.field_1351;
   }

   static class_243[] simulate(class_238 box, class_243 velocity, boolean onGround, double gravity, int ticks, Trajectory.Collider collider) {
      class_243[] path = new class_243[Math.max(0, ticks)];
      double vx = velocity.field_1352;
      double vy = velocity.field_1351;
      double vz = velocity.field_1350;

      for (int i = 0; i < path.length; i++) {
         double drag = onGround ? 0.546 : 0.91;
         class_243 wanted = new class_243(vx, vy, vz);
         class_243 moved = collider.move(box, wanted);
         box = box.method_997(moved);
         if (Math.abs(moved.field_1352 - vx) >= 1.0E-5) {
            vx = 0.0;
         }

         if (Math.abs(moved.field_1350 - vz) >= 1.0E-5) {
            vz = 0.0;
         }

         boolean vertical = moved.field_1351 != vy;
         onGround = vertical && vy < 0.0;
         if (vertical) {
            vy = 0.0;
         }

         vy = (vy - gravity) * 0.98;
         vx *= drag;
         vz *= drag;
         path[i] = new class_243((box.field_1323 + box.field_1320) / 2.0, box.field_1322, (box.field_1321 + box.field_1324) / 2.0);
      }

      return path;
   }

   @FunctionalInterface
   interface Collider {
      class_243 move(class_238 var1, class_243 var2);
   }
}
