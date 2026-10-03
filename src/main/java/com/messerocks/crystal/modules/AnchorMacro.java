package com.messerocks.crystal.modules;

import com.messerocks.crystal.utils.KeyPriority;
import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.AimUtils;
import com.messerocks.crystal.utils.AnchorActions;
import com.messerocks.crystal.utils.BlastShield;
import com.messerocks.crystal.utils.ClickGate;
import com.messerocks.crystal.utils.HotbarSwap;
import com.messerocks.crystal.utils.InventoryGuard;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaClick;
import com.messerocks.crystal.utils.VanillaLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Sent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
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
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_640;
import net.minecraft.class_2338.class_2339;

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
                  .description(
                     "How many cycles may start per second while the key is held, at an irregular spacing. A cycle is three or four clicks at least two ticks apart, so about two a second is already quick."
                  ))
               .defaultValue(3.0)
               .range(0.1, 20.0)
               .sliderRange(0.1, 5.0)
               .visible(() -> this.trigger.get() == AnchorMacro.Trigger.Hold))
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
                  "Clicks per second across the whole cycle - placing, charging, shielding and detonating all draw from this, at an irregular spacing. Applies to a single press too, not just a held key. Two clicks of the macro are at least one tick apart regardless."
               ))
            .defaultValue(20.0)
            .range(1.0, 20.0)
            .sliderRange(1.0, 20.0)
            .build()
      );
   private final Setting<Double> placeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("place-speed"))
               .description(
                  "Anchor placements per second, on top of speed - the lower of the two applies. Kept apart from explode-speed because the two want different rates - placing is cheap, detonating is what hurts you."
               ))
            .defaultValue(20.0)
            .range(0.5, 20.0)
            .sliderRange(0.5, 20.0)
            .build()
      );
   private final Setting<Double> explodeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("explode-speed"))
               .description("Detonations per second, on top of speed - the lower of the two applies."))
            .defaultValue(20.0)
            .range(0.5, 20.0)
            .sliderRange(0.5, 20.0)
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
               .description("Maximum distance. Never further than the server's block interaction range, whatever this says."))
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
                     "Turn onto each face before clicking it. Off, a step only goes out when the rotation the server already has lands on the right face - in Crosshair mode that is the spot you point at, but the glowstone shield and, after you move, the anchor itself are often out of it. Forced on while Stealth's force-rotate is on."
                  ))
               .defaultValue(true))
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
                     "Ticks a cycle may run before it is given up on. Ticks spent waiting for speed, place-speed or explode-speed, for a reaction time, for you to stop walking, for you to look back at a spot you turned away from, or for a hotbar slot to stand its tick before the click do not count - the last three have a limit of the same length of their own. The state machine retries by itself when the server refuses a step - it simply sees the spot is still empty next tick - so this only catches a spot that can never work."
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
                  .description(
                     "Pull anchors and glowstone from your inventory into a free hotbar slot - one swap click, only while you stand still and once a hand could have opened the inventory and pointed at the stack, with a pause before the next click."
                  ))
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
                  .description(
                     "Hotbar really moves your selection onto anchor and glowstone, the way you would yourself - the number key a tick before the click, while the head still turns or the pace runs out, since a slot has to stand a tick before it clicks. Silent goes back to your slot once a few ticks have passed without a click; its click comes a tick after the switch."
                  ))
               .defaultValue(AnchorMacro.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> restoreSlot = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("restore-slot"))
                     .description(
                        "Go back to the slot you started on once the cycle is done and the key is let go - a tick or two after the last click, never in the same tick. A slot you picked yourself in between stays."
                     ))
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
                  .description(
                     "Do not bother shielding when the unshielded blast would already deal less than this to you - unless even that little would break damage-limit, keep-health in particular."
                  ))
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
   // A press seen as a key event: polling the key once per tick misses a tap that goes down and up between ticks.
   private boolean pressLatched;
   private static final int MACRO_ROTATION_PRIORITY = 90;
   private final ActionBudget budget = new ActionBudget();
   private final ActionBudget speedLimit = new ActionBudget();
   private final ActionBudget placeLimit = new ActionBudget();
   private final ActionBudget explodeLimit = new ActionBudget();
   private final AnchorActions.Sightings sightings = new AnchorActions.Sightings();
   private final AnchorActions.Refill refill = new AnchorActions.Refill();
   private int ticks;
   private int nextClickTick;
   private AnchorMacro.Blast blastShown;
   private boolean spotWaitsOnSight;
   private boolean restorePending;
   private int restoreAt;
   private int cycleSlot = -1;
   private class_2338 working;
   private AnchorMacro.AnchorState predicted;
   private int cycleTicks;
   private int cameraTicks;
   private int sightTicks;
   private int slotTicks;
   private boolean countedTick;
   private double nextCost = Double.NaN;
   private double nextCycleCost = Double.NaN;
   private int predictedAge;
   private boolean wasPressed;
   private boolean shieldKeyWasPressed;
   private int returnSlot = -1;
   private String lastShieldProblem;
   private static final String HIDES_ANCHOR = "a shield there would hide every face of the anchor you could still click to set it off";
   private float lastShieldGain;
   private boolean lastShieldDiagonal;
   private class_2338 shieldAssumed;
   private boolean shieldDone;
   private int shieldAge;
   private boolean unsafeWarned;
   private String lastRejection;
   private class_2338 shieldPlaced;
   private boolean sneakWarned;
   private boolean viewWarned;
   private class_2338 detonatedAt;
   private int detonatedAge;
   private boolean spotWaitsOnBlast;
   private boolean pressQueued;
   private final Set<String> heldWarnings = new HashSet<>();

   public AnchorMacro() {
      super(CrystalAddon.CATEGORY, "crystal-anchor-macro", "Runs one full anchor cycle on a keypress.");
   }

   public void onDeactivate() {
      this.finish();
      if (this.restorePending
         && this.mc.field_1724 != null
         && this.returnSlot != -1
         && this.cycleSlot != -1
         && this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar
         && (Boolean)this.restoreSlot.get()
         && HotbarSwap.stillOn(this.cycleSlot)) {
         HotbarSwap.selectLater(this.returnSlot, this.cycleSlot, Stealth.reactionTicks());
      }

      this.restorePending = false;
      this.returnSlot = -1;
      this.cycleSlot = -1;
      this.sightings.clear();
      this.refill.reset();
      this.blastShown = null;
      this.spotWaitsOnSight = false;
      this.budget.reset();
      this.speedLimit.reset();
      this.placeLimit.reset();
      this.explodeLimit.reset();
      this.wasPressed = false;
      this.shieldKeyWasPressed = false;
      this.detonatedAt = null;
      this.detonatedAge = 0;
      this.pressQueued = false;
      this.heldWarnings.clear();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.isActive()) {
         if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
            if (this.sessionChanged()) {
               this.returnSlot = -1;
               this.onDeactivate();
            }

            this.ticks++;
            this.sightings.tick();
            if (this.detonatedAt != null && (AnchorActions.charges(this.detonatedAt) <= 0 || ++this.detonatedAge > this.predictionWindow())) {
               this.blastShown = new AnchorMacro.Blast(this.detonatedAt, this.ticks);
               this.detonatedAt = null;
               this.detonatedAge = 0;
            }

            if (this.blastShown != null && this.sightings.reacted(this.blastShown)) {
               this.blastShown = null;
            }

            this.noteAnchors();
            if (this.mc.field_1755 != null) {
               this.wasPressed = false;
               this.pressLatched = false;
               this.shieldKeyWasPressed = false;
               this.pressQueued = false;
               this.heldWarnings.clear();
            } else {
               boolean pressed = ((Keybind)this.bind.get()).isPressed();
               boolean justPressed = pressed && !this.wasPressed || this.pressLatched;
               this.pressLatched = false;
               this.wasPressed = pressed;
               if (!pressed) {
                  this.heldWarnings.clear();
               }

               boolean shieldKeyPressed = ((Keybind)this.shieldBind.get()).isSet() && ((Keybind)this.shieldBind.get()).isPressed();
               if (shieldKeyPressed && !this.shieldKeyWasPressed) {
                  this.shield.set(!(Boolean)this.shield.get());
               }

               this.shieldKeyWasPressed = shieldKeyPressed;
               if ((Boolean)this.autoRefill.get() && (pressed || this.working != null)) {
                  this.refill.tick(class_1802.field_23141, class_1802.field_8801);
               }

               this.budget.update((Double)this.cyclesPerSecond.get(), 1);
               this.speedLimit.update((Double)this.speed.get(), 1);
               this.placeLimit.update((Double)this.placeSpeed.get(), 1);
               this.explodeLimit.update((Double)this.explodeSpeed.get(), 1);
               if (this.working != null) {
                  KeyPriority.hold();
                  // A press during a running cycle starts the next one once this cycle is done, instead of being lost.
                  if (justPressed && this.trigger.get() == AnchorMacro.Trigger.Press) {
                     this.pressQueued = true;
                  }

                  this.runAt(this.working);
               } else {
                  boolean start = this.trigger.get() == AnchorMacro.Trigger.Hold ? pressed : justPressed || this.pressQueued;
                  this.pressQueued = false;
                  if (!start && !pressed) {
                     this.restoreIfDue(false);
                  }

                  if (start) {
                     if (this.blastShown != null) {
                        this.pressQueued = this.trigger.get() == AnchorMacro.Trigger.Press;
                        if (this.mode.get() == AnchorMacro.Mode.BestDamage) {
                           this.findSpot();
                        }
                     } else if ((Boolean)this.netherGuard.get() && this.mc.field_1687.method_27983() == class_1937.field_25180) {
                        if ((Boolean)this.chatInfo.get()) {
                           this.report("In the Nether an anchor sets your spawn instead of exploding.");
                        }
                     } else {
                        class_2338 spot = this.findSpot();
                        if (spot != null) {
                           if (this.trigger.get() == AnchorMacro.Trigger.Hold) {
                              if (Double.isNaN(this.nextCycleCost)) {
                                 this.nextCycleCost = Stealth.actionCost();
                              }

                              if (!this.budget.tryConsume(this.nextCycleCost)) {
                                 return;
                              }

                              this.nextCycleCost = Double.NaN;
                           }

                           if ((Boolean)this.debug.get()) {
                              this.info("Spot: %s", new Object[]{AnchorActions.describe(spot)});
                           }

                           this.rememberSlot();
                           this.restorePending = false;
                           this.startCycle(spot);
                        } else if (!this.spotWaitsOnBlast && !this.spotWaitsOnSight) {
                           if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                              this.report("No usable anchor spot. %s", this.spotHint());
                           }
                        } else {
                           this.pressQueued = this.trigger.get() == AnchorMacro.Trigger.Press;
                        }
                     }
                  }
               }
            }
         }
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

   private static String format(class_2338 pos) {
      return pos.method_10263() + " " + pos.method_10264() + " " + pos.method_10260();
   }

   private void report(String message, Object... args) {
      if (this.trigger.get() != AnchorMacro.Trigger.Hold || !this.wasPressed || this.heldWarnings.add(message)) {
         this.warning(message, args);
      }
   }

   private void rememberSlot() {
      if (this.returnSlot == -1) {
         this.returnSlot = HotbarSwap.homeSlot();
         this.cycleSlot = -1;
      }
   }

   private void restoreIfDue(boolean now) {
      if (this.restorePending && (now || this.ticks >= this.restoreAt)) {
         boolean mine = this.mc.field_1724 != null && this.returnSlot != -1 && this.cycleSlot != -1 && HotbarSwap.homeSlot() == this.cycleSlot;
         if (!mine
            || this.switchMode.get() != AnchorMacro.SwitchMode.Hotbar
            || !(Boolean)this.restoreSlot.get()
            || this.returnSlot == this.cycleSlot
            || HotbarSwap.select(this.returnSlot)
            || now) {
            this.restorePending = false;
            this.returnSlot = -1;
            this.cycleSlot = -1;
         }
      }
   }

   private void noteAnchors() {
      class_2338 origin = this.mc.field_1724.method_24515();
      int radius = (int)Math.ceil(this.reach());
      class_2339 pos = new class_2339();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               pos.method_25504(origin, x, y, z);
               if (AnchorActions.charges(pos) >= 0) {
                  this.sightings.ready(pos);
               }
            }
         }
      }
   }

   @EventHandler
   private void onSent(Sent event) {
      if (this.mc.method_18854()) {
         this.sightings.onSent(event.packet);
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
      this.sneakWarned = false;
      this.viewWarned = false;
      this.shieldAssumed = null;
      this.shieldPlaced = null;
      this.working = spot;
      this.predicted = null;
      this.predictedAge = 0;
      this.cycleTicks = 0;
      this.cameraTicks = 0;
      this.sightTicks = 0;
      this.slotTicks = 0;
      AnchorActions.resetTurn(this);
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
      int before = this.mc.field_1724.method_31548().method_67532();
      this.advanceAt(spot);
      int after = this.mc.field_1724.method_31548().method_67532();
      if (after != before) {
         this.cycleSlot = after;
      }

      if (this.working != null && ClickGate.slotChangedThisTick()) {
         if (this.countedTick) {
            this.cycleTicks--;
         }

         this.countedTick = false;
         if (after != before && (Boolean)this.debug.get()) {
            this.info("Slot %d selected - the click follows once the slot has stood a tick.", new Object[]{after});
         }

         if (++this.slotTicks > this.cycleTimeout()) {
            if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
               this.report("Gave up on the spot: the hotbar slot kept changing - a slot has to stand a tick before anything is clicked with it.");
            }

            this.finish();
         }
      }
   }

   private void advanceAt(class_2338 spot) {
      this.countedTick = false;
      boolean unnoticed = AnchorActions.charges(spot) >= 0 && !this.sightings.noticed(spot);
      boolean offhandBusy = InventoryGuard.offhandInFlight();
      boolean outOfSight = !Stealth.inView(new class_238(spot));
      if (this.mustRotate() && TurnProgress.cameraNeeded()) {
         if (++this.cameraTicks > this.cycleTimeout()) {
            if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
               this.report(
                  "Gave up on the spot: while you walk, glide or click yourself - or a module clicks along your crosshair - the macro does not turn on its own and only clicks what your view lands on."
               );
            }

            this.finish();
            return;
         }
      } else if (outOfSight) {
         if (++this.sightTicks > this.cycleTimeout()) {
            if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
               this.report(
                  "Gave up on the spot at %s: it is out of sight - outside Stealth's view-angle, and the macro does not turn round for it.", format(spot)
               );
            }

            this.finish();
            return;
         }
      } else if (!unnoticed && !offhandBusy && !this.waitingOnRate(this.stateAt(spot))) {
         this.countedTick = true;
         if (++this.cycleTicks > this.cycleTimeout()) {
            if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
               this.report("Gave up on the spot after %d ticks.", this.cycleTimeout());
            }

            this.finish();
            return;
         }
      }

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

      if (!unnoticed && !offhandBusy && !outOfSight) {
         String unsafe = this.unsafeEvenShielded(spot);
         if (unsafe != null && this.worldStateAt(spot) != AnchorMacro.AnchorState.Air) {
            // The anchor already stands: stopping now would leave it - charged, perhaps - right next to you. Wait for
            // the spot to become safe again (you step back, the shield goes up); the cycle timeout still ends it.
            this.waitUnsafe(unsafe);
         } else if (unsafe != null) {
            if ((Boolean)this.chatInfo.get()) {
               this.report("Cycle stopped by safe-anchor: %s.", unsafe);
            }

            this.finish();
         } else {
            AnchorMacro.AnchorState state = this.stateAt(spot);
            int detonator = this.detonationPreference();
            boolean sneakBlocks = state == AnchorMacro.AnchorState.Anchor
               ? AnchorActions.sneakBlocksCharge()
               : state == AnchorMacro.AnchorState.Loaded && AnchorActions.sneakBlocksDetonation(detonator);
            if (sneakBlocks) {
               if (!this.sneakWarned && ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get())) {
                  this.report(
                     "Let go of sneak - sneaking with an item in hand, the server puts glowstone beside the anchor instead of charging it, and will not set it off."
                  );
               }

               this.sneakWarned = true;
            } else if (state != AnchorMacro.AnchorState.Air && !this.mustRotate() && !AnchorActions.viewLandsOn(spot)) {
               if (!this.viewWarned && ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get())) {
                  this.report("Look at the anchor at %s - with rotate off the macro only clicks what your own view lands on.", format(spot));
               }

               this.viewWarned = true;
            } else {
               switch (state) {
                  case Air:
                     if (!AnchorActions.findAnchor().found()) {
                        if ((Boolean)this.chatInfo.get()) {
                           this.report("No anchor in the hotbar.");
                        }

                        this.finish();
                        return;
                     }

                     if (AnchorActions.placeHit(spot, this.mustRotate()) == null) {
                        if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                           this.report(
                              this.mustRotate()
                                 ? "Nothing to click that puts an anchor at %s from where you stand."
                                 : "Your view does not land on anything that puts an anchor at %s - look at it, or turn rotate on.",
                              format(spot)
                           );
                        }

                        this.finish();
                        return;
                     }

                     if (!AnchorActions.canGrip(class_1802.field_23141, AnchorActions.placeHit(spot, this.mustRotate()))) {
                        if ((Boolean)this.chatInfo.get()) {
                           this.report(
                              "The anchor is only in your offhand, and the item in your main hand would take the click - put an anchor in the hotbar or empty your main hand."
                           );
                        }

                        this.finish();
                        return;
                     }

                     if (!BlockUtils.canPlaceBlock(spot, true, class_2246.field_23152)) {
                        return;
                     }

                     AnchorActions.place(spot, this.options(this.placeLimit, () -> {
                        this.predicted = AnchorMacro.AnchorState.Anchor;
                        if ((Boolean)this.debug.get()) {
                           this.info("Placed anchor at %s", new Object[]{format(spot)});
                        }
                     }));
                     break;
                  case Anchor:
                     if (AnchorActions.charges(spot) < 0) {
                        return;
                     }

                     if (!AnchorActions.findGlowstone().found()) {
                        if ((Boolean)this.chatInfo.get()) {
                           this.report(
                              "No glowstone in the hotbar - glowstone in the offhand cannot charge without the main hand setting a charged anchor off."
                           );
                        }

                        this.finish();
                        return;
                     }

                     AnchorActions.charge(spot, this.options(null, () -> {
                        this.predicted = AnchorMacro.AnchorState.Loaded;
                        if ((Boolean)this.debug.get()) {
                           this.info("Charged anchor at %s", new Object[]{format(spot)});
                        }
                     }));
                     break;
                  case Loaded:
                     int charges = AnchorActions.charges(spot);
                     if (charges <= 0) {
                        return;
                     }

                     if ((Boolean)this.shield.get() && !this.shieldDone) {
                        AnchorMacro.ShieldStep step = this.placeShieldNow(spot);
                        if (step == AnchorMacro.ShieldStep.Turning) {
                           return;
                        }

                        this.shieldDone = true;
                        if (step == AnchorMacro.ShieldStep.Placed) {
                           return;
                        }
                     }

                     if (this.shieldDone && this.shieldAssumed != null) {
                        if (!this.shieldStands()) {
                           // The server refused the shield (or it was broken): place it again rather than detonating
                           // as if it stood.
                           this.shieldDone = false;
                           this.shieldAssumed = null;
                           this.shieldPlaced = null;
                           return;
                        }

                        // Detonate only once the shield had time to be confirmed; the client shows it the moment the
                        // click goes out, whether the server accepts it or not.
                        if (this.shieldAge++ < Math.max(1, this.predictionWindow() - 3)) {
                           return;
                        }
                     }

                     String blast = this.safetyProblem(spot);
                     if (blast != null) {
                        this.waitUnsafe(blast);
                        return;
                     }

                     if (AnchorActions.offhandBlocksDetonation(charges)) {
                        if (!AnchorActions.findGlowstone().found()) {
                           if ((Boolean)this.chatInfo.get()) {
                              this.report(
                                 "Glowstone in your offhand keeps the anchor from going off until it is full, and there is none in the hotbar to fill it."
                              );
                           }

                           this.finish();
                           return;
                        }

                        AnchorActions.charge(spot, this.options(null, () -> {
                           if ((Boolean)this.debug.get()) {
                              this.info("Topped up the anchor at %s - glowstone in your offhand", new Object[]{format(spot)});
                           }
                        }));
                        return;
                     }

                     AnchorActions.detonate(spot, charges, this.options(this.explodeLimit, () -> {
                        if ((Boolean)this.debug.get()) {
                           this.info("Detonated at %s", new Object[]{format(spot)});
                        }

                        this.detonatedAt = spot;
                        this.detonatedAge = 0;
                        this.finish();
                     }), detonator);
               }
            }
         }
      }
   }

   private void waitUnsafe(String why) {
      if (!this.unsafeWarned && ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get())) {
         this.report("Holding the anchor at %s until it is safe: %s.", format(this.working), why);
      }

      this.unsafeWarned = true;
   }

   private int detonationPreference() {
      if (this.returnSlot == -1) {
         return -1;
      } else {
         int home = HotbarSwap.homeSlot();
         return home != this.returnSlot && home != this.cycleSlot ? -1 : this.returnSlot;
      }
   }

   private void finish() {
      if (this.working != null) {
         KeyPriority.release();
      }

      this.unsafeWarned = false;
      this.working = null;
      this.predicted = null;
      this.predictedAge = 0;
      this.shieldDone = false;
      this.sneakWarned = false;
      this.viewWarned = false;
      this.shieldAssumed = null;
      this.shieldPlaced = null;
      this.cycleTicks = 0;
      this.cameraTicks = 0;
      this.sightTicks = 0;
      this.slotTicks = 0;
      AnchorActions.resetTurn(this);
      if (this.returnSlot != -1 && !this.restorePending) {
         this.restorePending = true;
         this.restoreAt = this.ticks + AnchorActions.stepGap();
      }
   }

   private int cycleTimeout() {
      double cap = Stealth.turnCap();
      return cap <= 0.0 ? (Integer)this.timeout.get() : (Integer)this.timeout.get() + (int)Math.ceil(180.0 / cap) * 4;
   }

   private boolean waitingOnRate(AnchorMacro.AnchorState state) {
      if (this.ticks < this.nextClickTick) {
         return true;
      } else if (this.working != null && !this.sightings.settled(this.working)) {
         return true;
      } else {
         double cost = this.clickCost();
         if (!this.speedLimit.canAfford(cost)) {
            return true;
         } else {
            return switch (state) {
               case Air -> !this.placeLimit.canAfford(cost);
               case Anchor -> false;
               case Loaded -> !this.explodeLimit.canAfford(cost);
            };
         }
      }
   }

   private double clickCost() {
      if (Double.isNaN(this.nextCost)) {
         this.nextCost = Stealth.actionCost();
      }

      return this.nextCost;
   }

   private AnchorActions.Options options() {
      return this.options(null);
   }

   private AnchorActions.Options options(ActionBudget extra) {
      return this.options(extra, () -> {});
   }

   private boolean mustRotate() {
      return (Boolean)this.rotate.get() || Stealth.legitPlace();
   }

   private AnchorActions.Options options(ActionBudget extra, Runnable onSent) {
      boolean turn = this.mustRotate() && !TurnProgress.cameraNeeded();
      class_2338 expected = this.working;
      long version = this.activationVersion();
      BooleanSupplier ready = () -> this.isActive()
         && version == this.activationVersion()
         && Objects.equals(expected, this.working)
         && this.ticks >= this.nextClickTick
         && (expected == null || this.sightings.settled(expected))
         && (extra == null || extra.canAfford(this.clickCost()))
         && this.speedLimit.canAfford(this.clickCost());
      BooleanSupplier ahead = () -> this.isActive()
         && version == this.activationVersion()
         && Objects.equals(expected, this.working)
         && this.ticks + 1 >= this.nextClickTick
         && (expected == null || this.sightings.settledSoon(expected))
         && (extra == null || extra.canAfford(this.clickCost()))
         && this.speedLimit.canAfford(this.clickCost());
      BooleanSupplier gate = () -> {
         if (!ready.getAsBoolean()) {
            return false;
         } else {
            double cost = this.clickCost();
            if (!Stealth.claimUse()) {
               return false;
            } else if (extra != null && !extra.tryConsume(cost)) {
               return false;
            } else if (!this.speedLimit.tryConsume(cost)) {
               return false;
            } else {
               this.nextCost = Double.NaN;
               int gap = AnchorActions.stepGap();
               this.nextClickTick = this.ticks + gap;
               this.sightings.pace(gap);
               return true;
            }
         }
      };
      return new AnchorActions.Options(turn, (Boolean)this.swing.get(), this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar, 90, gate, onSent)
         .withOwner(this)
         .withReady(ready)
         .withAhead(ahead);
   }

   private AnchorMacro.ShieldStep placeShieldNow(class_2338 anchorPos) {
      float bare = BlastShield.anchorDamage(this.mc.field_1724, anchorPos.method_46558());
      if (bare <= (Double)this.shieldSkipBelow.get() && this.safetyProblem(anchorPos, null) == null) {
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
               this.report("No shield: %s.", this.lastShieldProblem);
            }

            return AnchorMacro.ShieldStep.Skipped;
         } else {
            class_3965 support = this.supportFor(spot, anchorPos);
            if (support == null) {
               if ((Boolean)this.debug.get() || (Boolean)this.chatInfo.get()) {
                  this.report(
                     this.mustRotate()
                        ? "No shield: no face you can see or reach to click against, other than the anchor."
                        : "No shield: your view does not land on a face that puts the block at %s - turn rotate on for the shield.",
                     format(spot)
                  );
               }

               return AnchorMacro.ShieldStep.Skipped;
            } else {
               class_1792 item = ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item();
               boolean inHotbar = InvUtils.find(stack -> stack.method_31574(item), 0, 8).found();
               boolean fromOffhand = this.mc.field_1724.method_6079().method_31574(item)
                  && !InventoryGuard.offhandInFlight()
                  && VanillaClick.reaches(support, class_1268.field_5810);
               if (!inHotbar && !fromOffhand) {
                  if ((Boolean)this.chatInfo.get()) {
                     this.report("No %s in the hotbar for the shield.", item.toString());
                  }

                  return AnchorMacro.ShieldStep.Skipped;
               } else {
                  if (this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar) {
                     this.rememberSlot();
                  }

                  AnchorActions.Options shieldOptions = this.options(
                     null,
                     () -> {
                        if ((Boolean)this.debug.get()) {
                           this.info(
                              "Shield at %s%s (blast %.1f, saves %.1f)",
                              new Object[]{format(spot), this.lastShieldDiagonal ? " diagonal" : "", bare, this.lastShieldGain}
                           );
                        }

                        this.shieldAssumed = spot;
                        this.shieldPlaced = spot;
                        this.shieldDone = true;
                        this.shieldAge = 0;
                     }
                  );
                  if (!AnchorActions.clickWith(support, item, shieldOptions)) {
                     return AnchorMacro.ShieldStep.Turning;
                  } else {
                     return shieldOptions.rotate() ? AnchorMacro.ShieldStep.Turning : AnchorMacro.ShieldStep.Placed;
                  }
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

   private double crosshairReach() {
      return this.reach();
   }

   private double reach() {
      return Math.min((Double)this.range.get(), VanillaLimits.blockRange());
   }

   private class_2338 findSpot() {
      this.spotWaitsOnBlast = false;
      this.spotWaitsOnSight = false;
      if (this.mode.get() == AnchorMacro.Mode.Crosshair) {
         class_3965 hit = AimUtils.lookingAtBlock(this.crosshairReach());
         if (hit == null) {
            return null;
         } else {
            class_2338 spot = this.spotFor(hit);
            if (spot == null) {
               return null;
            } else if (this.blastPending(spot)) {
               this.spotWaitsOnBlast = true;
               return null;
            } else if (AnchorActions.charges(spot) >= 0 && !this.sightings.ready(spot)) {
               this.spotWaitsOnSight = true;
               return null;
            } else if (AnchorActions.charges(spot) >= 0 || AnchorActions.canPlace(spot) && AnchorActions.placeHit(spot, this.mustRotate()) != null) {
               if (!Stealth.allowsBlock(spot, spot.method_46558())) {
                  return null;
               } else if (this.unsafeEvenShielded(spot) != null) {
                  return null;
               } else {
                  return this.inView(spot.method_46558()) ? spot : null;
               }
            } else {
               return null;
            }
         }
      } else {
         class_1657 target = TargetInfo.findPlayerTarget(this.reach() + 8.0, SortPriority.LowestHealth);
         if (target == null) {
            this.lastRejection = "no target in range";
            return null;
         } else {
            double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
            class_2338 origin = this.mc.field_1724.method_24515();
            class_243 eyes = this.mc.field_1724.method_33571();
            int radius = (int)Math.ceil(this.reach());
            class_2338 best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int outOfRange = 0;
            int notInView = 0;
            int outOfSight = 0;
            int stealthBlocked = 0;
            int cannotPlace = 0;
            int lethal = 0;
            int unsafe = 0;
            int tooWeak = 0;
            int badTrade = 0;
            String unsafeWhy = null;
            float bestRejectedDamage = 0.0F;
            float worstRatio = 0.0F;
            boolean skippedBlast = false;
            boolean skippedSight = false;

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= radius; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     class_2338 pos = origin.method_10069(x, y, z);
                     class_243 center = pos.method_46558();
                     if (eyes.method_1022(center) > this.reach()) {
                        outOfRange++;
                     } else if (!this.inView(center)) {
                        notInView++;
                     } else if (!Stealth.inView(center)) {
                        outOfSight++;
                     } else if (this.blastPending(pos)) {
                        skippedBlast = true;
                     } else {
                        boolean anchorThere = AnchorActions.charges(pos) >= 0;
                        if (!anchorThere && !AnchorActions.canPlace(pos)) {
                           cannotPlace++;
                        } else if (!Stealth.allowsBlock(pos, center)) {
                           stealthBlocked++;
                        } else {
                           float self = this.selfDamage(center);
                           if (self >= selfHealth) {
                              lethal++;
                           } else {
                              float damage = BlastShield.anchorDamage(target, center);
                              if (damage < (Double)this.minDamage.get()) {
                                 tooWeak++;
                                 bestRejectedDamage = Math.max(bestRejectedDamage, damage);
                              } else if (self > 0.0F && damage / self < (Double)this.minRatio.get()) {
                                 badTrade++;
                                 worstRatio = Math.max(worstRatio, damage / self);
                              } else if (!this.sightings.pickable(pos)) {
                                 skippedSight = true;
                              } else {
                                 double score = damage - self;
                                 if (!(score <= bestScore)) {
                                    String problem = this.safetyProblemFor(self);
                                    if (problem != null) {
                                       class_2338 rescue = this.shieldSpotFor(pos);
                                       if (rescue == null || this.safetyProblem(pos, rescue) != null) {
                                          unsafe++;
                                          unsafeWhy = problem;
                                          continue;
                                       }
                                    }

                                    class_3965 click = anchorThere
                                       ? AnchorActions.hitResultFor(pos, this.mustRotate())
                                       : AnchorActions.placeHit(pos, this.mustRotate());
                                    if (click == null) {
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
            }

            if (best == null) {
               this.spotWaitsOnBlast = skippedBlast;
               this.spotWaitsOnSight = skippedSight;
               this.lastRejection = skippedBlast
                  ? "the anchor just set off has not gone yet on your screen"
                  : (
                     skippedSight
                        ? "the spot has only just appeared or become worth it"
                        : this.describeSpotRejection(
                           outOfRange,
                           notInView,
                           outOfSight,
                           stealthBlocked,
                           cannotPlace,
                           lethal,
                           unsafe,
                           unsafeWhy,
                           tooWeak,
                           bestRejectedDamage,
                           badTrade,
                           worstRatio
                        )
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
      int outOfSight,
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
      if (unsafe > 0) {
         return String.format("%d spots refused by damage-limit: %s", unsafe, unsafeWhy);
      } else if (badTrade > 0) {
         return String.format("%d spots refused by min-damage-ratio %.1f - best trade was only %.1fx", badTrade, this.minRatio.get(), bestRatio);
      } else if (tooWeak > 0) {
         return String.format("%d spots in reach, but none reached min-damage %.1f (best was %.1f)", tooWeak, this.minDamage.get(), bestRejected);
      } else if (lethal > 0) {
         return String.format("%d spots would simply kill you", lethal);
      } else if (cannotPlace > 0) {
         return "nowhere an anchor could stand";
      } else if (stealthBlocked > 0) {
         return "blocked by Stealth's limits - out of reach or no line of sight";
      } else if (notInView > 0) {
         return "everything usable is outside your field of view";
      } else if (outOfSight > 0) {
         return "everything usable is out of sight - outside Stealth's view-angle, behind or beside you";
      } else {
         return outOfRange > 0 ? String.format("nothing within range %.1f", this.reach()) : "nothing in range at all";
      }
   }

   private class_3965 supportFor(class_2338 target, class_2338 anchorPos) {
      double reach = VanillaLimits.blockRange();
      boolean anchorPending = AnchorActions.charges(anchorPos) < 0;
      if (this.mustRotate()) {
         LegitPlace.Result result = LegitPlace.forBlock(target, reach, anchorPos, anchorPending);
         return result == null ? null : result.hit();
      } else {
         class_3965 hit = LegitPlace.confirmPlacement(
            target, ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item(), LegitPlace.currentYaw(), LegitPlace.currentPitch(), reach
         );
         return hit != null && anchorPending && LegitPlace.passesThrough(hit.method_17784(), anchorPos) ? null : hit;
      }
   }

   private class_2338 shieldSpotFor(class_2338 anchorPos) {
      this.lastShieldProblem = "no candidate position";
      if (!(Boolean)this.shield.get()) {
         return null;
      } else if (!this.shieldItemAvailable(anchorPos)) {
         // A spot that is only safe behind a shield must not be approved when there is nothing to build the shield
         // from - the cycle would place and charge the anchor and then have to leave it standing.
         this.lastShieldProblem = String.format("no %s left for the shield", ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item().toString());
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
            boolean hidesAnchor = false;
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
                  } else {
                     class_3965 support = this.supportFor(pos, anchorPos);
                     if (support == null) {
                        this.lastShieldProblem = "no face you can see or reach to click against there, other than the anchor";
                     } else if (!Stealth.inView(support.method_17784())) {
                        this.lastShieldProblem = "the only face to click for it is out of sight - outside Stealth's view-angle";
                     } else {
                        float gain = bare - BlastShield.anchorDamageBehindShield(this.mc.field_1724, anchor, pos, shieldState);
                        if (gain < (Double)this.minShieldGain.get()) {
                           bestRejectedGain = Math.max(bestRejectedGain, gain);
                        } else if (!(gain <= bestGain)) {
                           if (!LegitPlace.stillClickable(anchorPos, VanillaLimits.blockRange(), pos)) {
                              hidesAnchor = true;
                              this.lastShieldProblem = "a shield there would hide every face of the anchor you could still click to set it off";
                           } else {
                              best = pos;
                              bestGain = gain;
                              bestIndex = index;
                           }
                        }
                     }
                  }
               } else {
                  this.lastShieldProblem = "every spot between you and the anchor is occupied";
               }
            }

            if (best == null) {
               if (hidesAnchor) {
                  this.lastShieldProblem = "a shield there would hide every face of the anchor you could still click to set it off";
               } else if (bestRejectedGain >= 0.0F) {
                  this.lastShieldProblem = String.format("best spot only saves %.1f, min-shield-gain is %.1f", bestRejectedGain, this.minShieldGain.get());
               }
            }

            this.lastShieldGain = bestGain;
            this.lastShieldDiagonal = bestIndex >= 3;
            return best;
         }
      }
   }

   // Enough of the shield item in the hotbar or offhand. A glowstone shield on an anchor that still needs its charge
   // needs two: one for the charge, one for the shield.
   private boolean shieldItemAvailable(class_2338 anchorPos) {
      class_1792 item = ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item();
      int count = this.mc.field_1724.method_6079().method_31574(item) ? this.mc.field_1724.method_6079().method_7947() : 0;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(item)) {
            count += stack.method_7947();
         }
      }

      int needed = item == class_1802.field_8801 && AnchorActions.charges(anchorPos) <= 0 ? 2 : 1;
      return count >= needed;
   }

   private class_2338 effectiveShield() {
      return this.shieldStands() ? this.shieldPlaced : null;
   }

   private String unsafeEvenShielded(class_2338 pos) {
      String problem = this.safetyProblem(pos);
      if (problem != null && (Boolean)this.shield.get() && !this.shieldDone && this.effectiveShield() == null) {
         class_2338 rescue = this.shieldSpotFor(pos);
         return rescue != null && this.safetyProblem(pos, rescue) == null ? null : problem;
      } else {
         return problem;
      }
   }

   private boolean blastPending(class_2338 pos) {
      return this.detonatedAt != null && this.detonatedAt.equals(pos);
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
         class_3965 hit = AimUtils.lookingAtBlock(this.crosshairReach());
         if (hit == null) {
            return String.format("Your crosshair is not on a block within %.1f blocks.", this.crosshairReach());
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
            } else if (AnchorActions.charges(spot) < 0 && AnchorActions.placeHit(spot, this.mustRotate()) == null) {
               return String.format(
                  "No face to click that puts an anchor at %d %d %d from where you stand - something is in the way of the click.",
                  spot.method_10263(),
                  spot.method_10264(),
                  spot.method_10260()
               );
            } else if (this.blastPending(spot)) {
               return "The anchor you just set off has not gone yet on your screen - waiting for the server.";
            } else if (!Stealth.inView(spot.method_46558())) {
               return "The spot is out of sight - outside Stealth's view-angle.";
            } else if (!Stealth.allowsBlock(spot, spot.method_46558())) {
               return "Stealth blocks the spot - out of reach, or no line of sight to it.";
            } else {
               String unsafe = this.unsafeEvenShielded(spot);
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
      } else {
         return this.requireTotem.get() && !this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)
            ? "no totem in your offhand"
            : this.safetyProblemFor(this.selfDamageBehind(pos.method_46558(), shieldAt));
      }
   }

   private String safetyProblemFor(float selfDamage) {
      if (!(Boolean)this.damageLimit.get()) {
         return null;
      } else if ((Boolean)this.requireTotem.get() && !this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
         return "no totem in your offhand";
      } else if (selfDamage > (Double)this.maxSelfDamage.get()) {
         return String.format("it would deal %.1f to you, cap is %.1f", selfDamage, this.maxSelfDamage.get());
      } else {
         double left = EntityUtils.getTotalHealth(this.mc.field_1724) - selfDamage;
         return left < this.keepHealth.get() ? String.format("it would leave you on %.1f health", Math.max(0.0, left)) : null;
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

   private record Blast(class_2338 pos, int tick) {
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
