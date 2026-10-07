package dev.crystaladdon.utils;

import dev.crystaladdon.mixin.MinecraftClientAccessor;
import dev.crystaladdon.modules.Stealth;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent.Post;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2824;
import net.minecraft.class_2846;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_310;
import net.minecraft.class_315;
import net.minecraft.class_3532;
import net.minecraft.class_2846.class_2847;

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
   // The requester whose look the server has now, kept after its turn is over (finishTurn clears owner): it may end
   // the hold on that look early through releaseHold.
   private Object lookOwner;
   private int ownerLastCallTick = -1;
   private int ownerPriority;
   public static final int DEFAULT_PRIORITY = 50;
   private Object player;
   private static int cameraRequestUntil = -1;
   private static Field meteorQueue;
   private static boolean meteorQueueUnreadable;
   private static Field meteorHoldTimer;
   private static boolean meteorHoldUnwritable;
   private static Field meteorEntryYaw;
   private static Field meteorEntryPitch;
   private static boolean meteorEntryUnreadable;
   private static Field meteorEntryPriority;
   private static Field meteorEntryCallback;
   private static boolean meteorEntryDetailUnreadable;
   // The tick onMovementQueue dropped a foreign rotation with an action of its own (its callback) to keep our pinned
   // look: one queued after our click had already gone out, too late to be refused. Our clicks and turns stand back on
   // the tick after, so that module's retry goes out with its own look instead of being dropped again.
   private int droppedForeignTick = -10;
   // The entry in Meteor's rotation queue that holds the look our clicks of this tick go along, and the tick it is for.
   // onMovementQueue sends it whatever else is queued: Meteor sorts equal priorities by arrival, so a foreign rotation
   // queued earlier at Integer.MAX_VALUE would otherwise go out instead, and the server would judge our click along a
   // look it never had.
   private Object pinnedEntry;
   private int pinnedTick = -1;
   // A click of the addon's own went out this tick along this look without a turn of TurnProgress's (noteOwnClick).
   private int ownClickTick = -1;
   private double ownClickYaw;
   private double ownClickPitch;

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
      } else if (mc.field_1724.field_6012 != this.lastSentTick && this.yieldsToForeign(yaw, pitch)) {
         // turnTo refuses this look now (see yieldsToForeign): no reach to report.
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
      } else if (mc.field_1724.field_6012 != this.lastSentTick && this.yieldsToForeign(yaw, pitch)) {
         // Our look pinned on top would bury another module's rotation and the place, mine or throw that waits on it
         // (see yieldsToForeign): the turn waits a tick instead, as after another clicker's interaction.
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
            this.lookOwner = requester;
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
               this.lookOwner = requester;
               this.queue(yaw, pitch);
               this.finishTurn(yaw, pitch);
               if (action != null) {
                  action.run();
               }

               return true;
            } else {
               double progress = ease((double)this.planStep / this.planTicks, smooth);
               double stepYaw = this.startYaw + yawDelta(this.startYaw, this.targetYaw) * progress;
               double stepPitch = this.startPitch + (this.targetPitch - this.startPitch) * progress;
               if (smooth > 0.0) {
                  stepYaw += (Math.random() * 2.0 - 1.0) * 0.2 * smooth;
                  stepPitch += (Math.random() * 2.0 - 1.0) * 0.1 * smooth;
               }

               double fromYaw = Double.isNaN(this.sentYaw) ? mc.field_1724.method_36454() : this.sentYaw;
               double fromPitch = Double.isNaN(this.sentPitch) ? mc.field_1724.method_36455() : this.sentPitch;
               stepYaw = VanillaLimits.limitTurn(fromYaw, stepYaw, cap);
               stepPitch = class_3532.method_15350(VanillaLimits.limitPitch(fromPitch, stepPitch, cap), -90.0, 90.0);
               if (this.foreignLookConflict(stepYaw, stepPitch)) {
                  // A foreign rotation queued with the target's very look passed the check above; the step look
                  // would still bury it. This step waits a tick.
                  this.planStep--;
                  this.blocked = true;
                  return false;
               }

               this.pin(stepYaw, stepPitch);
               this.noteTickRotation(stepYaw, stepPitch);
               this.lastSentTick = mc.field_1724.field_6012;
               this.lastModuleTick = mc.field_1724.field_6012;
               this.lookOwner = requester;
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
      this.pin(yaw, pitch);
      this.noteTickRotation(yaw, pitch);
      this.queued = new TurnProgress.Batch(yaw, pitch);
      this.lastSentTick = mc.field_1724.field_6012;
      this.lastModuleTick = mc.field_1724.field_6012;
      this.easing = true;
   }

   private boolean movementSent() {
      return this.movementSentTick == this.clientTick;
   }

   // A click of the addon's own is going out now along this look - the one the server already has (LegitPlace.currentYaw),
   // with no turn of TurnProgress's queued this tick. That look has to be the one this tick's movement packet carries:
   // a rotation another module has queued, or queues later in the tick, would have the server judge the click along a
   // look it never had. Only our own clicks are held this way - a foreign module that queues its look and clicks along
   // it at once (Meteor's Kill Aura) is left its own look when the addon has not clicked.
   // False, and nothing held, when the click must not go out at all (clickLookFree): the caller then sends nothing.
   public boolean noteOwnClick(double yaw, double pitch) {
      if (mc.field_1724 != null && !this.movementSent()) {
         if (!this.clickLookFree(yaw, pitch)) {
            return false;
         }

         this.ownClickTick = this.clientTick;
         this.ownClickYaw = yaw;
         this.ownClickPitch = pitch;
         this.holdClickLook();
      }

      return true;
   }

   // Whether a click of ours along (yaw, pitch) may go out now without a turn of TurnProgress's. Not when another
   // clicker's interaction has already gone out this tick and a look of its own, a different one, is queued to go with
   // this tick's movement packet - Meteor's Kill Aura queues its look and hits along it at once. Only one look goes out
   // with the packet: pinning ours would leave that hit judged along a look it was never sent with, and leaving theirs
   // would do the same to ours. turnTo refuses a new look after another interaction in the tick for the same reason.
   // Nor when pinning our look would bury another module's rotation that must go out with its own look (yieldsToForeign):
   // the click then waits a tick and that module's place, mine or throw goes out first.
   public boolean clickLookFree(double yaw, double pitch) {
      if (mc.field_1724 == null || this.movementSent()) {
         return true;
      } else {
         this.forgetIfNewPlayer();
         if (this.ownClickTick == this.clientTick || mc.field_1724.field_6012 == this.lastSentTick) {
            // A click or turn of ours already decided this tick's look: a further click along it changes nothing, and
            // a foreign rotation queued since has to give way to the click that already went out (easeBack).
            return true;
         } else if (this.yieldsToForeign(yaw, pitch)) {
            return false;
         } else if (this.interactionTick != this.clientTick) {
            return true;
         } else {
            List<?> queue = meteorQueueList();
            if (queue == null || queue.isEmpty()) {
               // Nothing of anyone's queued: the earlier click and ours both go along the look the server already has.
               return true;
            } else {
               double[] head = queuedLook(queue.get(0));
               return head != null && sameRotation(head[0], head[1], yaw, pitch);
            }
         }
      }
   }

   // The look a Meteor queue entry holds, or null when it cannot be read (then it counts as a different one).
   private static double[] queuedLook(Object entry) {
      if (entry == null || meteorEntryUnreadable) {
         return null;
      } else {
         try {
            if (meteorEntryYaw == null || meteorEntryYaw.getDeclaringClass() != entry.getClass()) {
               Field yaw = entry.getClass().getDeclaredField("yaw");
               Field pitch = entry.getClass().getDeclaredField("pitch");
               yaw.setAccessible(true);
               pitch.setAccessible(true);
               meteorEntryYaw = yaw;
               meteorEntryPitch = pitch;
            }

            return new double[]{meteorEntryYaw.getDouble(entry), meteorEntryPitch.getDouble(entry)};
         } catch (RuntimeException | ReflectiveOperationException var3) {
            meteorEntryUnreadable = true;
            return null;
         }
      }
   }

   // Loads the priority and callback fields of Meteor's queue entries; false when they cannot be read.
   private static boolean entryDetailReadable(Object entry) {
      if (entry == null || meteorEntryDetailUnreadable) {
         return false;
      } else {
         try {
            if (meteorEntryCallback == null || meteorEntryCallback.getDeclaringClass() != entry.getClass()) {
               Field priority = entry.getClass().getDeclaredField("priority");
               Field callback = entry.getClass().getDeclaredField("callback");
               priority.setAccessible(true);
               callback.setAccessible(true);
               meteorEntryPriority = priority;
               meteorEntryCallback = callback;
            }

            return true;
         } catch (RuntimeException | ReflectiveOperationException var2) {
            meteorEntryDetailUnreadable = true;
            return false;
         }
      }
   }

   // The action a Meteor queue entry runs once its look went out (Surround's place, Packet Mine's mine, NoFall's
   // bucket...), null when it has none. Throws when it cannot be read.
   private static Runnable queuedCallback(Object entry) throws ReflectiveOperationException {
      if (!entryDetailReadable(entry)) {
         throw new ReflectiveOperationException("Meteor rotation callback unreadable");
      } else {
         return (Runnable)meteorEntryCallback.get(entry);
      }
   }

   // Whether a foreign entry must go out with its own look this tick: it carries an action of its own, or it is queued
   // at Integer.MAX_VALUE, which nothing does for a look it could do without (NoFall's air place). An entry that cannot
   // be read counts as one.
   private static boolean queuedLookNeeded(Object entry) {
      try {
         if (queuedCallback(entry) != null) {
            return true;
         } else {
            return meteorEntryPriority.getInt(entry) == Integer.MAX_VALUE;
         }
      } catch (RuntimeException | ReflectiveOperationException var2) {
         meteorEntryDetailUnreadable = true;
         return true;
      }
   }

   // Whether Meteor's queue holds a foreign rotation that pinning (yaw, pitch) would bury: one that must go out with its
   // own look (queuedLookNeeded) and holds a different one. onMovementQueue sends one look a tick and drops the rest,
   // callbacks and all - Surround's obsidian would never be placed, NoFall's block never put under you. A foreign
   // rotation with our very look is no conflict: onMovementQueue runs its action along the look that goes out. Nor is
   // one without an action below Integer.MAX_VALUE (Kill Aura on Always, Freecam, AntiAFK): pinning over it costs
   // nothing.
   private boolean foreignLookConflict(double yaw, double pitch) {
      List<?> queue = meteorQueueList();
      if (queue != null) {
         for (Object entry : queue) {
            if (entry == this.pinnedEntry && this.pinnedTick == this.clientTick) {
               continue;
            }

            double[] look = queuedLook(entry);
            if ((look == null || !sameRotation(look[0], look[1], yaw, pitch)) && queuedLookNeeded(entry)) {
               return true;
            }
         }
      }

      return false;
   }

   // Whether a click or turn of ours that is not out yet stands back this tick for another module's rotation: one queued
   // now that our pin would bury (foreignLookConflict), or the retry of one onMovementQueue had to drop last tick for a
   // click of ours that was already out (droppedForeignTick) - that module queues it again this tick, maybe after us.
   private boolean yieldsToForeign(double yaw, double pitch) {
      return this.droppedForeignTick == this.clientTick - 1 || this.foreignLookConflict(yaw, pitch);
   }

   // Pinned on top it goes out and onMovementQueue drops the rest; easeBack brings it home to the camera afterwards.
   // Nothing while nothing is queued or held: the camera's look then goes out by itself.
   private void holdClickLook() {
      if (mc.field_1724.field_6012 != this.lastSentTick && (Rotations.rotating || meteorQueueBusy())) {
         this.pin(this.ownClickYaw, this.ownClickPitch);
         this.noteTickRotation(this.ownClickYaw, this.ownClickPitch);
         this.queued = new TurnProgress.Batch(this.ownClickYaw, this.ownClickPitch);
         this.lastSentTick = mc.field_1724.field_6012;
         this.sentYaw = this.ownClickYaw;
         this.sentPitch = this.ownClickPitch;
         this.easing = true;
      }
   }

   // Queues the look on top of Meteor's queue and remembers its entry (see pinnedEntry). The new entry is found as the
   // one the queue did not hold before: Meteor's Rotation class is private, and nothing frees queue entries back to
   // its pool before the movement packet, so identity is enough.
   private void pin(double yaw, double pitch) {
      List<?> queue = meteorQueueList();
      List<Object> before = queue == null ? List.of() : new ArrayList<>(queue);
      Rotations.rotate(yaw, pitch, Integer.MAX_VALUE);
      this.pinnedEntry = null;
      if (queue != null) {
         for (Object entry : queue) {
            if (indexOfSame(before, entry) < 0) {
               this.pinnedEntry = entry;
               this.pinnedTick = this.clientTick;
               break;
            }
         }
      }
   }

   private static int indexOfSame(List<?> list, Object wanted) {
      if (wanted != null) {
         for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == wanted) {
               return i;
            }
         }
      }

      return -1;
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
         // Our pinned look goes out if it is queued at all, also behind a foreign entry of the same priority. A pin of
         // ours only lands behind a foreign entry that must go out with a different look (queuedLookNeeded) when that
         // entry was queued after our click had already gone out: clickLookFree and turnTo refuse one queued before.
         // The click cannot be taken back, so that look loses (droppedForeignTick lets its retry through next tick).
         int keep = this.pinnedTick == this.clientTick ? indexOfSame(queue, this.pinnedEntry) : -1;
         Object kept = queue.get(Math.max(0, keep));
         List<Runnable> carried = new ArrayList<>();
         boolean dropped = false;

         for (int i = 0; i < queue.size(); i++) {
            Object entry = queue.get(i);
            if (entry != kept) {
               Runnable callback = foreignCallbackOrNull(entry);
               if (callback != null) {
                  if (sameLook(entry, kept)) {
                     carried.add(callback);
                  } else if (keep >= 0) {
                     dropped = true;
                  }
               }
            }
         }

         for (int i = 0; i < keep; i++) {
            queue.remove(0);
         }

         while (queue.size() > 1) {
            queue.remove(queue.size() - 1);
         }

         // An action queued along the very look that goes out still runs, after the movement packet that carries it,
         // as Meteor would have run it had it gone out first.
         if (!carried.isEmpty() && !carryCallbacks(kept, carried)) {
            dropped |= keep >= 0;
         }

         if (dropped) {
            this.droppedForeignTick = this.clientTick;
         }
      }
   }

   private static Runnable foreignCallbackOrNull(Object entry) {
      try {
         return queuedCallback(entry);
      } catch (RuntimeException | ReflectiveOperationException var2) {
         return null;
      }
   }

   private static boolean sameLook(Object a, Object b) {
      double[] lookA = queuedLook(a);
      double[] lookB = queuedLook(b);
      return lookA != null && lookB != null && sameRotation(lookA[0], lookA[1], lookB[0], lookB[1]);
   }

   // Runs the carried actions after the kept entry's own: Meteor runs the kept entry's callback once its look went out.
   private static boolean carryCallbacks(Object kept, List<Runnable> carried) {
      try {
         Runnable own = queuedCallback(kept);
         List<Runnable> actions = new ArrayList<>();
         if (own != null) {
            actions.add(own);
         }

         actions.addAll(carried);
         meteorEntryCallback.set(kept, (Runnable)() -> {
            for (Runnable action : actions) {
               action.run();
            }
         });
         return true;
      } catch (RuntimeException | ReflectiveOperationException var4) {
         meteorEntryDetailUnreadable = true;
         return false;
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
               if (this.ownClickTick == this.clientTick) {
                  // A rotation another module queued after our click, before this point of the tick.
                  this.holdClickLook();
               } else if (Rotations.rotating && !meteorQueueBusy()) {
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
   // The held repeat is read the way vanilla reads it: the key binding's state, not the raw button, and not while an
   // item is in use. Sword Place and AutoBlock clear that state for a press they took, and vanilla then never
   // repeats - the raw button would report a click every tick that never comes.
   public static boolean ownClickPending() {
      class_315 options = mc.field_1690;
      if (((KeyBindingAccessor)options.field_1886).meteor$getTimesPressed() > 0
         || ((KeyBindingAccessor)options.field_1904).meteor$getTimesPressed() > 0) {
         return true;
      } else {
         return options.field_1904.method_1434()
            && mc.field_1724 != null
            && !mc.field_1724.method_6115()
            && ((MinecraftClientAccessor)mc).crystal$getItemUseCooldown() <= 1;
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
      } else if (event.packet instanceof class_2846 action
         && (action.method_12363() == class_2847.field_12968
            || action.method_12363() == class_2847.field_12973
            || action.method_12363() == class_2847.field_12971)) {
         // Mining is a click along the look too: a dig another module sent this tick (Packet Mine or Nuker without
         // rotate) is judged along this tick's look like any other click, so no different look of ours goes after it.
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

   // The way a turn goes from one yaw to another: the shorter arc, unless both lie within the yaw a look may have off the
   // camera's (Stealth.allowsLook: view-angle, never less than 90) and that arc leaves it. Then the long way round,
   // through the camera's yaw: with a view-angle above 90, two allowed looks on either side were joined through the
   // camera's yaw plus 180, and the steps on the way sent the head round behind you.
   private static double yawDelta(double fromYaw, double toYaw) {
      double shortest = class_3532.method_15338(toYaw - fromYaw);
      if (mc.field_1724 == null) {
         return shortest;
      } else {
         double camera = mc.field_1724.method_36454();
         double limit = Math.max(90.0, Stealth.viewAngle());
         double from = class_3532.method_15338(fromYaw - camera);
         double to = class_3532.method_15338(toYaw - camera);
         return Math.abs(from) <= limit && Math.abs(to) <= limit ? to - from : shortest;
      }
   }

   private static int ticksFor(double fromYaw, double fromPitch, double yaw, double pitch, double cap, double smooth) {
      double distance = Math.max(Math.abs(yawDelta(fromYaw, yaw)), Math.abs(pitch - fromPitch));
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

   // The requester is done with the look it last sent - the anchor macro at the end of a cycle. Meteor would keep the
   // server on that look for its rotation-hold ticks, and a module that only clicks along the camera (Crosshair
   // Auto Crystal) would find nothing along it all that time; easeBack starts bringing the look home on the next tick
   // instead. A look another requester has sent since is left alone.
   public void releaseHold(Object requester) {
      if (mc.field_1724 != null && requester != null) {
         this.forgetIfNewPlayer();
         if (this.lookOwner == requester && this.easing) {
            int hold = Math.max(1, (Integer)Config.get().rotationHoldTicks.get());
            this.lastModuleTick = Math.min(this.lastModuleTick, mc.field_1724.field_6012 - hold);
         }
      }
   }

   private void forgetIfNewPlayer() {
      if (mc.field_1724 != this.player) {
         this.reset();
         this.lookOwner = null;
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
