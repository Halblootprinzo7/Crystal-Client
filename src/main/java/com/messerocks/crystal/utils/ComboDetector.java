package com.messerocks.crystal.utils;

import java.util.ArrayDeque;
import net.minecraft.class_243;

public final class ComboDetector {
   private final ArrayDeque<Integer> pops = new ArrayDeque<>();
   private int tick;
   private int lastPopTick = -1073741824;
   private class_243 lastPop;

   public void tick(int window) {
      this.tick++;

      while (!this.pops.isEmpty() && this.tick - this.pops.peekFirst() > window) {
         this.pops.removeFirst();
      }
   }

   public void record(class_243 pos) {
      this.pops.addLast(this.tick);
      this.lastPopTick = this.tick;
      this.lastPop = pos;
   }

   public int count() {
      return this.pops.size();
   }

   public int sinceLast() {
      return this.tick - this.lastPopTick;
   }

   public class_243 lastPos() {
      return this.lastPop;
   }

   public void reset() {
      this.pops.clear();
      this.lastPop = null;
      this.lastPopTick = -1073741824;
      this.tick = 0;
   }
}
