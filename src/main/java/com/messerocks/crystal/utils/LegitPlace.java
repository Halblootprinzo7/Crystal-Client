package com.messerocks.crystal.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1268;
import net.minecraft.class_1750;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2272;
import net.minecraft.class_2328;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2362;
import net.minecraft.class_238;
import net.minecraft.class_2401;
import net.minecraft.class_243;
import net.minecraft.class_2457;
import net.minecraft.class_2462;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_3830;
import net.minecraft.class_3959;
import net.minecraft.class_3962;
import net.minecraft.class_3965;
import net.minecraft.class_4969;
import net.minecraft.class_5545;
import net.minecraft.class_5803;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class LegitPlace {
   private static final class_310 mc = class_310.method_1551();
   private static final double OFF_CENTRE = 0.6;

   private LegitPlace() {
   }

   public static LegitPlace.Result forCrystal(class_2338 base, double reach) {
      return clickOn(base, reach);
   }

   public static LegitPlace.Result forExistingBlock(class_2338 pos, double reach) {
      return clickOn(pos, reach);
   }

   public static LegitPlace.Result forBlock(class_2338 pos, double reach) {
      return forBlock(pos, reach, null);
   }

   public static LegitPlace.Result forBlock(class_2338 pos, double reach, class_2338 exclude) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         Predicate<class_3965> placesHere = hit -> placesAt(hit, pos, exclude);
         LegitPlace.Result result = best(placementPoints(pos, exclude, false, reach), reach, placesHere);
         return result != null ? result : best(placementPoints(pos, exclude, true, reach), reach, placesHere);
      } else {
         return null;
      }
   }

   public static boolean isInteractive(class_2680 state) {
      class_2248 block = state.method_26204();
      return BlockUtils.isClickable(block)
         || block instanceof class_4969
         || block instanceof class_2401
         || block instanceof class_2462
         || block instanceof class_2272
         || block instanceof class_5545
         || block instanceof class_2328
         || block instanceof class_2457
         || block instanceof class_3830
         || block instanceof class_5803
         || block instanceof class_3962
         || block instanceof class_2362;
   }

   private static LegitPlace.Result clickOn(class_2338 block, double reach) {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         return null;
      } else if (mc.field_1687.method_8320(block).method_26215()) {
         return null;
      } else {
         Predicate<class_3965> onBlock = hit -> hit.method_17777().equals(block);
         LegitPlace.Result result = best(facePoints(block, null, false, reach), reach, onBlock);
         return result != null ? result : best(facePoints(block, null, true, reach), reach, onBlock);
      }
   }

   private static List<class_243> placementPoints(class_2338 pos, class_2338 exclude, boolean offCentre, double reach) {
      List<class_243> points = new ArrayList<>();
      if (!offCentre) {
         for (class_238 box : mc.field_1687.method_8320(pos).method_26218(mc.field_1687, pos).method_1090()) {
            points.add(box.method_996(pos).method_1005());
         }
      }

      for (class_2350 toNeighbour : class_2350.values()) {
         class_2338 neighbour = pos.method_10093(toNeighbour);
         if (!neighbour.equals(exclude)) {
            points.addAll(facePoints(neighbour, toNeighbour.method_10153(), offCentre, reach));
         }
      }

      return points;
   }

   private static List<class_243> facePoints(class_2338 block, class_2350 only, boolean offCentre, double reach) {
      List<class_243> points = new ArrayList<>();
      class_243 eyes = mc.field_1724.method_33571();

      for (class_238 local : mc.field_1687.method_8320(block).method_26218(mc.field_1687, block).method_1090()) {
         class_238 box = local.method_996(block);
         if (!(box.method_49271(eyes) > reach * reach)) {
            class_243 centre = box.method_1005();

            for (class_2350 face : class_2350.values()) {
               if (only == null || face == only) {
                  class_243 faceCentre = new class_243(
                     face.method_10148() == 0 ? centre.field_1352 : (face.method_10148() > 0 ? box.field_1320 : box.field_1323),
                     face.method_10164() == 0 ? centre.field_1351 : (face.method_10164() > 0 ? box.field_1325 : box.field_1322),
                     face.method_10165() == 0 ? centre.field_1350 : (face.method_10165() > 0 ? box.field_1324 : box.field_1321)
                  );
                  class_243 toEyes = eyes.method_1020(faceCentre);
                  double facing = toEyes.field_1352 * face.method_10148() + toEyes.field_1351 * face.method_10164() + toEyes.field_1350 * face.method_10165();
                  if (!(facing <= 1.0E-6)) {
                     if (!offCentre) {
                        points.add(faceCentre);
                        class_243 nearest = new class_243(
                           face.method_10166() == class_2351.field_11048 ? faceCentre.field_1352 : clampInside(eyes.field_1352, box.field_1323, box.field_1320),
                           face.method_10166() == class_2351.field_11052 ? faceCentre.field_1351 : clampInside(eyes.field_1351, box.field_1322, box.field_1325),
                           face.method_10166() == class_2351.field_11051 ? faceCentre.field_1350 : clampInside(eyes.field_1350, box.field_1321, box.field_1324)
                        );
                        if (nearest.method_1025(faceCentre) > 1.0E-6) {
                           points.add(nearest);
                        }
                     } else {
                        double ax = box.method_17939() / 2.0 * 0.6;
                        double ay = box.method_17940() / 2.0 * 0.6;
                        double az = box.method_17941() / 2.0 * 0.6;

                        for (int du = -1; du <= 1; du++) {
                           for (int dv = -1; dv <= 1; dv++) {
                              if (du != 0 || dv != 0) {
                                 points.add(switch (face.method_10166()) {
                                    case field_11048 -> faceCentre.method_1031(0.0, du * ay, dv * az);
                                    case field_11052 -> faceCentre.method_1031(du * ax, 0.0, dv * az);
                                    case field_11051 -> faceCentre.method_1031(du * ax, dv * ay, 0.0);
                                    default -> throw new MatchException(null, null);
                                 });
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return points;
   }

   private static double clampInside(double value, double min, double max) {
      double inset = Math.min(0.05, (max - min) / 4.0);
      return class_3532.method_15350(value, min + inset, max - inset);
   }

   private static LegitPlace.Result best(List<class_243> points, double reach, Predicate<class_3965> accept) {
      LegitPlace.Result best = null;
      double bestCost = Double.MAX_VALUE;

      for (class_243 aim : points) {
         LegitPlace.Result candidate = cast(aim, reach, accept);
         if (candidate != null) {
            double cost = turnCost(candidate.yaw(), candidate.pitch());
            if (cost < bestCost) {
               bestCost = cost;
               best = candidate;
            }
         }
      }

      return best;
   }

   private static LegitPlace.Result cast(class_243 aim, double reach, Predicate<class_3965> accept) {
      double yaw = Rotations.getYaw(aim);
      double pitch = Rotations.getPitch(aim);
      class_3965 hit = along(yaw, pitch, reach);
      return hit != null && accept.test(hit) ? new LegitPlace.Result(hit.method_17777(), hit.method_17780(), hit.method_17784(), yaw, pitch) : null;
   }

   public static class_3965 along(double yaw, double pitch, double reach) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_243 eyes = mc.field_1724.method_33571();
         class_243 direction = class_243.method_1030((float)pitch, (float)yaw);
         class_3965 hit = mc.field_1687
            .method_17742(new class_3959(eyes, eyes.method_1019(direction.method_1021(reach)), class_3960.field_17559, class_242.field_1348, mc.field_1724));
         return hit != null && hit.method_17783() == class_240.field_1332 ? hit : null;
      } else {
         return null;
      }
   }

   public static class_3965 confirmCrystal(class_2338 base, double yaw, double pitch, double reach) {
      class_3965 hit = along(yaw, pitch, reach);
      return hit != null && hit.method_17777().equals(base) ? hit : null;
   }

   public static class_3965 confirm(class_3965 planned, double yaw, double pitch, double reach) {
      class_3965 hit = along(yaw, pitch, reach);
      if (hit == null || !hit.method_17777().equals(planned.method_17777())) {
         return null;
      } else if (isInteractive(mc.field_1687.method_8320(hit.method_17777()))) {
         return hit;
      } else {
         class_1750 now = new class_1750(mc.field_1724, class_1268.field_5808, class_1799.field_8037, hit);
         class_1750 then = new class_1750(mc.field_1724, class_1268.field_5808, class_1799.field_8037, planned);
         return now.method_7716() && now.method_8037().equals(then.method_8037()) ? hit : null;
      }
   }

   private static boolean placesAt(class_3965 hit, class_2338 pos, class_2338 exclude) {
      class_2338 clicked = hit.method_17777();
      if (clicked.equals(exclude)) {
         return false;
      } else if (!clicked.equals(pos) && isInteractive(mc.field_1687.method_8320(clicked))) {
         return false;
      } else {
         class_1750 context = new class_1750(mc.field_1724, class_1268.field_5808, class_1799.field_8037, hit);
         return context.method_7716() && context.method_8037().equals(pos);
      }
   }

   private static double turnCost(double yaw, double pitch) {
      if (mc.field_1724 == null) {
         return 0.0;
      } else {
         double fromYaw = Rotations.rotating ? Rotations.serverYaw : mc.field_1724.method_36454();
         double fromPitch = Rotations.rotating ? Rotations.serverPitch : mc.field_1724.method_36455();
         return Math.abs(class_3532.method_15338(yaw - fromYaw)) + Math.abs(pitch - fromPitch);
      }
   }

   public record Result(class_2338 support, class_2350 side, class_243 hitVec, double yaw, double pitch) {
      public class_3965 hit() {
         return new class_3965(this.hitVec, this.side, this.support, false);
      }
   }
}
