package com.messerocks.crystal.utils;

import com.messerocks.crystal.modules.Stealth;
import net.minecraft.class_2338;
import net.minecraft.class_310;

public final class AnchorSequence {
   private final AnchorSequence.Actions actions;
   private class_2338 pos;
   private AnchorSequence.Stage stage;
   private boolean sent;
   private int waited;
   private int slotWaited;
   private String failure;
   private long generation;
   private int predicted = -1;
   private int chargedFrom = -1;
   private class_2338 detonated;
   private int detonatedAge;
   private int clock;
   private int nextStepAt;

   public AnchorSequence() {
      this(new AnchorSequence.ClientActions(null));
   }

   public AnchorSequence(AnchorActions.Sightings sightings) {
      this(new AnchorSequence.ClientActions(sightings));
   }

   AnchorSequence(AnchorSequence.Actions actions) {
      this.actions = actions;
   }

   public boolean isRunning() {
      return this.stage != null;
   }

   public class_2338 pos() {
      return this.pos;
   }

   public AnchorSequence.Stage stage() {
      return this.stage;
   }

   public String failure() {
      return this.failure;
   }

   public void start(class_2338 pos) {
      this.generation++;
      this.pos = pos.method_10062();
      this.stage = AnchorSequence.Stage.Place;
      this.sent = false;
      this.waited = 0;
      this.slotWaited = 0;
      this.failure = null;
      this.predicted = -1;
      this.chargedFrom = -1;
   }

   public void cancel() {
      this.generation++;
      this.pos = null;
      this.stage = null;
      this.sent = false;
      this.waited = 0;
      this.slotWaited = 0;
      this.predicted = -1;
      this.chargedFrom = -1;
   }

   public void reset() {
      this.cancel();
      this.detonated = null;
      this.detonatedAge = 0;
   }

   public void tick() {
      this.clock++;
      if (this.detonated != null) {
         this.detonatedAge++;
      }
   }

   public boolean pacing() {
      return this.clock < this.nextStepAt;
   }

   public boolean awaitingBlast(class_2338 pos, int window) {
      if (this.detonated == null || !this.detonated.equals(pos)) {
         return false;
      } else if (this.detonatedAge <= window && this.actions.charges(pos) > 0) {
         return true;
      } else {
         this.detonated = null;
         return false;
      }
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout) {
      return this.step(options, timeout, AnchorSequence.Phases.ALL, false);
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout, AnchorSequence.Phases phases) {
      return this.step(options, timeout, phases, false);
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout, AnchorSequence.Phases phases, boolean predict) {
      return this.step(options, timeout, phases, predict, false);
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout, AnchorSequence.Phases phases, boolean predict, boolean hold) {
      if (this.stage == null || !this.actions.available()) {
         return AnchorSequence.Step.None;
      } else if (this.actions.charges(this.pos) >= 0 && !this.actions.noticed(this.pos)) {
         return AnchorSequence.Step.Waiting;
      } else {
         int charges = this.charges(predict);
         if (!hold || charges < 0 && (this.stage != AnchorSequence.Stage.Place || this.sent)) {
            return switch (this.stage) {
               case Place -> this.stepPlace(options, timeout, charges, phases, predict);
               case Charge -> this.stepCharge(options, timeout, charges, phases, predict);
               case Detonate -> this.stepDetonate(options, timeout, charges, phases, predict);
            };
         } else {
            return AnchorSequence.Step.Waiting;
         }
      }
   }

   private int charges(boolean predict) {
      int world = this.actions.charges(this.pos);
      if (!predict || this.predicted < 0) {
         return world;
      } else if (world >= this.predicted) {
         this.predicted = -1;
         return world;
      } else {
         return this.predicted;
      }
   }

   private AnchorSequence.Step stepPlace(AnchorActions.Options options, int timeout, int charges, AnchorSequence.Phases phases, boolean predict) {
      if (charges >= 0) {
         this.advance(AnchorSequence.Stage.Charge);
         return this.step(options, timeout, phases, predict);
      } else if (!phases.place()) {
         return this.stop();
      } else if (!this.sent) {
         if (!this.actions.hasAnchor()) {
            return this.fail("no anchor in the hotbar");
         } else {
            this.actions.place(this.pos, this.onSend(options, () -> {
               this.sent = true;
               this.waited = 0;
               if (predict) {
                  this.predicted = 0;
                  this.advance(AnchorSequence.Stage.Charge);
               }
            }));
            return !this.sent && this.stage == AnchorSequence.Stage.Place ? this.waitForAction(timeout) : AnchorSequence.Step.Placed;
         }
      } else {
         return ++this.waited > timeout ? this.fail("placement was not accepted") : AnchorSequence.Step.Waiting;
      }
   }

