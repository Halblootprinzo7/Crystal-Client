package dev.crystaladdon.utils;

public final class HealthLedger {
   static final int POP_SETTLE_TICKS = 10;
   private static final float POST_POP_HEALTH = 1.0F;
   private static final float POST_POP_ABSORPTION = 8.0F;
   private float health = -1.0F;
   private float absorption = -1.0F;
   private boolean popPending;
   private float snapHealth;
   private float snapAbsorption;
   private int popWait;

   public void clear() {
      this.health = this.absorption = -1.0F;
      this.popPending = false;
      this.popWait = 0;
   }

   public boolean hasBaseline() {
      return this.health >= 0.0F;
   }

   public float sample(float newHealth, float newAbsorption, boolean absorptionMayHaveExpired) {
      if (this.popPending) {
         if (newHealth == this.snapHealth && newAbsorption == this.snapAbsorption && ++this.popWait < 10) {
            return 0.0F;
         } else {
            this.popPending = false;
            this.health = newHealth;
            this.absorption = newAbsorption;
            return 0.0F;
         }
      } else {
         float booked = 0.0F;
         if (this.hasBaseline()) {
            float healthDrop = Math.max(0.0F, this.health - newHealth);
            float absorptionDrop = Math.max(0.0F, this.absorption - newAbsorption);
            if (newAbsorption == 0.0F && healthDrop == 0.0F && absorptionMayHaveExpired) {
               absorptionDrop = 0.0F;
            }

            booked = healthDrop + absorptionDrop;
         }

         this.health = newHealth;
         this.absorption = newAbsorption;
         return booked;
      }
   }

   public float pop(float liveHealth, float liveAbsorption) {
      float booked = this.lostToZero(liveHealth, liveAbsorption);
      if (liveHealth == 1.0F && liveAbsorption == 8.0F) {
         this.popPending = false;
         this.health = liveHealth;
         this.absorption = liveAbsorption;
         return booked;
      } else {
         this.popPending = true;
         this.popWait = 0;
         this.snapHealth = liveHealth;
         this.snapAbsorption = liveAbsorption;
         return booked;
      }
   }

   public float death(float liveHealth, float liveAbsorption) {
      float booked = this.lostToZero(liveHealth, liveAbsorption);
      this.clear();
      return booked;
   }

   private float lostToZero(float liveHealth, float liveAbsorption) {
      float live = liveHealth + liveAbsorption;
      if (!this.popPending) {
         return Math.max(this.hasBaseline() ? this.health + this.absorption : 0.0F, live);
      } else {
         boolean stale = liveHealth == this.snapHealth && liveAbsorption == this.snapAbsorption;
         return stale ? 9.0F : live;
      }
   }
}
