package com.messerocks.crystal.utils;

import java.util.Random;
import net.minecraft.class_1297;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class VanillaLimits {
   private static final class_310 mc = class_310.method_1551();
   private static final Random random = new Random();

   private VanillaLimits() {
   }

   public static boolean canReachBlock(class_2338 pos) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         double range = mc.field_1724.method_55754();
         return new class_238(pos).method_49271(mc.field_1724.method_33571()) <= range * range;
      }
   }

   public static boolean canReachEntity(class_1297 entity) {
      if (mc.field_1724 != null && entity != null) {
         double range = mc.field_1724.method_55755();
         return entity.method_5829().method_49271(mc.field_1724.method_33571()) < range * range;
      } else {
         return false;
      }
   }

   public static double blockRange() {
      return mc.field_1724 == null ? 4.5 : mc.field_1724.method_55754();
   }

   public static double entityRange() {
      return mc.field_1724 == null ? 3.0 : mc.field_1724.method_55755();
   }

   public static boolean hasLineOfSight(class_243 pos) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_3965 hit = mc.field_1687
            .method_17742(new class_3959(mc.field_1724.method_33571(), pos, class_3960.field_17558, class_242.field_1348, mc.field_1724));
         return hit != null && hit.method_17783() != class_240.field_1333
            ? hit.method_17783() == class_240.field_1332 && new class_238(hit.method_17777()).method_1014(1.0E-4).method_1006(pos)
            : true;
      } else {
         return false;
      }
   }

   public static double limitTurn(double currentYaw, double wantedYaw, double maxStep) {
      if (maxStep <= 0.0) {
         return wantedYaw;
      } else {
         double delta = wrapDegrees(wantedYaw - currentYaw);
         return Math.abs(delta) <= maxStep ? wantedYaw : currentYaw + Math.copySign(maxStep, delta);
      }
   }

   public static double limitPitch(double currentPitch, double wantedPitch, double maxStep) {
      if (maxStep <= 0.0) {
         return wantedPitch;
      } else {
         double delta = wantedPitch - currentPitch;
         return Math.abs(delta) <= maxStep ? wantedPitch : currentPitch + Math.copySign(maxStep, delta);
      }
   }

   private static double wrapDegrees(double degrees) {
      degrees %= 360.0;
      if (degrees >= 180.0) {
         degrees -= 360.0;
      }

      if (degrees < -180.0) {
         degrees += 360.0;
      }

      return degrees;
   }

   public static int jitter(int ticks, double jitter) {
      if (!(jitter <= 0.0) && ticks > 0) {
         double spread = ticks * Math.min(1.0, jitter);
         int offset = (int)Math.round((random.nextDouble() * 2.0 - 1.0) * spread);
         return Math.max(0, ticks + offset);
      } else {
         return ticks;
      }
   }

   public static boolean roll(double chance) {
      return chance > 0.0 && random.nextDouble() < chance;
   }
}