   private AnchorSequence.Step stepCharge(AnchorActions.Options options, int timeout, int charges, AnchorSequence.Phases phases, boolean predict) {
      if (charges < 0) {
         return this.fail("anchor disappeared before charging");
      } else if (charges <= 0 || phases.detonate() && this.actions.offhandBlocksDetonation(charges)) {
         if (!phases.charge()) {
            return this.stop();
         } else {
            if (this.sent && charges > this.chargedFrom) {
               this.sent = false;
            }

            if (!this.sent) {
               if (!this.actions.hasGlowstone()) {
                  return this.fail("no glowstone in the hotbar");
               } else {
                  this.actions.charge(this.pos, this.onSend(options, () -> {
                     this.sent = true;
                     this.waited = 0;
                     this.chargedFrom = charges;
                     if (predict) {
                        this.predicted = charges + 1;
                        if (!phases.detonate() || !this.actions.offhandBlocksDetonation(charges + 1)) {
                           this.advance(AnchorSequence.Stage.Detonate);
                        }
                     }
                  }));
                  return !this.sent && this.stage == AnchorSequence.Stage.Charge ? this.waitForAction(timeout) : AnchorSequence.Step.Charged;
               }
            } else {
               return ++this.waited > timeout ? this.fail("charge was not accepted") : AnchorSequence.Step.Waiting;
            }
         }
      } else {
         this.advance(AnchorSequence.Stage.Detonate);
         return this.step(options, timeout, phases, predict);
      }
   }

   private AnchorSequence.Step stepDetonate(AnchorActions.Options options, int timeout, int charges, AnchorSequence.Phases phases, boolean predict) {
      if (charges < 0) {
         this.cancel();
         return AnchorSequence.Step.Done;
      } else if (!phases.detonate()) {
         return this.stop();
      } else if (!this.sent) {
         if (charges == 0 || this.actions.offhandBlocksDetonation(charges)) {
            this.advance(AnchorSequence.Stage.Charge);
            return this.step(options, timeout, phases, predict);
         } else if (this.actions.detonationSlot() < 0) {
            return this.fail("no usable item to click with");
         } else {
            class_2338 at = this.pos;
            this.actions.detonate(this.pos, charges, this.onSend(options, () -> {
               this.sent = true;
               this.waited = 0;
               this.detonated = at;
               this.detonatedAge = 0;
               if (predict) {
                  this.cancel();
               }
            }));
            if (!this.isRunning()) {
               return AnchorSequence.Step.Done;
            } else {
               return this.sent ? AnchorSequence.Step.Detonated : this.waitForAction(timeout);
            }
         }
      } else {
         return ++this.waited > timeout ? this.fail("detonation was not accepted") : AnchorSequence.Step.Waiting;
      }
   }

   private void advance(AnchorSequence.Stage next) {
      this.stage = next;
      this.sent = false;
      this.waited = 0;
      this.chargedFrom = -1;
   }

   private AnchorActions.Options onSend(AnchorActions.Options options, Runnable sentAction) {
      long expected = this.generation;
      AnchorSequence.Stage expectedStage = this.stage;
      return new AnchorActions.Options(options.rotate(), options.swing(), options.visibleSwap(), options.rotationPriority(), () -> {
            if (this.generation != expected || this.stage != expectedStage || this.sent || this.pacing()) {
               return false;
            } else if (!this.actions.settled(this.pos)) {
               return false;
            } else if (!options.gate().getAsBoolean()) {
               return false;
            } else {
               int gap = this.actions.stepGap();
               this.nextStepAt = this.clock + gap;
               this.actions.paced(gap);
               return true;
            }
         }, () -> {
            if (this.generation == expected && this.stage == expectedStage) {
               sentAction.run();
               options.onSent().run();
            }
         })
         .withOwner(options.turnOwner())
         .withReady(
            () -> this.generation == expected
               && this.stage == expectedStage
               && !this.sent
               && !this.pacing()
               && this.actions.settled(this.pos)
               && options.ready().getAsBoolean()
         )
         .withAhead(
            () -> this.generation == expected
               && this.stage == expectedStage
               && !this.sent
               && this.clock + 1 >= this.nextStepAt
               && this.actions.settledSoon(this.pos)
               && options.dueSoon()
         );
   }

