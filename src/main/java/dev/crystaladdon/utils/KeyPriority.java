package dev.crystaladdon.utils;

// A key-driven module (the anchor macro, Sword Place, Double Tap) is in the middle of something the player asked
// for with a key press. While it is, the auras stay out of the way: their hotbar switches and clicks every tick
// would otherwise keep taking the one slot change and the one click a tick allows, and the macro would crawl.
public final class KeyPriority {
   // Long enough to bridge one tick to the next, short enough that a module that stops calling hold() lets go at once.
   private static final long HOLD_NANOS = 120_000_000L;
   // nanoTime has an arbitrary origin and may be negative, so "not held" is the smallest long, not 0.
   private static long heldUntil = Long.MIN_VALUE;

   private KeyPriority() {
   }

   public static void hold() {
      heldUntil = System.nanoTime() + HOLD_NANOS;
   }

   // The key-driven module is done: let the auras back in now rather than when the hold runs out.
   public static void release() {
      heldUntil = Long.MIN_VALUE;
   }

   public static boolean active() {
      return System.nanoTime() < heldUntil;
   }
}
