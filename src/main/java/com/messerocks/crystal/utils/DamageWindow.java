package com.messerocks.crystal.utils;

import net.minecraft.class_1309;

public final class DamageWindow {
   public static final int WINDOW_TICKS = 10;

   private DamageWindow() {
   }

   public static boolean reduced(class_1309 target) {
      return target != null && target.field_6235 > 0;
   }

   public static boolean openForFullDamage(class_1309 target) {
      return target != null && target.field_6235 <= 0;
   }

   public static int ticksUntilOpen(class_1309 target) {
      return target == null ? 0 : Math.max(0, target.field_6235);
   }
}