   private AnchorSequence.Step waitForAction(int timeout) {
      if (!this.pacing() && (this.pos == null || this.actions.settled(this.pos)) && !this.actions.lookHeld()) {
         double cap = this.actions.turnCap();
         int turnTicks = cap > 0.0 ? (int)Math.ceil(180.0 / cap) * 2 : 0;
         if (this.actions.slotChanging()) {
            return ++this.slotWaited > timeout + turnTicks
               ? this.fail("the hotbar slot kept changing - a slot has to stand a tick before the click")
               : AnchorSequence.Step.Waiting;
         } else {
            return ++this.waited > timeout + turnTicks ? this.fail("no reachable click or action allowance before timeout") : AnchorSequence.Step.Waiting;
         }
      } else {
         return AnchorSequence.Step.Waiting;
      }
   }

   private AnchorSequence.Step stop() {
      this.cancel();
      return AnchorSequence.Step.Stopped;
   }

   private AnchorSequence.Step fail(String reason) {
      this.failure = reason;
      this.cancel();
      return AnchorSequence.Step.Failed;
   }

   interface Actions {
      boolean available();

      int charges(class_2338 var1);

      boolean hasAnchor();

      boolean hasGlowstone();

      int detonationSlot();

      boolean offhandBlocksDetonation(int var1);

      boolean place(class_2338 var1, AnchorActions.Options var2);

      boolean charge(class_2338 var1, AnchorActions.Options var2);

      boolean detonate(class_2338 var1, int var2, AnchorActions.Options var3);

      double turnCap();

      default int stepGap() {
         return 0;
      }

      default boolean noticed(class_2338 pos) {
         return true;
      }

      default boolean settled(class_2338 pos) {
         return true;
      }

      default boolean settledSoon(class_2338 pos) {
         return this.settled(pos);
      }

      default void paced(int gap) {
      }

      default boolean lookHeld() {
         return false;
      }

      default boolean slotChanging() {
         return false;
      }
   }

   private static final class ClientActions implements AnchorSequence.Actions {
      private final AnchorActions.Sightings sightings;

      ClientActions(AnchorActions.Sightings sightings) {
         this.sightings = sightings;
      }

      @Override
      public boolean noticed(class_2338 pos) {
         return this.sightings == null || this.sightings.noticed(pos);
      }

      @Override
      public boolean settled(class_2338 pos) {
         return this.sightings == null || this.sightings.settled(pos);
      }

      @Override
      public boolean settledSoon(class_2338 pos) {
         return this.sightings == null || this.sightings.settledSoon(pos);
      }

      @Override
      public void paced(int gap) {
         if (this.sightings != null) {
            this.sightings.pace(gap);
         }
      }

      @Override
      public boolean lookHeld() {
         return TurnProgress.cameraNeeded();
      }

      @Override
      public boolean slotChanging() {
         return ClickGate.slotChangedThisTick();
      }

      @Override
      public boolean available() {
         class_310 mc = class_310.method_1551();
         return mc.field_1687 != null && mc.field_1724 != null;
      }

      @Override
      public int charges(class_2338 pos) {
         return AnchorActions.charges(pos);
      }

      @Override
      public boolean hasAnchor() {
         return AnchorActions.findAnchor().found();
      }

      @Override
      public boolean hasGlowstone() {
         return AnchorActions.findGlowstone().found();
      }

      @Override
      public int detonationSlot() {
         return AnchorActions.findDetonationSlot();
      }

      @Override
      public boolean offhandBlocksDetonation(int charges) {
         return AnchorActions.offhandBlocksDetonation(charges);
      }

      @Override
      public boolean place(class_2338 pos, AnchorActions.Options options) {
         return AnchorActions.place(pos, options);
      }

      @Override
      public boolean charge(class_2338 pos, AnchorActions.Options options) {
         return AnchorActions.charges(pos) >= 0 && AnchorActions.charge(pos, options);
      }

      @Override
      public boolean detonate(class_2338 pos, int charges, AnchorActions.Options options) {
         return AnchorActions.detonate(pos, charges, options);
      }

      @Override
      public double turnCap() {
         return Stealth.turnCap();
      }

      @Override
      public int stepGap() {
         return AnchorActions.stepGap();
      }
   }

   public record Phases(boolean place, boolean charge, boolean detonate) {
      public static final AnchorSequence.Phases ALL = new AnchorSequence.Phases(true, true, true);
   }

   public static enum Stage {
      Place,
      Charge,
      Detonate;
   }

   public static enum Step {
      None,
      Placed,
      Charged,
      Detonated,
      Waiting,
      Done,
      Stopped,
      Failed;
   }
}
