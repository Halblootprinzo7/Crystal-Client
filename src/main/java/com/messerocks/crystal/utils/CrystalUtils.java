package com.messerocks.crystal.utils;

import java.util.function.Predicate;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;

public final class CrystalUtils {
   private static final class_310 mc = class_310.method_1551();
   private static final Predicate<class_1297> NOTHING = entity -> false;
   private static final Predicate<class_1297> CRYSTALS = entity -> entity instanceof class_1511;

   private CrystalUtils() {
   }

   public static class_243 crystalPos(class_2338 base) {
      return new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5);
   }

   public static boolean isBase(class_2338 pos) {
      if (mc.field_1687 == null) {
         return false;
      } else {
         class_2680 state = mc.field_1687.method_8320(pos);
         return state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987);
      }
   }

   public static boolean canPlace(class_2338 base, boolean legacy, boolean ignoreCrystals) {
      return canPlace(base, legacy, ignoreCrystals ? CRYSTALS : NOTHING);
   }

   public static boolean canPlace(class_2338 base, boolean legacy, Predicate<class_1297> ignore) {
      if (mc.field_1687 != null && isBase(base)) {
         class_2338 above = base.method_10084();
         if (!mc.field_1687.method_22347(above)) {
            return false;
         } else {
            return legacy && !mc.field_1687.method_22347(above.method_10084()) ? false : !isObstructed(above, ignore);
         }
      } else {
         return false;
      }
   }

   public static boolean isObstructed(class_2338 above, boolean ignoreCrystals) {
      return isObstructed(above, ignoreCrystals ? CRYSTALS : NOTHING);
   }

   public static boolean isObstructed(class_2338 above, Predicate<class_1297> ignore) {
      for (class_1297 entity : mc.field_1687.method_8335(null, crystalBox(above))) {
         if (!ignore.test(entity)) {
            return true;
         }
      }

      return false;
   }

   public static class_238 crystalBox(class_2338 above) {
      return new class_238(
         above.method_10263(), above.method_10264(), above.method_10260(), above.method_10263() + 1.0, above.method_10264() + 2.0, above.method_10260() + 1.0
      );
   }

   public static class_238 crystalHitbox(class_2338 base) {
      double x = base.method_10263() + 0.5;
      double y = base.method_10264() + 1.0;
      double z = base.method_10260() + 0.5;
      return new class_238(x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0);
   }

   public static int confirmTicks(int latencyMs) {
      int travel = (int)Math.ceil(Math.max(0, latencyMs) / 50.0);
      return Math.min(20, travel + 2);
   }

   public static boolean playerMovingInto(class_2338 above, int lookaheadTicks) {
      return playerMovingInto(above, lookaheadTicks, null, null);
   }

   public static boolean playerMovingInto(class_2338 above, int lookaheadTicks, class_1657 special, class_243 specialVelocity) {
      if (mc.field_1687 != null && lookaheadTicks > 0) {
         class_238 box = crystalBox(above);

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724) {
               class_243 velocity = player == special && specialVelocity != null ? specialVelocity : player.method_18798();
               class_238 moving = player.method_5829();

               for (int tick = 1; tick <= lookaheadTicks; tick++) {
                  moving = moving.method_997(velocity);
                  if (moving.method_994(box)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
