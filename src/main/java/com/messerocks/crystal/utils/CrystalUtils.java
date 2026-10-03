package com.messerocks.crystal.utils;

import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class CrystalUtils {
   private static final class_310 mc = class_310.method_1551();

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
      if (mc.field_1687 != null && isBase(base)) {
         class_2338 above = base.method_10084();
         if (!mc.field_1687.method_22347(above)) {
            return false;
         } else {
            return legacy && !mc.field_1687.method_22347(above.method_10084()) ? false : !isObstructed(above, ignoreCrystals);
         }
      } else {
         return false;
      }
   }

   public static boolean isObstructed(class_2338 above, boolean ignoreCrystals) {
      List<class_1297> entities = mc.field_1687.method_8335(null, crystalBox(above));
      if (entities.isEmpty()) {
         return false;
      } else if (!ignoreCrystals) {
         return true;
      } else {
         for (class_1297 entity : entities) {
            if (!(entity instanceof class_1511)) {
               return true;
            }
         }

         return false;
      }
   }

   public static class_238 crystalBox(class_2338 above) {
      return new class_238(
         above.method_10263(), above.method_10264(), above.method_10260(), above.method_10263() + 1.0, above.method_10264() + 2.0, above.method_10260() + 1.0
      );
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
               if (player.method_5829().method_997(velocity.method_1021(lookaheadTicks)).method_994(box)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean canSee(class_243 pos) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_3965 result = mc.field_1687
            .method_17742(new class_3959(mc.field_1724.method_33571(), pos, class_3960.field_17558, class_242.field_1348, mc.field_1724));
         return result == null || result.method_17783() == class_240.field_1333;
      } else {
         return false;
      }
   }

   public static boolean inRange(class_243 pos, double range, double wallRange) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         double distance = mc.field_1724.method_33571().method_1022(pos);
         return distance > range ? false : distance <= wallRange || canSee(pos);
      }
   }
}
