package dev.crystaladdon.utils;

import dev.crystaladdon.mixin.MinecraftClientAccessor;
import dev.crystaladdon.modules.Stealth;
import java.lang.reflect.Field;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent.Post;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2824;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_310;
import net.minecraft.class_315;
import net.minecraft.class_3532;

public final class TurnProgress {
   public static final TurnProgress SHARED = new TurnProgress();
   private static final double MIN_JERK_PEAK = 1.875;
   private static final class_310 mc = class_310.method_1551();
   private double sentYaw = Double.NaN;
   private double sentPitch = Double.NaN;
   private int lastSentTick = -1;
   private TurnProgress.Batch queued;
   private int clientTick;
   private int movementSentTick = -1;
   private int tickRotationAt = -1;
   private double tickYaw;
   private double tickPitch;
   private int interactionTick = -1;
   private double packetYaw = Double.NaN;
   private double packetPitch = Double.NaN;
   private boolean blocked;
   private static final int METEOR_PRIORITY = Integer.MAX_VALUE;
   private boolean easing;
   private int lastModuleTick = -1;
   private boolean planned;
   private double startYaw;
   private double startPitch;
   private double targetYaw;
   private double targetPitch;
   private int planTicks;
   private int planStep;
   private Object owner;
   private int ownerLastCallTick = -1;
   private int ownerPriority;
   public static final int DEFAULT_PRIORITY = 50;
   private Object player;
   private static int cameraRequestUntil = -1;
   private static Field meteorQueue;
   private static boolean meteorQueueUnreadable;
   private static Field meteorHoldTimer;
   private static boolean meteorHoldUnwritable;

   public void reset() {
      this.sentYaw = Double.NaN;
      this.sentPitch = Double.NaN;
      this.planned = false;
      this.planStep = 0;
      this.owner = null;
      this.ownerLastCallTick = -1;
   }

   static boolean sameRotation(double yawA, double pitchA, double yawB, double pitchB) {
      return Math.abs(class_3532.method_15338(yawA - yawB)) < 0.001 && Math.abs(pitchA - pitchB) < 0.001;
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
      return this.wouldReach(requester, yaw, pitch, 50);
   }

   public boolean wouldReach(Object requester, double yaw, double pitch, int priority) {
      this.blocked = true;
      if (mc.field_1724 == null) {
         return false;
      } else if (this.movementSent()) {
         return false;
      } else if (refusesLook(yaw, pitch)) {
         return false;
      } else if (mc.field_1724.field_6012 != this.lastSentTick && this.interactionTick == this.clientTick) {
         return false;
      } else if (!this.claim(requester, priority)) {
         return false;
      } else if (mc.field_1724.field_6012 != this.lastSentTick) {
         this.blocked = false;
         double cap = Stealth.turnCap();
         return cap <= 0.0 ? true : this.remainingTicks(yaw, pitch, cap) <= 1;
      } else {
         return this.queued != null && this.queued.accepts(yaw, pitch);
      }
   }

   // The look already going out with this tick's movement packet, or null when none is queued yet. A further click in
   // the same tick can only use this look.
   public double[] lookThisTick() {
      return mc.field_1724 != null && mc.field_1724.field_6012 == this.lastSentTick && this.queued != null && !this.movementSent()
         ? new double[]{this.queued.yaw, this.queued.pitch}
         : null;
   }

   public boolean lastCallBlocked() {
      return this.blocked;
   }

   public boolean serverHas(double yaw, double pitch) {
      // packetYaw is the float that went out; compare in float too, or a long session's large unwrapped yaw rounds
      // outside the tolerance and a settled look is never recognised.
      return mc.field_1724 != null
         && !this.movementSent()
         && !Double.isNaN(this.packetYaw)
         && sameRotation(this.packetYaw, this.packetPitch, (float)yaw, (float)pitch);
   }

   public double serverLookYaw(double fallback) {
      return !Double.isNaN(this.packetYaw) && !this.movementSent() ? this.packetYaw : fallback;
   }

   public boolean wouldReachSettled(Object requester, double yaw, double pitch, int priority) {
      if (!this.serverHas(yaw, pitch)) {
         this.blocked = false;
         return false;
      } else {
         return this.wouldReach(requester, yaw, pitch, priority);
      }
   }

