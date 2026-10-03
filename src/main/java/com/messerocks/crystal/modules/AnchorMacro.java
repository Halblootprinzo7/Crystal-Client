package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.AimUtils;
import com.messerocks.crystal.utils.AnchorActions;
import com.messerocks.crystal.utils.BlastShield;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.VanillaLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1750;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_640;

public class AnchorMacro extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Keybind> bind = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("macro-bind")).description("Key that runs the anchor cycle.")).defaultValue(Keybind.fromKey(86)))
            .build()
      );
   private final Setting<AnchorMacro.Trigger> trigger = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("trigger"))
                  .description("One cycle per press, or keep going while held."))
               .defaultValue(AnchorMacro.Trigger.Press))
            .build()
      );
   private final Setting<AnchorMacro.Mode> mode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("mode"))
                  .description("Where to put the anchor."))
               .defaultValue(AnchorMacro.Mode.Crosshair))
            .build()
      );
   private final Setting<Double> cyclesPerSecond = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("cycles-per-second"))
                  .description("Rate limit while the key is held. 0 runs a cycle every tick."))
               .defaultValue(0.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(() -> this.trigger.get() == AnchorMacro.Trigger.Hold))
            .build()
      );
   private final Setting<Integer> actionsPerTick = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("actions-per-tick"))
                  .description(
                     "How many steps of the cycle may go out in one tick. 1 keeps every click on its own rotation, which is what an anticheat needs. Raising it collapses place, charge and detonate together - fast, but a single rotation cannot serve three different clicks."
                  ))
               .defaultValue(1))
            .min(1)
            .sliderRange(1, 4)
            .build()
      );
   private final Setting<Integer> predictionTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("prediction-ticks"))
                  .description(
                     "How long a step we have sent is believed before the world gets the last word. 0 derives it from your ping, which is what you want. Without an expiry a placement the server quietly refused would still be charged and detonated into thin air - two junk interactions per rejected anchor, which is both useless and exactly the sort of thing that draws attention."
                  ))
               .defaultValue(0))
            .min(0)
            .sliderRange(0, 40)
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("speed"))
               .description(
                  "Actions per second across the whole cycle - placing, charging, shielding and detonating all draw from this. 0 is unlimited, which is also the most obviously non-human thing the module can do. Applies to a single press too, not just a held key."
               ))
            .defaultValue(8.0)
            .min(0.0)
            .sliderMax(40.0)
            .build()
      );
   private final Setting<Double> placeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("place-speed"))
               .description(
                  "Anchor placements per second. 0 is unlimited. Kept apart from explode-speed because the two want different rates - placing is cheap, detonating is what hurts you."
               ))
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Double> explodeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("explode-speed"))
               .description("Detonations per second. 0 is unlimited."))
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Boolean> requireFov = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("require-field-of-view"))
                  .description("Only fire at anchors that are actually on your screen."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> fovMargin = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("fov-margin"))
                  .description("Degrees shaved off each screen edge."))
               .defaultValue(5.0)
               .min(0.0)
               .sliderMax(30.0)
               .visible(this.requireFov::get))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("Maximum distance."))
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description(
                     "Face the anchor before clicking. Forced on anyway while Stealth's legit-place is enabled - the hit point only matches the click if the server has the rotation it was computed for."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Render the hand swing client side."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> timeout = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("cycle-timeout"))
                  .description(
                     "Ticks a cycle may run before it is given up on. The state machine retries by itself when the server refuses a step - it simply sees the spot is still empty next tick - so this only catches a spot that can never work."
                  ))
               .defaultValue(20))
            .min(1)
            .sliderMax(60)
            .build()
      );
   private final Setting<Boolean> autoRefill = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-refill"))
                  .description("Pull anchors and glowstone from your inventory into a free hotbar slot."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> netherGuard = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("nether-guard"))
                  .description("Refuse to fire in the Nether, where an anchor sets your spawn instead."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug"))
                  .description("Report the spot, charge level and item slots on every press, so a silent failure becomes readable."))
               .defaultValue(false))
            .build()
      );
   private final Setting<AnchorMacro.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description("Hotbar really moves your selection onto anchor and glowstone, the way you would yourself. Silent swaps back within the tick."))
               .defaultValue(AnchorMacro.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> restoreSlot = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("restore-slot"))
                     .description("Go back to the slot you started on once the cycle is done."))
                  .defaultValue(true))
               .visible(() -> this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Report when a cycle aborts and why."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> shield = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("safe-anchor"))
                  .description(
                     "Put a block at your feet between you and the anchor before setting it off, and count that cover when judging the shot. Spot selection, the placement and the damage maths all use the same position, so a spot is only approved on cover that is really going to be there."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Keybind> shieldBind = this.sgSafety
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("safe-anchor-key"))
                  .description(
                     "Key that turns safe-anchor on and off. Unbound by default. It toggles silently - no chat line either way; the module info shows \"unsafe\" while it is off."
                  ))
               .defaultValue(Keybind.none()))
            .build()
      );
   private final Setting<AnchorMacro.ShieldBlock> shieldBlock = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shield-block"))
                     .description("What to shield with. Glowstone is already in your hotbar for the anchor."))
                  .defaultValue(AnchorMacro.ShieldBlock.Glowstone))
               .visible(this.shield::get))
            .build()
      );
   private final Setting<Double> shieldSkipBelow = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("skip-shield-below"))
                  .description("Do not bother shielding when the unshielded blast would already deal less than this to you."))
               .defaultValue(2.0)
               .min(0.0)
               .sliderMax(10.0)
               .visible(this.shield::get))
            .build()
      );
   private final Setting<Double> minShieldGain = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("min-shield-gain"))
                  .description("Damage the shield has to actually take off before it is worth placing."))
               .defaultValue(1.0)
               .min(0.0)
               .sliderMax(10.0)
               .visible(this.shield::get))
            .build()
      );
   private final Setting<Boolean> damageLimit = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("damage-limit"))
                  .description("On top of the shield, refuse to fire when the blast would still hurt you too much."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> maxSelfDamage = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("max-self-damage"))
                  .description("Hard cap on the damage the anchor may deal to you."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.damageLimit::get))
            .build()
      );
   private final Setting<Double> keepHealth = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("keep-health"))
                  .description("Health plus absorption you have to be left with afterwards. Catches the case where the cap alone would still finish you."))
               .defaultValue(4.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.damageLimit::get))
            .build()
      );
   private final Setting<Double> minDamage = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("min-damage"))
               .description("The target has to take at least this much, otherwise the anchor is not worth the items or the blast you eat for it."))
            .defaultValue(4.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Double> minRatio = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("min-damage-ratio"))
               .description(
                  "How many times more the target has to take than you. 1 is an even trade, 2 means they take twice what you do. This is what actually keeps an anchor safe - a shield only trims the blast, the spot decides it."
               ))
            .defaultValue(1.5)
            .min(0.0)
            .sliderRange(0.0, 5.0)
            .build()
      );
   private final Setting<Boolean> requireTotem = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("require-totem"))
                     .description("Only fire while a totem sits in your offhand."))
                  .defaultValue(false))
               .visible(this.damageLimit::get))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Draw the anchor position being worked on."))
               .defaultValue(true))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shape-mode"))
                     .description("How the shape is rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("side-color"))
                  .description("Side colour."))
               .defaultValue(new SettingColor(255, 200, 40, 40))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("line-color"))
                  .description("Line colour."))
               .defaultValue(new SettingColor(255, 200, 40, 180))
               .visible(this.render::get))
            .build()
      );
   private final ActionBudget budget = new ActionBudget();
   private final ActionBudget speedLimit = new ActionBudget();
   private final ActionBudget placeLimit = new ActionBudget();
   private final ActionBudget explodeLimit = new ActionBudget();
   private class_2338 working;
   private AnchorMacro.AnchorState predicted;
   private int cycleTicks;
   private int predictedAge;
   private boolean wasPressed;
   private boolean shieldKeyWasPressed;
   private int returnSlot = -1;
   private String lastShieldProblem;
   private float lastShieldGain;
   private boolean lastShieldDiagonal;
   private class_2338 shieldAssumed;
   private boolean shieldDone;
   private String lastRejection;
   private class_2338 shieldPlaced;

   public AnchorMacro() {
      super(CrystalAddon.CATEGORY, "crystal-anchor-macro", "Runs one full anchor cycle on a keypress.");
   }

   public void onDeactivate() {
      this.restore();
      this.shieldPlaced = null;
      this.clearShield();
      this.budget.reset();
      this.wasPressed = false;
   }

   private void clearShield() {
      this.shieldAssumed = null;
      this.restore();
      this.shieldPlaced = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         if (this.mc.field_1755 != null) {
            this.wasPressed = false;
            this.shieldKeyWasPressed = false;
         } else {
            boolean pressed = ((Keybind)this.bind.get()).isPressed();
            boolean justPressed = pressed && !this.wasPressed;
            this.wasPressed = pressed;
            boolean shieldKeyPressed = ((Keybind)this.shieldBind.get()).isSet() && ((Keybind)this.shieldBind.get()).isPressed();
            if (shieldKeyPressed && !this.shieldKeyWasPressed) {
               this.shield.set(!(Boolean)this.shield.get());
            }

            this.shieldKeyWasPressed = shieldKeyPressed;
            if ((Boolean)this.autoRefill.get() && (pressed || this.working != null) && !AnchorActions.refillHotbar(class_1802.field_23141)) {
               AnchorActions.refillHotbar(class_1802.field_8801);
            }

            this.budget.update((Double)this.cyclesPerSecond.get(), 1);
            this.speedLimit.update((Double)this.speed.get(), 1);
            this.placeLimit.update((Double)this.placeSpeed.get(), 1);
            this.explodeLimit.update((Double)this.explodeSpeed.get(), 1);
            if (this.working != null) {
               this.runAt(this.working);
            } else {
               boolean start = this.trigger.get() == AnchorMacro.Trigger.Hold ? pressed : justPressed;
               if (start) {
                  if (this.trigger.get() != AnchorMacro.Trigger.Hold || !((Double)this.cyclesPerSecond.get() > 0.0) || this.budget.tryConsume()) {
                     if ((Boolean)this.netherGuard.get() && this.mc.field_1687.method_27983() == class_1937.field_25180) {
                        if ((Boolean)this.chatInfo.get()) {
                           this.warning("In the Nether an anchor sets your spawn instead of exploding.", new Object[0]);
                        }
                     } else {
                        class_2338 spot = this.findSpot();
                        if (spot != null) {
                           if ((Boolean)this.debug.get()) {
                              this.info("Spot: %s", new Object[]{AnchorActions.describe(spot)});
                           }

                           this.rememberSlot();
                           this.startCycle(spot);
                        } else {
                           if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                              this.warning("No usable anchor spot. %s", new Object[]{this.spotHint()});
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static String format(class_2338 pos) {
      return pos.method_10263() + " " + pos.method_10264() + " " + pos.method_10260();
   }

   private void rememberSlot() {
      if (this.returnSlot == -1) {
         this.returnSlot = this.mc.field_1724.method_31548().method_67532();
      }
   }

   private void restore() {
      if (this.returnSlot != -1) {
         if (this.mc.field_1724 == null) {
            this.returnSlot = -1;
         } else {
            if (this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar
               && (Boolean)this.restoreSlot.get()
               && this.returnSlot != this.mc.field_1724.method_31548().method_67532()) {
               InvUtils.swap(this.returnSlot, false);
            }

            this.returnSlot = -1;
         }
      }
   }

   private List<class_2338> shieldPositions(class_2338 spot) {
      if (this.mc.field_1724 == null) {
         return List.of();
      } else {
         class_2350 facing = BlastShield.shieldDirection(spot);
         if (facing == null) {
            return List.of();
         } else {
            class_2350 toPlayer = facing.method_10153();
            class_2338 beside = spot.method_10093(toPlayer);
            List<class_2338> candidates = new ArrayList<>(List.of(beside, beside.method_10084(), spot.method_10079(toPlayer, 2)));
            class_243 eyes = this.mc.field_1724.method_33571();
            List<class_2338> diagonals = new ArrayList<>();

            for (class_2350 side : new class_2350[]{toPlayer.method_10170(), toPlayer.method_10160()}) {
               class_2338 diagonal = beside.method_10093(side);
               diagonals.add(diagonal);
               diagonals.add(diagonal.method_10084());
            }

            diagonals.sort(Comparator.comparingDouble(pos -> pos.method_46558().method_1025(eyes)));
            candidates.addAll(diagonals);
            return candidates;
         }
      }
   }

   private boolean shieldStands() {
      if (this.shieldPlaced == null
         && this.shieldAssumed != null
         && this.mc.field_1687 != null
         && this.mc.field_1687.method_8320(this.shieldAssumed).method_27852(((AnchorMacro.ShieldBlock)this.shieldBlock.get()).block())) {
         this.shieldPlaced = this.shieldAssumed;
      }

      return this.shieldPlaced != null
         && this.mc.field_1687 != null
         && this.mc.field_1687.method_8320(this.shieldPlaced).method_27852(((AnchorMacro.ShieldBlock)this.shieldBlock.get()).block());
   }

   private void startCycle(class_2338 spot) {
      this.shieldDone = false;
      this.shieldAssumed = null;
      this.shieldPlaced = null;
      this.working = spot;
      this.predicted = null;
      this.predictedAge = 0;
      this.cycleTicks = 0;
      AnchorActions.resetTurn();
      this.runAt(spot);
   }

   private AnchorMacro.AnchorState stateAt(class_2338 pos) {
      AnchorMacro.AnchorState world = this.worldStateAt(pos);
      return this.predicted != null && world.ordinal() < this.predicted.ordinal() ? this.predicted : world;
   }

   private AnchorMacro.AnchorState worldStateAt(class_2338 pos) {
      int charges = AnchorActions.charges(pos);
      return charges < 0 ? AnchorMacro.AnchorState.Air : (charges > 0 ? AnchorMacro.AnchorState.Loaded : AnchorMacro.AnchorState.Anchor);
   }

   private int predictionWindow() {
      if ((Integer)this.predictionTicks.get() > 0) {
         return (Integer)this.predictionTicks.get();
      } else {
         int ping = 0;
         if (this.mc.method_1562() != null && this.mc.field_1724 != null) {
            class_640 entry = this.mc.method_1562().method_2871(this.mc.field_1724.method_5667());
            if (entry != null) {
               ping = entry.method_2959();
            }
         }

         return class_3532.method_15340(ping / 50 + 3, 3, 40);
      }
   }

   private void runAt(class_2338 spot) {
      if (++this.cycleTicks > this.cycleTimeout()) {
         if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
            this.warning("Gave up on the spot after %d ticks.", new Object[]{this.cycleTimeout()});
         }

         this.finish();
      } else {
         if (this.predicted != null) {
            AnchorMacro.AnchorState real = this.worldStateAt(spot);
            if (real.ordinal() >= this.predicted.ordinal() || ++this.predictedAge > this.predictionWindow()) {
               if ((Boolean)this.debug.get() && real.ordinal() < this.predicted.ordinal()) {
                  this.info("Prediction %s never confirmed - back to %s", new Object[]{this.predicted, real});
               }

               this.predicted = null;
               this.predictedAge = 0;
            }
         }

         int budgetLeft = (Integer)this.actionsPerTick.get();
         if (Stealth.turnCap() > 0.0) {
            budgetLeft = 1;
         }

         while (budgetLeft-- > 0) {
            String unsafe = this.safetyProblem(spot);
            if (unsafe != null && (Boolean)this.shield.get() && !this.shieldDone && this.effectiveShield() == null) {
               class_2338 rescue = this.shieldSpotFor(spot);
               if (rescue != null && this.safetyProblem(spot, rescue) == null) {
                  unsafe = null;
               }
            }

            if (unsafe != null) {
               if ((Boolean)this.chatInfo.get()) {
                  this.warning("Cycle stopped by safe-anchor: %s.", new Object[]{unsafe});
               }

               this.finish();
               return;
            }

            switch (this.stateAt(spot)) {
               case Air:
                  if (!InvUtils.findInHotbar(new class_1792[]{class_1802.field_23141}).found()) {
                     if ((Boolean)this.chatInfo.get()) {
                        this.warning("No anchor in the hotbar.", new Object[0]);
                     }

                     this.finish();
                     return;
                  }

                  if (AnchorActions.placeHit(spot) == null) {
                     if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                        this.warning("Nothing to click that puts an anchor at %s from where you stand.", new Object[]{format(spot)});
                     }

                     this.finish();
                     return;
                  }

                  if (!AnchorActions.place(spot, this.options(this.placeLimit))) {
                     return;
                  }

                  this.predicted = AnchorMacro.AnchorState.Anchor;
                  if ((Boolean)this.debug.get()) {
                     this.info("Placed anchor at %s", new Object[]{format(spot)});
                  }
                  break;
               case Anchor:
                  if (AnchorActions.charges(spot) < 0) {
                     return;
                  }

                  if (!InvUtils.findInHotbar(new class_1792[]{class_1802.field_8801}).found()) {
                     if ((Boolean)this.chatInfo.get()) {
                        this.warning("No glowstone in the hotbar.", new Object[0]);
                     }

                     this.finish();
                     return;
                  }

                  if (!AnchorActions.charge(spot, this.options())) {
                     return;
                  }

                  this.predicted = AnchorMacro.AnchorState.Loaded;
                  if ((Boolean)this.debug.get()) {
                     this.info("Charged anchor at %s", new Object[]{format(spot)});
                  }
                  break;
               case Loaded:
                  if ((Boolean)this.shield.get() && !this.shieldDone) {
                     AnchorMacro.ShieldStep step = this.placeShieldNow(spot);
                     if (step == AnchorMacro.ShieldStep.Turning) {
                        return;
                     }

                     this.shieldDone = true;
                     if (step == AnchorMacro.ShieldStep.Placed && Stealth.turnCap() > 0.0) {
                        return;
                     }
                  }

                  String blast = this.safetyProblem(spot);
                  if (blast != null) {
                     if ((Boolean)this.chatInfo.get()) {
                        this.warning("Cycle stopped by safe-anchor: %s.", new Object[]{blast});
                     }

                     this.finish();
                     return;
                  }

                  int charges = Math.max(AnchorActions.charges(spot), 1);
                  if (AnchorActions.offhandBlocksDetonation(charges)) {
                     if ((Boolean)this.chatInfo.get()) {
                        this.warning("Glowstone in your offhand would charge the anchor instead of setting it off.", new Object[0]);
                     }

                     this.finish();
                     return;
                  }

                  if (!AnchorActions.detonate(spot, charges, this.options(this.explodeLimit))) {
                     return;
                  }

                  if ((Boolean)this.debug.get()) {
                     this.info("Detonated at %s", new Object[]{format(spot)});
                  }

                  this.finish();
                  return;
            }
         }
      }
   }

   private void finish() {
      this.working = null;
      this.predicted = null;
      this.predictedAge = 0;
      this.shieldDone = false;
      this.shieldAssumed = null;
      this.shieldPlaced = null;
      this.cycleTicks = 0;
      AnchorActions.resetTurn();
      this.restore();
   }

   private int cycleTimeout() {
      double cap = Stealth.turnCap();
      return cap <= 0.0 ? (Integer)this.timeout.get() : (Integer)this.timeout.get() + (int)Math.ceil(180.0 / cap) * 4;
   }

   private AnchorActions.Options options() {
      return this.options(null);
   }

   private AnchorActions.Options options(ActionBudget extra) {
      boolean mustRotate = (Boolean)this.rotate.get() || Stealth.legitPlace();
      BooleanSupplier gate = () -> {
         double cost = Stealth.actionCost();
         if (extra != null && !extra.canAfford(cost)) {
            return false;
         } else if (!this.speedLimit.canAfford(cost)) {
            return false;
         } else if (!Stealth.claimAction()) {
            return false;
         } else {
            return extra != null && !extra.tryConsume(cost) ? false : this.speedLimit.tryConsume(cost);
         }
      };
      return new AnchorActions.Options(mustRotate, (Boolean)this.swing.get(), this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar, 50, gate);
   }

   private AnchorMacro.ShieldStep placeShieldNow(class_2338 anchorPos) {
      float bare = BlastShield.anchorDamage(this.mc.field_1724, anchorPos.method_46558());
      if (bare <= (Double)this.shieldSkipBelow.get()) {
         if ((Boolean)this.debug.get()) {
            this.info("No shield: blast is only %.1f, skip-shield-below is %.1f", new Object[]{bare, this.shieldSkipBelow.get()});
         }

         return AnchorMacro.ShieldStep.Skipped;
      } else if (this.shieldStands() && this.shieldPositions(anchorPos).contains(this.shieldPlaced)) {
         if ((Boolean)this.debug.get()) {
            this.info("Shield already up at %s", new Object[]{format(this.shieldPlaced)});
         }

         return AnchorMacro.ShieldStep.Skipped;
      } else {
         this.shieldPlaced = null;
         class_2338 spot = this.shieldSpotFor(anchorPos);
         if (spot == null) {
            if ((Boolean)this.debug.get() || (Boolean)this.chatInfo.get()) {
               this.warning("No shield: %s.", new Object[]{this.lastShieldProblem});
            }

            return AnchorMacro.ShieldStep.Skipped;
         } else {
            FindItemResult block = InvUtils.findInHotbar(new class_1792[]{((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item()});
            if (!block.found()) {
               if ((Boolean)this.chatInfo.get()) {
                  this.warning("No %s in the hotbar for the shield.", new Object[]{((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item().toString()});
               }

               return AnchorMacro.ShieldStep.Skipped;
            } else {
               class_3965 support = this.supportFor(spot, anchorPos);
               if (support != null) {
                  if (this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar) {
                     this.rememberSlot();
                  }

                  if (!AnchorActions.clickWith(support, block, this.options())) {
                     return AnchorMacro.ShieldStep.Turning;
                  } else {
                     if ((Boolean)this.debug.get()) {
                        this.info(
                           "Shield at %s%s (blast %.1f, saves %.1f)",
                           new Object[]{format(spot), this.lastShieldDiagonal ? " diagonal" : "", bare, this.lastShieldGain}
                        );
                     }

                     this.shieldAssumed = spot;
                     this.shieldPlaced = spot;
                     return AnchorMacro.ShieldStep.Placed;
                  }
               } else {
                  if ((Boolean)this.debug.get() || (Boolean)this.chatInfo.get()) {
                     this.warning("No shield: no face you can see or reach to click against, other than the anchor.", new Object[0]);
                  }

                  return AnchorMacro.ShieldStep.Skipped;
               }
            }
         }
      }
   }

   private class_2338 spotFor(class_3965 hit) {
      class_2338 looking = hit.method_17777();
      if (AnchorActions.charges(looking) >= 0) {
         return looking;
      } else {
         class_1750 context = new class_1750(this.mc.field_1724, class_1268.field_5808, new class_1799(class_1802.field_23141), hit);
         return !context.method_7716() ? null : context.method_8037();
      }
   }

   private class_2338 findSpot() {
      if (this.mode.get() == AnchorMacro.Mode.Crosshair) {
         class_3965 hit = AimUtils.lookingAtBlock((Double)this.range.get());
         if (hit == null) {
            return null;
         } else {
            class_2338 spot = this.spotFor(hit);
            if (spot == null) {
               return null;
            } else if (AnchorActions.charges(spot) >= 0 || AnchorActions.canPlace(spot) && AnchorActions.placeHit(spot) != null) {
               if (!Stealth.allowsBlock(spot, spot.method_46558())) {
                  return null;
               } else if (this.safetyProblem(spot) != null) {
                  return null;
               } else {
                  return this.inView(spot.method_46558()) ? spot : null;
               }
            } else {
               return null;
            }
         }
      } else {
         class_1657 target = TargetUtils.getPlayerTarget((Double)this.range.get() + 8.0, SortPriority.LowestHealth);
         if (target == null) {
            this.lastRejection = "no target in range";
            return null;
         } else {
            double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
            class_2338 origin = this.mc.field_1724.method_24515();
            int radius = (int)Math.ceil((Double)this.range.get());
            class_2338 best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int outOfRange = 0;
            int notInView = 0;
            int stealthBlocked = 0;
            int cannotPlace = 0;
            int lethal = 0;
            int unsafe = 0;
            int tooWeak = 0;
            int badTrade = 0;
            String unsafeWhy = null;
            float bestRejectedDamage = 0.0F;
            float worstRatio = 0.0F;

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= radius; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     class_2338 pos = origin.method_10069(x, y, z);
                     class_243 center = pos.method_46558();
                     if (this.mc.field_1724.method_33571().method_1022(center) > (Double)this.range.get()) {
                        outOfRange++;
                     } else if (!this.inView(center)) {
                        notInView++;
                     } else if (!Stealth.allowsBlock(pos, center)) {
                        stealthBlocked++;
                     } else if (AnchorActions.charges(pos) < 0 && !AnchorActions.canPlace(pos)) {
                        cannotPlace++;
                     } else if (this.selfDamage(center) >= selfHealth) {
                        lethal++;
                     } else {
                        String problem = this.safetyProblem(pos);
                        if (problem != null) {
                           class_2338 rescue = this.shieldSpotFor(pos);
                           if (rescue == null || this.safetyProblem(pos, rescue) != null) {
                              unsafe++;
                              unsafeWhy = problem;
                              continue;
                           }
                        }

                        float damage = BlastShield.anchorDamage(target, center);
                        if (damage < (Double)this.minDamage.get()) {
                           tooWeak++;
                           bestRejectedDamage = Math.max(bestRejectedDamage, damage);
                        } else {
                           float self = this.selfDamageBehind(center, this.shieldStands() ? this.shieldPlaced : null);
                           if (self > 0.0F && damage / self < (Double)this.minRatio.get()) {
                              badTrade++;
                              worstRatio = Math.max(worstRatio, damage / self);
                           } else {
                              double score = damage - self;
                              if (score > bestScore) {
                                 if (AnchorActions.charges(pos) < 0 && AnchorActions.placeHit(pos) == null) {
                                    cannotPlace++;
                                 } else {
                                    bestScore = score;
                                    best = pos;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (best == null) {
               this.lastRejection = this.describeSpotRejection(
                  outOfRange, notInView, stealthBlocked, cannotPlace, lethal, unsafe, unsafeWhy, tooWeak, bestRejectedDamage, badTrade, worstRatio
               );
            } else {
               this.lastRejection = null;
            }

            return best;
         }
      }
   }

   private String describeSpotRejection(
      int outOfRange,
      int notInView,
      int stealthBlocked,
      int cannotPlace,
      int lethal,
      int unsafe,
      String unsafeWhy,
      int tooWeak,
      float bestRejected,
      int badTrade,
      float bestRatio
   ) {
      if (badTrade > 0) {
         return String.format("%d spots refused by min-damage-ratio %.1f - best trade was only %.1fx", badTrade, this.minRatio.get(), bestRatio);
      } else if (tooWeak > 0) {
         return String.format("%d spots were safe but none reached min-damage %.1f (best was %.1f)", tooWeak, this.minDamage.get(), bestRejected);
      } else if (unsafe > 0) {
         return String.format("%d spots refused by damage-limit: %s", unsafe, unsafeWhy);
      } else if (lethal > 0) {
         return String.format("%d spots would simply kill you", lethal);
      } else if (cannotPlace > 0) {
         return "nowhere an anchor could stand";
      } else if (stealthBlocked > 0) {
         return "blocked by Stealth's limits";
      } else if (notInView > 0) {
         return "everything usable is outside your field of view";
      } else {
         return outOfRange > 0 ? String.format("nothing within range %.1f", this.range.get()) : "nothing in range at all";
      }
   }

   private class_3965 supportFor(class_2338 target, class_2338 anchorPos) {
      LegitPlace.Result result = LegitPlace.forBlock(target, VanillaLimits.blockRange(), anchorPos);
      return result == null ? null : result.hit();
   }

   private class_2338 shieldSpotFor(class_2338 anchorPos) {
      this.lastShieldProblem = "no candidate position";
      if (!(Boolean)this.shield.get()) {
         return null;
      } else {
         List<class_2338> candidates = this.shieldPositions(anchorPos);
         if (candidates.isEmpty()) {
            this.lastShieldProblem = "anchor is straight above or below you, no side to shield";
            return null;
         } else {
            class_243 anchor = anchorPos.method_46558();
            float bare = BlastShield.anchorDamage(this.mc.field_1724, anchor);
            class_2680 shieldState = ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).block().method_9564();
            class_2338 best = null;
            float bestGain = 0.0F;
            int bestIndex = -1;
            float bestRejectedGain = -1.0F;
            int index = -1;

            for (class_2338 pos : candidates) {
               index++;
               if (pos.equals(anchorPos)) {
                  this.lastShieldProblem = "the only spot left is the anchor position itself";
               } else if (this.mc.field_1687.method_22347(pos)
                  || this.mc.field_1687.method_8320(pos).method_45474()
                     && AnchorActions.canReplaceAt(pos, ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item())) {
                  if (!BlockUtils.canPlaceBlock(pos, true, ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).block())) {
                     this.lastShieldProblem = "the shield spots overlap your own hitbox - step to the middle of your block";
                  } else if (this.supportFor(pos, anchorPos) == null) {
                     this.lastShieldProblem = "no face you can see or reach to click against there, other than the anchor";
                  } else {
                     float gain = bare - BlastShield.anchorDamageBehindShield(this.mc.field_1724, anchor, pos, shieldState);
                     if (gain < (Double)this.minShieldGain.get()) {
                        bestRejectedGain = Math.max(bestRejectedGain, gain);
                     } else if (gain > bestGain) {
                        best = pos;
                        bestGain = gain;
                        bestIndex = index;
                     }
                  }
               } else {
                  this.lastShieldProblem = "every spot between you and the anchor is occupied";
               }
            }

            if (best == null && bestRejectedGain >= 0.0F) {
               this.lastShieldProblem = String.format("best spot only saves %.1f, min-shield-gain is %.1f", bestRejectedGain, this.minShieldGain.get());
            }

            this.lastShieldGain = bestGain;
            this.lastShieldDiagonal = bestIndex >= 3;
            return best;
         }
      }
   }

   private class_2338 effectiveShield() {
      return this.shieldStands() ? this.shieldPlaced : this.shieldAssumed;
   }

   private float selfDamageBehind(class_243 center, class_2338 shieldAt) {
      return shieldAt == null
         ? this.selfDamage(center)
         : BlastShield.anchorDamageBehindShield(this.mc.field_1724, center, shieldAt, ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).block().method_9564());
   }

   private float selfDamage(class_243 center) {
      return BlastShield.anchorDamage(this.mc.field_1724, center);
   }

   private String spotHint() {
      if (this.mode.get() != AnchorMacro.Mode.Crosshair) {
         return this.lastRejection != null ? this.lastRejection + "." : "Nothing in range that an anchor could act on.";
      } else {
         class_3965 hit = AimUtils.lookingAtBlock((Double)this.range.get());
         if (hit == null) {
            return String.format("Your crosshair is not on a block within %.1f blocks.", this.range.get());
         } else {
            class_2338 spot = this.spotFor(hit);
            if (spot == null) {
               return String.format(
                  "Vanilla would not place an anchor at %d %d %d - the block there does not give way.",
                  hit.method_17777().method_10263(),
                  hit.method_17777().method_10264(),
                  hit.method_17777().method_10260()
               );
            } else if (AnchorActions.charges(spot) < 0 && !AnchorActions.canPlace(spot)) {
               return String.format(
                  "Cannot put an anchor at %d %d %d - blocked, or nothing to click against.", spot.method_10263(), spot.method_10264(), spot.method_10260()
               );
            } else {
               String unsafe = this.safetyProblem(spot);
               if (unsafe != null) {
                  return "Blocked by safe-anchor: " + unsafe + ".";
               } else {
                  return !this.inView(spot.method_46558()) ? "The spot is outside your field of view. Lower fov-margin or turn require-field-of-view off." : "";
               }
            }
         }
      }
   }

   private String safetyProblem(class_2338 pos) {
      return this.safetyProblem(pos, this.effectiveShield());
   }

   private String safetyProblem(class_2338 pos, class_2338 shieldAt) {
      if (!(Boolean)this.damageLimit.get()) {
         return null;
      } else if ((Boolean)this.requireTotem.get() && !this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
         return "no totem in your offhand";
      } else {
         float selfDamage = this.selfDamageBehind(pos.method_46558(), shieldAt);
         if (selfDamage > (Double)this.maxSelfDamage.get()) {
            return String.format("it would deal %.1f to you, cap is %.1f", selfDamage, this.maxSelfDamage.get());
         } else {
            double left = EntityUtils.getTotalHealth(this.mc.field_1724) - selfDamage;
            return left < this.keepHealth.get() ? String.format("it would leave you on %.1f health", Math.max(0.0, left)) : null;
         }
      }
   }

   private boolean inView(class_243 pos) {
      return !(Boolean)this.requireFov.get() || AimUtils.inFieldOfView(pos, (Double)this.fovMargin.get());
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.working != null) {
         event.renderer.box(this.working, (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
      }
   }

   public String getInfoString() {
      String state = this.working == null ? null : this.stateAt(this.working).toString();
      if (!(Boolean)this.shield.get()) {
         return state == null ? "unsafe" : state + " unsafe";
      } else {
         return state;
      }
   }

   public static enum AnchorState {
      Air,
      Anchor,
      Loaded;
   }

   public static enum Mode {
      Crosshair,
      BestDamage;
   }

   public static enum ShieldBlock {
      Glowstone,
      Obsidian;

      public class_1792 item() {
         return this == Obsidian ? class_1802.field_8281 : class_1802.field_8801;
      }

      public class_2248 block() {
         return this == Obsidian ? class_2246.field_10540 : class_2246.field_10171;
      }
   }

   private static enum ShieldStep {
      Placed,
      Skipped,
      Turning;
   }

   public static enum SwitchMode {
      Hotbar,
      Silent;
   }

   public static enum Trigger {
      Press,
      Hold;
   }
}
