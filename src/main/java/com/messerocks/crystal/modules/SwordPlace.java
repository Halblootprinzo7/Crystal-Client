package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.HumanSwap;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaLimits;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1750;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3489;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class SwordPlace extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Keybind> bind = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("place-bind"))
                  .description("Press this to place one obsidian at your crosshair. Set it to a mouse button for the classic feel."))
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
                     "SwapBack selects obsidian and returns to the sword within the same tick. Stay leaves obsidian selected. Both move the real hotbar selection - there is no invisible swap."
                  ))
               .defaultValue(SwordPlace.SwitchMode.SwapBack))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("How far your crosshair may reach for a block to place against."))
            .defaultValue(4.5)
            .min(0.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Integer> cooldown = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("cooldown"))
                  .description(
                     "Ticks after a placement in which no second one can go out. Vanilla uses 4 between block placements. A press inside the cooldown is not lost, it goes out as soon as the cooldown ends."
                  ))
               .defaultValue(4))
            .min(0)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Swing the hand on place."))
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
   // A press stays valid this long while it waits for the cooldown or for the head to come back to the crosshair,
   // as long as the crosshair stays on something placeable.
   private static final int PRESS_VALID_TICKS = 15;
   private static final Object TURN_OWNER = new Object();
   private static final int TURN_PRIORITY = 100;
   private final TurnProgress turn = TurnProgress.SHARED;
   private boolean wasPressed;
   private int lockout;
   private int pending;
   // Human mode: the slot to go back to once this press is done, when we switched to obsidian for it.
   private int humanReturn = -1;
   private static final int SWAP_PRIORITY = 100;
   private class_2338 previewPos;
   private class_2338 lastPlaced;

   public SwordPlace() {
      super(CrystalAddon.CATEGORY, "sword-place", "Place obsidian at your crosshair without dropping your sword.");
   }

   public void onDeactivate() {
      this.wasPressed = false;
      this.lockout = 0;
      this.pending = 0;
      this.previewPos = null;
      this.turn.reset(TURN_OWNER);
      this.finishHumanPress();
   }

   @EventHandler
   private void onTick(Pre event) {
      this.previewPos = null;
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.lockout > 0) {
            this.lockout--;
         }

         boolean pressed = ((Keybind)this.bind.get()).isPressed();
         if (pressed && !this.wasPressed) {
            this.pending = PRESS_VALID_TICKS;
         }

         this.wasPressed = pressed;
         class_2338 target = this.weaponInHand() ? this.placementTarget(this.trace()) : null;
         if (target == null) {
            // Nothing placeable under the crosshair any more: drop the press rather than firing it later
            // somewhere you have since looked at.
            this.pending = 0;
            this.finishHumanPress();
         } else {
            this.previewPos = target;
            if (this.pending > 0 && this.lockout == 0 && this.place(target)) {
               this.pending = 0;
               this.finishHumanPress();
               this.lockout = (Integer)this.cooldown.get();
               this.lastPlaced = target;
               if ((Boolean)this.debug.get()) {
                  this.info("Sent click -> %d %d %d", new Object[]{target.method_10263(), target.method_10264(), target.method_10260()});
                  if (this.mc.field_1690.field_1904.method_1434()) {
                     this.warning("Your bind is also Minecraft's \"Use Item\" key - vanilla places a second block. Unbind one of the two.", new Object[0]);
                  }
               }
            } else {
               this.expirePress();
            }
         }
      }
   }

   private void expirePress() {
      if (this.pending > 0) {
         this.pending--;
         if (this.pending == 0) {
            this.finishHumanPress();
         }
      }
   }

   // Back to the weapon after a press we switched for, a tick after the click like a hand would.
   private void finishHumanPress() {
      if (this.humanReturn != -1) {
         if (this.switchMode.get() == SwordPlace.SwitchMode.SwapBack) {
            HumanSwap.returnLater(this.humanReturn);
         }

         this.humanReturn = -1;
      }
   }

   private boolean weaponInHand() {
      return !(Boolean)this.onlyWithWeapon.get()
         || this.isWeapon(this.mc.field_1724.method_6047())
         || this.humanReturn != -1 && this.mc.field_1724.method_6047().method_31574(class_1802.field_8281);
   }

   // Human mode: obsidian is only placed once it has been in hand for a full tick. Starts the switch.
   private boolean obsidianInHand() {
      FindItemResult obsidian = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281});
      if (obsidian.getHand() == class_1268.field_5810) {
         return true;
      } else {
         int selected = this.mc.field_1724.method_31548().method_67532();
         if (obsidian.slot() != selected && this.humanReturn == -1) {
            this.humanReturn = selected;
         }

         return HumanSwap.ready(obsidian.slot(), SWAP_PRIORITY);
      }
   }

   // Where obsidian would land for this crosshair hit, or null. Blocks that react to a click are left alone:
   // the click would open a chest or, worse, set off a charged anchor in front of you instead of placing.
   private class_2338 placementTarget(class_3965 look) {
      if (look == null || LegitPlace.isInteractive(this.mc.field_1687.method_8320(look.method_17777()))) {
         return null;
      } else {
         class_1750 context = new class_1750(this.mc.field_1724, class_1268.field_5808, new class_1799(class_1802.field_8281), look);
         if (!context.method_7716()) {
            return null;
         } else {
            class_2338 target = context.method_8037();
            return BlockUtils.canPlaceBlock(target, true, class_2246.field_10540) ? target : null;
         }
      }
   }

   private boolean place(class_2338 target) {
      if (!InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281}).found()) {
         this.pending = 0;
         return false;
      } else if (Stealth.humanMode() && !this.obsidianInHand()) {
         return false;
      } else if ((Stealth.legitPlace() || Stealth.humanMode()) && this.headElsewhere()) {
         // A rotation module still holds the head somewhere else on the server. Clicking now would not line up
         // with what the server thinks you look at, so turn back to the crosshair first and click from there.
         double yaw = this.mc.field_1724.method_36454();
         double pitch = this.mc.field_1724.method_36455();
         if (!this.turn.wouldReach(TURN_OWNER, yaw, pitch, TURN_PRIORITY)) {
            this.turn.turnTo(TURN_OWNER, yaw, pitch, TURN_PRIORITY, null);
            return false;
         } else {
            return !Stealth.claimAction() ? false : this.turn.turnTo(TURN_OWNER, yaw, pitch, TURN_PRIORITY, () -> this.click(target));
         }
      } else {
         return !Stealth.claimAction() ? false : this.click(target);
      }
   }

   private boolean headElsewhere() {
      return Rotations.rotating
         && (
            Math.abs(class_3532.method_15338(Rotations.serverYaw - this.mc.field_1724.method_36454())) > 0.5
               || Math.abs(Rotations.serverPitch - this.mc.field_1724.method_36455()) > 0.5
         );
   }

   private boolean click(class_2338 target) {
      class_3965 hit = this.trace();
      if (hit != null && target.equals(this.placementTarget(hit))) {
         FindItemResult obsidian = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281});
         if (!obsidian.found()) {
            return false;
         } else {
            class_1268 hand = obsidian.getHand();
            boolean swapped = false;
            if (hand == null) {
               swapped = this.selectSlot(obsidian.slot());
               hand = class_1268.field_5808;
            }

            if (this.mc.field_1724.method_5998(hand).method_31574(class_1802.field_8281)) {
               BlockUtils.interact(hit, hand, (Boolean)this.swing.get());
            }

            if (swapped) {
               InvUtils.swapBack();
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private boolean selectSlot(int slot) {
      if (slot == this.mc.field_1724.method_31548().method_67532()) {
         return false;
      } else {
         boolean swapBack = this.switchMode.get() == SwordPlace.SwitchMode.SwapBack;
         InvUtils.swap(slot, swapBack);
         return swapBack;
      }
   }

   private class_3965 trace() {
      double reach = Stealth.legitPlace() ? Math.min((Double)this.range.get(), VanillaLimits.blockRange()) : (Double)this.range.get();
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

   public static enum SwitchMode {
      SwapBack,
      Stay;
   }
}
