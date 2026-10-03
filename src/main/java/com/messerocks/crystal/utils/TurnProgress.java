package com.messerocks.crystal.utils;

import com.messerocks.crystal.modules.Stealth;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.class_310;
import net.minecraft.class_3532;

public final class TurnProgress {
   public static final TurnProgress SHARED = new TurnProgress();
   private static final double MIN_JERK_PEAK = 1.875;
   private static final class_310 mc = class_310.method_1551();
   private double sentYaw = Double.NaN;
   private double sentPitch = Double.NaN;
   private int lastSentTick = -1;
   private boolean planned;
   private double startYaw;
   private double startPitch;
   private double targetYaw;
   private double targetPitch;
   private int planTicks;
   private int planStep;
   private Object owner;
   private int ownerLastCallTick = -1;
   private Object player;

   public void reset() {
      this.sentYaw = Double.NaN;
      this.sentPitch = Double.NaN;
      this.planned = false;
      this.planStep = 0;
      this.owner = null;
      this.ownerLastCallTick = -1;
   }

   public void reset(Object requester) {
      if (this.owner == null || this.owner == requester) {
         this.reset();
      }
   }

   public boolean wouldReach(double yaw, double pitch) {
      return this.wouldReach(null, yaw, pitch);
   }

   public boolean wouldReach(Object requester, double yaw, double pitch) {
      if (mc.field_1724 == null) {
         return false;
      } else if (!this.claim(requester)) {
         return false;
      } else {
         double cap = Stealth.turnCap();
         if (cap <= 0.0) {
            return true;
         } else {
            return mc.field_1724.field_6012 == this.lastSentTick ? false : this.remainingTicks(yaw, pitch, cap) <= 1;
         }
      }
   }

   public boolean turnTo(double yaw, double pitch, int priority, Runnable action) {
      return this.turnTo(null, yaw, pitch, priority, action);
   }

   public boolean turnTo(Object requester, double yaw, double pitch, int priority, Runnable action) {
      if (mc.field_1724 == null) {
         return false;
      } else if (!this.claim(requester)) {
         return false;
      } else {
         double cap = Stealth.turnCap();
         if (cap <= 0.0) {
            Rotations.rotate(yaw, pitch, priority, action);
            this.reset();
            return true;
         } else if (mc.field_1724.field_6012 == this.lastSentTick) {
            return false;
         } else {
            double smooth = Stealth.aimSmoothness();
            this.plan(yaw, pitch, cap, smooth);
            this.planStep++;
            if (this.planStep >= this.planTicks) {
               Rotations.rotate(yaw, pitch, priority, action);
               this.lastSentTick = mc.field_1724.field_6012;
               this.reset();
               return true;
            } else {
               double progress = ease((double)this.planStep / this.planTicks, smooth);
               double stepYaw = this.startYaw + class_3532.method_15338(this.targetYaw - this.startYaw) * progress;
               double stepPitch = this.startPitch + (this.targetPitch - this.startPitch) * progress;
               if (smooth > 0.0) {
                  stepYaw += (Math.random() * 2.0 - 1.0) * 0.2 * smooth;
                  stepPitch += (Math.random() * 2.0 - 1.0) * 0.1 * smooth;
               }

               double fromYaw = Double.isNaN(this.sentYaw) ? mc.field_1724.method_36454() : this.sentYaw;
               double fromPitch = Double.isNaN(this.sentPitch) ? mc.field_1724.method_36455() : this.sentPitch;
               stepYaw = VanillaLimits.limitTurn(fromYaw, stepYaw, cap);
               stepPitch = class_3532.method_15350(VanillaLimits.limitPitch(fromPitch, stepPitch, cap), -90.0, 90.0);
               Rotations.rotate(stepYaw, stepPitch, priority);
               this.lastSentTick = mc.field_1724.field_6012;
               this.sentYaw = stepYaw;
               this.sentPitch = stepPitch;
               return false;
            }
         }
      }
   }

   private void plan(double yaw, double pitch, double cap, double smooth) {
      if (!this.planned || !this.targetClose(yaw, pitch, cap) || this.planStep + 1 >= this.planTicks && !this.canReachInOneStep(yaw, pitch, cap)) {
         this.startYaw = Double.isNaN(this.sentYaw) ? mc.field_1724.method_36454() : this.sentYaw;
         this.startPitch = Double.isNaN(this.sentPitch) ? mc.field_1724.method_36455() : this.sentPitch;
         this.targetYaw = yaw;
         this.targetPitch = pitch;
         this.planTicks = ticksFor(this.startYaw, this.startPitch, yaw, pitch, cap, smooth);
         this.planStep = 0;
         this.planned = true;
      } else {
         this.targetYaw = yaw;
         this.targetPitch = pitch;
      }
   }

   private int remainingTicks(double yaw, double pitch, double cap) {
      double smooth = Stealth.aimSmoothness();
      if (!this.planned || !this.targetClose(yaw, pitch, cap) || this.planStep + 1 >= this.planTicks && !this.canReachInOneStep(yaw, pitch, cap)) {
         double fromYaw = Double.isNaN(this.sentYaw) ? mc.field_1724.method_36454() : this.sentYaw;
         double fromPitch = Double.isNaN(this.sentPitch) ? mc.field_1724.method_36455() : this.sentPitch;
         return ticksFor(fromYaw, fromPitch, yaw, pitch, cap, smooth);
      } else {
         return this.planTicks - this.planStep;
      }
   }

   private static int ticksFor(double fromYaw, double fromPitch, double yaw, double pitch, double cap, double smooth) {
      double distance = Math.max(Math.abs(class_3532.method_15338(yaw - fromYaw)), Math.abs(pitch - fromPitch));
      if (distance <= cap) {
         return 1;
      } else {
         double straight = Math.ceil(distance / cap);
         return Math.max(1, (int)Math.ceil(straight * (1.0 + 0.875 * smooth)));
      }
   }

   private static double ease(double t, double smooth) {
      double minJerk = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
      return t + (minJerk - t) * smooth;
   }

   private boolean targetClose(double yaw, double pitch, double cap) {
      double tolerance = Math.max(0.5, cap * 0.5);
      return Math.abs(class_3532.method_15338(this.targetYaw - yaw)) <= tolerance && Math.abs(this.targetPitch - pitch) <= tolerance;
   }

   private boolean canReachInOneStep(double yaw, double pitch, double cap) {
      double fromYaw = Double.isNaN(this.sentYaw) ? mc.field_1724.method_36454() : this.sentYaw;
      double fromPitch = Double.isNaN(this.sentPitch) ? mc.field_1724.method_36455() : this.sentPitch;
      return Math.max(Math.abs(class_3532.method_15338(yaw - fromYaw)), Math.abs(pitch - fromPitch)) <= cap;
   }

   public boolean heldByOther(Object requester) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         this.forgetIfNewPlayer();
         return this.owner != null && this.owner != requester && mc.field_1724.field_6012 - this.ownerLastCallTick <= 2;
      }
   }

   private void forgetIfNewPlayer() {
      if (mc.field_1724 != this.player) {
         this.reset();
         this.lastSentTick = -1;
         this.player = mc.field_1724;
      }
   }

   private boolean claim(Object requester) {
      this.forgetIfNewPlayer();
      if (this.owner != null && this.owner != requester) {
         if (mc.field_1724.field_6012 - this.ownerLastCallTick <= 2) {
            return false;
         }

         this.reset();
      }

      this.owner = requester;
      this.ownerLastCallTick = mc.field_1724.field_6012;
      return true;
   }
}
