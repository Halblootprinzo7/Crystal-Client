package com.messerocks.crystal.utils;

import com.messerocks.crystal.modules.Stealth;
import java.lang.reflect.Field;
import java.util.List;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.class_310;
import net.minecraft.class_3532;

public final class TurnProgress {
   public static final TurnProgress SHARED = new TurnProgress();
   public static final int DEFAULT_PRIORITY = 50;
   private static final int RETURN_PRIORITY = -100;
   private static final double MIN_JERK_PEAK = 1.875;
   private static final class_310 mc = class_310.method_1551();
   private int lastSentTick = -1;
   private boolean planned;
   private double startYaw;
   private double startPitch;
   private double targetYaw;
   private double targetPitch;
   private int planTicks;
   private int planStep;
   private Object owner;
   private int ownerPriority;
   private int ownerLastCallTick = -1;
   private Object player;
   private static Field rotationQueue;

   public void reset() {
      this.planned = false;
      this.planStep = 0;
      this.owner = null;
      this.ownerPriority = 0;
      this.ownerLastCallTick = -1;
   }

   public void reset(Object requester) {
      if (this.owner == null || this.owner == requester) {
         this.reset();
      }
   }

   public boolean wouldReach(double yaw, double pitch) {
      return this.wouldReach(null, yaw, pitch, DEFAULT_PRIORITY);
   }

   public boolean wouldReach(Object requester, double yaw, double pitch) {
      return this.wouldReach(requester, yaw, pitch, DEFAULT_PRIORITY);
   }

   public boolean wouldReach(Object requester, double yaw, double pitch, int priority) {
      if (mc.field_1724 == null) {
         return false;
      } else if (!this.claim(requester, priority)) {
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
      } else if (!this.claim(requester, priority)) {
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

               stepYaw = VanillaLimits.limitTurn(serverYaw(), stepYaw, cap);
               stepPitch = class_3532.method_15350(VanillaLimits.limitPitch(serverPitch(), stepPitch, cap), -90.0, 90.0);
               Rotations.rotate(stepYaw, stepPitch, priority);
               this.lastSentTick = mc.field_1724.field_6012;
               return false;
            }
         }
      }
   }

   // Meteor holds the last rotation for a few ticks and then drops straight back to the camera in one packet,
   // however far away that is. This runs after every module had its chance this tick and only fills ticks in
   // which nobody turned: while a module still owns the turn (waiting on its budget, say) the head is kept where
   // it is, and once nobody does it is walked back to the camera inside max-turn-per-tick.
   public void easeBack() {
      if (mc.field_1724 == null) {
         return;
      }

      this.forgetIfNewPlayer();
      double cap = Stealth.turnCap();
      int age = mc.field_1724.field_6012;
      if (!Rotations.rotating || cap <= 0.0 || this.lastSentTick == age || rotationQueued()) {
         return;
      }

      double fromYaw = Rotations.serverYaw;
      double fromPitch = Rotations.serverPitch;
      if (this.owner != null && age - this.ownerLastCallTick <= 2) {
         // Same angles again: no look packet goes out, Meteor just does not let go yet.
         Rotations.rotate(fromYaw, fromPitch, RETURN_PRIORITY);
         return;
      }

      double yawLeft = class_3532.method_15338(mc.field_1724.method_36454() - fromYaw);
      double pitchLeft = mc.field_1724.method_36455() - fromPitch;
      double distance = Math.max(Math.abs(yawLeft), Math.abs(pitchLeft));
      if (distance <= cap) {
         return;
      }

      double step = class_3532.method_15350(distance * (1.0 - 0.4 * Stealth.aimSmoothness()), cap * 0.5, cap);
      double scale = step / distance;
      Rotations.rotate(fromYaw + yawLeft * scale, class_3532.method_15350(fromPitch + pitchLeft * scale, -90.0, 90.0), RETURN_PRIORITY);
      this.lastSentTick = age;
   }

   // Something else (a Meteor module) already asked for a rotation this tick. Adding ours would make Meteor send
   // it as a second look packet in the same tick, which is worse than whatever we were about to fix.
   private static boolean rotationQueued() {
      try {
         if (rotationQueue == null) {
            rotationQueue = Rotations.class.getDeclaredField("rotations");
            rotationQueue.setAccessible(true);
         }

         return !((List<?>)rotationQueue.get(null)).isEmpty();
      } catch (ReflectiveOperationException | RuntimeException e) {
         return false;
      }
   }

   // Where the server last saw the head: the held rotation while Meteor is rotating, otherwise the camera.
   private static double serverYaw() {
      return Rotations.rotating ? Rotations.serverYaw : mc.field_1724.method_36454();
   }

   private static double serverPitch() {
      return Rotations.rotating ? Rotations.serverPitch : mc.field_1724.method_36455();
   }

   private void plan(double yaw, double pitch, double cap, double smooth) {
      if (!this.planned || !this.targetClose(yaw, pitch, cap) || this.planStep + 1 >= this.planTicks && !this.canReachInOneStep(yaw, pitch, cap)) {
         this.startYaw = serverYaw();
         this.startPitch = serverPitch();
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
         return ticksFor(serverYaw(), serverPitch(), yaw, pitch, cap, smooth);
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
      return Math.max(Math.abs(class_3532.method_15338(yaw - serverYaw())), Math.abs(pitch - serverPitch())) <= cap;
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

   // The owner keeps the turn while it keeps asking. Only a strictly more important requester may take it
   // over, so a key press (the macros) is never starved by an aura that wants the head every tick.
   private boolean claim(Object requester, int priority) {
      this.forgetIfNewPlayer();
      if (this.owner != null && this.owner != requester) {
         if (mc.field_1724.field_6012 - this.ownerLastCallTick <= 2 && priority <= this.ownerPriority) {
            return false;
         }

         this.reset();
      }

      this.owner = requester;
      this.ownerPriority = priority;
      this.ownerLastCallTick = mc.field_1724.field_6012;
      return true;
   }
}
