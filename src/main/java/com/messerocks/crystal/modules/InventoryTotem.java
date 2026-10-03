package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.mixin.HandledScreenAccessor;
import com.messerocks.crystal.utils.ClickGate;
import com.messerocks.crystal.utils.InventoryGuard;
import com.messerocks.crystal.utils.ReactionClock;
import com.messerocks.crystal.utils.TotemRules;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1041;
import net.minecraft.class_11908;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1802;
import net.minecraft.class_3675;
import net.minecraft.class_465;
import net.minecraft.class_481;
import net.minecraft.class_3675.class_306;
import net.minecraft.class_3675.class_307;
import org.lwjgl.glfw.GLFW;

public class InventoryTotem extends CrystalModule {
   private static final int RETRY_TICKS = 10;
   private static final int MAX_FAILURES = 3;
   private static final int FIRST_REPORT_WAIT = 2;
   private static final int LATER_REPORT_WAIT = 4;
   private static final double SCATTER = 5.0;
   private static final double MAX_CURVE = 0.12;
   private static final Object NEED = new Object();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> health = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("health"))
               .description("Only act when your health plus absorption is at or below this. 20 acts whenever the offhand has no totem, absorption or not."))
            .defaultValue(20.0)
            .min(1.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Boolean> moveCursor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("move-cursor"))
                  .description(
                     "Move the real pointer onto the totem and press the swap-hands key. Where the window system does not report the move (X11, Wayland), or with this off, the same vanilla swap click is sent without the pointer - after the time the movement would have taken."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> settleTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("settle-ticks"))
                  .description(
                     "Ticks to wait once the pointer is on the slot before the key goes in, spread by Stealth's timing-jitter. Vanilla only fills focusedSlot while the screen renders under the pointer, so pressing on arrival would find nothing there."
                  ))
               .defaultValue(2))
            .min(1)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say what it did."))
               .defaultValue(false))
            .build()
      );
   private final ReactionClock clock = new ReactionClock();
   private InventoryTotem.Approach approach;
   private boolean warpUnreported;
   private int cooldown;
   private int failures;
   private class_465<?> failureScreen;

   public InventoryTotem() {
      super(CrystalAddon.CATEGORY, "inventory-totem", "Moves the cursor onto a totem in your open inventory and presses the swap-hands key.");
   }

   public void onDeactivate() {
      this.approach = null;
      this.warpUnreported = false;
      this.cooldown = 0;
      this.failures = 0;
      this.failureScreen = null;
      this.clock.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null) {
         this.clock.tick();
         if (this.mc.field_1755 instanceof class_465<?> screen) {
            if (screen != this.failureScreen) {
               this.failures = 0;
               this.failureScreen = screen;
            }

            if (this.approach != null) {
               this.proceed(screen);
            } else if (this.cooldown > 0) {
               this.cooldown--;
            } else if (this.failures < 3) {
               class_1735 totem = this.wanted(screen) ? this.findTotem(screen) : null;
               if (totem == null) {
                  this.clock.forget(NEED);
               } else if (this.clock.ready(NEED)) {
                  this.start(screen, totem);
                  if (this.approach != null) {
                     this.proceed(screen);
                  }
               }
            }
         } else {
            this.approach = null;
            this.failures = 0;
            this.failureScreen = null;
            this.clock.forget(NEED);
         }
      }
   }

   private boolean wanted(class_465<?> screen) {
      if (this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
         return false;
      } else if (!TotemRules.healthAllows((Double)this.health.get(), EntityUtils.getTotalHealth(this.mc.field_1724))) {
         return false;
      } else {
         return !screen.method_17577().method_34255().method_7960()
            ? false
            : !(screen instanceof class_481) || (Boolean)this.moveCursor.get() && !this.warpUnreported;
      }
   }

   private class_1735 findTotem(class_465<?> screen) {
      int selected = this.mc.field_1724.method_31548().method_67532();
      class_1735 mainHand = null;

      for (class_1735 slot : screen.method_17577().field_7761) {
         if (this.isOwnTotem(slot) && slot.method_7682()) {
            if (slot.method_34266() != selected) {
               return slot;
            }

            mainHand = slot;
         }
      }

      return mainHand;
   }

   private boolean isOwnTotem(class_1735 slot) {
      return slot.field_7871 == this.mc.field_1724.method_31548()
         && slot.method_7681()
         && slot.method_7677().method_31574(class_1802.field_8288)
         && slot.method_7674(this.mc.field_1724);
   }

   private void start(class_465<?> screen, class_1735 slot) {
      HandledScreenAccessor access = (HandledScreenAccessor)screen;
      class_1041 window = this.mc.method_22683();
      double guiX = access.crystal$getX() + slot.field_7873 + 8 + scatter();
      double guiY = access.crystal$getY() + slot.field_7872 + 8 + scatter();
      double x = TotemRules.guiToWindow(guiX, window.method_4486(), window.method_4480());
      double y = TotemRules.guiToWindow(guiY, window.method_4502(), window.method_4507());
      double startX = this.mc.field_1729.method_1603();
      double startY = this.mc.field_1729.method_1604();
      boolean inWindow = x >= 0.0 && y >= 0.0 && x < window.method_4480() && y < window.method_4507();
      boolean pointer = (Boolean)this.moveCursor.get() && !this.warpUnreported && this.mc.method_1569() && inWindow;
      if (pointer || !(screen instanceof class_481)) {
         double slotWidth = TotemRules.guiToWindow(16.0, window.method_4486(), window.method_4480());
         double distance = Math.hypot(x - startX, y - startY);
         int steps = Math.max(6, Stealth.pace(TotemRules.glideTicks(distance, slotWidth)));
         int settle = Math.max(1, Stealth.pace((Integer)this.settleTicks.get()));
         double curve = (Math.random() * 2.0 - 1.0) * 0.12;
         this.approach = new InventoryTotem.Approach(screen, slot, pointer, startX, startY, x, y, curve, steps, settle);
      }
   }

   private static double scatter() {
      return (Math.random() + Math.random() - 1.0) * 5.0;
   }

   private void proceed(class_465<?> screen) {
      InventoryTotem.Approach a = this.approach;
      if (screen == a.screen && this.wanted(screen) && this.isOwnTotem(a.slot) && a.slot.method_7682()) {
         if (a.pointer && !a.answered) {
            if (!this.checkWarp(a)) {
               return;
            }

            if (a.pointer && a.step == a.steps) {
               return;
            }
         }

         if (a.step < a.steps) {
            a.step++;
            if (a.pointer) {
               this.warp(a);
            }
         } else if (++a.settled >= a.settleNeeded) {
            this.finish(screen, a);
         }
      } else {
         this.approach = null;
      }
   }

   private boolean checkWarp(InventoryTotem.Approach a) {
      double mouseX = this.mc.field_1729.method_1603();
      double mouseY = this.mc.field_1729.method_1604();
      if (TotemRules.samePoint(a.beforeX, a.beforeY, a.lastX, a.lastY)) {
         if (TotemRules.samePoint(mouseX, mouseY, a.lastX, a.lastY)) {
            a.answered = true;
            return true;
         } else {
            this.approach = null;
            this.cooldown = Stealth.pace(10);
            return false;
         }
      } else {
         switch (TotemRules.warpOutcome(mouseX, mouseY, a.lastX, a.lastY, a.beforeX, a.beforeY)) {
            case ARRIVED:
               a.answered = true;
               a.reported = true;
               return true;
            case UNREPORTED:
               if (++a.unanswered < (a.reported ? 4 : 2)) {
                  return false;
               } else if (a.reported) {
                  this.approach = null;
                  this.cooldown = Stealth.pace(10);
                  return false;
               } else {
                  this.warpUnreported = true;
                  GLFW.glfwSetCursorPos(this.mc.method_22683().method_4490(), a.startX, a.startY);
                  a.pointer = false;
                  a.answered = true;
                  if ((Boolean)this.chatInfo.get()) {
                     this.info("The window system does not report pointer moves; clicking the totem slot directly from now on.", new Object[0]);
                  }

                  if (a.screen instanceof class_481) {
                     this.approach = null;
                     return false;
                  }

                  return true;
               }
            default:
               this.approach = null;
               this.cooldown = Stealth.pace(10);
               return false;
         }
      }
   }

   private void warp(InventoryTotem.Approach a) {
      double[] point = TotemRules.glidePoint(a.startX, a.startY, a.targetX, a.targetY, a.curve, (double)a.step / a.steps);
      a.beforeX = this.mc.field_1729.method_1603();
      a.beforeY = this.mc.field_1729.method_1604();
      a.lastX = point[0];
      a.lastY = point[1];
      a.answered = false;
      a.unanswered = 0;
      GLFW.glfwSetCursorPos(this.mc.method_22683().method_4490(), point[0], point[1]);
   }

   private void finish(class_465<?> screen, InventoryTotem.Approach a) {
      if (a.pointer) {
         if (!InventoryGuard.canMoveIn(screen.method_17577())) {
            this.attempt(false);
            this.approach = null;
         } else if (ClickGate.canClickInventory() && InventoryGuard.claimMoveIn(screen.method_17577())) {
            this.attempt(this.press(screen, a.slot));
            this.approach = null;
         }
      } else {
         InventoryTotem.ClickResult result = this.clickDirectly(screen, a.slot);
         if (result != InventoryTotem.ClickResult.CONTENDED) {
            this.attempt(result == InventoryTotem.ClickResult.SENT);
            this.approach = null;
         }
      }
   }

   private boolean press(class_465<?> screen, class_1735 slot) {
      class_1735 focused = ((HandledScreenAccessor)screen).crystal$getFocusedSlot();
      if (focused != null && focused == slot && this.isOwnTotem(focused) && screen.method_17577().method_34255().method_7960()) {
         class_306 key = class_3675.method_15981(this.mc.field_1690.field_1831.method_1428());
         if (key.method_1444() == -1 || key.method_1442() != class_307.field_1668) {
            return this.sendSwap(screen, focused);
         } else if (screen.method_25399() != null) {
            return this.sendSwap(screen, focused);
         } else {
            return screen.method_25404(new class_11908(key.method_1444(), 0, 0)) ? false : this.mc.field_1724.method_6079().method_31574(class_1802.field_8288);
         }
      } else {
         return false;
      }
   }

   private InventoryTotem.ClickResult clickDirectly(class_465<?> screen, class_1735 slot) {
      if (screen instanceof class_481) {
         return InventoryTotem.ClickResult.FAILED;
      } else if (!this.isOwnTotem(slot) || !slot.method_7682()) {
         return InventoryTotem.ClickResult.FAILED;
      } else if (!InventoryGuard.canMoveIn(screen.method_17577())) {
         return InventoryTotem.ClickResult.FAILED;
      } else if (ClickGate.canClickInventory() && InventoryGuard.claimMoveIn(screen.method_17577())) {
         return this.sendSwap(screen, slot) ? InventoryTotem.ClickResult.SENT : InventoryTotem.ClickResult.FAILED;
      } else {
         return InventoryTotem.ClickResult.CONTENDED;
      }
   }

   private boolean sendSwap(class_465<?> screen, class_1735 slot) {
      if (screen instanceof class_481) {
         return false;
      } else {
         this.mc.field_1761.method_2906(screen.method_17577().field_7763, slot.field_7874, 40, class_1713.field_7791, this.mc.field_1724);
         return true;
      }
   }

   private void attempt(boolean sent) {
      this.cooldown = Stealth.pace(10);
      if (sent) {
         this.failures = 0;
         if ((Boolean)this.chatInfo.get() && this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
            this.info("Totem moved to the offhand.", new Object[0]);
         }
      } else {
         if (++this.failures == 3) {
            this.warning("Inventory Totem could not swap a totem in on this screen and stops trying until it is reopened.", new Object[0]);
         }
      }
   }

   private static final class Approach {
      final class_465<?> screen;
      final class_1735 slot;
      boolean pointer;
      final double startX;
      final double startY;
      final double targetX;
      final double targetY;
      final double curve;
      final int steps;
      final int settleNeeded;
      int step;
      int settled;
      double lastX;
      double lastY;
      double beforeX;
      double beforeY;
      boolean answered = true;
      int unanswered;
      boolean reported;

      Approach(
         class_465<?> screen,
         class_1735 slot,
         boolean pointer,
         double startX,
         double startY,
         double targetX,
         double targetY,
         double curve,
         int steps,
         int settleNeeded
      ) {
         this.screen = screen;
         this.slot = slot;
         this.pointer = pointer;
         this.startX = startX;
         this.startY = startY;
         this.targetX = targetX;
         this.targetY = targetY;
         this.curve = curve;
         this.steps = steps;
         this.settleNeeded = settleNeeded;
      }
   }

   private static enum ClickResult {
      SENT,
      FAILED,
      CONTENDED;
   }
}
