package dev.crystaladdon.utils;

import java.util.function.LongSupplier;

public final class ActionBudget {
   private static final double MAX_ACTION_COST = 2.0;
   private double budget;
   private double capacity = 2.0;
   private int maxPerTick = 1;
   private int takenThisTick;
   private long lastUpdate = -1L;
   private final LongSupplier clock;

   public ActionBudget() {
      this(System::nanoTime);
   }

   ActionBudget(LongSupplier clock) {
      this.clock = clock;
   }

   public void update(double speed, int maxPerTick) {
      this.maxPerTick = Math.max(1, maxPerTick);
      this.capacity = this.maxPerTick * 2.0;
      this.takenThisTick = 0;
      long now = this.clock.getAsLong();
      if (this.lastUpdate < 0L) {
         this.lastUpdate = now;
         this.budget = this.capacity;
      } else {
         double delta = (now - this.lastUpdate) / 1.0E9;
         this.lastUpdate = now;
         if (speed <= 0.0) {
            this.budget = this.capacity;
         } else {
            this.budget = Math.min(this.budget + delta * speed, this.capacity);
         }
      }
   }

   public boolean tryConsume() {
      return this.tryConsume(1.0);
   }

   public boolean canAfford() {
      return this.canAfford(1.0);
   }

   public boolean tryConsume(double cost) {
      double charge = normalise(cost);
      if (!this.canAfford(charge)) {
         return false;
      } else {
         this.budget -= charge;
         this.takenThisTick++;
         return true;
      }
   }

   public boolean canAfford(double cost) {
      double charge = normalise(cost);
      return this.takenThisTick < this.maxPerTick && this.budget >= charge;
   }

   private static double normalise(double cost) {
      return Double.isFinite(cost) && !(cost < 0.0) ? cost : Double.POSITIVE_INFINITY;
   }

   public void reset() {
      this.budget = 0.0;
      this.takenThisTick = 0;
      this.lastUpdate = -1L;
   }
}
