package com.messerocks.crystal.utils;

import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_638;
import net.minecraft.class_2350.class_2353;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public enum TargetState {
   Open,
   Burrowed,
   SoftBurrow,
   InHole,
   Trapped,
   Surrounded,
   SoftHole;

   public static final float HARD_RESISTANCE = 600.0F;

   private static class_310 mc() {
      return class_310.method_1551();
   }

   private static boolean isCover(class_638 world, class_2338 pos) {
      class_2680 state = world.method_8320(pos);
      return state.method_26215() ? false : state.method_26204().method_9520() >= 600.0F;
   }

   private static boolean isSoftCover(class_638 world, class_2338 pos) {
      class_2680 state = world.method_8320(pos);
      return !state.method_26215() && !(state.method_26204().method_9520() >= 600.0F) ? !state.method_26220(world, pos).method_1110() : false;
   }

   private static int burrow(class_638 world, class_1657 player) {
      class_238 box = player.method_5829();
      int result = 0;

      for (class_2338 pos : class_2338.method_10094(
         class_3532.method_15357(box.field_1323),
         class_3532.method_15357(box.field_1322),
         class_3532.method_15357(box.field_1321),
         class_3532.method_15357(box.field_1320 - 1.0E-7),
         class_3532.method_15357(box.field_1322 + 0.5),
         class_3532.method_15357(box.field_1324 - 1.0E-7)
      )) {
         class_2680 state = world.method_8320(pos);
         if (!state.method_26215()) {
            boolean inside = false;

            for (class_238 shape : state.method_26220(world, pos).method_1090()) {
               if (shape.method_996(pos).method_994(box)) {
                  inside = true;
                  break;
               }
            }

            if (inside) {
               if (state.method_26204().method_9520() >= 600.0F) {
                  return 2;
               }

               result = 1;
            }
         }
      }

      return result;
   }

   public static TargetState of(class_1657 player) {
      class_638 world = mc().field_1687;
      if (player != null && world != null) {
         class_2338 feet = player.method_24515();
         int hardFeet = 0;
         int softFeet = 0;
         int hardHead = 0;

         for (class_2350 side : class_2353.field_11062) {
            class_2338 wall = feet.method_10093(side);
            if (isCover(world, wall)) {
               hardFeet++;
            } else if (isSoftCover(world, wall)) {
               softFeet++;
            }

            if (isCover(world, wall.method_10084())) {
               hardHead++;
            }
         }

         int ceilingY = class_3532.method_15357(player.method_5829().field_1325 - 1.0E-7) + 1;
         boolean ceiling = isCover(world, new class_2338(feet.method_10263(), ceilingY, feet.method_10260()));
         int burrow = burrow(world, player);
         return classify(burrow == 2, burrow == 1, hardFeet, softFeet, hardHead, ceiling);
      } else {
         return Open;
      }
   }

   static TargetState classify(boolean hardBurrow, boolean softBurrow, int hardFeet, int softFeet, int hardHead, boolean ceiling) {
      if (hardBurrow) {
         return Burrowed;
      } else if (hardFeet >= 4) {
         if (ceiling) {
            return Trapped;
         } else {
            return hardHead >= 4 ? InHole : Surrounded;
         }
      } else if (softBurrow) {
         return SoftBurrow;
      } else {
         return hardFeet + softFeet >= 4 ? SoftHole : Open;
      }
   }

   public static float exposure(class_1657 player, class_243 explosion) {
      class_638 world = mc().field_1687;
      if (player != null && world != null) {
         class_238 box = player.method_5829();
         double d = 1.0 / ((box.field_1320 - box.field_1323) * 2.0 + 1.0);
         double e = 1.0 / ((box.field_1325 - box.field_1322) * 2.0 + 1.0);
         double f = 1.0 / ((box.field_1324 - box.field_1321) * 2.0 + 1.0);
         double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
         double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
         if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
            int misses = 0;
            int total = 0;

            for (double k = 0.0; k <= 1.0; k += d) {
               for (double l = 0.0; l <= 1.0; l += e) {
                  for (double m = 0.0; m <= 1.0; m += f) {
                     class_243 point = new class_243(
                        class_3532.method_16436(k, box.field_1323, box.field_1320) + g,
                        class_3532.method_16436(l, box.field_1322, box.field_1325),
                        class_3532.method_16436(m, box.field_1321, box.field_1324) + h
                     );
                     if (world.method_17742(new class_3959(point, explosion, class_3960.field_17558, class_242.field_1348, player)).method_17783()
                        == class_240.field_1333) {
                        misses++;
                     }

                     total++;
                  }
               }
            }

            return total == 0 ? 0.0F : (float)misses / total;
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   public boolean crystalWorks() {
      return this == Open || this == SoftBurrow || this == SoftHole;
   }

   public boolean firstCrystalOpens() {
      return this == SoftBurrow || this == SoftHole;
   }

   public boolean cityable() {
      return this == Surrounded || this == InHole || this == Trapped;
   }

   public String label() {
      return switch (this) {
         case Open -> "open";
         case Burrowed -> "burrowed";
         case SoftBurrow -> "soft burrow";
         case InHole -> "deep hole";
         case Trapped -> "trapped";
         case Surrounded -> "surrounded";
         case SoftHole -> "soft hole";
      };
   }
}
