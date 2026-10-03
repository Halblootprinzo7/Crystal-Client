package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.AimUtils;
import com.messerocks.crystal.utils.CrystalUtils;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.RevivedPlayers;
import com.messerocks.crystal.utils.ServerVersion;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaLimits;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1934;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_2879;
import net.minecraft.class_3489;
import net.minecraft.class_3965;
import net.minecraft.class_642;
import net.minecraft.class_6862;

public class AutoCrystal extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPlace = this.settings.createGroup("Place");
   private final SettingGroup sgBreak = this.settings.createGroup("Break");
   private final SettingGroup sgAim = this.settings.createGroup("Aim");
   private final SettingGroup sgHeight = this.settings.createGroup("Height");
   private final SettingGroup sgFacePlace = this.settings.createGroup("Face Place");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("target-range")).description("Only players this close are considered targets."))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("priority"))
                  .description("How to pick a target when several are in range."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<AutoCrystal.Placement> placement = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("placement-rules"))
                  .description("Auto reads it from the version ViaFabricPlus is translating to, so older servers work without touching this."))
               .defaultValue(AutoCrystal.Placement.Auto))
            .build()
      );
   private final Setting<AutoCrystal.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description(
                     "Hotbar really moves your selection onto the crystal. Silent swaps back within the tick, which suits an aura that places continuously."
                  ))
               .defaultValue(AutoCrystal.SwitchMode.Silent))
            .build()
      );
   private final Setting<Boolean> returnToWeapon = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("return-to-sword"))
                     .description(
                        "Go back to your sword once the aura has nothing left to do. Only Hotbar switch-mode needs this - Silent already swaps back inside the tick."
                     ))
                  .defaultValue(true))
               .visible(() -> this.switchMode.get() == AutoCrystal.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<AutoCrystal.Weapon> returnWeapon = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("return-weapon"))
                     .description(
                        "Which weapon to go back to. Sword by default: scoring purely by damage picks a netherite axe over a netherite sword, which is not what you want back in hand after an exchange."
                     ))
                  .defaultValue(AutoCrystal.Weapon.Sword))
               .visible(() -> this.switchMode.get() == AutoCrystal.SwitchMode.Hotbar && (Boolean)this.returnToWeapon.get()))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Face the crystal before placing or breaking it. Ignored while aim mode is Crosshair."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Render the hand swing client side. Off still sends the swing packet."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> pauseOnUse = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-on-use"))
                  .description("Stop while eating or drinking so the item does not get cancelled."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> pauseOnMine = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-on-mine"))
                  .description("Stop while breaking a block."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug"))
                  .description(
                     "When a target is in range but nothing gets placed, print once a second why: how many obsidian/bedrock bases were found and how each was rejected. This is how we tell 'no anticheat problem, there is just no obsidian' apart from 'the legit-place filter is too strict'."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> place = this.sgPlace
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("place"))
                  .description("Place crystals."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> placeSpeed = this.sgPlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("place-speed"))
                  .description("Placements per second. 0 is unlimited. Above 20 places more than once per tick."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(60.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Integer> maxPlacesPerTick = this.sgPlace
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("max-places-per-tick"))
                     .description("Ceiling per tick, so a pause cannot bank placements into one burst."))
                  .defaultValue(1))
               .min(1)
               .sliderMax(8)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Double> placeRange = this.sgPlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("place-range")).description("Maximum distance to place a crystal at."))
               .defaultValue(4.5)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Double> placeWallRange = this.sgPlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("place-wall-range")).description("Place range when there is a block in the way."))
               .defaultValue(4.0)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Double> minDamage = this.sgPlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("min-damage")).description("Do not place unless the crystal deals at least this much to the target."))
               .defaultValue(6.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Double> maxSelfDamage = this.sgPlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("max-self-damage")).description("Do not place a crystal that would deal more than this to you."))
               .defaultValue(7.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Boolean> doBreak = this.sgBreak
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("break"))
                  .description("Break crystals."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> breakSpeed = this.sgBreak
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("break-speed"))
                  .description("Hits per second. 0 is unlimited. Above 20 hits more than once per tick."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(60.0)
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Integer> maxBreaksPerTick = this.sgBreak
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("max-breaks-per-tick"))
                     .description("Ceiling per tick."))
                  .defaultValue(1))
               .min(1)
               .sliderMax(8)
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Boolean> stateLog = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("log-state"))
                  .description(
                     "Write one line to latest.log whenever the aura changes what it is doing or why it is idle: no target, target ignored because of its game mode, turn held by another module, no usable spot and why. The log file only, never the chat."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> vanillaReach = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("vanilla-reach"))
                  .description(
                     "Never act beyond the distance vanilla allows: 4.5 for a block being placed on, 3.0 for hitting a crystal. Off lets the range sliders go further, which is a single packet a server can prove is impossible - keep it on anywhere that checks."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> breakRange = this.sgBreak
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("break-range"))
                  .description(
                     "Maximum distance to hit a crystal at. A crystal is an entity, so vanilla stops at getEntityInteractionRange - 3.0 - not at the 4.5 that applies to blocks. Anything above that is a hit no legitimate client can send."
                  ))
               .defaultValue(3.0)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Double> breakWallRange = this.sgBreak
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("break-wall-range")).description("Break range when there is a block in the way."))
               .defaultValue(4.0)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Double> minBreakDamage = this.sgBreak
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("min-break-damage")).description("Only hit crystals that deal at least this much to the target."))
               .defaultValue(6.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Double> maxBreakSelfDamage = this.sgBreak
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("max-break-self-damage")).description("Do not hit a crystal that would deal more than this to you."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Boolean> smartDelay = this.sgBreak
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("smart-delay"))
                     .description("Skip hitting while the target still has hurt resistance, so no damage is wasted."))
                  .defaultValue(true))
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<AutoCrystal.AimMode> aimMode = this.sgAim
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("aim-mode"))
                  .description("Fov only places where the spot is on your screen. Crosshair narrows that to the one block you point at."))
               .defaultValue(AutoCrystal.AimMode.Off))
            .build()
      );
   private final Setting<Double> maxAngle = this.sgAim
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("max-angle")).description("Half angle of the cone around your view direction."))
               .defaultValue(45.0)
               .min(1.0)
               .sliderRange(5.0, 180.0)
               .visible(() -> this.aimMode.get() == AutoCrystal.AimMode.Angle))
            .build()
      );
   private final Setting<Double> fovMargin = this.sgAim
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("fov-margin"))
                  .description("Degrees shaved off each screen edge, so spots right at the border do not count."))
               .defaultValue(5.0)
               .min(0.0)
               .sliderMax(30.0)
               .visible(() -> this.aimMode.get() == AutoCrystal.AimMode.Fov))
            .build()
      );
   private final Setting<Boolean> aimAppliesToBreak = this.sgAim
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("also-limit-breaking"))
                     .description("Apply the same restriction to breaking, not just placing."))
                  .defaultValue(false))
               .visible(() -> this.aimMode.get() != AutoCrystal.AimMode.Off))
            .build()
      );
   private final Setting<Boolean> heightFilter = this.sgHeight
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("require-higher-target"))
                  .description("Only act while the target is above you. Idles otherwise, the module stays on."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Double> minHeight = this.sgHeight
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("min-height-difference")).description("How far above you the target has to be, in blocks."))
               .defaultValue(0.5)
               .min(0.0)
               .sliderMax(10.0)
               .visible(this.heightFilter::get))
            .build()
      );
   private final Setting<Double> maxHeight = this.sgHeight
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("max-height-difference"))
                  .description("Upper bound, so someone far above you does not count. 0 disables it."))
               .defaultValue(0.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.heightFilter::get))
            .build()
      );
   private final Setting<Boolean> facePlace = this.sgFacePlace
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("face-place"))
                  .description("Ignore the damage requirement once the target is low."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> facePlaceHealth = this.sgFacePlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("face-place-health"))
                  .description("Target health (plus absorption) below which face placing kicks in."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(36.0)
               .visible(this.facePlace::get))
            .build()
      );
   private final Setting<Double> facePlaceMinDamage = this.sgFacePlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("face-place-min-damage")).description("Damage requirement while face placing."))
               .defaultValue(1.5)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.facePlace::get))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Draw the position the next crystal goes to."))
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
                  .description("Side colour of the placement box."))
               .defaultValue(new SettingColor(160, 60, 255, 40))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("line-color"))
                  .description("Line colour of the placement box."))
               .defaultValue(new SettingColor(160, 60, 255, 180))
               .visible(this.render::get))
            .build()
      );
   private class_1657 target;
   private class_2338 renderPos;
   private int returnSlot = -1;
   private static final Object TURN_OWNER = new Object();
   private final TurnProgress turn = TurnProgress.SHARED;
   private boolean workAvailable;
   private double renderDamage;
   private long lastDebugLog;
   private String idleReason;
   private boolean turnBusy;
   private String placeSkip;
   private String loggedState;
   private String candidateState;
   private int candidateTicks;
   private final ActionBudget placeBudget = new ActionBudget();
   private final ActionBudget breakBudget = new ActionBudget();
   private final Set<class_2338> usedPositions = new HashSet<>();
   private final Set<Integer> hitCrystals = new HashSet<>();
   private final Set<class_2338> brokenBases = new HashSet<>();

   public AutoCrystal() {
      super(CrystalAddon.CATEGORY, "auto-crystal", "Places and breaks end crystals on the best target.");
   }

   public void onDeactivate() {
      this.restoreSlot();
      this.turn.reset(TURN_OWNER);
      this.loggedState = null;
      this.candidateState = null;
      this.workAvailable = false;
      this.target = null;
      this.renderPos = null;
      this.renderDamage = 0.0;
      this.placeBudget.reset();
      this.breakBudget.reset();
      this.usedPositions.clear();
      this.hitCrystals.clear();
      this.brokenBases.clear();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         this.placeBudget.update((Double)this.placeSpeed.get(), (Integer)this.maxPlacesPerTick.get());
         this.breakBudget.update((Double)this.breakSpeed.get(), (Integer)this.maxBreaksPerTick.get());
         this.renderPos = null;
         this.renderDamage = 0.0;
         this.usedPositions.clear();
         this.hitCrystals.clear();
         this.brokenBases.clear();
         this.workAvailable = false;
         this.turnBusy = false;
         this.idleReason = null;
         this.placeSkip = null;
         if ((Boolean)this.pauseOnUse.get() && this.mc.field_1724.method_6115()) {
            this.logState("paused", () -> "using an item (pause-on-use)");
         } else if ((Boolean)this.pauseOnMine.get() && this.mc.field_1761.method_2923()) {
            this.logState("paused", () -> "mining (pause-on-mine)");
         } else {
            this.target = this.findTarget();
            if (this.target == null) {
               this.target = null;
               this.workAvailable = false;
               this.idleReason = this.ignoredPlayerReason();
               this.turn.reset(TURN_OWNER);
               String why = this.idleReason != null ? this.idleReason : "nobody in range";
               this.logState("no-target", () -> why);
               if ((Boolean)this.returnToWeapon.get() && this.switchMode.get() == AutoCrystal.SwitchMode.Hotbar) {
                  this.returnToSword();
               } else {
                  this.restoreSlot();
               }
            } else if (!this.heightOk()) {
               this.turn.reset(TURN_OWNER);
               this.returnToSword();
               String name = this.target.method_5477().getString();
               this.logState("height", () -> name + " is outside the height filter");
            } else {
               if ((Boolean)this.doBreak.get()) {
                  this.tryBreak();
               }

               if ((Boolean)this.place.get()) {
                  this.tryPlace();
               }

               if (!this.workAvailable) {
                  this.returnToSword();
               }

               String name = this.target.method_5477().getString();
               if (this.turnBusy) {
                  this.logState("turn-busy", () -> name + ": another module holds the shared rotation");
               } else if (this.workAvailable) {
                  this.logState("working", () -> name);
               } else if (this.placeSkip != null) {
                  this.logState("no-spot:" + this.placeSkip, () -> name + ": " + this.placeSkip);
               } else if ((Boolean)this.place.get()) {
                  this.logState("no-spot", () -> name + ": " + this.placementReport());
               } else {
                  this.logState("no-spot", () -> name + ": place is off and nothing to break");
               }

               if ((Boolean)this.debug.get() && (Boolean)this.place.get() && this.renderPos == null) {
                  this.logWhyNoPlacement();
               }
            }
         }
      }
   }

   private void logWhyNoPlacement() {
      long now = System.currentTimeMillis();
      if (now - this.lastDebugLog >= 1000L) {
         this.lastDebugLog = now;
         this.info(this.placementReport(), new Object[0]);
      }
   }

   private String placementReport() {
      boolean legacy = switch ((AutoCrystal.Placement)this.placement.get()) {
         case Auto -> ServerVersion.needsLegacyCrystalPlacement();
         case Modern -> false;
         case Legacy -> true;
      };
      double required = this.requiredDamage((Double)this.minDamage.get());
      double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
      class_2338 origin = this.mc.field_1724.method_24515();
      int radius = (int)Math.ceil(this.placeReach());
      int bases = 0;
      int reachLos = 0;
      int aimBlocked = 0;
      int cantPlace = 0;
      int unbreakable = 0;
      int legitFail = 0;
      int lowDamage = 0;
      int tooMuchSelf = 0;
      int ok = 0;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               class_2338 base = origin.method_10069(x, y, z);
               if (CrystalUtils.isBase(base)) {
                  bases++;
                  class_243 crystal = CrystalUtils.crystalPos(base);
                  if (!CrystalUtils.inRange(crystal, this.placeReach(), Math.min((Double)this.placeWallRange.get(), this.placeReach()))
                     || !Stealth.allowsBlock(base, crystal)) {
                     reachLos++;
                  } else if (!this.aimAllows(crystal)) {
                     aimBlocked++;
                  } else if (!CrystalUtils.canPlace(base, legacy, false)) {
                     cantPlace++;
                  } else if ((Boolean)this.doBreak.get() && !this.canHit(CrystalUtils.crystalBox(base.method_10084()))) {
                     unbreakable++;
                  } else if (Stealth.legitPlace() && LegitPlace.forCrystal(base, VanillaLimits.blockRange()) == null) {
                     legitFail++;
                  } else {
                     float selfDamage = DamageUtils.crystalDamage(this.mc.field_1724, crystal);
                     if (selfDamage > (Double)this.maxSelfDamage.get() || selfDamage >= selfHealth) {
                        tooMuchSelf++;
                     } else if (DamageUtils.crystalDamage(this.target, crystal) < required) {
                        lowDamage++;
                     } else {
                        ok++;
                     }
                  }
               }
            }
         }
      }

      return String.format(
         "Basen %d | verworfen: Reichweite/Sicht %d, Winkel %d, canPlace %d, nicht schlagbar %d, legit %d, Schaden<min %d, Selbstschaden %d | brauchbar %d | Regeln %s",
         bases,
         reachLos,
         aimBlocked,
         cantPlace,
         unbreakable,
         legitFail,
         lowDamage,
         tooMuchSelf,
         ok,
         legacy ? "legacy" : "modern"
      );
   }

   private void logState(String key, Supplier<String> detail) {
      if ((Boolean)this.stateLog.get()) {
         class_642 server = this.mc.method_1558();
         String where = server == null ? "singleplayer" : server.field_3761;
         String full = key + "@" + where;
         if (!full.equals(this.candidateState)) {
            this.candidateState = full;
            this.candidateTicks = 0;
         } else if (++this.candidateTicks == 10 && !full.equals(this.loggedState)) {
            this.loggedState = full;
            CrystalAddon.LOG.info("[AutoCrystal] {} on {} ({}): {}", new Object[]{key, where, ServerVersion.describe(), detail.get()});
         }
      }
   }

   private void returnToSword() {
      if ((Boolean)this.returnToWeapon.get() && this.switchMode.get() == AutoCrystal.SwitchMode.Hotbar) {
         if (this.mc.field_1724 != null) {
            if (this.returnSlot != -1) {
               int weapon = this.bestWeaponSlot();
               if (weapon == -1) {
                  this.restoreSlot();
               } else {
                  if (weapon != this.mc.field_1724.method_31548().method_67532()) {
                     InvUtils.swap(weapon, false);
                  }

                  this.returnSlot = -1;
               }
            }
         }
      }
   }

   private int bestWeaponSlot() {
      if (this.returnWeapon.get() == AutoCrystal.Weapon.Sword) {
         int sword = this.firstMatching(class_3489.field_42611);
         if (sword != -1) {
            return sword;
         }
      } else if (this.returnWeapon.get() == AutoCrystal.Weapon.Axe) {
         int axe = this.firstMatching(class_3489.field_42612);
         if (axe != -1) {
            return axe;
         }
      }

      int best = -1;
      float bestDamage = 0.0F;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (!stack.method_7960()) {
            if (this.target == null) {
               if (stack.method_31573(class_3489.field_42611) || stack.method_31573(class_3489.field_42612)) {
                  return i;
               }
            } else {
               float damage = DamageUtils.getAttackDamage(this.mc.field_1724, this.target, stack);
               if (damage > bestDamage) {
                  bestDamage = damage;
                  best = i;
               }
            }
         }
      }

      return best;
   }

   private int firstMatching(class_6862<class_1792> tag) {
      for (int i = 0; i <= 8; i++) {
         if (this.mc.field_1724.method_31548().method_5438(i).method_31573(tag)) {
            return i;
         }
      }

      return -1;
   }

   private void restoreSlot() {
      if (this.returnSlot != -1) {
         if (this.mc.field_1724 == null) {
            this.returnSlot = -1;
         } else {
            if (this.switchMode.get() == AutoCrystal.SwitchMode.Hotbar && this.returnSlot != this.mc.field_1724.method_31548().method_67532()) {
               InvUtils.swap(this.returnSlot, false);
            }

            this.returnSlot = -1;
         }
      }
   }

   private boolean heightOk() {
      if ((Boolean)this.heightFilter.get() && this.target != null) {
         double difference = this.target.method_23318() - this.mc.field_1724.method_23318();
         return difference < this.minHeight.get() ? false : (Double)this.maxHeight.get() <= 0.0 || difference <= (Double)this.maxHeight.get();
      } else {
         return true;
      }
   }

   private boolean facePlacing() {
      return (Boolean)this.facePlace.get()
         && this.target != null
         && this.target.method_6032() > 0.0F
         && EntityUtils.getTotalHealth(this.target) <= (Double)this.facePlaceHealth.get();
   }

   private PopWindow popWindow() {
      PopWindow module = (PopWindow)Modules.get().get(PopWindow.class);
      return module != null && module.isOpenFor(this.target) ? module : null;
   }

   private double requiredDamage(double normal) {
      PopWindow pop = this.popWindow();
      if (pop != null) {
         return pop.crystalMinDamage();
      } else {
         return this.facePlacing() ? (Double)this.facePlaceMinDamage.get() : normal;
      }
   }

   private class_2338 lookingAt() {
      return AimUtils.lookingAt(this.placeReach());
   }

   private boolean aimAllows(class_243 pos) {
      return switch ((AutoCrystal.AimMode)this.aimMode.get()) {
         case Angle -> AimUtils.withinCone(pos, (Double)this.maxAngle.get());
         case Fov -> AimUtils.inFieldOfView(pos, (Double)this.fovMargin.get());
         default -> true;
      };
   }

   private double placeReach() {
      double wanted = (Double)this.placeRange.get();
      return this.vanillaReach.get() ? Math.min(wanted, VanillaLimits.blockRange()) : wanted;
   }

   private double breakReach() {
      double wanted = (Double)this.breakRange.get();
      return this.vanillaReach.get() ? Math.min(wanted, VanillaLimits.entityRange()) : wanted;
   }

   private boolean shouldRotate() {
      return ((Boolean)this.rotate.get() || Stealth.legitPlace()) && this.aimMode.get() != AutoCrystal.AimMode.Crosshair;
   }

   private boolean canHit(class_238 box) {
      class_243 eyes = this.mc.field_1724.method_33571();
      double reach = this.breakReach();
      double distanceSq = box.method_49271(eyes);
      if (distanceSq > reach * reach) {
         return false;
      } else {
         double wall = Math.min((Double)this.breakWallRange.get(), reach);
         return distanceSq <= wall * wall || CrystalUtils.canSee(box.method_1005());
      }
   }

   private void tryBreak() {
      boolean limitAim = (Boolean)this.aimAppliesToBreak.get() && this.aimMode.get() != AutoCrystal.AimMode.Off;
      double required = this.requiredDamage((Double)this.minBreakDamage.get());
      double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);

      while (true) {
         class_1511 best = null;
         double bestDamage = 0.0;

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal && !crystal.method_31481() && !this.hitCrystals.contains(crystal.method_5628())) {
               class_243 pos = crystal.method_73189();
               if (this.canHit(crystal.method_5829())
                  && Stealth.allowsEntity(crystal)
                  && (!limitAim || (this.aimMode.get() == AutoCrystal.AimMode.Crosshair ? this.looksAtCrystal(crystal) : this.aimAllows(pos)))) {
                  float selfDamage = DamageUtils.crystalDamage(this.mc.field_1724, pos);
                  if (!(selfDamage > (Double)this.maxBreakSelfDamage.get()) && !(selfDamage >= selfHealth)) {
                     float damage = DamageUtils.crystalDamage(this.target, pos);
                     if (!(damage < required) && damage > bestDamage) {
                        bestDamage = damage;
                        best = crystal;
                     }
                  }
               }
            }
         }

         if (best == null) {
            return;
         }

         this.workAvailable = true;
         if ((Boolean)this.smartDelay.get() && this.target.field_6235 > 0) {
            return;
         }

         if (!this.readyToAct(best.method_5829().method_1005())) {
            return;
         }

         if (!this.spend(this.breakBudget)) {
            return;
         }

         if (bestDamage > this.renderDamage) {
            this.renderDamage = bestDamage;
         }

         this.hitCrystals.add(best.method_5628());
         if (this.attack(best)) {
            this.brokenBases.add(class_2338.method_49638(best.method_73189()).method_10074());
         }
      }
   }

   private String ignoredPlayerReason() {
      double rangeSq = (Double)this.targetRange.get() * (Double)this.targetRange.get();

      for (class_1657 player : this.mc.field_1687.method_18456()) {
         if (player != this.mc.field_1724
            && !player.method_31481()
            && Friends.get().shouldAttack(player)
            && !(this.mc.field_1724.method_5858(player) > rangeSq)) {
            if (player.method_6032() <= 0.0F && !RevivedPlayers.isRevived(player)) {
               return player.method_5477().getString() + " ignored: dead to the client (health 0), not seen moving since";
            }

            class_1934 mode = EntityUtils.getGameMode(player);
            if (mode != class_1934.field_9215) {
               return player.method_5477().getString() + " ignored: " + (mode == null ? "no tab entry" : mode.name().toLowerCase());
            }
         }
      }

      return null;
   }

   private class_1657 findTarget() {
      class_1657 found = TargetUtils.getPlayerTarget((Double)this.targetRange.get(), (SortPriority)this.priority.get());
      if (!TargetUtils.isBadTarget(found, (Double)this.targetRange.get())) {
         return found;
      } else {
         return TargetUtils.get(
               entity -> entity instanceof class_1657 playerx
                  && playerx != this.mc.field_1724
                  && RevivedPlayers.isRevived(playerx)
                  && Friends.get().shouldAttack(playerx)
                  && EntityUtils.getGameMode(playerx) == class_1934.field_9215
                  && PlayerUtils.isWithin(playerx, (Double)this.targetRange.get()),
               (SortPriority)this.priority.get()
            ) instanceof class_1657 player
            ? player
            : null;
      }
   }

   private boolean readyToAct(class_243 aim) {
      if (!this.shouldRotate()) {
         return true;
      } else {
         double yaw = Rotations.getYaw(aim);
         double pitch = Rotations.getPitch(aim);
         if (this.turn.wouldReach(TURN_OWNER, yaw, pitch)) {
            return true;
         } else {
            if (this.turn.heldByOther(TURN_OWNER)) {
               this.turnBusy = true;
            }

            this.turn.turnTo(TURN_OWNER, yaw, pitch, 50, null);
            return false;
         }
      }
   }

   private boolean spend(ActionBudget budget) {
      double cost = Stealth.actionCost();
      if (!budget.canAfford(cost)) {
         return false;
      } else {
         return !Stealth.claimAction() ? false : budget.tryConsume(cost);
      }
   }

   private boolean looksAtCrystal(class_1511 crystal) {
      class_2338 looking = this.lookingAt();
      if (looking == null) {
         return false;
      } else {
         class_2338 base = class_2338.method_49638(crystal.method_73189()).method_10074();
         return looking.equals(base) || looking.equals(base.method_10084());
      }
   }

   private void tryPlace() {
      FindItemResult crystals = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8301});
      if (!crystals.found()) {
         this.placeSkip = "no end crystals in the hotbar";
      } else if (this.switchMode.get() == AutoCrystal.SwitchMode.None && crystals.getHand() == null) {
         this.placeSkip = "crystals are not in a hand and switch-mode is None";
      } else {
         boolean legacy = switch ((AutoCrystal.Placement)this.placement.get()) {
            case Auto -> ServerVersion.needsLegacyCrystalPlacement();
            case Modern -> false;
            case Legacy -> true;
         };
         double required = this.requiredDamage((Double)this.minDamage.get());
         double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);

         while (true) {
            class_2338 best = this.findPlacement(legacy, required, selfHealth);
            if (best == null) {
               return;
            }

            this.workAvailable = true;
            class_243 aim = this.placeAim(best);
            if (aim == null) {
               return;
            }

            if (!this.readyToAct(aim)) {
               return;
            }

            if (!this.spend(this.placeBudget)) {
               return;
            }

            this.usedPositions.add(best);
            this.placeAt(best, aim);
         }
      }
   }

   private class_243 placeAim(class_2338 base) {
      if (!Stealth.legitPlace()) {
         return class_243.method_26410(base, 1.0);
      } else {
         LegitPlace.Result result = LegitPlace.forCrystal(base, VanillaLimits.blockRange());
         return result == null ? null : result.hitVec();
      }
   }

   private class_2338 findPlacement(boolean legacy, double required, double selfHealth) {
      if (this.aimMode.get() == AutoCrystal.AimMode.Crosshair) {
         class_2338 looking = this.lookingAt();
         if (looking != null && !this.usedPositions.contains(looking)) {
            return this.acceptable(looking, legacy, required, selfHealth) ? looking : null;
         } else {
            return null;
         }
      } else {
         class_2338 origin = this.mc.field_1724.method_24515();
         int radius = (int)Math.ceil(this.placeReach());
         class_2338 best = null;
         double bestDamage = 0.0;

         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  class_2338 base = origin.method_10069(x, y, z);
                  if (!this.blockedByPending(base)) {
                     double damage = this.placementDamage(base, legacy, required, selfHealth);
                     if (damage > bestDamage) {
                        bestDamage = damage;
                        best = base;
                     }
                  }
               }
            }
         }

         if (best != null && bestDamage > this.renderDamage) {
            this.renderPos = best;
            this.renderDamage = bestDamage;
         }

         return best;
      }
   }

   private boolean blockedByPending(class_2338 base) {
      for (class_2338 used : this.usedPositions) {
         if (Math.abs(used.method_10263() - base.method_10263()) <= 1
            && Math.abs(used.method_10264() - base.method_10264()) <= 1
            && Math.abs(used.method_10260() - base.method_10260()) <= 1) {
            return true;
         }
      }

      return false;
   }

   private boolean acceptable(class_2338 base, boolean legacy, double required, double selfHealth) {
      double damage = this.placementDamage(base, legacy, required, selfHealth);
      if (damage > 0.0) {
         this.renderPos = base;
         if (damage > this.renderDamage) {
            this.renderDamage = damage;
         }

         return true;
      } else {
         return false;
      }
   }

   private double placementDamage(class_2338 base, boolean legacy, double required, double selfHealth) {
      class_243 crystal = CrystalUtils.crystalPos(base);
      if (!CrystalUtils.inRange(crystal, this.placeReach(), Math.min((Double)this.placeWallRange.get(), this.placeReach()))) {
         return 0.0;
      } else if (!Stealth.allowsBlock(base, crystal)) {
         return 0.0;
      } else if (!this.aimAllows(crystal)) {
         return 0.0;
      } else if (!CrystalUtils.canPlace(base, legacy, this.brokenBases.contains(base))) {
         return 0.0;
      } else if ((Boolean)this.doBreak.get() && !this.canHit(CrystalUtils.crystalBox(base.method_10084()))) {
         return 0.0;
      } else if (Stealth.legitPlace() && LegitPlace.forCrystal(base, VanillaLimits.blockRange()) == null) {
         return 0.0;
      } else {
         float selfDamage = DamageUtils.crystalDamage(this.mc.field_1724, crystal);
         if (!(selfDamage > (Double)this.maxSelfDamage.get()) && !(selfDamage >= selfHealth)) {
            float damage = DamageUtils.crystalDamage(this.target, crystal);
            return damage < required ? 0.0 : damage;
         } else {
            return 0.0;
         }
      }
   }

   private boolean attack(class_1511 crystal) {
      Runnable action = () -> {
         this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(crystal, this.mc.field_1724.method_5715()));
         if ((Boolean)this.swing.get()) {
            this.mc.field_1724.method_6104(class_1268.field_5808);
         } else {
            this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
         }
      };
      return this.turnAndRun(crystal.method_5829().method_1005(), action);
   }

   private void placeAt(class_2338 base, class_243 aimPos) {
      boolean legit = Stealth.legitPlace();
      boolean rotating = this.shouldRotate();
      double yaw = rotating ? Rotations.getYaw(aimPos) : this.mc.field_1724.method_36454();
      double pitch = rotating ? Rotations.getPitch(aimPos) : this.mc.field_1724.method_36455();
      Runnable action = () -> {
         class_3965 hitResult;
         if (legit) {
            hitResult = LegitPlace.confirmCrystal(base, yaw, pitch, VanillaLimits.blockRange());
            if (hitResult == null) {
               return;
            }
         } else {
            hitResult = new class_3965(class_243.method_26410(base, 1.0), class_2350.field_11036, base, false);
         }

         FindItemResult crystals = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8301});
         if (crystals.found()) {
            class_1268 hand = crystals.getHand();
            boolean swapped = false;
            if (hand == null) {
               if (this.switchMode.get() == AutoCrystal.SwitchMode.None) {
                  return;
               }

               boolean silent = this.switchMode.get() == AutoCrystal.SwitchMode.Silent;
               if (!silent && this.returnSlot == -1) {
                  this.returnSlot = this.mc.field_1724.method_31548().method_67532();
               }

               InvUtils.swap(crystals.slot(), silent);
               swapped = silent;
               hand = class_1268.field_5808;
            }

            BlockUtils.interact(hitResult, hand, (Boolean)this.swing.get());
            if (swapped) {
               InvUtils.swapBack();
            }
         }
      };
      this.turnAndRun(aimPos, action);
   }

   private boolean turnAndRun(class_243 pos, Runnable action) {
      if (!this.shouldRotate()) {
         action.run();
         return true;
      } else {
         return this.turn.turnTo(TURN_OWNER, Rotations.getYaw(pos), Rotations.getPitch(pos), 50, action);
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.renderPos != null) {
         event.renderer
            .box(
               this.renderPos.method_10263(),
               this.renderPos.method_10264() + 1.0,
               this.renderPos.method_10260(),
               this.renderPos.method_10263() + 1.0,
               this.renderPos.method_10264() + 2.0,
               this.renderPos.method_10260() + 1.0,
               (Color)this.sideColor.get(),
               (Color)this.lineColor.get(),
               (ShapeMode)this.shapeMode.get(),
               0
            );
      }
   }

   public class_1657 getTarget() {
      return this.isActive() ? this.target : null;
   }

   public double getBestDamage() {
      return this.renderDamage;
   }

   public String getInfoString() {
      if (this.target == null) {
         return this.idleReason;
      } else if (!this.heightOk()) {
         return this.target.method_5477().getString() + " (too low)";
      } else if (this.turnBusy) {
         return this.target.method_5477().getString() + " (turn busy)";
      } else {
         return this.popWindow() != null
            ? this.target.method_5477().getString() + " pop"
            : String.format("%s %.1f", this.target.method_5477().getString(), this.renderDamage);
      }
   }

   public static enum AimMode {
      Off,
      Angle,
      Fov,
      Crosshair;
   }

   public static enum Placement {
      Auto,
      Modern,
      Legacy;
   }

   public static enum SwitchMode {
      None,
      Hotbar,
      Silent;
   }

   public static enum Weapon {
      Sword,
      Axe,
      MostDamage;
   }
}
