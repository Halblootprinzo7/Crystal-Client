package com.messerocks.crystal.utils;

import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_2350.class_2353;

public enum TargetState {
   Open,
   Burrowed,
   InHole,
   Surrounded;

   private static final class_310 mc = class_310.method_1551();

   private static boolean isCover(class_2338 pos) {
      if (mc.field_1687 == null) {
         return false;
      } else {
         class_2680 state = mc.field_1687.method_8320(pos);
         return state.method_26215() ? false : state.method_26204().method_9520() >= 600.0F;
      }
   }

   private static boolean isSolid(class_2338 pos) {
      return mc.field_1687 != null && !mc.field_1687.method_8320(pos).method_26215();
   }

   private static int wallsAround(class_2338 pos) {
      int walls = 0;

      for (class_2350 side : class_2353.field_11062) {
         if (isCover(pos.method_10093(side))) {
            walls++;
         }
      }

      return walls;
   }

   public static TargetState of(class_1657 player) {
      if (player != null && mc.field_1687 != null) {
         class_2338 feet = player.method_24515();
         if (isSolid(feet)) {
            return Burrowed;
         } else {
            int atFeet = wallsAround(feet);
            if (atFeet < 4) {
               return Open;
            } else {
               return wallsAround(feet.method_10084()) >= 4 ? InHole : Surrounded;
            }
         }
      } else {
         return Open;
      }
   }

   public boolean crystalWorks() {
      return this == Open;
   }

   public String label() {
      return switch (this) {
         case Open -> "open";
         case Burrowed -> "burrowed";
         case InHole -> "in hole";
         case Surrounded -> "surrounded";
      };
   }
}