   public boolean turnToSettled(Object requester, double yaw, double pitch, int priority, Runnable action) {
      if (this.serverHas(yaw, pitch)) {
         return this.turnTo(requester, yaw, pitch, priority, action);
      } else {
         this.turnTo(requester, yaw, pitch, priority, null);
         return false;
      }
   }

   public boolean turnTo(double yaw, double pitch, int priority, Runnable action) {
      return this.turnTo(null, yaw, pitch, priority, action);
   }

   public boolean turnTo(Object requester, double yaw, double pitch, int priority, Runnable action) {
      this.blocked = true;
      if (mc.field_1724 == null) {
         return false;
      } else if (this.movementSent()) {
         return false;
      } else if (refusesLook(yaw, pitch)) {
         return false;
      } else if (mc.field_1724.field_6012 != this.lastSentTick && this.interactionTick == this.clientTick) {
         return false;
      } else if (!this.claim(requester, priority)) {
         return false;
      } else if (mc.field_1724.field_6012 == this.lastSentTick) {
         if (this.queued != null && this.queued.accepts(yaw, pitch)) {
            if (action != null) {
               action.run();
            }

            return true;
         } else {
            return false;
         }
      } else {
         this.blocked = false;
         double cap = Stealth.turnCap();
         if (cap <= 0.0) {
            this.queue(yaw, pitch);
            this.finishTurn(yaw, pitch);
            if (action != null) {
               action.run();
            }

            return true;
         } else {
            double smooth = Stealth.aimSmoothness();
            this.plan(yaw, pitch, cap, smooth);
            this.planStep++;
            if (this.planStep >= this.planTicks) {
               this.queue(yaw, pitch);
               this.finishTurn(yaw, pitch);
               if (action != null) {
                  action.run();
               }

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
               Rotations.rotate(stepYaw, stepPitch, Integer.MAX_VALUE);
               this.noteTickRotation(stepYaw, stepPitch);
               this.lastSentTick = mc.field_1724.field_6012;
               this.lastModuleTick = mc.field_1724.field_6012;
               this.queued = null;
               this.easing = true;
               this.sentYaw = stepYaw;
               this.sentPitch = stepPitch;
               return false;
            }
         }
      }
   }

   private void queue(double yaw, double pitch) {
      Rotations.rotate(yaw, pitch, Integer.MAX_VALUE);
      this.noteTickRotation(yaw, pitch);
      this.queued = new TurnProgress.Batch(yaw, pitch);
      this.lastSentTick = mc.field_1724.field_6012;
      this.lastModuleTick = mc.field_1724.field_6012;
      this.easing = true;
   }

   private boolean movementSent() {
      return this.movementSentTick == this.clientTick;
   }

   @EventHandler(
      priority = 10000
   )
   private void onTickStart(Pre event) {
      this.clientTick++;
   }

   @EventHandler(
      priority = 100
   )
   private void onMovementQueue(meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent.Pre event) {
      List<?> queue = meteorQueueList();
      if (queue != null && queue.size() > 1) {
         while (queue.size() > 1) {
            queue.remove(queue.size() - 1);
         }
      }
   }

   private static List<?> meteorQueueList() {
      if (meteorQueueUnreadable) {
         return null;
      } else {
         try {
            if (meteorQueue == null) {
               meteorQueue = Rotations.class.getDeclaredField("rotations");
               meteorQueue.setAccessible(true);
            }

            return (List<?>)meteorQueue.get(null);
         } catch (RuntimeException | ReflectiveOperationException var1) {
            meteorQueueUnreadable = true;
            return null;
         }
      }
   }

   @EventHandler(
      priority = -10000
   )
   private void onMovementSending(meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent.Pre event) {
      if (mc.field_1724 != null) {
         this.packetYaw = mc.field_1724.method_36454();
         this.packetPitch = mc.field_1724.method_36455();
      }
   }

   @EventHandler(
      priority = -10000
   )
   private void onMovementSent(Post event) {
      this.movementSentTick = this.clientTick;
   }

