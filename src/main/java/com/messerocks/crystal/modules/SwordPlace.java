package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ClickGate;
import com.messerocks.crystal.utils.HotbarSwap;
import net.minecraft.class_2680;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import com.messerocks.crystal.utils.KeyPriority;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaClick;
import com.messerocks.crystal.utils.VanillaLimits;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1750;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3489;
import net.minecraft.class_3532;
import net.minecraft.class_3675;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3675.class_306;
import net.minecraft.class_3675.class_307;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class SwordPlace extends CrystalModule {
   private static final double ON_CAMERA = 0.5;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Keybind> bind = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("place-bind"))
                  .description(
                     "Hold this to place obsidian at your crosshair. Set it to a mouse button for the classic feel - but not the one Minecraft uses for Use Item: then vanilla right-clicks on the same press too, and the module refuses to act."
                  ))
               .defaultValue(Keybind.none()))
            .build()
      );
   private final Setting<Boolean> onlyWithWeapon = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-with-weapon"))
                  .description("Only act while a sword or axe is the selected item, so it never fires when you are actually holding blocks."))
               .defaultValue(true))
            .build()
      );
   private final Setting<SwordPlace.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description(
                     "SwapBack selects obsidian and returns to the sword once a few ticks pass without a click. Stay leaves obsidian selected. Both move the real hotbar selection - there is no invisible swap."
                  ))
               .defaultValue(SwordPlace.SwitchMode.SwapBack))
            .build()
      );
   private final Setting<Boolean> swapBack = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("swap-back"))
                     .description("Return to the sword after placing - a few quiet ticks later, never in the tick of the click. Off leaves obsidian selected."))
                  .defaultValue(true))
               .visible(() -> this.switchMode.get() == SwordPlace.SwitchMode.SwapBack))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description(
                  "How far your crosshair may reach for a block to place against. Never more than the server's block reach - 4.5 in vanilla - whatever this says."
               ))
            .defaultValue(4.5)
            .min(1.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Integer> cooldown = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("cooldown"))
                  .description(
                     "Ticks after a placement in which no second one can go out, spread by Stealth's timing-jitter. Vanilla uses 4 between held-down placements; this also swallows a key that bounces and would otherwise read as two presses."
                  ))
               .defaultValue(4))
            .min(2)
            .sliderRange(2, 10)
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Show the hand swing on place. Off only hides the animation; the swing packet goes out wherever vanilla's would."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug"))
                  .description(
                     "Print a line for every click this module sends, with the position. If you see two blocks appear but only one line, the second one is not coming from here."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Outline the block that would be placed."))
               .defaultValue(true))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shape-mode"))
                     .description("How the preview is drawn."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("side-color"))
                  .description("Fill colour of the preview."))
               .defaultValue(new SettingColor(150, 90, 220, 40))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("line-color"))
                  .description("Outline colour of the preview."))
               .defaultValue(new SettingColor(180, 120, 255, 220))
               .visible(this.render::get))
            .build()
      );
   private boolean wasPressed;
   private int lockout;
   private class_2338 previewPos;
   private final SwordPlace.Press press = new SwordPlace.Press();
   private int pressSlot = -1;
   private boolean saidWaiting;
   // A shared Use-key press Sword Place took: vanilla must not see the key until it is let go.
   private boolean ownsUseKey;
   private int switchedTo = -1;
   private int switchedFrom = -1;
   private class_2338 lastPlaced;
   // A press seen as a key event. The tick-time poll of the key misses a tap that goes down and up between ticks.
   private boolean pressLatched;
   // SwapBack: the borrowed slot is only handed back once the click went out or the press ended. Handing it back on
   // the switch tick started the quiet-tick countdown early, so a click that had to wait a tick or two lost its
   // obsidian to the swap-back and never happened.
   private HotbarSwap borrowed;
   private int borrowedFrom = -1;

   public SwordPlace() {
      super(CrystalAddon.CATEGORY, "sword-place", "Place obsidian at your crosshair without dropping your sword.");
   }

   public void onDeactivate() {
      this.wasPressed = false;
      this.pressLatched = false;
      this.ownsUseKey = false;
      this.lockout = 0;
      this.endPress();
      this.previewPos = null;
      this.lastPlaced = null;
      this.switchedTo = -1;
      this.switchedFrom = -1;
   }

   @EventHandler
   private void onTick(Pre event) {
      this.previewPos = null;
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.mc.field_1755 != null) {
            this.wasPressed = ((Keybind)this.bind.get()).isPressed();
            this.pressLatched = false;
            this.endPress();
         } else {
            if (this.lockout > 0) {
               this.lockout--;
            }

            this.press.countDown();
            if (!this.press.live()) {
               this.giveBack();
            }

            boolean pressed = ((Keybind)this.bind.get()).isPressed();
            boolean firstDown = pressed && !this.wasPressed || this.pressLatched;
            this.pressLatched = false;
            this.wasPressed = pressed;
            // place-bind may be Minecraft's own Use key. Then each press is shared: Sword Place takes it when it would
            // put obsidian down (weapon in hand, crosshair on a spot for it), and vanilla keeps every other one - a
            // crystal on obsidian, an anchor click, eating. A press Sword Place takes is kept from vanilla entirely.
            boolean shared = this.bindIsUseKey();
            if (!pressed) {
               this.ownsUseKey = false;
            }

            if (shared && firstDown && !this.takesSharedPress()) {
               firstDown = false;
            }

            {
               if (firstDown && this.lockout == 0) {
                  this.press.start();
                  this.saidWaiting = false;
                  this.ownsUseKey = shared;
               }

               // Before anything reads the Use key this tick: Sword Place's own checks below, and vanilla right after.
               if (shared && (this.ownsUseKey || this.press.live())) {
                  this.suppressVanillaUse();
               }

               if (!this.press.hasSwitched()) {
                  this.pressSlot = -1;
               }

               int selected = this.mc.field_1724.method_31548().method_67532();
               if (this.switchedTo != -1 && selected != this.switchedTo) {
                  this.switchedTo = -1;
                  this.switchedFrom = -1;
               }

               if (this.pressSlot != -1 && selected != this.pressSlot) {
                  this.endPress();
               }

               if (!(Boolean)this.onlyWithWeapon.get() || this.weaponInHand()) {
                  class_3965 look = this.trace();
                  if (look != null) {
                     class_1750 context = new class_1750(this.mc.field_1724, class_1268.field_5808, new class_1799(class_1802.field_8281), look);
                     if (context.method_7716()) {
                        class_2338 target = context.method_8037();
                        if (BlockUtils.canPlaceBlock(target, true, class_2246.field_10540)) {
                           class_3965 crosshair = LegitPlace.confirmPlacement(
                              target, class_1802.field_8281, this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455(), this.reach()
                           );
                           if (crosshair != null) {
                              this.previewPos = target;
                              if (this.press.live()) {
                                 double sentYaw = LegitPlace.currentYaw();
                                 double sentPitch = LegitPlace.currentPitch();
                                 if (!this.onCamera(sentYaw, sentPitch)) {
                                    if (this.worthTheLook(crosshair)) {
                                       TurnProgress.requestCamera();
                                       if ((Boolean)this.debug.get() && !this.saidWaiting) {
                                          this.saidWaiting = true;
                                          this.info("Waiting for the look another module holds to come back to your crosshair.", new Object[0]);
                                       }
                                    }
                                 } else {
                                    class_3965 click = LegitPlace.confirmPlacement(target, class_1802.field_8281, sentYaw, sentPitch, this.reach());
                                    if (click != null) {
                                       class_2338 previous = this.lastPlaced;
                                       this.lastPlaced = target;
                                       if (!this.place(click)) {
                                          this.lastPlaced = previous;
                                          if (this.press.live() && this.worthTheLook(crosshair)) {
                                             TurnProgress.requestCamera();
                                          }
                                       } else {
                                          this.endPress();
                                          this.lockout = Math.max(2, Stealth.pace((Integer)this.cooldown.get()));
                                          if ((Boolean)this.debug.get()) {
                                             this.info(
                                                "Sent click -> %d %d %d", new Object[]{target.method_10263(), target.method_10264(), target.method_10260()}
                                             );
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (this.press.live()) {
               KeyPriority.hold();
            }
         }
      }
   }

   // Whether a shared Use-key press is Sword Place's: weapon in hand and the crosshair on a spot obsidian would go,
   // not on obsidian or bedrock (that press is for a crystal) or on a block that reacts to a click.
   private boolean takesSharedPress() {
      if ((Boolean)this.onlyWithWeapon.get() && !this.weaponInHand()) {
         return false;
      } else {
         class_3965 look = this.trace();
         if (look == null) {
            return false;
         } else {
            class_2680 state = this.mc.field_1687.method_8320(look.method_17777());
            if (state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987) || LegitPlace.isInteractive(state)) {
               return false;
            } else {
               class_1750 context = new class_1750(this.mc.field_1724, class_1268.field_5808, new class_1799(class_1802.field_8281), look);
               return context.method_7716() && BlockUtils.canPlaceBlock(context.method_8037(), true, class_2246.field_10540);
            }
         }
      }
   }

   // Keep a press Sword Place took away from vanilla: drop what is queued on the Use key and its held state, so
   // neither the press nor the held-button repeat places from whatever is in hand.
   private void suppressVanillaUse() {
      KeyBindingAccessor use = (KeyBindingAccessor)this.mc.field_1690.field_1904;
      use.meteor$setTimesPressed(0);
      this.mc.field_1690.field_1904.method_23481(false);
   }

   private void endPress() {
      if (this.press.live()) {
         KeyPriority.release();
      }

      this.press.end();
      this.pressSlot = -1;
      this.giveBack();
   }

   private void giveBack() {
      this.giveBack(false);
   }

   // soon: the click went out, so the obsidian has done its job - back to the weapon on the next tick that allows
   // it, which also frees the off-hand for the crystal that comes next.
   private void giveBack(boolean soon) {
      if (this.borrowed != null) {
         this.back(this.borrowed, this.borrowedFrom, soon);
         this.borrowed = null;
         this.borrowedFrom = -1;
      }
   }

   @EventHandler
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press && this.mc.field_1755 == null && ((Keybind)this.bind.get()).matches(event.input)) {
         this.pressLatched = true;
      }
   }

   @EventHandler
   private void onMouse(MouseClickEvent event) {
      if (event.action == KeyAction.Press && this.mc.field_1755 == null && ((Keybind)this.bind.get()).matches(event.input)) {
         this.pressLatched = true;
      }
   }

   private boolean onCamera(double yaw, double pitch) {
      return Math.abs(class_3532.method_15338(yaw - this.mc.field_1724.method_36454())) <= 0.5 && Math.abs(pitch - this.mc.field_1724.method_36455()) <= 0.5;
   }

   private boolean worthTheLook(class_3965 hit) {
      if (!this.mc.field_1690.field_1904.method_1434() && !Stealth.handsBusy()) {
         if (!Stealth.allowsBlock(hit.method_17777(), hit.method_17784())) {
            return false;
         } else if (this.mc.field_1724.method_6047().method_31574(class_1802.field_8281)) {
            return true;
         } else {
            return this.mc.field_1724.method_6079().method_31574(class_1802.field_8281) && VanillaClick.reaches(hit, class_1268.field_5810)
               ? true
               : InvUtils.find(stack -> stack.method_31574(class_1802.field_8281), 0, 8).found();
         }
      } else {
         return false;
      }
   }

   private boolean place(class_3965 hit) {
      if (!Stealth.allowsBlock(hit.method_17777(), hit.method_17784())) {
         return false;
      } else if (this.mc.field_1690.field_1904.method_1434()) {
         return false;
      } else if (!ClickGate.canUse()) {
         return false;
      } else {
         HotbarSwap silent = null;
         int selected = this.mc.field_1724.method_31548().method_67532();
         class_1268 hand;
         if (this.mc.field_1724.method_6047().method_31574(class_1802.field_8281)) {
            hand = class_1268.field_5808;
         } else if (this.mc.field_1724.method_6079().method_31574(class_1802.field_8281) && VanillaClick.reaches(hit, class_1268.field_5810)) {
            hand = class_1268.field_5810;
         } else {
            FindItemResult obsidian = InvUtils.find(stack -> stack.method_31574(class_1802.field_8281), 0, 8);
            if (!obsidian.found()) {
               return false;
            }

            if (this.goesBack()) {
               silent = HotbarSwap.silently(obsidian.slot());
               if (!silent.ready()) {
                  return false;
               }
            } else {
               if (!HotbarSwap.select(obsidian.slot())) {
                  return false;
               }

               // Stay: remember the switch, so only-with-weapon still recognises obsidian as Sword Place's own
               // and the next press works without reselecting the sword by hand.
               this.switchedTo = obsidian.slot();
               if (this.switchedFrom == -1) {
                  this.switchedFrom = selected;
               }
            }

            hand = class_1268.field_5808;
            if (ClickGate.slotChangedThisTick()) {
               if (silent != null && silent.swapped() && this.borrowed == null) {
                  this.borrowed = silent;
                  this.borrowedFrom = selected;
               }

               this.pressSlot = obsidian.slot();
               this.press.switched();
               return false;
            }
         }

         boolean sent = false;
         if (this.mc.field_1724.method_5998(hand).method_31574(class_1802.field_8281) && Stealth.claimUse()) {
            // use() returns null when nothing went out (breaking a block, riding); only a sent click uses the press up.
            int usesBefore = ClickGate.usesThisTick();
            sent = VanillaClick.use(hit, (Boolean)this.swing.get()) != null || ClickGate.usesThisTick() > usesBefore;
         }

         if (silent != null && this.borrowed == null) {
            this.back(silent, selected);
         }

         if (sent) {
            this.giveBack(true);
         }

         return sent;
      }
   }

   private boolean goesBack() {
      return this.switchMode.get() == SwordPlace.SwitchMode.SwapBack && (Boolean)this.swapBack.get();
   }

   private void back(HotbarSwap silent, int from) {
      this.back(silent, from, false);
   }

   private void back(HotbarSwap silent, int from, boolean soon) {
      if (soon) {
         silent.backSoon();
      } else {
         silent.back();
      }

      if (silent.swapped()) {
         this.switchedTo = this.mc.field_1724.method_31548().method_67532();
         if (this.switchedFrom == -1) {
            this.switchedFrom = from;
         }
      }
   }

   private boolean weaponInHand() {
      if (this.isWeapon(this.mc.field_1724.method_6047())) {
         return true;
      } else {
         int selected = this.mc.field_1724.method_31548().method_67532();
         return this.press.hasSwitched() && selected == this.pressSlot
            ? true
            : this.switchedTo != -1
               && this.switchedFrom != -1
               && selected == this.switchedTo
               && this.isWeapon(this.mc.field_1724.method_31548().method_5438(this.switchedFrom));
      }
   }

   private boolean bindIsUseKey() {
      Keybind keybind = (Keybind)this.bind.get();
      if (!keybind.isSet()) {
         return false;
      } else {
         class_306 use = class_3675.method_15981(this.mc.field_1690.field_1904.method_1428());
         class_307 type = keybind.isKey() ? class_307.field_1668 : class_307.field_1672;
         return use.method_1442() == type && use.method_1444() == keybind.getValue();
      }
   }

   private double reach() {
      return Math.min((Double)this.range.get(), VanillaLimits.blockRange());
   }

   private class_3965 trace() {
      double reach = this.reach();
      class_243 eyes = this.mc.field_1724.method_33571();
      class_243 dir = this.mc.field_1724.method_5828(1.0F);
      class_243 end = eyes.method_1019(dir.method_1021(reach));
      class_3965 hit = this.mc.field_1687.method_17742(new class_3959(eyes, end, class_3960.field_17559, class_242.field_1348, this.mc.field_1724));
      if (hit == null || hit.method_17783() != class_240.field_1332) {
         return null;
      } else {
         return eyes.method_1025(hit.method_17784()) > reach * reach ? null : hit;
      }
   }

   private boolean isWeapon(class_1799 stack) {
      return stack.method_31573(class_3489.field_42611) || stack.method_31573(class_3489.field_42612);
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if ((Boolean)this.debug.get() && this.mc.field_1724 != null) {
         if (event.newState.method_27852(class_2246.field_10540) && !event.oldState.method_27852(class_2246.field_10540)) {
            if (!(this.mc.field_1724.method_24515().method_10262(event.pos) > 144.0)) {
               boolean mine = event.pos.equals(this.lastPlaced);
               this.info(
                  "Obsidian appeared at %d %d %d (%s)",
                  new Object[]{
                     event.pos.method_10263(), event.pos.method_10264(), event.pos.method_10260(), mine ? "from this module" : "NOT from this module"
                  }
               );
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.previewPos != null) {
         event.renderer.box(new class_238(this.previewPos), (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
      }
   }

   static final class Press {
      static final int TICKS = 3;
      private int left;
      private boolean switched;

      void countDown() {
         if (this.left > 0 && --this.left == 0) {
            this.switched = false;
         }
      }

      void start() {
         this.left = Math.max(this.left, 3);
      }

      void switched() {
         if (!this.switched && this.left > 0) {
            this.switched = true;
            this.left++;
         }
      }

      boolean live() {
         return this.left > 0;
      }

      boolean hasSwitched() {
         return this.switched && this.left > 0;
      }

      void end() {
         this.left = 0;
         this.switched = false;
      }
   }

   public static enum SwitchMode {
      SwapBack,
      Stay;
   }
}
