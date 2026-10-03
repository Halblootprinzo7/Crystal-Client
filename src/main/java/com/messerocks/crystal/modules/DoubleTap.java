package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.CrystalUtils;
import com.messerocks.crystal.utils.DamageWindow;
import com.messerocks.crystal.utils.KnockbackPredictor;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.ServerVersion;
import com.messerocks.crystal.utils.Trajectory;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaLimits;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
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
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_2879;
import net.minecraft.class_3965;

public class DoubleTap extends CrystalModule {
   private static final int MAX_RETRIES = 2;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgHit = this.settings.createGroup("Hit");
   private final SettingGroup sgObsidian = this.settings.createGroup("Obsidian");
   private final SettingGroup sgCrystal = this.settings.createGroup("Crystal");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Keybind> bind = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("combo-bind")).description("Key that runs the combo.")).defaultValue(Keybind.fromKey(71))).build());
   private final Setting<DoubleTap.Execution> execution = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("execution"))
                  .description("Instant fires the whole combo in one tick. Verified confirms each step first."))
               .defaultValue(DoubleTap.Execution.Verified))
            .build()
      );
   private final Setting<Integer> timeout = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("confirm-timeout"))
                  .description("Ticks to wait for a step. Instant still waits this long for the crystal entity to show up before it can be set off."))
               .defaultValue(10))
            .min(1)
            .sliderMax(40)
            .build()
      );
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("target-range"))
               .description("How far away a player can be to become the combo target."))
            .defaultValue(6.0)
            .min(0.0)
            .sliderMax(12.0)
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
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Face the target and the ground while the combo runs. This is the looking down part."))
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
   private final Setting<Integer> actionsPerTick = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("actions-per-tick"))
                     .description(
                        "How many combo steps may go out in one tick. 1 is one step per tick; raising it collapses hit, obsidian and crystal into the same tick."
                     ))
                  .defaultValue(1))
               .min(1)
               .sliderMax(4)
               .visible(() -> this.execution.get() == DoubleTap.Execution.Verified))
            .build()
      );
   private final Setting<DoubleTap.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description(
                     "Hotbar really moves your selection onto each item, the way you would yourself. Silent swaps and swaps back within the tick, so nothing visibly moves."
                  ))
               .defaultValue(DoubleTap.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> restoreSlot = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("restore-slot"))
                     .description("Go back to the slot you started on once the combo is done."))
                  .defaultValue(true))
               .visible(() -> this.switchMode.get() == DoubleTap.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> debugKnockback = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug-knockback"))
                  .description("Report the computed knockback strength and predicted landing spot when the combo starts."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Report when the combo aborts and why."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> doHit = this.sgHit
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hit"))
                  .description("Open with a melee hit on the target."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> hitRange = this.sgHit
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("hit-range"))
                  .description("Reach for the melee hit, measured to the target's hitbox like vanilla. 3 is the vanilla attack range."))
               .defaultValue(3.0)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.doHit::get))
            .build()
      );
   private final Setting<Boolean> swapWeapon = this.sgHit
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("swap-to-weapon"))
                     .description("Swap to whatever in your hotbar hits this target hardest."))
                  .defaultValue(true))
               .visible(this.doHit::get))
            .build()
      );
   private final Setting<Boolean> requireHit = this.sgHit
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("require-hit"))
                     .description("Abort the combo when the target is out of melee reach, instead of going straight to the crystal."))
                  .defaultValue(false))
               .visible(this.doHit::get))
            .build()
      );
   private final Setting<Boolean> doObsidian = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("place-obsidian"))
                  .description("Put obsidian down for the crystal. Off uses obsidian that is already there."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> placeRange = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("place-range"))
               .description("How far away the obsidian may go."))
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Integer> leadTicks = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("lead-ticks"))
                  .description(
                     "How far along the target's knockback path to aim the obsidian. A knockback sword makes that path predictable, which is the whole point of opening with one."
                  ))
               .defaultValue(4))
            .min(0)
            .sliderMax(12)
            .build()
      );
   private final Setting<DoubleTap.Placement> placement = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("placement-rules"))
                  .description(
                     "Crystals needed two free blocks above the base before 1.13 and one from then on. Auto reads it from the version ViaFabricPlus is translating to, which is what makes this work on older servers."
                  ))
               .defaultValue(DoubleTap.Placement.Auto))
            .build()
      );
   private final Setting<Boolean> noAirplace = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("no-airplace"))
                  .description(
                     "Only place obsidian where a solid neighbour exists to click against. Vanilla cannot place a block floating in mid-air, so without this the placement is just refused."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> predictKnockback = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-knockback"))
                  .description(
                     "Work out where the hit will send them - Knockback level, sprint bonus and their knockback resistance included - and aim the obsidian there. This is what stops it landing inside the target."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> keepInView = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("keep-everything-in-view"))
                  .description(
                     "Re-check the angle before every single action, not only when the spot is picked. Several ticks pass in between, and turning away in that time is what puts a crystal off behind your back."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<DoubleTap.PlacementSide> placementSide = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("placement-side"))
                  .description(
                     "Where around the target the obsidian may go. BehindTarget puts it on the far side so the blast drives them back toward you; Any allows every side, including behind the target."
                  ))
               .defaultValue(DoubleTap.PlacementSide.Any))
            .build()
      );
   private final Setting<Double> maxPlaceAngle = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-place-angle"))
               .description("Half angle around where you are looking. 90 keeps everything in front and to the sides and never behind you."))
            .defaultValue(90.0)
            .min(10.0)
            .sliderRange(45.0, 180.0)
            .build()
      );
   private final Setting<Double> minTargetDistance = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("min-target-distance"))
               .description(
                  "Blocks of clearance the obsidian has to keep from the target, measured horizontally. 1 keeps it out of the block they stand in. 0 turns the check off."
               ))
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(4.0)
            .build()
      );
   private final Setting<Boolean> damageAtLanding = this.sgObsidian
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("score-at-landing"))
                  .description(
                     "Rate each spot by the damage it deals where the target will be, not where they still stand. Off rates against their current position, which the knockback is about to invalidate."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> doCrystal = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("place-crystal"))
                  .description("Place the crystal on the obsidian."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> crystalSpeed = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("crystal-speed"))
               .description(
                  "Throttle for the crystal place and break, in actions per second. 20 is one per tick, which is the natural ceiling anyway - lower it to slow the combo down. 0 removes the throttle."
               ))
            .defaultValue(20.0)
            .min(0.0)
            .sliderMax(40.0)
            .build()
      );
   private final Setting<Integer> lookahead = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("movement-lookahead"))
                  .description(
                     "Ticks of target movement to account for. The spot has to still be clear once the server gets the packet, not only now. 0 is exactly vanilla."
                  ))
               .defaultValue(3))
            .min(0)
            .sliderMax(10)
            .build()
      );
   private final Setting<Boolean> doBreak = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("break-crystal"))
                  .description("Set the crystal off. Off leaves it standing."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> crystalCount = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("crystal-count"))
                  .description(
                     "How many crystals to run through on the obsidian. 2 is the classic d-tap; more keeps the pressure on while they are still in the air."
                  ))
               .defaultValue(2))
            .min(1)
            .sliderRange(1, 8)
            .build()
      );
   private final Setting<DoubleTap.Timing> timing = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("timing"))
                     .description("Fixed uses the tick gaps below. DamageWindow waits for the target to be hittable in full again."))
                  .defaultValue(DoubleTap.Timing.Fixed))
               .visible(() -> (Integer)this.crystalCount.get() > 1))
            .build()
      );
   private final Setting<Integer> obsidianDelay = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("obsidian-delay"))
                  .description("Ticks between the obsidian going down and the first crystal. Gives the server a moment to accept the block."))
               .defaultValue(2))
            .min(0)
            .sliderMax(10)
            .build()
      );
   private final Setting<Integer> crystalGap = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("crystal-gap"))
                     .description("Ticks between the two crystals. 0 sends them back to back; 10 lines the second one up with the next damage window."))
                  .defaultValue(2))
               .min(0)
               .sliderMax(20)
               .visible(() -> (Integer)this.crystalCount.get() > 1 && this.timing.get() == DoubleTap.Timing.Fixed))
            .build()
      );
   private final Setting<Boolean> waitForDamage = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("wait-for-damage"))
                     .description(
                        "Hold the second crystal until it would actually hurt. The first blast throws them up and away from the spot, so the open window alone means nothing."
                     ))
                  .defaultValue(true))
               .visible(() -> (Integer)this.crystalCount.get() > 1 && this.timing.get() == DoubleTap.Timing.DamageWindow))
            .build()
      );
   private final Setting<Double> secondMinDamage = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("follow-up-min-damage"))
                  .description("Damage a follow-up crystal has to reach before it fires."))
               .defaultValue(5.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(() -> (Integer)this.crystalCount.get() > 1 && this.timing.get() == DoubleTap.Timing.DamageWindow && (Boolean)this.waitForDamage.get()))
            .build()
      );
   private final Setting<Integer> windowTimeout = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("window-timeout"))
                     .description("Ticks to wait for the window and for the target to fall back into range before giving up on the second crystal."))
                  .defaultValue(25))
               .min(1)
               .sliderMax(60)
               .visible(() -> (Integer)this.crystalCount.get() > 1 && this.timing.get() == DoubleTap.Timing.DamageWindow))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Draw the spot the combo is working on."))
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
               .defaultValue(new SettingColor(255, 60, 120, 40))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("line-color"))
                  .description("Line colour."))
               .defaultValue(new SettingColor(255, 60, 120, 180))
               .visible(this.render::get))
            .build()
      );
   private final ActionBudget crystalBudget = new ActionBudget();
   private int returnSlot = -1;
   private static final Object TURN_OWNER = new Object();
   private final TurnProgress turn = TurnProgress.SHARED;
   private int crystalsDone;
   private boolean hitDone;
   private float bestDamage;
   private String lastRejection;
   private boolean obsidianPlaced;
   private int settleTicks;
   private DoubleTap.Stage stage = DoubleTap.Stage.Idle;
   private class_1657 target;
   private class_2338 obsidianPos;
   private boolean sent;
   private int waited;
   private boolean wasPressed;
   private boolean acted;
   private int retries;

   public DoubleTap() {
      super(CrystalAddon.CATEGORY, "double-tap", "One key: hit the target, place obsidian, crystal it, set it off.");
   }

   public void onDeactivate() {
      this.turn.reset(TURN_OWNER);
      this.abort(null);
      this.crystalBudget.reset();
      this.wasPressed = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         if (this.mc.field_1755 != null) {
            this.wasPressed = false;
         } else {
            int cap = this.execution.get() == DoubleTap.Execution.Instant ? 4 : (Integer)this.actionsPerTick.get();
            this.crystalBudget.update((Double)this.crystalSpeed.get(), cap);
            boolean pressed = ((Keybind)this.bind.get()).isPressed();
            boolean justPressed = pressed && !this.wasPressed;
            this.wasPressed = pressed;
            if (this.stage != DoubleTap.Stage.Idle) {
               this.advance();
            } else if (justPressed) {
               this.target = TargetUtils.getPlayerTarget((Double)this.targetRange.get(), (SortPriority)this.priority.get());
               if (this.target == null) {
                  if ((Boolean)this.chatInfo.get()) {
                     this.warning("No target within %.1f blocks.", new Object[]{this.targetRange.get()});
                  }
               } else {
                  this.obsidianPos = null;
                  this.stage = DoubleTap.Stage.Hit;
                  this.sent = false;
                  this.waited = 0;
                  this.retries = 0;
                  this.returnSlot = this.mc.field_1724.method_31548().method_67532();
                  this.crystalsDone = 0;
                  this.obsidianPlaced = false;
                  this.settleTicks = 0;
                  this.hitDone = false;
                  if ((Boolean)this.debugKnockback.get()) {
                     int slot = this.swapWeapon.get() && this.switchMode.get() != DoubleTap.SwitchMode.None
                        ? this.bestWeaponSlot()
                        : this.mc.field_1724.method_31548().method_67532();
                     class_1799 weapon = this.mc.field_1724.method_31548().method_5438(slot);
                     class_243 landing = this.predictedTargetPos();
                     this.info("Server: %s, crystal rules: %s", new Object[]{ServerVersion.describe(), this.legacyRules() ? "legacy" : "modern"});
                     this.info(
                        "Knockback %.2f (%s, sprint %s) -> %.1f %.1f %.1f",
                        new Object[]{
                           KnockbackPredictor.strength(weapon),
                           weapon.method_7960() ? "empty hand" : weapon.method_7909().toString(),
                           this.mc.field_1724.method_5624() ? "yes" : "no",
                           landing.field_1352,
                           landing.field_1351,
                           landing.field_1350
                        }
                     );
                     class_2338 preview = this.findSpot();
                     if (preview == null) {
                        this.warning("No spot: %s.", new Object[]{this.lastRejection});
                     } else {
                        this.info(
                           "Best spot %d %d %d for %.1f damage.",
                           new Object[]{preview.method_10263(), preview.method_10264(), preview.method_10260(), this.bestDamage}
                        );
                     }
                  }

                  this.advance();
               }
            }
         }
      }
   }

   private void advance() {
      if (this.target != null && this.target.method_5805()) {
         boolean instant = this.execution.get() == DoubleTap.Execution.Instant;
         int budget = instant ? Integer.MAX_VALUE : (Integer)this.actionsPerTick.get();
         this.acted = false;

         while (true) {
            DoubleTap.Stage before = this.stage;
            switch (this.stage) {
               case Hit:
                  this.stepHit();
                  break;
               case Obsidian:
                  this.stepObsidian();
                  break;
               case Crystal:
                  this.stepCrystal();
                  break;
               case Break:
                  this.stepBreak();
                  break;
               case Gap:
                  this.stepGap();
                  break;
               default:
                  return;
            }

            if (this.stage == DoubleTap.Stage.Idle || this.stage == before) {
               break;
            }

            if (this.acted) {
               this.acted = false;
               if (--budget <= 0) {
                  break;
               }
            }
         }
      } else {
         this.abort("target gone");
      }
   }

   private void stepHit() {
      if (!(Boolean)this.doHit.get()) {
         this.hitDone = true;
         this.stage = DoubleTap.Stage.Obsidian;
      } else {
         double reachSq = this.target.method_5829().method_49271(this.mc.field_1724.method_33571());
         if (reachSq > (Double)this.hitRange.get() * (Double)this.hitRange.get()) {
            if ((Boolean)this.requireHit.get()) {
               this.abort(String.format("target is %.1f away, out of the %.1f hit range", Math.sqrt(reachSq), this.hitRange.get()));
            } else {
               if ((Boolean)this.chatInfo.get()) {
                  this.warning("Target out of melee reach (%.1f), skipping the sword hit.", new Object[]{Math.sqrt(reachSq)});
               }

               this.hitDone = true;
               this.stage = DoubleTap.Stage.Obsidian;
            }
         } else if (!this.actionInView(this.target.method_33571())) {
            if ((Boolean)this.chatInfo.get()) {
               this.warning("Target is outside your view, turn towards them.", new Object[0]);
            }

            this.abort(null);
         } else {
            boolean maySwap = this.switchMode.get() != DoubleTap.SwitchMode.None && (Boolean)this.swapWeapon.get();
            Runnable action = () -> {
               boolean swapped = false;
               if (maySwap) {
                  swapped = this.selectSlot(this.bestWeaponSlot());
               }

               this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(this.target, this.mc.field_1724.method_5715()));
               if ((Boolean)this.swing.get()) {
                  this.mc.field_1724.method_6104(class_1268.field_5808);
               } else {
                  this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
               }

               if (swapped) {
                  InvUtils.swapBack();
               }
            };
            if (this.turnTowards(this.target.method_33571(), action)) {
               this.acted = true;
               this.hitDone = true;
               this.stage = DoubleTap.Stage.Obsidian;
            }
         }
      }
   }

   private int bestWeaponSlot() {
      int best = this.mc.field_1724.method_31548().method_67532();
      float bestDamage = -1.0F;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         float damage = DamageUtils.getAttackDamage(this.mc.field_1724, this.target, stack);
         if (damage > bestDamage) {
            bestDamage = damage;
            best = i;
         }
      }

      return best;
   }

   private void stepObsidian() {
      if (this.obsidianPos == null) {
         this.obsidianPos = this.findSpot();
         if (this.obsidianPos == null) {
            this.abort(this.lastRejection != null ? this.lastRejection : "no usable spot");
            return;
         }
      }

      if (CrystalUtils.isBase(this.obsidianPos)) {
         if (!this.obsidianPlaced || this.settleTicks++ >= Stealth.pace((Integer)this.obsidianDelay.get())) {
            this.stage = DoubleTap.Stage.Crystal;
            this.sent = false;
            this.waited = 0;
         }
      } else if (!(Boolean)this.doObsidian.get()) {
         this.abort("no obsidian at the spot and placing is off");
      } else if (!this.sent) {
         FindItemResult obsidian = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281});
         if (!obsidian.found()) {
            this.abort("no obsidian in the hotbar");
         } else if (this.switchMode.get() == DoubleTap.SwitchMode.None && obsidian.getHand() == null) {
            this.abort("obsidian is not in a hand and switch-mode is None");
         } else {
            this.sent = true;
            this.placeObsidian(this.obsidianPos);
            if (this.sent) {
               this.acted = true;
               this.obsidianPlaced = true;
               this.waited = 0;
               if (this.execution.get() == DoubleTap.Execution.Instant) {
                  this.stage = DoubleTap.Stage.Crystal;
                  this.sent = false;
               }
            }
         }
      } else {
         if (++this.waited > (Integer)this.timeout.get()) {
            this.abort("obsidian placement was not accepted");
         }
      }
   }

   private void stepCrystal() {
      if (!(Boolean)this.doCrystal.get()) {
         this.finish();
      } else if (this.crystalAt(this.obsidianPos) != null) {
         this.stage = DoubleTap.Stage.Break;
         this.sent = false;
         this.waited = 0;
      } else if (!this.sent) {
         if (this.occupied(this.obsidianPos)) {
            if (this.retries++ >= 2) {
               this.abort("someone is standing where the crystal would go");
            } else {
               if (!this.obsidianPlaced) {
                  this.obsidianPos = null;
                  this.stage = DoubleTap.Stage.Obsidian;
                  this.sent = false;
               }

               this.waited = 0;
            }
         } else {
            FindItemResult crystals = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8301});
            if (!crystals.found()) {
               this.abort("no end crystal in the hotbar");
            } else if (this.switchMode.get() == DoubleTap.SwitchMode.None && crystals.getHand() == null) {
               this.abort("no crystal in a hand and switch-mode is None");
            } else if (this.legacyRules() && !this.mc.field_1687.method_22347(this.obsidianPos.method_10086(2))) {
               this.abort("no second free block above the obsidian, which this server version needs");
            } else if (this.crystalBudget.canAfford()) {
               this.sent = true;
               this.placeCrystal(this.obsidianPos);
               if (this.sent) {
                  this.crystalBudget.tryConsume();
                  this.acted = true;
                  this.waited = 0;
                  if (this.execution.get() == DoubleTap.Execution.Instant) {
                     this.stage = DoubleTap.Stage.Break;
                     this.waited = 0;
                  }
               }
            }
         }
      } else {
         if (++this.waited > (Integer)this.timeout.get()) {
            this.abort("crystal placement was not accepted");
         }
      }
   }

   private void stepBreak() {
      if (!(Boolean)this.doBreak.get()) {
         this.finish();
      } else {
         class_1511 crystal = this.crystalAt(this.obsidianPos);
         if (crystal == null) {
            if (++this.waited > (Integer)this.timeout.get()) {
               this.abort("crystal never appeared");
            }
         } else if (this.crystalBudget.tryConsume()) {
            if (this.actionInView(crystal.method_73189())) {
               Runnable action = () -> {
                  this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(crystal, this.mc.field_1724.method_5715()));
                  if ((Boolean)this.swing.get()) {
                     this.mc.field_1724.method_6104(class_1268.field_5808);
                  } else {
                     this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
                  }
               };
               if (this.turnTowards(crystal.method_73189(), action)) {
                  this.acted = true;
                  this.crystalsDone++;
                  if (this.crystalsDone < (Integer)this.crystalCount.get()) {
                     this.stage = DoubleTap.Stage.Gap;
                     this.sent = false;
                     this.waited = 0;
                  } else {
                     this.finish();
                  }
               }
            }
         }
      }
   }

   private void stepGap() {
      if (this.timing.get() == DoubleTap.Timing.Fixed) {
         if (++this.waited >= Stealth.pace((Integer)this.crystalGap.get())) {
            this.stage = DoubleTap.Stage.Crystal;
            this.sent = false;
            this.waited = 0;
         }
      } else if (!DamageWindow.openForFullDamage(this.target)) {
         if (++this.waited > (Integer)this.windowTimeout.get()) {
            this.abort("damage window never opened");
         }
      } else {
         if ((Boolean)this.waitForDamage.get()) {
            float damage = DamageUtils.crystalDamage(this.target, CrystalUtils.crystalPos(this.obsidianPos));
            if (damage < (Double)this.secondMinDamage.get()) {
               if (++this.waited > (Integer)this.windowTimeout.get()) {
                  this.abort(String.format("target never came back into range, best was %.1f", damage));
               }

               return;
            }
         }

         this.stage = DoubleTap.Stage.Crystal;
         this.sent = false;
         this.waited = 0;
      }
   }

   private class_2338 findSpot() {
      double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
      class_243 aim = this.predictedTargetPos();
      class_2338 origin = this.mc.field_1724.method_24515();
      int radius = (int)Math.ceil((Double)this.placeRange.get());
      class_2338 best = null;
      double bestScore = Double.NEGATIVE_INFINITY;
      this.bestDamage = 0.0F;
      int noBlock = 0;
      int noRoom = 0;
      int outOfReach = 0;
      int behindYou = 0;
      int tooClose = 0;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               class_2338 pos = origin.method_10069(x, y, z);
               boolean isBase = CrystalUtils.isBase(pos);
               if (!isBase && !BlockUtils.canPlaceBlock(pos, true, class_2246.field_10540)) {
                  noBlock++;
               } else if (!isBase && (Boolean)this.noAirplace.get() && BlockUtils.getPlaceSide(pos) == null) {
                  noBlock++;
               } else if (!this.mc.field_1687.method_22347(pos.method_10084())) {
                  noRoom++;
               } else if (this.legacyRules() && !this.mc.field_1687.method_22347(pos.method_10086(2))) {
                  noRoom++;
               } else if (this.occupied(pos)) {
                  noRoom++;
               } else {
                  class_243 crystal = CrystalUtils.crystalPos(pos);
                  if (!this.withinReach(pos, crystal)) {
                     outOfReach++;
                  } else if (!this.inFrontOfMe(crystal)) {
                     behindYou++;
                  } else if (!this.sideAllowed(crystal)) {
                     behindYou++;
                  } else if (this.tooCloseToTarget(pos, aim)) {
                     tooClose++;
                  } else {
                     float damage = this.damageAtLanding(crystal, aim);
                     double score = damage + (isBase ? 0.001 : 0.0);
                     if (score > bestScore) {
                        bestScore = score;
                        this.bestDamage = damage;
                        best = pos;
                     }
                  }
               }
            }
         }
      }

      if (best == null) {
         this.lastRejection = this.describeRejection(noBlock, noRoom, outOfReach, behindYou, tooClose);
      } else {
         this.lastRejection = null;
      }

      return best;
   }

   private boolean tooCloseToTarget(class_2338 pos, class_243 landing) {
      double required = (Double)this.minTargetDistance.get();
      if (required <= 0.0) {
         return false;
      } else {
         class_243 block = pos.method_46558();
         return horizontalDistance(block, this.target.method_73189()) < required || horizontalDistance(block, landing) < required;
      }
   }

   private static double horizontalDistance(class_243 a, class_243 b) {
      double dx = a.field_1352 - b.field_1352;
      double dz = a.field_1350 - b.field_1350;
      return Math.sqrt(dx * dx + dz * dz);
   }

   private float damageAtLanding(class_243 crystal, class_243 landing) {
      if (!(Boolean)this.damageAtLanding.get()) {
         return DamageUtils.crystalDamage(this.target, crystal);
      } else {
         class_243 current = this.target.method_73189();
         class_238 box = this.target
            .method_5829()
            .method_989(landing.field_1352 - current.field_1352, landing.field_1351 - current.field_1351, landing.field_1350 - current.field_1350);
         return DamageUtils.crystalDamage(this.target, landing, box, crystal, DamageUtils.HIT_FACTORY);
      }
   }

   private String describeRejection(int noBlock, int noRoom, int outOfReach, int behindYou, int tooClose) {
      if (tooClose > 0) {
         return String.format("%d spots were blocked by min-target-distance %.1f", tooClose, this.minTargetDistance.get());
      } else if (behindYou > 0) {
         return String.format("%d spots were outside max-place-angle %.0f", behindYou, this.maxPlaceAngle.get());
      } else if (outOfReach > 0) {
         return String.format("nothing within place-range %.1f", this.placeRange.get());
      } else if (noRoom > 0) {
         return "no room above any candidate block";
      } else {
         return noBlock > 0 ? "nowhere an obsidian could be placed" : "nothing in range at all";
      }
   }

   private boolean withinReach(class_2338 pos, class_243 point) {
      return this.mc.field_1724.method_33571().method_1022(point) <= (Double)this.placeRange.get() && Stealth.allowsBlock(pos, point);
   }

   private boolean sideAllowed(class_243 spot) {
      if (this.placementSide.get() == DoubleTap.PlacementSide.Any) {
         return true;
      } else {
         class_243 targetPos = this.target.method_73189();
         class_243 reference = this.placementSide.get() == DoubleTap.PlacementSide.KnockbackSide
            ? this.plannedKnockbackVelocity()
            : targetPos.method_1020(this.mc.field_1724.method_33571());
         class_243 toSpot = spot.method_1020(targetPos);
         return !(reference.method_1027() < 1.0E-6) && !(toSpot.method_1027() < 1.0E-6)
            ? reference.method_1029().method_1026(toSpot.method_1029()) > 0.0
            : true;
      }
   }

   private boolean actionInView(class_243 pos) {
      return !(Boolean)this.keepInView.get() || this.inFrontOfMe(pos);
   }

   private boolean turnTowards(class_243 pos, Runnable action) {
      return this.turnTowards(pos, action, false);
   }

   private boolean turnTowards(class_243 pos, Runnable action, boolean placement) {
      if ((Boolean)this.rotate.get() || placement && Stealth.legitPlace()) {
         return this.turn.turnTo(TURN_OWNER, Rotations.getYaw(pos), Rotations.getPitch(pos), 50, action);
      } else {
         action.run();
         return true;
      }
   }

   private boolean inFrontOfMe(class_243 pos) {
      class_243 eyes = this.mc.field_1724.method_33571();
      class_243 toSpot = new class_243(pos.field_1352 - eyes.field_1352, 0.0, pos.field_1350 - eyes.field_1350);
      if (toSpot.method_1027() < 1.0E-4) {
         return true;
      } else {
         float yaw = this.mc.field_1724.method_36454() * (float) (Math.PI / 180.0);
         class_243 facing = new class_243(-Math.sin(yaw), 0.0, Math.cos(yaw));
         double dot = facing.method_1026(toSpot.method_1029());
         dot = Math.max(-1.0, Math.min(1.0, dot));
         return Math.toDegrees(Math.acos(dot)) <= (Double)this.maxPlaceAngle.get();
      }
   }

   private class_243 predictedTargetPos() {
      if ((Integer)this.leadTicks.get() <= 0) {
         return this.target.method_73189();
      } else {
         class_243 velocity = this.target.method_18798();
         if ((Boolean)this.predictKnockback.get() && !this.hitDone) {
            velocity = this.plannedKnockbackVelocity();
         }

         class_243 eyes = this.mc.field_1724.method_33571();
         double reach = (Double)this.placeRange.get() + 1.5;

         for (int ticks = (Integer)this.leadTicks.get(); ticks > 0; ticks--) {
            class_243 predicted = Trajectory.predict(this.target, velocity, ticks);
            if (predicted.method_1022(eyes) <= reach) {
               return predicted;
            }
         }

         return this.target.method_73189();
      }
   }

   private class_1511 crystalAt(class_2338 base) {
      if (base == null) {
         return null;
      } else {
         class_243 expected = CrystalUtils.crystalPos(base);

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal && !crystal.method_31481() && crystal.method_73189().method_1025(expected) < 0.5) {
               return crystal;
            }
         }

         return null;
      }
   }

   private void placeObsidian(class_2338 pos) {
      if (!this.actionInView(class_243.method_24953(pos))) {
         this.sent = false;
      } else {
         boolean legit = Stealth.legitPlace();
         class_243 aimPos;
         class_3965 staticHit;
         if (legit) {
            LegitPlace.Result r = LegitPlace.forBlock(pos, VanillaLimits.blockRange());
            if (r == null) {
               this.sent = false;
               return;
            }

            aimPos = r.hitVec();
            staticHit = r.hit();
         } else {
            class_2350 side = BlockUtils.getPlaceSide(pos);
            if (side == null) {
               if ((Boolean)this.noAirplace.get()) {
                  this.sent = false;
                  return;
               }

               side = class_2350.field_11036;
            }

            class_2338 neighbour = pos.method_10093(side);
            aimPos = class_243.method_24953(pos).method_1031(side.method_10148() * 0.5, side.method_10164() * 0.5, side.method_10165() * 0.5);
            staticHit = new class_3965(aimPos, side.method_10153(), neighbour, false);
         }

         double yaw = Rotations.getYaw(aimPos);
         double pitch = Rotations.getPitch(aimPos);
         Runnable action = () -> {
            class_3965 hitResult;
            if (legit) {
               hitResult = LegitPlace.confirm(staticHit, yaw, pitch, VanillaLimits.blockRange());
               if (hitResult == null) {
                  this.sent = false;
                  return;
               }
            } else {
               hitResult = staticHit;
            }

            FindItemResult obsidian = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281});
            if (!obsidian.found()) {
               this.sent = false;
            } else {
               class_1268 hand = obsidian.getHand();
               boolean swapped = false;
               if (hand == null) {
                  if (this.switchMode.get() == DoubleTap.SwitchMode.None) {
                     this.sent = false;
                     return;
                  }

                  swapped = this.selectSlot(obsidian.slot());
                  hand = class_1268.field_5808;
               }

               if (this.mc.field_1724.method_5998(hand).method_31574(class_1802.field_8281)) {
                  BlockUtils.interact(hitResult, hand, (Boolean)this.swing.get());
               } else {
                  this.sent = false;
               }

               if (swapped) {
                  InvUtils.swapBack();
               }
            }
         };
         if (!this.turnTowards(aimPos, action, true)) {
            this.sent = false;
         }
      }
   }

   private boolean legacyRules() {
      return switch ((DoubleTap.Placement)this.placement.get()) {
         case Auto -> ServerVersion.needsLegacyCrystalPlacement();
         case Modern -> false;
         case Legacy -> true;
      };
   }

   private boolean occupied(class_2338 base) {
      class_243 targetVelocity = this.predictKnockback.get() && !this.hitDone && this.target != null ? this.plannedKnockbackVelocity() : null;
      return CrystalUtils.isObstructed(base.method_10084(), false)
         || CrystalUtils.playerMovingInto(base.method_10084(), (Integer)this.lookahead.get(), this.target, targetVelocity);
   }

   private class_243 plannedKnockbackVelocity() {
      int slot = this.swapWeapon.get() && this.switchMode.get() != DoubleTap.SwitchMode.None
         ? this.bestWeaponSlot()
         : this.mc.field_1724.method_31548().method_67532();
      class_1799 weapon = this.mc.field_1724.method_31548().method_5438(slot);
      return KnockbackPredictor.velocityAfterHit(this.target, KnockbackPredictor.strength(weapon));
   }

   private void placeCrystal(class_2338 base) {
      if (!this.actionInView(class_243.method_26410(base, 1.0))) {
         this.sent = false;
      } else {
         boolean legit = Stealth.legitPlace();
         class_243 aimPos;
         if (legit) {
            LegitPlace.Result initial = LegitPlace.forCrystal(base, VanillaLimits.blockRange());
            if (initial == null) {
               this.sent = false;
               return;
            }

            aimPos = initial.hitVec();
         } else {
            aimPos = class_243.method_26410(base, 1.0);
         }

         double yaw = Rotations.getYaw(aimPos);
         double pitch = Rotations.getPitch(aimPos);
         Runnable action = () -> {
            if (this.occupied(base)) {
               this.sent = false;
            } else {
               class_3965 hitResult;
               if (legit) {
                  hitResult = LegitPlace.confirmCrystal(base, yaw, pitch, VanillaLimits.blockRange());
                  if (hitResult == null) {
                     this.sent = false;
                     return;
                  }
               } else {
                  hitResult = new class_3965(class_243.method_26410(base, 1.0), class_2350.field_11036, base, false);
               }

               FindItemResult crystals = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8301});
               if (!crystals.found()) {
                  this.sent = false;
               } else {
                  class_1268 hand = crystals.getHand();
                  boolean swapped = false;
                  if (hand == null) {
                     if (this.switchMode.get() == DoubleTap.SwitchMode.None) {
                        this.sent = false;
                        return;
                     }

                     swapped = this.selectSlot(crystals.slot());
                     hand = class_1268.field_5808;
                  }

                  if (this.mc.field_1724.method_5998(hand).method_31574(class_1802.field_8301)) {
                     BlockUtils.interact(hitResult, hand, (Boolean)this.swing.get());
                  } else {
                     this.sent = false;
                  }

                  if (swapped) {
                     InvUtils.swapBack();
                  }
               }
            }
         };
         if (!this.turnTowards(aimPos, action, true)) {
            this.sent = false;
         }
      }
   }

   private void finish() {
      if (this.returnSlot != -1) {
         if (this.switchMode.get() == DoubleTap.SwitchMode.Hotbar
            && (Boolean)this.restoreSlot.get()
            && this.returnSlot != this.mc.field_1724.method_31548().method_67532()) {
            InvUtils.swap(this.returnSlot, false);
         }

         this.returnSlot = -1;
      }

      this.stage = DoubleTap.Stage.Idle;
      this.obsidianPos = null;
      this.sent = false;
      this.waited = 0;
      this.retries = 0;
      this.crystalsDone = 0;
      this.obsidianPlaced = false;
      this.settleTicks = 0;
      this.hitDone = false;
   }

   private boolean selectSlot(int slot) {
      if (slot == this.mc.field_1724.method_31548().method_67532()) {
         return false;
      } else if (this.switchMode.get() == DoubleTap.SwitchMode.Silent) {
         InvUtils.swap(slot, true);
         return true;
      } else {
         InvUtils.swap(slot, false);
         return false;
      }
   }

   private void abort(String reason) {
      this.turn.reset(TURN_OWNER);
      if (reason != null && (Boolean)this.chatInfo.get()) {
         this.warning("Combo stopped: %s.", new Object[]{reason});
      }

      this.finish();
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.obsidianPos != null) {
         event.renderer.box(this.obsidianPos, (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
      }
   }

   public String getInfoString() {
      if (this.stage == DoubleTap.Stage.Idle) {
         return null;
      } else if (this.stage != DoubleTap.Stage.Crystal && this.stage != DoubleTap.Stage.Break) {
         if (this.stage == DoubleTap.Stage.Gap && this.timing.get() == DoubleTap.Timing.Fixed) {
            return "gap " + Math.max(0, (Integer)this.crystalGap.get() - this.waited);
         } else {
            if (this.stage == DoubleTap.Stage.Gap) {
               int left = DamageWindow.ticksUntilOpen(this.target);
               if (left > 0) {
                  return "window " + left;
               }

               if (this.obsidianPos != null) {
                  return String.format("range %.1f", DamageUtils.crystalDamage(this.target, CrystalUtils.crystalPos(this.obsidianPos)));
               }
            }

            return this.stage.toString();
         }
      } else {
         return String.format("%s %d/%d %.1f", this.stage, this.crystalsDone + 1, this.crystalCount.get(), this.bestDamage);
      }
   }

   public static enum Execution {
      Instant,
      Verified;
   }

   public static enum Placement {
      Auto,
      Modern,
      Legacy;
   }

   public static enum PlacementSide {
      Any,
      BehindTarget,
      KnockbackSide;
   }

   private static enum Stage {
      Idle,
      Hit,
      Obsidian,
      Crystal,
      Break,
      Gap;
   }

   public static enum SwitchMode {
      None,
      Hotbar,
      Silent;
   }

   public static enum Timing {
      Fixed,
      DamageWindow;
   }
}
