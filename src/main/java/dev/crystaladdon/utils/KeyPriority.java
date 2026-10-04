package dev.crystaladdon.utils;

import java.util.IdentityHashMap;
import java.util.Map;

// A key-driven module (the anchor macro, Sword Place, Double Tap) is in the middle of something the player asked
// for with a key press. While it is, the auras stay out of the way: their hotbar switches and clicks every tick
// would otherwise keep taking the one slot change and the one click a tick allows, and the macro would crawl.
public final class KeyPriority {
   // Long enough to bridge one tick to the next, short enough that a module that stops calling hold() lets go at once.
   private static final long HOLD_NANOS = 120_000_000L;
   // One deadline per module: a module that is done lets go of its own hold only, so Sword Place finishing a press
   // cannot let the auras back in while the anchor macro or Double Tap is still at work.
   private static final Map<Object, Long> holds = new IdentityHashMap<>();

   private KeyPriority() {
   }

   public static void hold(Object owner) {
      holds.put(owner, System.nanoTime() + HOLD_NANOS);
   }

   // The key-driven module is done: let the auras back in now rather than when the hold runs out.
   public static void release(Object owner) {
      holds.remove(owner);
   }

   public static boolean active() {
      if (holds.isEmpty()) {
         return false;
      } else {
         // nanoTime has an arbitrary origin and may be negative: compare the difference, not the values.
         long now = System.nanoTime();
         holds.values().removeIf(until -> until - now <= 0L);
         return !holds.isEmpty();
      }
   }
}
