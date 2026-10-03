package dev.crystaladdon.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_243;

public final class ComboDetector {
   static final int OWN_TTL = 20;
   static final double BLAST_REACH = 12.0;
   private final ArrayDeque<Integer> pops = new ArrayDeque<>();
   private final ArrayDeque<Float> damages = new ArrayDeque<>();
   private final Map<Long, Integer> expectedPlacements = new HashMap<>();
   private final Map<Integer, Integer> ownCrystals = new HashMap<>();
   private final Map<Integer, Integer> attacked = new HashMap<>();
   private int tick;
   private int lastPopTick = -1073741824;
   private class_243 lastPop;
   private float damageInWindow;
   private boolean latched;
   private final List<class_243> popsThisTick = new ArrayList<>();
   private int popsThisTickAt = Integer.MIN_VALUE;

   public void tick(int window) {
      this.tick++;

      while (!this.pops.isEmpty() && this.tick - this.pops.peekFirst() > window) {
         this.pops.removeFirst();
         this.damageInWindow = this.damageInWindow - this.damages.removeFirst();
      }

      if (this.pops.isEmpty()) {
         this.damageInWindow = 0.0F;
      }

      this.expectedPlacements.values().removeIf(sent -> this.tick - sent > 20);
      this.ownCrystals.values().removeIf(seen -> this.tick - seen > 20);
      this.attacked.values().removeIf(sent -> this.tick - sent > 20);
   }

   public void record(class_243 pos, float damage) {
      if (this.popsThisTickAt != this.tick) {
         this.popsThisTick.clear();
         this.popsThisTickAt = this.tick;
      } else {
         for (class_243 other : this.popsThisTick) {
            if (other.method_1025(pos) <= 144.0) {
               this.mergeIntoLast(pos, damage);
               return;
            }
         }
      }

      this.popsThisTick.add(pos);
      this.pops.addLast(this.tick);
      this.damages.addLast(damage);
      this.damageInWindow += damage;
      this.lastPopTick = this.tick;
      this.lastPop = pos;
   }

   private void mergeIntoLast(class_243 pos, float damage) {
      float last = this.damages.peekLast();
      if (!(damage <= last)) {
         this.damages.removeLast();
         this.damages.addLast(damage);
         this.damageInWindow += damage - last;
         this.lastPop = pos;
      }
   }

   public boolean updateLatch(int hits, int hold) {
      if (this.sinceLast() > Math.max(1, hold)) {
         this.latched = false;
      } else if (this.count() >= hits) {
         this.latched = true;
      }

      return this.latched;
   }

   public boolean latched() {
      return this.latched;
   }

   public int count() {
      return this.pops.size();
   }

   public float damageInWindow() {
      return Math.max(0.0F, this.damageInWindow);
   }

   public int sinceLast() {
      return this.tick - this.lastPopTick;
   }

   public class_243 lastPos() {
      return this.lastPop;
   }

   public void markPlacement(long crystalBlock) {
      this.expectedPlacements.put(crystalBlock, this.tick);
   }

   public boolean onCrystalAdded(int id, long block) {
      if (this.expectedPlacements.remove(block) == null) {
         return false;
      } else {
         this.ownCrystals.put(id, this.tick);
         return true;
      }
   }

   public void markAttack(int id) {
      this.attacked.put(id, this.tick);
   }

   public boolean isOwn(int id) {
      return this.ownCrystals.containsKey(id) || this.attacked.containsKey(id);
   }

   public void forget(int id) {
      this.ownCrystals.remove(id);
      this.attacked.remove(id);
   }

   public void reset() {
      this.pops.clear();
      this.damages.clear();
      this.expectedPlacements.clear();
      this.ownCrystals.clear();
      this.attacked.clear();
      this.damageInWindow = 0.0F;
      this.latched = false;
      this.lastPop = null;
      this.lastPopTick = -1073741824;
      this.popsThisTick.clear();
      this.popsThisTickAt = Integer.MIN_VALUE;
      this.tick = 0;
   }
}
