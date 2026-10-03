package dev.crystaladdon.utils;

public final class CrystalScore {
   public static final double LETHAL_BONUS = 1000.0;
   public static final double LETHAL_MARGIN = 1.25;
   static final double CERTAINTY_POINTS = 8.0;

   private CrystalScore() {
   }

   public static float need(float health, float absorption) {
      return health > 0.0F ? health + Math.max(0.0F, absorption) : 0.0F;
   }

   public static boolean lethal(float damage, float need) {
      return need > 0.0F && damage >= need * 1.25;
   }

   public static double upperBound(float damage, float need) {
      return lethal(damage, need) ? 1000.0 + certainty(damage, need) : damage;
   }

   public static double score(float damage, float selfDamage, double selfWeight, float need) {
      double self = Math.max(0.0F, selfDamage);
      return lethal(damage, need) ? 1000.0 + certainty(damage, need) - self : damage - Math.max(0.0, selfWeight) * self;
   }

   private static double certainty(float damage, float need) {
      return 8.0 * (Math.min((double)(damage / need), 2.0) - 1.0);
   }
}
