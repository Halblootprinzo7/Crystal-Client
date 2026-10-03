package com.messerocks.crystal.utils;

import net.minecraft.class_2338;
import net.minecraft.class_310;

public final class AnchorSequence {
   private static final class_310 mc = class_310.method_1551();
   private class_2338 pos;
   private AnchorSequence.Stage stage;
   private boolean sent;
   private int waited;
   private String failure;
   private int predicted = -1;

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
      this.pos = pos;
      this.stage = AnchorSequence.Stage.Place;
      this.sent = false;
      this.waited = 0;
      this.failure = null;
      this.predicted = -1;
   }

   public void cancel() {
      this.pos = null;
      this.stage = null;
      this.sent = false;
      this.waited = 0;
      this.predicted = -1;
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout) {
      return this.step(options, timeout, AnchorSequence.Phases.ALL, false);
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout, AnchorSequence.Phases phases) {
      return this.step(options, timeout, phases, false);
   }

   public AnchorSequence.Step step(AnchorActions.Options options, int timeout, AnchorSequence.Phases phases, boolean predict) {
      if (this.stage != null && mc.field_1687 != null && mc.field_1724 != null) {
         int charges = this.charges(predict);

         return switch (this.stage) {
            case Place -> this.stepPlace(options, timeout, charges, phases, predict);
            case Charge -> this.stepCharge(options, timeout, charges, phases, predict);
            case Detonate -> this.stepDetonate(options, timeout, charges, phases, predict);
         };
      } else {
         return AnchorSequence.Step.None;
      }
   }

   private int charges(boolean predict) {
      int world = AnchorActions.charges(this.pos);
      return predict && this.predicted >= 0 ? Math.max(world, this.predicted) : world;
   }

   private AnchorSequence.Step stepPlace(AnchorActions.Options options, int timeout, int charges, AnchorSequence.Phases phases, boolean predict) {
      if (charges >= 0) {
         this.advance(AnchorSequence.Stage.Charge);
         return this.step(options, timeout, phases, predict);
      } else if (!phases.place()) {
         return this.stop();
      } else if (!this.sent) {
         if (!AnchorActions.place(this.pos, options)) {
            return this.fail("no anchor in the hotbar");
         } else {
            if (predict) {
               this.predicted = 0;
               this.advance(AnchorSequence.Stage.Charge);
            } else {
               this.sent = true;
               this.waited = 0;
            }

            return AnchorSequence.Step.Placed;
         }
      } else {
         return ++this.waited > timeout ? this.fail("placement was not accepted") : AnchorSequence.Step.Waiting;
      }
   }

   private AnchorSequence.Step stepCharge(AnchorActions.Options options, int timeout, int charges, AnchorSequence.Phases phases, boolean predict) {
      if (charges < 0) {
         return this.fail("anchor disappeared before charging");
      } else if (charges > 0) {
         this.advance(AnchorSequence.Stage.Detonate);
         return this.step(options, timeout, phases, predict);
      } else if (!phases.charge()) {
         return this.stop();
      } else if (!this.sent) {
         if (!AnchorActions.charge(this.pos, options)) {
            return this.fail("no glowstone in the hotbar");
         } else {
            if (predict) {
               this.predicted = 1;
               this.advance(AnchorSequence.Stage.Detonate);
            } else {
               this.sent = true;
               this.waited = 0;
            }

            return AnchorSequence.Step.Charged;
         }
      } else {
         return ++this.waited > timeout ? this.fail("charge was not accepted") : AnchorSequence.Step.Waiting;
      }
   }

   private AnchorSequence.Step stepDetonate(AnchorActions.Options options, int timeout, int charges, AnchorSequence.Phases phases, boolean predict) {
      if (charges < 0) {
         this.cancel();
         return AnchorSequence.Step.Done;
      } else if (!phases.detonate()) {
         return this.stop();
      } else if (!this.sent) {
         if (AnchorActions.offhandBlocksDetonation(charges)) {
            return this.fail("glowstone in the offhand would charge the anchor instead of setting it off");
         } else if (!AnchorActions.detonate(this.pos, charges, options)) {
            return this.fail("no usable item to click with");
         } else if (predict) {
            this.cancel();
            return AnchorSequence.Step.Done;
         } else {
            this.sent = true;
            this.waited = 0;
            return AnchorSequence.Step.Detonated;
         }
      } else {
         return ++this.waited > timeout ? this.fail("detonation was not accepted") : AnchorSequence.Step.Waiting;
      }
   }

   private void advance(AnchorSequence.Stage next) {
      this.stage = next;
      this.sent = false;
      this.waited = 0;
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