   public void easeBack() {
      if (mc.field_1724 != null) {
         this.forgetIfNewPlayer();
         if (mc.field_1724.field_6012 != this.lastSentTick) {
            if (this.interactionTick == this.clientTick) {
               if (Rotations.rotating && !meteorQueueBusy()) {
                  Rotations.rotate(Rotations.serverYaw, Rotations.serverPitch, Integer.MIN_VALUE);
                  this.finishEase(Rotations.serverYaw, Rotations.serverPitch);
               }
            } else if (this.easing) {
               double cap = Stealth.turnCap();
               if (cap <= 0.0) {
                  this.easing = false;
               } else {
                  int hold = (Integer)Config.get().rotationHoldTicks.get();
                  boolean moving = cameraNeeded();
                  if (moving || mc.field_1724.field_6012 - this.lastModuleTick >= Math.max(1, hold)) {
                     if (!Rotations.rotating) {
                        this.easing = false;
                     } else if (!meteorQueueBusy()) {
                        double fromYaw = Rotations.serverYaw;
                        double fromPitch = Rotations.serverPitch;
                        if (!moving && this.owner != null && mc.field_1724.field_6012 - this.ownerLastCallTick <= 2) {
                           Rotations.rotate(fromYaw, fromPitch, Integer.MIN_VALUE);
                           this.finishEase(fromYaw, fromPitch);
                           this.lastModuleTick = mc.field_1724.field_6012;
                        } else {
                           double cameraYaw = mc.field_1724.method_36454();
                           double cameraPitch = mc.field_1724.method_36455();
                           double yawGap = class_3532.method_15338(cameraYaw - fromYaw);
                           double pitchGap = cameraPitch - fromPitch;
                           if (Math.abs(yawGap) <= cap && Math.abs(pitchGap) <= cap) {
                              if (!endMeteorHold(hold)) {
                                 Rotations.rotate(cameraYaw, cameraPitch, Integer.MIN_VALUE);
                                 this.finishEase(cameraYaw, cameraPitch);
                              }

                              this.easing = false;
                           } else {
                              double stepYaw = VanillaLimits.limitTurn(fromYaw, fromYaw + yawGap, cap);
                              double stepPitch = class_3532.method_15350(VanillaLimits.limitPitch(fromPitch, cameraPitch, cap), -90.0, 90.0);
                              Rotations.rotate(stepYaw, stepPitch, Integer.MIN_VALUE);
                              this.finishEase(stepYaw, stepPitch);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void noteTickRotation(double yaw, double pitch) {
      this.tickRotationAt = this.clientTick;
      this.tickYaw = yaw;
      this.tickPitch = pitch;
   }

   public boolean rotationQueuedThisTick() {
      return mc.field_1724 != null && this.tickRotationAt == this.clientTick && !this.movementSent();
   }

   public double queuedYaw() {
      return this.tickYaw;
   }

   public double queuedPitch() {
      return this.tickPitch;
   }

   // A click of the player's own that vanilla is about to send this tick: a press still queued on attack or use,
   // or a held use button whose repeat comes due now (vanilla repeats a held use every 4 ticks, on the tick
   // itemUseCooldown runs out). Merely holding a button is not one - a held attack never repeats, and holding the
   // sword button through a fight must not stop every module for as long as it is down.
   public static boolean ownClickPending() {
      class_315 options = mc.field_1690;
      if (((KeyBindingAccessor)options.field_1886).meteor$getTimesPressed() > 0
         || ((KeyBindingAccessor)options.field_1904).meteor$getTimesPressed() > 0) {
         return true;
      } else {
         return Input.isPressed(options.field_1904) && ((MinecraftClientAccessor)mc).crystal$getItemUseCooldown() <= 1;
      }
   }

   public static void requestCamera() {
      cameraRequestUntil = SHARED.clientTick + 1;
   }

   public static boolean cameraRequested() {
      return SHARED.clientTick <= cameraRequestUntil;
   }

   // Rotations are server-side only and the camera stays where you put it. Walking does not need the head on the
   // camera any more: only your own click, an explicit request (Sword Place) and elytra steering do.
   public static boolean cameraNeeded() {
      return ownClickPending() || cameraRequested() || mc.field_1724 != null && mc.field_1724.method_6128();
   }

   private static boolean refusesLook(double yaw, double pitch) {
      boolean exact = mc.field_1724.method_6128() || ownClickPending() || cameraRequested();
      return exact && !sameRotation(yaw, pitch, mc.field_1724.method_36454(), mc.field_1724.method_36455());
   }

   public static boolean movementKeysHeld() {
      class_315 options = mc.field_1690;
      return options.field_1894.method_1434() || options.field_1881.method_1434() || options.field_1913.method_1434() || options.field_1849.method_1434();
   }

   // Used to stop your walking while a module held the head elsewhere. With server-only rotation you keep moving.
   public boolean holdsMovement() {
      return false;
   }

   public boolean ownClickMismatched() {
      if (mc.field_1724 == null) {
         return false;
      } else {
         double yaw;
         double pitch;
         if (this.rotationQueuedThisTick()) {
            yaw = this.tickYaw;
            pitch = this.tickPitch;
         } else {
            if (!this.easing || !Rotations.rotating) {
               return false;
            }

            yaw = Rotations.serverYaw;
            pitch = Rotations.serverPitch;
         }

         return !sameRotation(yaw, pitch, mc.field_1724.method_36454(), mc.field_1724.method_36455());
      }
   }

   @EventHandler(
      priority = -10000
   )
   private void onPacketSent(Send event) {
      if (event.packet instanceof class_2824 || event.packet instanceof class_2885 || event.packet instanceof class_2886) {
         this.interactionTick = this.clientTick;
      }
   }

   private void finishEase(double yaw, double pitch) {
      this.noteTickRotation(yaw, pitch);
      this.lastSentTick = mc.field_1724.field_6012;
      this.queued = null;
      this.sentYaw = yaw;
      this.sentPitch = pitch;
   }

   private static boolean endMeteorHold(int hold) {
      if (meteorHoldUnwritable) {
         return false;
      } else {
         try {
            if (meteorHoldTimer == null) {
               meteorHoldTimer = Rotations.class.getDeclaredField("lastRotationTimer");
               meteorHoldTimer.setAccessible(true);
            }

            meteorHoldTimer.setInt(null, Math.max(hold, meteorHoldTimer.getInt(null)));
            return true;
         } catch (RuntimeException | ReflectiveOperationException var2) {
            meteorHoldUnwritable = true;
            return false;
         }
      }
   }

   private static boolean meteorQueueBusy() {
      if (meteorQueueUnreadable) {
         return false;
      } else {
         try {
            if (meteorQueue == null) {
               meteorQueue = Rotations.class.getDeclaredField("rotations");
               meteorQueue.setAccessible(true);
            }

            return !((List)meteorQueue.get(null)).isEmpty();
         } catch (RuntimeException | ReflectiveOperationException var1) {
            meteorQueueUnreadable = true;
            return false;
         }
      }
   }

   private void finishTurn(double yaw, double pitch) {
      this.sentYaw = yaw;
      this.sentPitch = pitch;
      this.planned = false;
      this.planStep = 0;
      this.owner = null;
      this.ownerLastCallTick = -1;
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
         this.lastModuleTick = -1;
         this.packetYaw = Double.NaN;
         this.packetPitch = Double.NaN;
         this.queued = null;
         this.easing = false;
         this.player = mc.field_1724;
      }
   }

   private boolean claim(Object requester, int priority) {
      this.forgetIfNewPlayer();
      if (this.owner != null && this.owner != requester) {
         boolean ownerActive = mc.field_1724.field_6012 - this.ownerLastCallTick <= 2;
         if (ownerActive && priority <= this.ownerPriority) {
            return false;
         }

         this.reset();
      }

      if (this.lastSentTick != mc.field_1724.field_6012) {
         this.sentYaw = Rotations.rotating ? Rotations.serverYaw : mc.field_1724.method_36454();
         this.sentPitch = Rotations.rotating ? Rotations.serverPitch : mc.field_1724.method_36455();
      }

      this.owner = requester;
      this.ownerPriority = priority;
      this.ownerLastCallTick = mc.field_1724.field_6012;
      return true;
   }

   private static final class Batch {
      final double yaw;
      final double pitch;

      Batch(double yaw, double pitch) {
         this.yaw = yaw;
         this.pitch = pitch;
      }

      boolean accepts(double wantedYaw, double wantedPitch) {
         return TurnProgress.sameRotation(this.yaw, this.pitch, wantedYaw, wantedPitch);
      }
   }
}
