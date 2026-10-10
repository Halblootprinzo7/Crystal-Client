package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.mixin.HandledScreenAccessor;
import dev.crystaladdon.utils.ClickGate;
import dev.crystaladdon.utils.InventoryGuard;
import dev.crystaladdon.utils.ReactionClock;
import dev.crystaladdon.utils.TotemRules;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1041;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1802;
import net.minecraft.class_465;
import org.lwjgl.glfw.GLFW;

public class InventoryTotem extends CrystalModule {
   private static final int RETRY_TICKS = 10;
   private static final int SWAPPED_TICKS = 2;
   private static final int MAX_FAILURES = 3;
   private static final int FIRST_REPORT_WAIT = 2;
   private static final int LATER_REPORT_WAIT = 4;
   private static final int MIN_GLIDE_STEPS = 3;
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
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("delay"))
                  .description(
                     "Ticks the hand takes to reach the totem once the inventory is open, on top of Stealth's reaction-time and spread by its timing-jitter. 0 swaps as soon as the reaction time is up; keep the inventory open at least that long."
                  ))
               .defaultValue(2))
            .range(0, 20)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Boolean> moveCursor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("move-cursor"))
                  .description(
                     "Also glide the real pointer onto the totem before the swap. Only for the eye: the server gets the same click either way. Where the window system does not report the move (X11, Wayland) or you move the mouse yourself, the swap goes out without it."
                  ))
               .defaultValue(false))
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
      super(CrystalAddon.CATEGORY, "inventory-totem", "Puts a totem from your open inventory into the offhand with the swap-hands click, survival and creative.");
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
            } else if (this.failures < MAX_FAILURES) {
               class_1735 totem = this.wanted(screen) ? this.findTotem(screen) : null;
               if (totem == null) {
                  this.clock.forget(NEED);
               } else if (this.clock.ready(NEED)) {
                  this.start(screen, totem);
                  this.proceed(screen);
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
      } else {
         return !TotemRules.healthAllows((Double)this.health.get(), EntityUtils.getTotalHealth(this.mc.field_1724))
            ? false
            : screen.method_17577().method_34255().method_7960();
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

   // The swap goes out after the reaction time plus delay ticks. With move-cursor the pointer glides over those ticks
   // (at least MIN_GLIDE_STEPS, so it never snaps) and the swap waits one more tick for the frame that puts the
   // slot under it.
   private void start(class_465<?> screen, class_1735 slot) {
      int ticks = Stealth.pace((Integer)this.delay.get());
      boolean pointer = false;
      double startX = 0.0;
      double startY = 0.0;
      double x = 0.0;
      double y = 0.0;
      if ((Boolean)this.moveCursor.get() && !this.warpUnreported && this.mc.method_1569()) {
         HandledScreenAccessor access = (HandledScreenAccessor)screen;
         class_1041 window = this.mc.method_22683();
         double guiX = access.crystal$getX() + slot.field_7873 + 8 + scatter();
         double guiY = access.crystal$getY() + slot.field_7872 + 8 + scatter();
         x = TotemRules.guiToWindow(guiX, window.method_4486(), window.method_4480());
         y = TotemRules.guiToWindow(guiY, window.method_4502(), window.method_4507());
         startX = this.mc.field_1729.method_1603();
         startY = this.mc.field_1729.method_1604();
         pointer = x >= 0.0 && y >= 0.0 && x < window.method_4480() && y < window.method_4507();
      }

      int steps = pointer ? Math.max(MIN_GLIDE_STEPS, ticks) : ticks;
      double curve = (Math.random() * 2.0 - 1.0) * MAX_CURVE;
      this.approach = new InventoryTotem.Approach(screen, slot, pointer, startX, startY, x, y, curve, steps);
   }

   private static double scatter() {
      return (Math.random() + Math.random() - 1.0) * SCATTER;
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
         } else {
            this.finish(screen, a);
         }
      } else {
         this.approach = null;
         this.clock.forget(NEED);
      }
   }

   // True once the last move is answered and the approach can go on. A move the window system does not report, or a
   // hand on the mouse, ends the glide but never the swap: the rest of the delay runs without the pointer.
   private boolean checkWarp(InventoryTotem.Approach a) {
      double mouseX = this.mc.field_1729.method_1603();
      double mouseY = this.mc.field_1729.method_1604();
      if (TotemRules.samePoint(a.beforeX, a.beforeY, a.lastX, a.lastY)) {
         if (TotemRules.samePoint(mouseX, mouseY, a.lastX, a.lastY)) {
            a.answered = true;
            return true;
         } else {
            return this.dropPointer(a);
         }
      } else {
         switch (TotemRules.warpOutcome(mouseX, mouseY, a.lastX, a.lastY, a.beforeX, a.beforeY)) {
            case ARRIVED:
               a.answered = true;
               a.reported = true;
               return true;
            case UNREPORTED:
               if (++a.unanswered < (a.reported ? LATER_REPORT_WAIT : FIRST_REPORT_WAIT)) {
                  return false;
               } else {
                  if (!a.reported) {
                     this.warpUnreported = true;
                     GLFW.glfwSetCursorPos(this.mc.method_22683().method_4490(), a.startX, a.startY);
                     if ((Boolean)this.chatInfo.get()) {
                        this.info("The window system does not report pointer moves; swapping without the pointer from now on.", new Object[0]);
                     }
                  }

                  return this.dropPointer(a);
               }
            default:
               return this.dropPointer(a);
         }
      }
   }

   private boolean dropPointer(InventoryTotem.Approach a) {
      a.pointer = false;
      a.answered = true;
      return true;
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
      if (!InventoryGuard.canMoveIn(screen.method_17577())) {
         this.approach = null;
         this.attempt(false);
      } else if (ClickGate.canClickInventory() && InventoryGuard.claimMoveIn(screen.method_17577())) {
         this.approach = null;
         this.attempt(this.swap(screen, a.slot));
      }
   }

   // The click the swap-hands key sends over the slot, through the screen itself: the creative screen turns it into
   // its own creative packets, every other screen into a swap click on its handler.
   private boolean swap(class_465<?> screen, class_1735 slot) {
      ((HandledScreenAccessor)screen).crystal$onMouseClick(slot, slot.field_7874, 40, class_1713.field_7791);
      return this.mc.field_1724.method_6079().method_31574(class_1802.field_8288);
   }

   private void attempt(boolean sent) {
      this.clock.forget(NEED);
      if (sent) {
         this.cooldown = Stealth.pace(SWAPPED_TICKS);
         this.failures = 0;
         if ((Boolean)this.chatInfo.get()) {
            this.info("Totem moved to the offhand.", new Object[0]);
         }
      } else {
         this.cooldown = Stealth.pace(RETRY_TICKS);
         if (++this.failures == MAX_FAILURES) {
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
      int step;
      double lastX;
      double lastY;
      double beforeX;
      double beforeY;
      boolean answered = true;
      int unanswered;
      boolean reported;

      Approach(class_465<?> screen, class_1735 slot, boolean pointer, double startX, double startY, double targetX, double targetY, double curve, int steps) {
         this.screen = screen;
         this.slot = slot;
         this.pointer = pointer;
         this.startX = startX;
         this.startY = startY;
         this.targetX = targetX;
         this.targetY = targetY;
         this.curve = curve;
         this.steps = steps;
      }
   }
}
