package com.messerocks.crystal.utils;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class AimUtils {
   private static final class_310 mc = class_310.method_1551();

   private AimUtils() {
   }

   public static class_3965 lookingAtBlock(double range) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_243 eyes = mc.field_1724.method_33571();
         class_243 end = eyes.method_1019(mc.field_1724.method_5828(1.0F).method_1021(range));
         class_3965 hit = mc.field_1687.method_17742(new class_3959(eyes, end, class_3960.field_17559, class_242.field_1348, mc.field_1724));
         return hit != null && hit.method_17783() == class_240.field_1332 ? hit : null;
      } else {
         return null;
      }
   }

   public static class_2338 lookingAt(double range) {
      class_3965 hit = lookingAtBlock(range);
      return hit == null ? null : hit.method_17777();
   }

   public static boolean inFieldOfView(class_243 pos, double margin) {
      if (mc.field_1724 != null && mc.field_1690 != null && mc.method_22683() != null) {
         double verticalFov = ((Integer)mc.field_1690.method_41808().method_41753()).intValue();
         double aspect = (double)mc.method_22683().method_4489() / Math.max(1, mc.method_22683().method_4506());
         double horizontalFov = Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(verticalFov) / 2.0) * aspect));
         double deltaYaw = Math.abs(Rotations.getYaw(pos) - mc.field_1724.method_36454());
         double deltaPitch = Math.abs(Rotations.getPitch(pos) - mc.field_1724.method_36455());
         return deltaYaw <= horizontalFov / 2.0 - margin && deltaPitch <= verticalFov / 2.0 - margin;
      } else {
         return false;
      }
   }

   public static boolean withinCone(class_243 pos, double maxAngle) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         class_243 direction = pos.method_1020(mc.field_1724.method_33571());
         if (direction.method_1027() < 1.0E-6) {
            return true;
         } else {
            double dot = mc.field_1724.method_5828(1.0F).method_1026(direction.method_1029());
            dot = Math.max(-1.0, Math.min(1.0, dot));
            return Math.toDegrees(Math.acos(dot)) <= maxAngle;
         }
      }
   }
}
