package dev.crystaladdon.modules;

import dev.crystaladdon.utils.KeyPriority;
import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.utils.ActionBudget;
import dev.crystaladdon.utils.AimUtils;
import dev.crystaladdon.utils.AnchorActions;
import dev.crystaladdon.utils.BlastShield;
import dev.crystaladdon.utils.ClickGate;
import dev.crystaladdon.utils.HotbarSwap;
import dev.crystaladdon.utils.InventoryGuard;
import dev.crystaladdon.utils.LegitPlace;
import dev.crystaladdon.utils.TurnProgress;
import dev.crystaladdon.utils.VanillaClick;
import dev.crystaladdon.utils.VanillaLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.class_241;
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
               .sliderRange(0.1, 20.0)
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
   private final Setting<Integer> shieldWait = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("shield-wait")
            .description(
               "Ticks between placing the shield block and detonating. 0 works it out from your ping so the server has confirmed the shield first, which is the safe choice; 1 detonates on the very next tick - fastest, but if the server refused the shield you take the blast unshielded."
            )
            .defaultValue(0)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("click-speed"))
               .description(
                  "Clicks per second across the whole cycle - placing, charging, shielding and detonating all draw from this. Up to 20 that is one click a tick at most; above 20 several steps go out in the same tick when they need no other look - placing the anchor and charging it, say - up to 3 a tick at 41 and more. Raise Stealth's max-actions-per-second along with it."
               ))
            .defaultValue(20.0)
            .range(1.0, 50.0)
            .sliderRange(1.0, 50.0)
            .build()
      );
   private final Setting<Double> placeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("place-speed"))
               .description(
                  "Anchor placements per second, on top of click-speed - the lower of the two applies. Kept apart from explode-speed because the two want different rates - placing is cheap, detonating is what hurts you."
               ))
            .defaultValue(20.0)
            .range(0.5, 50.0)
            .sliderRange(0.5, 50.0)
            .build()
      );
   private final Setting<Double> explodeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("explode-speed"))
               .description("Detonations per second, on top of click-speed - the lower of the two applies."))
            .defaultValue(20.0)
            .range(0.5, 50.0)
            .sliderRange(0.5, 50.0)
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
                     "Ticks a cycle may run before it is given up on. Ticks spent waiting for speed, place-speed or explode-speed, for a reaction time, for you to stop walking, for you to look back at a spot you turned away from, or for a hotbar slot to stand its tick before the click (Stealth's same-tick-switch off) do not count - the last three have a limit of the same length of their own. The state machine retries by itself when the server refuses a step - it simply sees the spot is still empty next tick - so this only catches a spot that can never work."
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
                     "Hotbar really moves your selection onto anchor and glowstone, the way you would yourself - the number key ahead of the click, while the head still turns or the pace runs out. Silent goes back to your slot once a few ticks have passed without a click. With Stealth's same-tick-switch off a slot has to stand a tick before it clicks, so a Silent click then comes a tick after the switch."
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
                     "Key that turns safe-anchor on and off. Unbound by default. Each press says in chat which way it went, and the module info shows \"unsafe\" while it is off."
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
   private static final String HIDES_ANCHOR = "would hide every face of the anchor you could still click";
   // Stands in for the min-shield-gain reason until the best gain turned down is known.
   private static final String MIN_GAIN = "min-shield-gain";
   private static final String NO_TOTEM = "no totem in your offhand";
   private float lastShieldGain;
   // Which of the candidates the last shield spot was, for the debug line.
   private String lastShieldNote;
   // The last search for a shield spot turned a candidate worth having down for something that can clear within a
   // tick or two - a crystal or a player in the spot or in the way of the click, the face to click just outside the
   // view-angle, your own walking into it - rather than for the lie of the land.
   private boolean lastShieldTransient;
   // Ticks spent waiting this cycle for such a refusal to clear, before the anchor is charged without the shield.
   private int shieldRetries;
   private static final int SHIELD_RETRY_TICKS = 3;
   // A "No shield" line has been reported this cycle; the shield is looked at again on later ticks, the chat only once.
   private boolean shieldReported;
   // Ticks of your own walking a shield spot is checked against: only the cells your body really enters in that time
   // count, not everything near the line you walk - a shield there is refused for this tick and looked for again. Three ticks with a full key push on top of the speed
   // reached a block and a half ahead and kept the shield off the very line you walk toward the anchor on.
   private static final int PATH_TICKS = 2;
   // The anchor itself stands from its placement until the detonation - placed, shielded, charged and set off, with at
   // least a tick between the clicks - so it has to stay clear of four ticks of your walking: a cell you reach before
   // then would stop you dead with a charged anchor in it, right in front of you.
   private static final int ANCHOR_PATH_TICKS = 4;
   // In BestDamage a spot that drifts into your walking path is given up for another; this many times a press at most,
   // so a press that keeps finding spots in your way while you run does not set an anchor off long after it was made.
   private static final int WALK_REPICKS = 3;
   private int walkRepicks;
   private int walkTicks;
   private boolean walkWarned;
   private boolean faceWarned;
   private class_2338 shieldAssumed;
   private boolean shieldDone;
   private int shieldSentAt;
   private boolean unsafeWarned;
   private String lastRejection;
   private class_2338 shieldPlaced;
   private boolean sneakWarned;
   private boolean viewWarned;
   private class_2338 detonatedAt;
   // The anchor of this cycle has been seen charged. Gone again before our detonation counted - another mod took the
   // click over and removed it on the client, or someone else set it off - the cycle is over: placing again would only
   // throw a second anchor at a spot that has just exploded.
   private boolean sawLoaded;
   // Tick our charge went out, -1 before it has. The client shows the anchor charged the moment the click leaves, even
   // on an anchor the server refused, so the charged state only counts as seen once the server had time to answer.
   private int chargeSentAt = -1;
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
      this.walkRepicks = 0;
      this.heldWarnings.clear();
   }

   @EventHandler
   private void onTick(Pre event) {
      this.tick();
      // The press claimed KeyPriority the moment it came in (onKey). One that started no cycle - no spot, the Nether
      // guard, a blast still showing - lets the auras back in within the same tick.
      if (this.working == null) {
         KeyPriority.release(this);
      }
   }

   private void tick() {
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

            // It was our own blast: once the anchor is gone there is nothing to react to, the next cycle may start a tick
            // later. Waiting the reaction time out on top cost about 200 ms between two anchors.
            if (this.blastShown != null && this.ticks > this.blastShown.tick()) {
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

               // A fresh press gets its own few re-picks; one queued by giving a spot up does not.
               if (justPressed) {
                  this.walkRepicks = 0;
               }

               boolean shieldKeyPressed = ((Keybind)this.shieldBind.get()).isSet() && ((Keybind)this.shieldBind.get()).isPressed();
               if (shieldKeyPressed && !this.shieldKeyWasPressed) {
                  this.shield.set(!(Boolean)this.shield.get());
                  // Always said, whatever chat-info is: a key brushed by accident would otherwise switch the shield off
                  // without a trace, and every anchor after it would go off bare.
                  this.info("Safe-anchor %s.", new Object[]{this.shield.get() ? "on" : "off"});
               }

               this.shieldKeyWasPressed = shieldKeyPressed;
               if ((Boolean)this.autoRefill.get() && (pressed || this.working != null)) {
                  this.refill.tick(class_1802.field_23141, class_1802.field_8801);
               }

               this.budget.update((Double)this.cyclesPerSecond.get(), 1);
               this.speedLimit.update((Double)this.speed.get(), ClickGate.perTick((Double)this.speed.get()));
               this.placeLimit.update((Double)this.placeSpeed.get(), ClickGate.perTick((Double)this.placeSpeed.get()));
               this.explodeLimit.update((Double)this.explodeSpeed.get(), ClickGate.perTick((Double)this.explodeSpeed.get()));
               if (this.working != null) {
                  KeyPriority.hold(this);
                  // A press during a running cycle starts the next one once this cycle is done, instead of being lost.
                  if (justPressed && this.trigger.get() == AnchorMacro.Trigger.Press) {
                     this.pressQueued = true;
                  }

                  this.runBurst();
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

   // One step a tick, or with click-speed above 20 as many as the tick may carry: each pass sees the world the last
   // click predicted (the anchor placed, the charge in), and stops as soon as a pass sends nothing - a step that needs
   // a different look or the server's answer waits for the next tick as before.
   private void runBurst() {
      int burst = ClickGate.perTick((Double)this.speed.get());
      ClickGate.allowBurst(burst);

      for (int pass = 0; pass < burst && this.working != null; pass++) {
         int sent = ClickGate.usesThisTick();
         this.runAt(this.working);
         if (ClickGate.usesThisTick() == sent) {
            break;
         }
      }
   }

   // The press claims KeyPriority as it comes in, not in the tick that starts the cycle: modules of equal priority
   // run in an order that changes between launches, and an aura running ahead of the macro in that tick would take
   // its slot change or its click. onTick lets go again in that same tick if no cycle starts.
   @EventHandler
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press && this.mc.field_1755 == null && ((Keybind)this.bind.get()).matches(event.input)) {
         this.pressLatched = true;
         KeyPriority.hold(this);
      }
   }

   @EventHandler
   private void onMouse(MouseClickEvent event) {
      if (event.action == KeyAction.Press && this.mc.field_1755 == null && ((Keybind)this.bind.get()).matches(event.input)) {
         this.pressLatched = true;
         KeyPriority.hold(this);
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
            // An anchor one block up - on obsidian, on a surround block - has nothing but air around the spots at its
            // own height, and a block cannot be placed against air: one lower, on the floor, still covers your legs and
            // body. Only where that lower spot is open (for an anchor on the floor it is the floor itself), or where
            // our own shield already stands, so the "already up" check still recognises it.
            if (this.mc.field_1687 != null) {
               for (class_2338 level : new class_2338[]{
                  beside, spot.method_10079(toPlayer, 2), beside.method_10093(toPlayer.method_10170()), beside.method_10093(toPlayer.method_10160())
               }) {
                  class_2338 lower = level.method_10074();
                  if (this.mc.field_1687.method_8320(lower).method_45474() || lower.equals(this.shieldPlaced)) {
                     candidates.add(lower);
                  }
               }
            }

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
      this.resetCycleNotes();
      this.working = spot;
      this.predicted = null;
      this.predictedAge = 0;
      this.cycleTicks = 0;
      this.cameraTicks = 0;
      this.sightTicks = 0;
      this.slotTicks = 0;
      AnchorActions.resetTurn(this);
      // Before the first step, not from the next tick on: an aura that runs after the macro in this tick would
      // otherwise still take the click or the slot change the step needs.
      KeyPriority.hold(this);
      this.runBurst();
   }

   private AnchorMacro.AnchorState stateAt(class_2338 pos) {
      AnchorMacro.AnchorState world = this.worldStateAt(pos);
      return this.predicted != null && world.ordinal() < this.predicted.ordinal() ? this.predicted : world;
   }

   private AnchorMacro.AnchorState worldStateAt(class_2338 pos) {
      int charges = AnchorActions.charges(pos);
      return charges < 0 ? AnchorMacro.AnchorState.Air : (charges > 0 ? AnchorMacro.AnchorState.Loaded : AnchorMacro.AnchorState.Anchor);
   }

   private int shieldWaitTicks() {
      int fixed = (Integer)this.shieldWait.get();
      return fixed > 0 ? fixed : Math.max(1, this.predictionWindow() - 3);
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

      AnchorMacro.AnchorState seen = this.worldStateAt(spot);
      if (seen == AnchorMacro.AnchorState.Loaded) {
         // Our own charge shows at once, server or not; only once it has stood long enough for the server's answer
         // does it say the anchor really was charged.
         if (this.chargeSentAt < 0 || this.ticks - this.chargeSentAt >= this.predictionWindow()) {
            this.sawLoaded = true;
         }
      } else if (seen == AnchorMacro.AnchorState.Air && this.sawLoaded) {
         if ((Boolean)this.debug.get()) {
            this.info("Anchor at %s is gone - cycle done", new Object[]{format(spot)});
         }

         this.blastShown = new AnchorMacro.Blast(spot, this.ticks);
         this.finish();
         return;
      } else if (seen == AnchorMacro.AnchorState.Air && this.chargeSentAt >= 0) {
         // Gone before the server could confirm the charge. Either the server refused the anchor - it then took the
         // charge as glowstone placed on the empty spot, which usually fills it - or someone set it off within those
         // few ticks, which leaves air just the same. The two cannot be told apart here, and placing again in the
         // second case would throw a second anchor at a spot that has just exploded, so the cycle ends either way.
         if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
            this.report(
               this.mc.field_1687 != null && !this.mc.field_1687.method_8320(spot).method_45474()
                  ? "The server refused the anchor at %s, and the charge put glowstone there instead."
                  : "The anchor at %s is gone before its charge was confirmed - refused by the server, or set off by someone else.",
               format(spot)
            );
         }

         this.finish();
         return;
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
         if (this.shieldDone && this.shieldAssumed != null && !this.shieldStands()) {
            // The server refused the shield, or a blast broke it. Forget it before the safety check, so the rescue
            // there and the Anchor or Loaded step put it up again - left as done, a shield the shot needs would hold
            // the anchor until the cycle times out, in either state.
            if ((Boolean)this.debug.get()) {
               this.info("Shield at %s is gone - placing it again", new Object[]{format(this.shieldAssumed)});
            }

            this.shieldDone = false;
            this.shieldAssumed = null;
            this.shieldPlaced = null;
         }

         String unsafe = this.unsafeEvenShielded(spot);
         if (unsafe != null && this.worldStateAt(spot) != AnchorMacro.AnchorState.Air) {
            // The anchor already stands: stopping now would leave it - charged, perhaps - right next to you. Wait for
            // the spot to become safe again (you step back, the shield goes up); the cycle timeout still ends it.
            this.waitUnsafe(unsafe);
         } else if (unsafe != null) {
            if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
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
                        if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                           this.report("No anchor in the hotbar.");
                        }

                        this.finish();
                        return;
                     }

                     // Before the anchor goes down, not after: an anchor placed without the glowstone to charge it -
                     // or, with glowstone in the offhand, to fill it - would only be left standing next to you.
                     int glowstoneNeeded = this.glowstoneNeeded(spot);
                     if (this.hotbarCount(class_1802.field_8801) < glowstoneNeeded) {
                        if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                           this.report(
                              this.mc.field_1724.method_6079().method_31574(class_1802.field_8801)
                                 ? "Need %d glowstone in the hotbar - glowstone in your offhand keeps the anchor from going off until it is full."
                                 : "Need %d glowstone in the hotbar to charge the anchor.",
                              glowstoneNeeded
                           );
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
                        if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
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

                     if (this.inYourWay(spot, ANCHOR_PATH_TICKS)) {
                        this.waitWalking(spot);
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
                        if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
                           this.report(
                              "No glowstone in the hotbar - glowstone in the offhand cannot charge without the main hand setting a charged anchor off."
                           );
                        }

                        this.finish();
                        return;
                     }

                     // Shield before the charge: both use glowstone, so no extra switch, and the shield's confirmation
                     // runs out while the charge and the switch to the detonator happen instead of after them. If the
                     // shield cannot go up, no charged anchor is left standing either.
                     if (this.shieldPending(spot)) {
                        AnchorMacro.ShieldStep step = this.placeShieldNow(spot, true);
                        if (step == AnchorMacro.ShieldStep.Turning || step == AnchorMacro.ShieldStep.Retrying) {
                           return;
                        }

                        if (step == AnchorMacro.ShieldStep.Placed) {
                           this.shieldDone = true;
                           return;
                        }

                        if (step == AnchorMacro.ShieldStep.Skipped) {
                           this.shieldDone = true;
                        } else {
                           // No shield to be had right now. Charge only if the bare blast is within damage-limit;
                           // otherwise keep the anchor uncharged, which is harmless, until the shot is safe or the
                           // cycle times out. shieldDone stays false: the Loaded step looks once more, right before
                           // the detonation.
                           String bare = this.safetyProblem(spot, null);
                           if (bare != null) {
                              this.waitUnsafe(bare);
                              return;
                           }
                        }
                     }

                     if (this.anchorUnclickable(spot)) {
                        return;
                     }

                     AnchorActions.charge(spot, this.options(null, () -> {
                        this.predicted = AnchorMacro.AnchorState.Loaded;
                        this.chargeSentAt = this.ticks;
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

                     if (this.shieldPending(spot)) {
                        // No waiting for a refusal to clear here: the anchor is charged, and one standing next to you
                        // is anyone's to set off. Whatever comes of it, the safety check below has the last word.
                        AnchorMacro.ShieldStep step = this.placeShieldNow(spot, false);
                        if (step == AnchorMacro.ShieldStep.Turning || step == AnchorMacro.ShieldStep.Retrying) {
                           return;
                        }

                        this.shieldDone = true;
                        if (step == AnchorMacro.ShieldStep.Placed) {
                           return;
                        }
                     }

                     // Detonate only once the shield had time to be confirmed; the client shows it the moment the click
                     // goes out, whether the server accepts it or not. A shield that is gone again was already dropped
                     // above, before the safety check, and is being placed anew.
                     if (this.shieldDone && this.shieldAssumed != null) {
                        int waited = this.ticks - this.shieldSentAt;
                        if (waited < this.shieldWaitTicks()) {
                           // On the last tick of the wait take up the detonator, so the click goes out the moment the
                           // wait ends instead of a tick later; until then the glowstone stays in hand in case the
                           // shield has to go up again.
                           if (waited + 1 >= this.shieldWaitTicks()) {
                              AnchorActions.preselectDetonator(spot, charges, this.options(this.explodeLimit), detonator);
                           }

                           return;
                        }
                     }

                     String blast = this.safetyProblem(spot);
                     if (blast != null) {
                        this.waitUnsafe(blast);
                        return;
                     }

                     if (this.anchorUnclickable(spot)) {
                        return;
                     }

                     if (AnchorActions.offhandBlocksDetonation(charges)) {
                        if (!AnchorActions.findGlowstone().found()) {
                           if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
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

   // What one cycle notes about itself, cleared when a cycle starts and when it ends.
   private void resetCycleNotes() {
      this.shieldRetries = 0;
      this.shieldReported = false;
      this.walkTicks = 0;
      this.walkWarned = false;
      this.faceWarned = false;
      this.chargeSentAt = -1;
   }

   // Whether this pass still has to see to the shield: not dealt with yet this cycle, or skipped while the shot was safe
   // without it and unsafe since - your health dropped, you stepped closer - with no shield of ours standing.
   private boolean shieldPending(class_2338 spot) {
      if (!(Boolean)this.shield.get()) {
         return false;
      } else if (!this.shieldDone) {
         return true;
      } else {
         return this.shieldAssumed == null && this.effectiveShield() == null && this.safetyProblem(spot) != null;
      }
   }

   // The anchor spot is where you are about to walk, and an anchor there would stop you dead. In BestDamage another spot
   // may well be out of your way: the cycle ends and the press picks again next tick - WALK_REPICKS times at most, since
   // every new pick restarts the cycle and its timeout. In Crosshair the spot is the one you aim at, so the step waits
   // and says why - outside the cycle-timeout, as its description says, but with a limit of the same length of its own,
   // since the cycle keeps the auras paused for as long as it runs.
   private void waitWalking(class_2338 spot) {
      if (this.countedTick) {
         this.cycleTicks--;
         this.countedTick = false;
      }

      if (this.mode.get() == AnchorMacro.Mode.BestDamage) {
         if (this.trigger.get() == AnchorMacro.Trigger.Press && ++this.walkRepicks > WALK_REPICKS) {
            if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
               this.report("Gave up: every anchor spot picked for this press moved into where you are walking - stop or step aside and press again.");
            }
         } else {
            if ((Boolean)this.debug.get()) {
               this.info("Anchor spot %s is where you are walking - picking another", new Object[]{format(spot)});
            }

            this.pressQueued = this.trigger.get() == AnchorMacro.Trigger.Press;
         }

         this.finish();
      } else if (++this.walkTicks > this.cycleTimeout()) {
         if ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get()) {
            this.report("Gave up on the spot at %s: it stayed where you are walking - an anchor there would stop you dead.", format(spot));
         }

         this.finish();
      } else {
         if (!this.walkWarned && ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get())) {
            this.report("Waiting: the anchor spot %s is where you are walking - stop or step aside and it goes down.", format(spot));
         }

         this.walkWarned = true;
      }
   }

   // No face of the anchor can be clicked: you have stepped out of block reach of it, or a player or a crystal is in the
   // way of each ray, or the shield once you have moved. The click would simply not go out; say so once instead of
   // running into the cycle timeout in silence. Without rotate, or while the camera is needed, the click goes along your
   // own view and other lines cover it.
   private boolean anchorUnclickable(class_2338 spot) {
      if (!this.mustRotate() || TurnProgress.cameraNeeded() || AnchorActions.hitResultFor(spot, true) != null) {
         return false;
      } else {
         if (!this.faceWarned && ((Boolean)this.chatInfo.get() || (Boolean)this.debug.get())) {
            // The face search drops every face beyond block reach, so out of reach reads just like covered - but the
            // cure is the opposite: step back toward the anchor rather than wait.
            double reach = VanillaLimits.blockRange();
            double distance = Math.sqrt(new class_238(spot).method_49271(this.mc.field_1724.method_33571()));
            if (distance > reach) {
               this.report("The anchor at %s is out of reach (%.1f, reach is %.1f) - step back toward it.", format(spot), distance, reach);
            } else {
               this.report(
                  "No face of the anchor at %s can be clicked - a player, a crystal or a block%s covers every face you could reach; waiting for it to clear.",
                  format(spot),
                  this.shieldPlaced != null ? " (the shield, now that you have moved)" : ""
               );
            }
         }

         this.faceWarned = true;
         return true;
      }
   }

   // Glowstone the anchor at pos still needs from the hotbar before it can go off: its charge, or with glowstone in the
   // offhand a full fill. That glowstone keeps the anchor from going off until it is full and cannot do the filling
   // itself - the main hand takes a click on the anchor first.
   private int glowstoneNeeded(class_2338 pos) {
      int charges = Math.max(0, AnchorActions.charges(pos));
      if (this.mc.field_1724.method_6079().method_31574(class_1802.field_8801)) {
         return Math.max(0, AnchorActions.MAX_CHARGES - charges);
      } else {
         return charges == 0 ? 1 : 0;
      }
   }

   private int hotbarCount(class_1792 item) {
      int count = 0;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(item)) {
            count += stack.method_7947();
         }
      }

      return count;
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
         KeyPriority.release(this);
      }

      this.unsafeWarned = false;
      this.sawLoaded = false;
      this.working = null;
      this.predicted = null;
      this.predictedAge = 0;
      this.shieldDone = false;
      this.sneakWarned = false;
      this.viewWarned = false;
      this.shieldAssumed = null;
      this.shieldPlaced = null;
      this.resetCycleNotes();
      this.cycleTicks = 0;
      this.cameraTicks = 0;
      this.sightTicks = 0;
      this.slotTicks = 0;
      AnchorActions.resetTurn(this);
      // The cycle's last look is of no further use: hand the head back now instead of after Meteor's rotation hold,
      // so a Crosshair Auto Crystal sees along your camera again right away.
      TurnProgress.SHARED.releaseHold(this);
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
               // While this tick may still carry another of the macro's clicks, the next step may follow at once.
               int gap = ClickGate.usesThisTick() + 1 < ClickGate.burst() ? 0 : AnchorActions.stepGap();
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

   // Skipped: deliberately left out (the blast is small, or a shield already stands). Unavailable: none could be put up
   // this time. mayWait: a refusal that can clear within a tick or two is waited out first, up to SHIELD_RETRY_TICKS a
   // cycle - only while the anchor is still uncharged, where waiting harms nothing.
   private AnchorMacro.ShieldStep placeShieldNow(class_2338 anchorPos, boolean mayWait) {
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
            return this.shieldRefused(this.lastShieldProblem, this.lastShieldTransient, mayWait);
         } else {
            class_3965 support = this.supportFor(spot, anchorPos);
            if (support == null) {
               // shieldSpotFor found this very face a moment ago: only something stepping into the click loses it.
               return this.shieldRefused(
                  this.mustRotate()
                     ? "the face to click for it was just lost - something is in the way of the click"
                     : String.format("your view does not land on a face that puts the block at %s - turn rotate on for the shield", format(spot)),
                  this.mustRotate(),
                  mayWait
               );
            } else {
               class_1792 item = ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item();
               boolean inHotbar = InvUtils.find(stack -> stack.method_31574(item), 0, 8).found();
               boolean fromOffhand = this.mc.field_1724.method_6079().method_31574(item)
                  && !InventoryGuard.offhandInFlight()
                  && VanillaClick.reaches(support, class_1268.field_5810);
               if (!inHotbar && !fromOffhand) {
                  return this.shieldRefused(String.format("no %s in the hotbar for it", item.toString()), false, mayWait);
               } else {
                  if (this.switchMode.get() == AnchorMacro.SwitchMode.Hotbar) {
                     this.rememberSlot();
                  }

                  // Taken now: the fields belong to whichever search for a shield spot ran last.
                  String note = this.lastShieldNote;
                  float gain = this.lastShieldGain;
                  AnchorActions.Options shieldOptions = this.options(
                     null,
                     () -> {
                        if ((Boolean)this.debug.get()) {
                           this.info("Shield at %s - %s (blast %.1f, saves %.1f)", new Object[]{format(spot), note, bare, gain});
                        }

                        this.shieldAssumed = spot;
                        this.shieldPlaced = spot;
                        this.shieldDone = true;
                        this.shieldSentAt = this.ticks;
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

   // No shield spot this pass. One refused only for a moment - a crystal or player in the spot or in the way of the
   // click, the face just outside the view-angle, you walking into it - is tried again on the next ticks; anything
   // else, or once those ticks are used up, the shield is not to be had this time. Said once a cycle, however often it
   // is looked at again.
   private AnchorMacro.ShieldStep shieldRefused(String why, boolean transientCause, boolean mayWait) {
      if (transientCause && mayWait && this.shieldRetries < SHIELD_RETRY_TICKS) {
         if (this.shieldRetries++ == 0 && (Boolean)this.debug.get()) {
            this.info("Shield refused for now (%s) - trying again", new Object[]{why});
         }

         return AnchorMacro.ShieldStep.Retrying;
      } else {
         if (!this.shieldReported && ((Boolean)this.debug.get() || (Boolean)this.chatInfo.get())) {
            this.report("No shield: %s.", why);
         }

         this.shieldReported = true;
         return AnchorMacro.ShieldStep.Unavailable;
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

   // The space your body moves through over the next ticks. A block that is merely clear of your hitbox now still lands
   // right in your way when you walk or strafe: a tick later you run into it and get stuck on it. Each tick is stepped
   // the way vanilla's travel() moves you - the speed you carry plus the push of your movement keys, then friction - and
   // only your hitbox at each of those ticks counts, not one box stretched over the whole sweep, which on a diagonal
   // covers far more than you pass through. The keys count because velocity lags a tick behind them: the first tick of
   // a strafe starts from standing still. Their push is vanilla's own (about 0.13 a tick sprinting on the ground), not a
   // guess on top of the speed you already have.
   private boolean inYourWay(class_2338 pos, int ticks) {
      class_238 box = this.mc.field_1724.method_5829();
      class_243 velocity = this.mc.field_1724.method_18798();
      double vx = velocity.field_1352;
      double vz = velocity.field_1350;
      boolean onGround = this.mc.field_1724.method_24828();
      float slipperiness = onGround ? this.mc.field_1687.method_8320(this.mc.field_1724.method_23314()).method_26204().method_9499() : 1.0F;
      double pushX = 0.0;
      double pushZ = 0.0;
      class_241 input = this.mc.field_1724.field_3913.method_3128();
      if (input.field_1343 != 0.0F || input.field_1342 != 0.0F) {
         // Vanilla's movementInputToVelocity: x is sideways, y forward, turned by the yaw you walk with, never longer
         // than one. The speed is getMovementSpeed() scaled by the ground's grip, or the fixed air control off the
         // ground, and the client takes 0.98 of the input.
         double yaw = Math.toRadians(this.mc.field_1724.method_36454());
         double sin = Math.sin(yaw);
         double cos = Math.cos(yaw);
         double ix = input.field_1343 * cos - input.field_1342 * sin;
         double iz = input.field_1342 * cos + input.field_1343 * sin;
         double length = Math.sqrt(ix * ix + iz * iz);
         if (length > 1.0E-4) {
            double speed = onGround
               ? this.mc.field_1724.method_6029() * (0.21600002F / (slipperiness * slipperiness * slipperiness))
               : (this.mc.field_1724.method_5624() ? 0.026 : 0.02);
            double push = speed * 0.98 / Math.max(1.0, length);
            pushX = ix * push;
            pushZ = iz * push;
         }
      }

      if (vx * vx + vz * vz < 1.0E-4 && pushX == 0.0 && pushZ == 0.0) {
         return false;
      } else {
         double friction = slipperiness * 0.91;
         class_238 cell = new class_238(pos);
         double x = 0.0;
         double z = 0.0;

         for (int tick = 0; tick < ticks; tick++) {
            vx += pushX;
            vz += pushZ;
            x += vx;
            z += vz;
            if (cell.method_994(box.method_989(x, 0.0, z))) {
               return true;
            }

            vx *= friction;
            vz *= friction;
         }

         return false;
      }
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
            } else if (AnchorActions.charges(spot) >= 0
               || AnchorActions.canPlace(spot) && !this.inYourWay(spot, ANCHOR_PATH_TICKS) && AnchorActions.placeHit(spot, this.mustRotate()) != null) {
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
            int inPath = 0;
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
                        } else if (!anchorThere && this.inYourWay(pos, ANCHOR_PATH_TICKS)) {
                           inPath++;
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
                           inPath,
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
      int inPath,
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
      } else if (inPath > 0) {
         return String.format("%d spots are where you are walking - an anchor there would stop you dead", inPath);
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
      this.lastShieldTransient = false;
      if (!(Boolean)this.shield.get()) {
         return null;
      } else if (!this.shieldItemAvailable(anchorPos)) {
         // A spot that is only safe behind a shield must not be approved when there is nothing to build the shield
         // from - the cycle would place and charge the anchor and then have to leave it standing.
         this.lastShieldProblem = String.format(
            "not enough %s in the hotbar - the cycle needs %d", ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item().toString(), this.shieldItemNeeded(anchorPos)
         );
         return null;
      } else {
         List<class_2338> candidates = this.shieldPositions(anchorPos);
         if (candidates.isEmpty()) {
            this.lastShieldProblem = "anchor is straight above or below you, no side to shield";
            return null;
         } else {
            class_243 anchor = anchorPos.method_46558();
            float bare = BlastShield.anchorDamage(this.mc.field_1724, anchor);
            class_2248 block = ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).block();
            class_2680 shieldState = block.method_9564();
            class_2338 best = null;
            float bestGain = 0.0F;
            String bestNote = null;
            // A glowstone spot your body only reaches on the tick after next is still a shield: the blast breaks it
            // before you get there, and the anchor sits one block further on that same line anyway. It is only passed
            // over for a spot out of your way - unless that one alone would not make the shot safe and the spot in your
            // path would. A spot you step into next tick, or obsidian anywhere on your path, is never used: the first
            // stops you dead the moment it goes up, the second outlasts the blast and stays in your way for good.
            float bestRejectedGain = -1.0F;
            boolean transientRefusal = false;
            // Why each candidate was turned down, by reason, in the order the candidates are tried - the one beside the
            // anchor first - so the report names what kept the spots that matter, not whichever was looked at last.
            Map<String, Set<String>> refused = new LinkedHashMap<>();

            for (class_2338 pos : candidates) {
               String label = shieldLabel(anchorPos, pos);
               if (pos.equals(anchorPos)) {
                  refuse(refused, "it is the anchor position itself", label);
               } else if (!this.mc.field_1687.method_22347(pos)
                  && (
                     !this.mc.field_1687.method_8320(pos).method_45474()
                        || !AnchorActions.canReplaceAt(pos, ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item())
                  )) {
                  refuse(refused, "occupied", label);
               } else {
                  // A refusal for the lie of the land, or one that can clear within a tick or two - something in the
                  // spot or in the way of the click, the face just outside the view-angle, your own walking.
                  String lasting = null;
                  String passing = null;
                  class_3965 support = null;
                  if (!BlockUtils.canPlaceBlock(pos, true, block)) {
                     if (new class_238(pos).method_994(this.mc.field_1724.method_5829())) {
                        lasting = "overlaps your own hitbox - step to the middle of your block";
                     } else {
                        passing = "a crystal or player is standing in it";
                     }
                  } else if ((support = this.supportFor(pos, anchorPos)) == null) {
                     if (this.entityBlocksSupport(pos, anchorPos)) {
                        passing = "a crystal or player is in the way of the click";
                     } else {
                        lasting = "no face you can see or reach to click against, other than the anchor";
                     }
                  } else if (!Stealth.inView(support.method_17784())) {
                     passing = "the face to click is out of sight - outside Stealth's view-angle";
                  }

                  if (lasting != null) {
                     refuse(refused, lasting, label);
                  } else {
                     // The gain before a passing refusal counts as one: a spot that would not save enough anyway must
                     // not hold the charge back for a shield that could never come of it.
                     float gain = bare - BlastShield.anchorDamageBehindShield(this.mc.field_1724, anchor, pos, shieldState);
                     if (gain < (Double)this.minShieldGain.get()) {
                        bestRejectedGain = Math.max(bestRejectedGain, gain);
                        refuse(refused, MIN_GAIN, label);
                     } else if (passing != null) {
                        refuse(refused, passing, label);
                        transientRefusal = true;
                     } else {
                        boolean inPath = this.inYourWay(pos, PATH_TICKS);
                        if (inPath && this.inYourWay(pos, 1)) {
                           refuse(refused, "where you step next tick - it would stop you dead", label);
                           transientRefusal = true;
                        } else if (inPath) {
                           // Even a glowstone shield stands until the server's blast update comes back, a round trip
                           // after the detonation - you would walk into it first.
                           refuse(refused, "where you are walking - you would run into it before the blast clears it", label);
                           transientRefusal = true;
                        } else if (!(gain <= bestGain)) {
                           if (!LegitPlace.stillClickable(anchorPos, VanillaLimits.blockRange(), pos)) {
                              refuse(refused, HIDES_ANCHOR, label);
                           } else {
                              best = pos;
                              bestGain = gain;
                              bestNote = label;
                           }
                        }
                     }
                  }
               }
            }

            if (best == null) {
               this.lastShieldProblem = this.describeShieldRefusals(refused, bestRejectedGain);
               this.lastShieldTransient = transientRefusal;
            }

            this.lastShieldGain = bestGain;
            this.lastShieldNote = bestNote;
            return best;
         }
      }
   }

   private static void refuse(Map<String, Set<String>> refused, String reason, String label) {
      refused.computeIfAbsent(reason, key -> new LinkedHashSet<>()).add(label);
   }

   private String describeShieldRefusals(Map<String, Set<String>> refused, float bestRejectedGain) {
      List<String> parts = new ArrayList<>();

      for (Map.Entry<String, Set<String>> entry : refused.entrySet()) {
         String reason = MIN_GAIN.equals(entry.getKey())
            ? String.format("saves only %.1f, min-shield-gain is %.1f", bestRejectedGain, this.minShieldGain.get())
            : entry.getKey();
         parts.add(reason + " (" + String.join(", ", entry.getValue()) + ")");
      }

      return parts.isEmpty() ? "no candidate position" : String.join("; ", parts);
   }

   // Where a shield candidate sits, the way the report names it: beside the anchor on your side, two out from it, or
   // diagonal, with +1 or -1 for a block above or below the anchor's own height.
   private static String shieldLabel(class_2338 anchorPos, class_2338 pos) {
      int dx = Math.abs(pos.method_10263() - anchorPos.method_10263());
      int dz = Math.abs(pos.method_10260() - anchorPos.method_10260());
      int dy = pos.method_10264() - anchorPos.method_10264();
      String where = dx == 1 && dz == 1 ? "diagonal" : (dx + dz >= 2 ? "two out" : "beside");
      return dy == 0 ? where : where + (dy > 0 ? " +" : " ") + dy;
   }

   // No face to click for the shield at pos - but would there be one without the crystals and players in the way?
   // Asked only to name the reason and to know it may clear; the click itself never passes through an entity. Only
   // when something other than you is near the line from your eyes to the spot, so open ground costs no second search.
   private boolean entityBlocksSupport(class_2338 pos, class_2338 anchorPos) {
      class_238 between = this.mc.field_1724.method_5829().method_991(new class_238(pos)).method_1014(1.0);
      return !this.mc.field_1687.method_8335(this.mc.field_1724, between).isEmpty()
         && LegitPlace.passingThrough(entity -> true, () -> this.supportFor(pos, anchorPos)) != null;
   }

   // The shield item the cycle still needs: one for the shield and, for a glowstone shield, the glowstone the anchor
   // itself still needs.
   private int shieldItemNeeded(class_2338 anchorPos) {
      return ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item() == class_1802.field_8801 ? 1 + this.glowstoneNeeded(anchorPos) : 1;
   }

   // The offhand only gets the shield click while the main hand holds nothing that would take it (a sword, a totem, an
   // empty hand). With Hotbar switching the main hand holds the anchor or the glowstone all cycle, so only the hotbar
   // counts; with Silent switching it keeps your own item, and obsidian in the offhand is used if that item lets it.
   // Glowstone in the offhand never counts: it keeps the anchor from going off until it is full, and the hotbar has to
   // bring all of that.
   private boolean shieldItemAvailable(class_2338 anchorPos) {
      class_1792 item = ((AnchorMacro.ShieldBlock)this.shieldBlock.get()).item();
      int count = this.hotbarCount(item);
      class_1799 offhand = this.mc.field_1724.method_6079();
      if (item != class_1802.field_8801
         && offhand.method_31574(item)
         && this.switchMode.get() == AnchorMacro.SwitchMode.Silent
         && VanillaClick.reaches(null, class_1268.field_5810)) {
         count += offhand.method_7947();
      }

      return count >= this.shieldItemNeeded(anchorPos);
   }

   private class_2338 effectiveShield() {
      return this.shieldStands() ? this.shieldPlaced : null;
   }

   // Whether the shot breaks damage-limit even with the help a shield could still give. The rescue is looked for while
   // no shield click of ours has gone out this cycle and none stands - also after the shield was skipped as needless or
   // could not be had, since the shot may have turned unsafe since (your health dropped, you stepped closer). The answer
   // says why no shield helps, so a refusal does not just read "it would deal X" with the cause left out.
   private String unsafeEvenShielded(class_2338 pos) {
      String problem = this.safetyProblem(pos);
      if (problem == null || !(Boolean)this.shield.get() || NO_TOTEM.equals(problem)) {
         return problem;
      } else if (this.effectiveShield() != null) {
         return problem + ", even behind the shield";
      } else if (this.shieldAssumed != null) {
         return problem;
      } else {
         class_2338 rescue = this.shieldSpotFor(pos);
         if (rescue == null) {
            return problem + " - and no shield: " + this.lastShieldProblem;
         } else {
            String shielded = this.safetyProblem(pos, rescue);
            return shielded == null ? null : shielded + ", even behind a shield";
         }
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
            } else if (AnchorActions.charges(spot) < 0 && this.inYourWay(spot, ANCHOR_PATH_TICKS)) {
               return String.format(
                  "The spot %d %d %d is where you are walking - an anchor there would stop you dead. Stop, or aim a little further.",
                  spot.method_10263(),
                  spot.method_10264(),
                  spot.method_10260()
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
            ? NO_TOTEM
            : this.safetyProblemFor(this.selfDamageBehind(pos.method_46558(), shieldAt));
      }
   }

   private String safetyProblemFor(float selfDamage) {
      if (!(Boolean)this.damageLimit.get()) {
         return null;
      } else if ((Boolean)this.requireTotem.get() && !this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
         return NO_TOTEM;
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
      // Deliberately left out: the blast is small enough, or a shield already stands.
      Skipped,
      Turning,
      // Refused for a moment only; looked at again next tick.
      Retrying,
      // None to be had this time.
      Unavailable;
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
