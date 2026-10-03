package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.ClickGate;
import com.messerocks.crystal.utils.CrystalUtils;
import com.messerocks.crystal.utils.DamageWindow;
import com.messerocks.crystal.utils.HotbarSwap;
import com.messerocks.crystal.utils.KnockbackPredictor;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.ReactionClock;
import com.messerocks.crystal.utils.RevivedPlayers;
import com.messerocks.crystal.utils.ServerVersion;
import com.messerocks.crystal.utils.Trajectory;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaClick;
import com.messerocks.crystal.utils.VanillaLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.misc.Keybind;
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
import net.minecraft.class_1301;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1934;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_2879;
import net.minecraft.class_3489;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_9334;

public class DoubleTap extends CrystalModule {
   private static final int MAX_RETRIES = 2;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgHit = this.settings.createGroup("Hit");
   private final SettingGroup sgObsidian = this.settings.createGroup("Obsidian");
   private final SettingGroup sgCrystal = this.settings.createGroup("Crystal");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Keybind> bind = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("combo-bind")).description("Key that runs the combo.")).defaultValue(Keybind.fromKey(71))).build());
   private final Setting<Integer> timeout = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("confirm-timeout"))
                  .description(
                     "Ticks to wait for a step: for the server to confirm a click, or for a click that cannot go out from here before the combo is given up."
                  ))
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
                  .description("Show the hand swing. Off only hides the animation; the swing packet goes out wherever vanilla's would."))
               .defaultValue(true))
            .build()
      );
   private final Setting<DoubleTap.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description(
                     "Hotbar moves your selection onto each item and leaves it there until the combo is done. Silent goes back to your slot after each click, once a few ticks pass without one. Both are real number-key presses."
                  ))
               .defaultValue(DoubleTap.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> restoreSlot = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("restore-slot"))
                     .description("Go back to the slot you started on once the combo is done - a few ticks after its last click, never in the same tick."))
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
                  .description(
                     "Reach for the melee hit, measured to the target's hitbox like vanilla. Never more than the server's entity reach - 3 in vanilla - whatever this says."
                  ))
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
               .description("How far away the obsidian may go. Never more than the server's block reach - 4.5 in vanilla - whatever this says."))
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
                  "Throttle for the crystal place and break, in actions per second. 20 is one per tick, the ceiling of a hand; lower it to slow the combo down."
               ))
            .defaultValue(20.0)
            .range(1.0, 20.0)
            .sliderRange(1.0, 20.0)
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
                  .description(
                     "Ticks between the obsidian going down and the first crystal, spread by Stealth's timing-jitter. Gives the server a moment to accept the block; at least 2, the quickest two right clicks a hand makes in a row."
                  ))
               .defaultValue(2))
            .min(2)
            .sliderRange(2, 10)
            .build()
      );
   private final Setting<Integer> crystalGap = this.sgCrystal
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("crystal-gap"))
                     .description(
                        "Ticks between one crystal going off and the next being placed, spread by Stealth's timing-jitter. 2 is as quick as a hand goes; 10 lines the second one up with the next damage window."
                     ))
                  .defaultValue(2))
               .min(2)
               .sliderRange(2, 20)
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
   // A key press: above the auras (Auto Crystal at 50), so an aura waiting on its budget cannot hold the head while
   // the combo key is down.
   private static final int TURN_PRIORITY = 70;
   private static final Object TURN_OWNER = new Object();
   private final TurnProgress turn = TurnProgress.SHARED;
   private int crystalsDone;
   private boolean hitDone;
   private class_243 hitVelocity;
   private int hitVelocityAge;
   private boolean yieldTick;
   private final Map<Integer, Integer> brokenCrystals = new HashMap<>();
   private boolean crystalSeen;
   private boolean placedHere;
   private int stalled;
   private final DoubleTap.HitAim hitAim = new DoubleTap.HitAim();
   private static final int CAMERA_PATIENCE = 40;
   private int cameraWaited;
   private DoubleTap.CameraHold cameraHold;
   private int paced = -1;
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
   private long cycleGeneration;
   private int ticks;
   private int nextClickAt;
   private Object reactingTo;
   private final ReactionClock reactions = new ReactionClock();
   private int held;
   private int restoreTo = -1;
   private int restoreFrom = -1;
   private int restoreAt;
   private static final DoubleTap.Held HELD_MAIN = new DoubleTap.Held(class_1268.field_5808, null);

   private void pruneBrokenCrystals() {
      if (this.mc.field_1687 != null && this.mc.field_1724 != null) {
         int window = CrystalUtils.confirmTicks(EntityUtils.getPing(this.mc.field_1724));
         int age = this.mc.field_1724.field_6012;
         this.brokenCrystals
            .entrySet()
            .removeIf(entry -> this.mc.field_1687.method_8469(entry.getKey()) == null || age - entry.getValue() > window || age < entry.getValue());
      } else {
         this.brokenCrystals.clear();
      }
   }

   public DoubleTap() {
      super(CrystalAddon.CATEGORY, "double-tap", "One key: hit the target, place obsidian, crystal it, set it off.");
   }

   public void onDeactivate() {
      this.turn.reset(TURN_OWNER);
      this.abort(null);
      if (this.restoreTo != -1 && this.mc.field_1724 != null && HotbarSwap.stillOn(this.restoreFrom)) {
         HotbarSwap.selectLater(this.restoreTo, this.restoreFrom, Stealth.reactionTicks());
      }

      this.restoreTo = -1;
      this.crystalBudget.reset();
      this.brokenCrystals.clear();
      this.reactions.clear();
      this.reactingTo = null;
      this.nextClickAt = 0;
      this.wasPressed = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.isActive()) {
         if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
            if (this.sessionChanged()) {
               this.returnSlot = -1;
               this.restoreTo = -1;
               this.target = null;
               this.onDeactivate();
            }

            this.ticks++;
            this.reactions.tick();
            this.pruneBrokenCrystals();
            if (this.mc.field_1755 != null) {
               this.wasPressed = false;
            } else {
               this.crystalBudget.update((Double)this.crystalSpeed.get(), 1);
               this.restoreLater();
               boolean pressed = ((Keybind)this.bind.get()).isPressed();
               boolean justPressed = pressed && !this.wasPressed;
               this.wasPressed = pressed;
               if (this.stage != DoubleTap.Stage.Idle) {
                  this.advance();
               } else if (justPressed) {
                  this.target = this.findTarget(true);
                  if (this.target == null) {
                     if ((Boolean)this.chatInfo.get()) {
                        if (this.findTarget(false) != null) {
                           this.warning(
                              "No target in front of you: everyone within %.1f blocks is outside Stealth's view-angle. Turn towards them.",
                              new Object[]{this.targetRange.get()}
                           );
                        } else {
                           this.warning("No target within %.1f blocks.", new Object[]{this.targetRange.get()});
                        }
                     }
                  } else {
                     this.obsidianPos = null;
                     this.cycleGeneration++;
                     this.enter(DoubleTap.Stage.Hit);
                     this.retries = 0;
                     this.reactingTo = null;
                     this.cameraWaited = 0;
                     this.returnSlot = this.restoreTo != -1 ? this.restoreTo : HotbarSwap.homeSlot();
                     this.restoreTo = -1;
                     this.crystalsDone = 0;
                     this.obsidianPlaced = false;
                     this.settleTicks = 0;
                     this.hitDone = false;
                     this.hitVelocity = null;
                     if ((Boolean)this.debugKnockback.get()) {
                        class_1799 weapon = this.mc.field_1724.method_31548().method_5438(this.plannedWeaponSlot());
                        class_243 landing = this.predictedTargetPos(this.predictKnockback.get() ? this.expectedKnockback() : null);
                        this.info("Server: %s, crystal rules: %s", new Object[]{ServerVersion.describe(), this.legacyRules() ? "legacy" : "modern"});
                        this.info(
                           "Knockback %.2f (%s, sprint hit %s) -> %.1f %.1f %.1f",
                           new Object[]{
                              KnockbackPredictor.strength(weapon),
                              weapon.method_7960() ? "empty hand" : weapon.method_7909().toString(),
                              KnockbackPredictor.sprintHit(this.mc.field_1724) ? "yes" : "no",
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
   }

   private class_1657 findTarget(boolean inFront) {
      double range = (Double)this.targetRange.get();
      Predicate<class_1297> view = entity -> !inFront || Stealth.inView(entity.method_5829());
      if (TargetUtils.get(
         entity -> entity instanceof class_1657 playerx
            && playerx != this.mc.field_1724
            && playerx.method_5805()
            && !playerx.method_29504()
            && playerx.method_6032() > 0.0F
            && PlayerUtils.isWithin(playerx, range)
            && Friends.get().shouldAttack(playerx)
            && !(playerx instanceof FakePlayerEntity fake ? fake.noHit : EntityUtils.getGameMode(playerx) != class_1934.field_9215)
            && view.test(entity),
         (SortPriority)this.priority.get()
      ) instanceof class_1657 player) {
         return player;
      } else {
         return TargetUtils.get(
               entity -> entity instanceof class_1657 playerx
                  && playerx != this.mc.field_1724
                  && RevivedPlayers.isRevived(playerx)
                  && Friends.get().shouldAttack(playerx)
                  && EntityUtils.getGameMode(playerx) == class_1934.field_9215
                  && PlayerUtils.isWithin(playerx, range)
                  && view.test(entity),
               (SortPriority)this.priority.get()
            ) instanceof class_1657 player
            ? player
            : null;
      }
   }

   private boolean targetGone() {
      return this.target == null || this.target.method_31481() || this.target.method_6032() <= 0.0F && !RevivedPlayers.isRevived(this.target);
   }

   private void enter(DoubleTap.Stage next) {
      this.stage = next;
      this.sent = false;
      this.waited = 0;
      this.stalled = 0;
      this.hitAim.reset();
      this.paced = -1;
      this.held = 0;
      this.crystalSeen = false;
   }

   private void stall(String reason) {
      if (++this.stalled > (Integer)this.timeout.get()) {
         this.abort(reason);
      }
   }

   private void advance() {
      if (this.targetGone()) {
         this.abort("target gone");
      } else {
         if (this.reactingTo != null && this.reactions.ready(this.reactingTo)) {
            this.reactingTo = null;
         }

         this.acted = false;
         this.yieldTick = false;
         this.cameraHold = null;

         DoubleTap.Stage before;
         do {
            before = this.stage;
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
         } while (this.stage != DoubleTap.Stage.Idle && this.stage != before && !this.yieldTick && !this.acted);
      }
   }

   private boolean clickAllowed() {
      return this.reactingTo == null && this.ticks >= this.nextClickAt;
   }

   private void clicked() {
      this.nextClickAt = Math.max(this.nextClickAt, this.ticks + Math.max(2, Stealth.pace(2)));
   }

   private void holdClicks(int wait) {
      this.nextClickAt = Math.max(this.nextClickAt, this.ticks + wait);
   }

   private void reactTo(Object key) {
      if (!key.equals(this.reactingTo)) {
         this.reactingTo = key;
         if (this.reactions.ready(key)) {
            this.reactingTo = null;
         }
      }
   }

   private void stepHit() {
      if (!(Boolean)this.doHit.get()) {
         this.hitDone = true;
         this.enter(DoubleTap.Stage.Obsidian);
      } else {
         double reachSq = this.target.method_5829().method_49271(this.mc.field_1724.method_33571());
         double hitReach = this.hitReach();
         if (reachSq > hitReach * hitReach) {
            if ((Boolean)this.requireHit.get()) {
               this.abort(String.format("target is %.1f away, out of the %.1f hit range", Math.sqrt(reachSq), hitReach));
            } else {
               if ((Boolean)this.chatInfo.get()) {
                  this.warning("Target out of melee reach (%.1f), skipping the sword hit.", new Object[]{Math.sqrt(reachSq)});
               }

               this.hitDone = true;
               this.enter(DoubleTap.Stage.Obsidian);
            }
         } else if (!this.actionInView(this.target.method_33571())) {
            if ((Boolean)this.chatInfo.get()) {
               this.warning("Target is outside your view (max-place-angle %.0f), turn towards them.", new Object[]{this.maxPlaceAngle.get()});
            }

            this.abort(null);
         } else {
            boolean rotating = this.shouldRotate();
            boolean alongCamera = false;
            double yaw;
            double pitch;
            boolean clear;
            if (rotating) {
               DoubleTap.Look camera = this.cameraLook((y, p) -> LegitPlace.confirmEntity(this.target.method_5829(), y, p, hitReach) != null);
               if (camera != null) {
                  clear = true;
                  alongCamera = true;
                  yaw = camera.yaw();
                  pitch = camera.pitch();
               } else {
                  class_238 box = this.target.method_5829();
                  BiPredicate<Double, Double> lands = (y, p) -> LegitPlace.confirmEntity(box, y, p, hitReach) != null;
                  DoubleTap.Look aim = this.hitAim.choose(this.serverLook(), lands, () -> DoubleTap.HitAim.preferMiddle(this.middleLook(box), lands, () -> {
                     LegitPlace.EntityResult fresh = LegitPlace.forEntity(box, hitReach);
                     return fresh == null ? null : new DoubleTap.Look(fresh.yaw(), fresh.pitch());
                  }));
                  clear = aim != null;
                  yaw = clear ? aim.yaw() : 0.0;
                  pitch = clear ? aim.pitch() : 0.0;
               }
            } else {
               yaw = LegitPlace.currentYaw();
               pitch = LegitPlace.currentPitch();
               clear = LegitPlace.confirmEntity(this.target.method_5829(), yaw, pitch, hitReach) != null;
            }

            boolean maySwap = this.switchMode.get() != DoubleTap.SwitchMode.None && (Boolean)this.swapWeapon.get();
            String why = null;
            String refused = entityRefusal(this.target);
            if (maySwap ? this.bestWeaponSlot() < 0 : stabs(this.mc.field_1724.method_6047())) {
               why = maySwap
                  ? "every hotbar slot holds a spear, which stabs instead of hitting"
                  : "a spear in your main hand stabs instead of hitting, and "
                     + (this.switchMode.get() == DoubleTap.SwitchMode.None ? "switch-mode is None" : "swap-to-weapon is off");
            } else if (refused != null) {
               why = "the target is " + refused;
            } else if (!clear) {
               why = rotating ? "no clear swing at the target from here" : "the target is not under your crosshair and rotate is off";
            }

            if (why != null) {
               this.skipHit(why);
            } else {
               Runnable action = () -> {
                  if (!Stealth.allowsEntity(this.target) || LegitPlace.confirmEntity(this.target.method_5829(), yaw, pitch, this.hitReach()) == null) {
                     this.stall("the hit no longer lands on the target from here");
                  } else if (this.clickAllowed()) {
                     if (!ClickGate.canAttack()) {
                        this.stall(clickBusy(true));
                     } else {
                        int slot = maySwap ? this.bestWeaponSlot() : this.mc.field_1724.method_31548().method_67532();
                        if (slot >= 0 && !stabs(this.mc.field_1724.method_31548().method_5438(slot))) {
                           DoubleTap.Held weapon = this.switchTo(slot);
                           if (weapon == null) {
                              this.stall("the hotbar could not switch to the weapon this tick");
                           } else if (!switchedNow(weapon)) {
                              if (!Stealth.claimAttack()) {
                                 weapon.release();
                              } else {
                                 this.hitVelocity = KnockbackPredictor.velocityAfterHit(
                                    this.target, KnockbackPredictor.strength(this.mc.field_1724.method_6047()), this.turn.serverLookYaw(yaw)
                                 );
                                 this.hitVelocityAge = this.mc.field_1724.field_6012;
                                 HotbarSwap.syncSelected();
                                 this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(this.target, this.mc.field_1724.method_5715()));
                                 if ((Boolean)this.swing.get()) {
                                    this.mc.field_1724.method_6104(class_1268.field_5808);
                                 } else {
                                    this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
                                 }

                                 KnockbackPredictor.afterAttack();
                                 weapon.release();
                                 this.clicked();
                                 this.acted = true;
                                 this.hitDone = true;
                                 this.enter(DoubleTap.Stage.Obsidian);
                              }
                           }
                        } else {
                           this.stall("a spear stabs instead of hitting");
                        }
                     }
                  }
               };
               int turnNeeds = rotating ? turnTicks(yaw, pitch) : 1;
               DoubleTap.Turn result = this.turnTowards(rotating, yaw, pitch, action, !alongCamera);
               if (result == DoubleTap.Turn.Turning && this.hitAim.waitedTooLong(turnNeeds, (Integer)this.timeout.get())) {
                  this.skipHit("the target kept moving off your look before the server had it");
                  this.yieldTick = true;
               } else {
                  this.notQueued(result);
               }
            }
         }
      }
   }

   private void skipHit(String why) {
      if ((Boolean)this.requireHit.get()) {
         this.abort(why);
      } else {
         if ((Boolean)this.chatInfo.get()) {
            this.warning("No hit: %s, skipping the sword hit.", new Object[]{why});
         }

         this.hitDone = true;
         this.enter(DoubleTap.Stage.Obsidian);
      }
   }

   private DoubleTap.Look serverLook() {
      double yaw = LegitPlace.currentYaw();
      double pitch = LegitPlace.currentPitch();
      return this.turn.serverHas(yaw, pitch) ? new DoubleTap.Look(yaw, pitch) : null;
   }

   private DoubleTap.Look middleLook(class_238 box) {
      class_243 middle = box.method_1005();
      double dx = middle.field_1352 - this.mc.field_1724.method_23317();
      double dz = middle.field_1350 - this.mc.field_1724.method_23321();
      return dx * dx + dz * dz < 1.0E-8 ? null : new DoubleTap.Look(Rotations.getYaw(middle), Rotations.getPitch(middle));
   }

   private static int turnTicks(double yaw, double pitch) {
      double distance = Math.max(Math.abs(class_3532.method_15338(yaw - LegitPlace.currentYaw())), Math.abs(pitch - LegitPlace.currentPitch()));
      return DoubleTap.HitAim.turnTicks(distance, Stealth.turnCap(), Stealth.aimSmoothness());
   }

   private int bestWeaponSlot() {
      int selected = this.mc.field_1724.method_31548().method_67532();
      class_1799 held = this.mc.field_1724.method_31548().method_5438(selected);
      int best = stabs(held) ? -1 : selected;
      float bestDamage = best == -1 ? Float.NEGATIVE_INFINITY : DamageUtils.getAttackDamage(this.mc.field_1724, this.target, held);

      for (int i = 0; i <= 8; i++) {
         if (i != selected) {
            class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
            if (!stabs(stack)) {
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

   private void stepObsidian() {
      if (this.obsidianPos == null) {
         this.obsidianPos = this.findSpot();
         if (this.obsidianPos == null) {
            this.abort(this.lastRejection != null ? this.lastRejection : "no usable spot");
            return;
         }
      }

      if (CrystalUtils.isBase(this.obsidianPos)) {
         if (this.obsidianPlaced) {
            if (this.paced < 0) {
               this.paced = Math.max(2, Stealth.pace((Integer)this.obsidianDelay.get()));
            }

            if (this.settleTicks++ < this.paced) {
               return;
            }
         }

         this.enter(DoubleTap.Stage.Crystal);
      } else if (!(Boolean)this.doObsidian.get()) {
         this.abort("no obsidian at the spot and placing is off");
      } else if (!this.sent) {
         FindItemResult obsidian = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281});
         if (!obsidian.found()) {
            this.abort("no obsidian in the hotbar");
         } else if (this.switchMode.get() == DoubleTap.SwitchMode.None && !this.inReachableHand(class_1802.field_8281)) {
            this.abort("obsidian is not in a hand a right click reaches and switch-mode is None");
         } else {
            this.sent = true;
            this.placeObsidian(this.obsidianPos);
            if (this.sent) {
               ;
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
         this.placedHere = this.sent;
         this.enter(DoubleTap.Stage.Break);
      } else if (!this.sent) {
         if (this.occupied(this.obsidianPos, this.spotMotion())) {
            this.reactTo(new DoubleTap.SpotBlocked(this.cycleGeneration, this.obsidianPos));
            if (this.retries++ >= 2) {
               this.abort("someone is standing where the crystal would go");
            } else {
               if (!this.obsidianPlaced) {
                  this.obsidianPos = null;
                  this.enter(DoubleTap.Stage.Obsidian);
               }

               this.waited = 0;
               this.yieldTick = true;
            }
         } else {
            FindItemResult crystals = InvUtils.findInHotbar(new class_1792[]{class_1802.field_8301});
            if (!crystals.found()) {
               this.abort("no end crystal in the hotbar");
            } else if (this.switchMode.get() == DoubleTap.SwitchMode.None && !this.inReachableHand(class_1802.field_8301)) {
               this.abort("no crystal in a hand a right click reaches and switch-mode is None");
            } else if (this.legacyRules() && !this.mc.field_1687.method_22347(this.obsidianPos.method_10086(2))) {
               this.abort("no second free block above the obsidian, which this server version needs");
            } else if (this.crystalBudget.canAfford()) {
               this.sent = true;
               this.placeCrystal(this.obsidianPos);
               if (this.sent) {
                  ;
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
            if (this.crystalSeen) {
               this.crystalDone();
            } else {
               if (++this.waited > (Integer)this.timeout.get()) {
                  this.abort("crystal never appeared");
               }
            }
         } else {
            if (!this.crystalSeen) {
               this.crystalSeen = true;
               if (this.placedHere) {
                  this.holdClicks(Math.max(2, Stealth.pace(2)));
               } else {
                  this.reactTo(new DoubleTap.ForeignCrystal(crystal.method_5628()));
               }
            }

            if (this.crystalBudget.canAfford()) {
               if (!this.actionInView(crystal.method_73189())) {
                  this.stall("the crystal is outside max-place-angle (keep-everything-in-view)");
               } else if (this.lethalToMe(crystal.method_73189())) {
                  this.abort("setting the crystal off would kill you");
               } else if (this.hittingSlot() < 0) {
                  this.abort(
                     "a spear in your main hand stabs instead of hitting the crystal, and "
                        + (this.switchMode.get() == DoubleTap.SwitchMode.None ? "switch-mode is None" : "every hotbar slot holds one")
                  );
               } else {
                  boolean rotating = this.shouldRotate();
                  double reach = VanillaLimits.entityRange();
                  DoubleTap.Look camera = rotating ? this.cameraLook((y, p) -> LegitPlace.confirmEntity(crystal.method_5829(), y, p, reach) != null) : null;
                  double yaw;
                  double pitch;
                  if (camera != null) {
                     yaw = camera.yaw();
                     pitch = camera.pitch();
                  } else if (rotating) {
                     LegitPlace.EntityResult aim = LegitPlace.forEntity(crystal.method_5829(), reach);
                     if (aim == null) {
                        this.stall("no ray from your eyes reaches the crystal");
                        return;
                     }

                     yaw = aim.yaw();
                     pitch = aim.pitch();
                  } else {
                     yaw = LegitPlace.currentYaw();
                     pitch = LegitPlace.currentPitch();
                     if (LegitPlace.confirmEntity(crystal.method_5829(), yaw, pitch, reach) == null) {
                        this.stall("the crystal is not under your crosshair and rotate is off");
                        return;
                     }
                  }

                  String refused = entityRefusal(crystal);
                  if (refused != null) {
                     this.stall("the crystal is " + refused);
                  } else {
                     Runnable action = () -> {
                        if (!crystal.method_31481()) {
                           if (!Stealth.allowsEntity(crystal) || LegitPlace.confirmEntity(crystal.method_5829(), yaw, pitch, reach) == null) {
                              this.stall("the crystal hit no longer lands from here");
                           } else if (this.lethalToMe(crystal.method_73189())) {
                              this.abort("setting the crystal off would kill you");
                           } else if (this.clickAllowed()) {
                              if (!ClickGate.canAttack()) {
                                 this.stall(clickBusy(true));
                              } else {
                                 int slot = this.hittingSlot();
                                 DoubleTap.Held hand = slot < 0 ? null : this.switchTo(slot);
                                 if (hand == null) {
                                    this.stall("the hotbar could not switch away from the spear this tick");
                                 } else if (!switchedNow(hand)) {
                                    if (!this.claimCrystal(true)) {
                                       hand.release();
                                    } else {
                                       HotbarSwap.syncSelected();
                                       this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(crystal, this.mc.field_1724.method_5715()));
                                       if ((Boolean)this.swing.get()) {
                                          this.mc.field_1724.method_6104(class_1268.field_5808);
                                       } else {
                                          this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
                                       }

                                       KnockbackPredictor.afterAttack();
                                       hand.release();
                                       this.brokenCrystals.put(crystal.method_5628(), this.mc.field_1724.field_6012);
                                       this.clicked();
                                       this.acted = true;
                                       this.crystalDone();
                                    }
                                 }
                              }
                           }
                        }
                     };
                     this.notQueued(this.turnTowards(rotating, yaw, pitch, action));
                  }
               }
            }
         }
      }
   }

   private void crystalDone() {
      this.crystalsDone++;
      if (this.crystalsDone < (Integer)this.crystalCount.get()) {
         this.enter(DoubleTap.Stage.Gap);
      } else {
         this.finish();
      }
   }

   private boolean lethalToMe(class_243 crystal) {
      return DamageUtils.crystalDamage(this.mc.field_1724, crystal) >= EntityUtils.getTotalHealth(this.mc.field_1724);
   }

   private void stepGap() {
      if (this.timing.get() == DoubleTap.Timing.Fixed) {
         if (this.paced < 0) {
            this.paced = Math.max(2, Stealth.pace((Integer)this.crystalGap.get()));
         }

         if (++this.waited >= this.paced) {
            this.enter(DoubleTap.Stage.Crystal);
         }
      } else {
         boolean wantDamage = (Boolean)this.waitForDamage.get();
         float damage = wantDamage ? DamageUtils.crystalDamage(this.target, CrystalUtils.crystalPos(this.obsidianPos)) : 0.0F;
         boolean inRange = !wantDamage || damage >= (Double)this.secondMinDamage.get();
         Object back = new DoubleTap.BackInRange(this.cycleGeneration, this.crystalsDone);
         boolean seen = !wantDamage;
         if (wantDamage) {
            if (inRange) {
               seen = this.reactions.ready(back);
            } else {
               this.reactions.forget(back);
            }
         }

         if (!DamageWindow.openForFullDamage(this.target)) {
            if (++this.waited > (Integer)this.windowTimeout.get()) {
               this.abort("damage window never opened");
            }
         } else {
            if (this.paced < 0) {
               this.paced = Stealth.pace(2);
            }

            if (this.held++ >= this.paced) {
               if (!inRange) {
                  if (++this.waited > (Integer)this.windowTimeout.get()) {
                     this.abort(String.format("target never came back into range, best was %.1f", damage));
                  }
               } else if (!seen) {
                  if (++this.waited > (Integer)this.windowTimeout.get()) {
                     this.abort("target did not stay in range long enough");
                  }
               } else {
                  this.enter(DoubleTap.Stage.Crystal);
               }
            }
         }
      }
   }

   private class_2338 findSpot() {
      double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
      class_243 knockback = this.expectedKnockback();
      class_243 motion = this.predictKnockback.get() ? knockback : null;
      class_243 aim = this.predictedTargetPos(motion);
      boolean legacy = this.legacyRules();
      class_2338 origin = this.mc.field_1724.method_24515();
      int radius = (int)Math.ceil(this.placeReach());
      List<DoubleTap.Spot> spots = new ArrayList<>();
      this.bestDamage = 0.0F;
      DoubleTap.Rejections rejected = new DoubleTap.Rejections();
      class_243 eyes = this.mc.field_1724.method_33571();
      double reach = this.placeReach();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               class_2338 pos = origin.method_10069(x, y, z);
               boolean isBase = CrystalUtils.isBase(pos);
               if (!isBase && !(Boolean)this.doObsidian.get()) {
                  rejected.add(DoubleTap.Filter.NoBlock);
               } else if (!isBase && !BlockUtils.canPlaceBlock(pos, true, class_2246.field_10540)) {
                  rejected.add(DoubleTap.Filter.NoBlock);
               } else if (!isBase && BlockUtils.getPlaceSide(pos) == null) {
                  rejected.add(DoubleTap.Filter.NoBlock);
               } else if (!this.mc.field_1687.method_22347(pos.method_10084())) {
                  rejected.add(DoubleTap.Filter.NoRoom);
               } else if (legacy && !this.mc.field_1687.method_22347(pos.method_10086(2))) {
                  rejected.add(DoubleTap.Filter.NoRoom);
               } else if (this.occupied(pos, motion)) {
                  rejected.add(DoubleTap.Filter.NoRoom);
               } else {
                  class_243 crystal = CrystalUtils.crystalPos(pos);
                  if (eyes.method_1022(crystal) > reach || !VanillaLimits.canReachBlock(pos)) {
                     rejected.add(DoubleTap.Filter.OutOfReach);
                  } else if (!Stealth.inView(crystal)) {
                     rejected.add(DoubleTap.Filter.OutOfView);
                  } else if (!Stealth.allowsBlock(pos, crystal)) {
                     rejected.add(DoubleTap.Filter.Hidden);
                  } else if (!this.inFrontOfMe(crystal)) {
                     rejected.add(DoubleTap.Filter.BehindYou);
                  } else if (!this.sideAllowed(crystal, knockback)) {
                     rejected.add(DoubleTap.Filter.BehindYou);
                  } else if (this.tooCloseToTarget(pos, aim)) {
                     rejected.add(DoubleTap.Filter.TooClose);
                  } else {
                     spots.add(new DoubleTap.Spot(pos, isBase, this.damageAtLanding(crystal, aim)));
                  }
               }
            }
         }
      }

      spots.sort(Comparator.comparingDouble(DoubleTap.Spot::score).reversed());

      for (DoubleTap.Spot spot : spots) {
         if (DamageUtils.crystalDamage(this.mc.field_1724, CrystalUtils.crystalPos(spot.pos())) >= selfHealth) {
            rejected.add(DoubleTap.Filter.Lethal);
         } else {
            if (this.clickable(spot)) {
               this.bestDamage = spot.damage();
               this.lastRejection = null;
               return spot.pos();
            }

            rejected.add(DoubleTap.Filter.NoClick);
         }
      }

      this.lastRejection = this.describeRejection(rejected);
      return null;
   }

   private boolean clickable(DoubleTap.Spot spot) {
      boolean rotating = this.shouldRotate();
      double yaw = LegitPlace.currentYaw();
      double pitch = LegitPlace.currentPitch();
      double blockReach = VanillaLimits.blockRange();
      if (!spot.isBase()) {
         boolean placeable = rotating
            ? LegitPlace.forBlock(spot.pos(), blockReach) != null
            : LegitPlace.confirmPlacement(spot.pos(), class_1802.field_8281, yaw, pitch, blockReach) != null;
         if (!placeable) {
            return false;
         }
      } else if ((Boolean)this.doCrystal.get()) {
         boolean placeable = rotating
            ? LegitPlace.forCrystal(spot.pos(), blockReach) != null
            : LegitPlace.confirmCrystal(spot.pos(), yaw, pitch, blockReach) != null;
         if (!placeable) {
            return false;
         }
      }

      if ((Boolean)this.doCrystal.get() && (Boolean)this.doBreak.get()) {
         class_238 hitbox = CrystalUtils.crystalHitbox(spot.pos());
         double entityReach = VanillaLimits.entityRange();
         return !rotating
            ? hitbox.method_49271(this.mc.field_1724.method_33571()) < entityReach * entityReach
            : LegitPlace.forEntity(hitbox, entityReach) != null;
      } else {
         return true;
      }
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

   private String describeRejection(DoubleTap.Rejections rejected) {
      DoubleTap.Filter named = rejected.named();
      if (named == null) {
         return "nothing in range at all";
      } else {
         int count = rejected.count(named);

         return switch (named) {
            case NoBlock -> this.doObsidian.get() ? "nowhere an obsidian could be placed" : "no obsidian or bedrock in range, and place-obsidian is off";
            case NoRoom -> "no room above any candidate block";
            case OutOfReach -> String.format("nothing within place-range %.1f", this.placeReach());
            case OutOfView -> String.format("%d spots were outside Stealth's view-angle - turn towards the target", count);
            case Hidden -> String.format("%d spots were behind blocks", count);
            case BehindYou -> String.format("%d spots were outside max-place-angle %.0f", count, this.maxPlaceAngle.get());
            case TooClose -> String.format("%d spots were blocked by min-target-distance %.1f", count, this.minTargetDistance.get());
            case Lethal -> String.format("%d spots would have killed you", count);
            case NoClick -> String.format(
               "%d spots had no click from here - nothing in reach and view to place against, or the crystal out of hitting reach%s",
               count,
               this.shouldRotate() ? "" : " (rotate is off, so only what your crosshair is on counts)"
            );
         };
      }
   }

   private static String blockRefusal(class_2338 pos, class_243 point) {
      if (Stealth.allowsBlock(pos, point)) {
         return null;
      } else if (!VanillaLimits.canReachBlock(pos)) {
         return "out of reach";
      } else {
         return !Stealth.inView(point) ? "outside Stealth's view-angle" : "behind a block";
      }
   }

   private static String entityRefusal(class_1297 entity) {
      if (Stealth.allowsEntity(entity)) {
         return null;
      } else if (!VanillaLimits.canReachEntity(entity)) {
         return "out of reach";
      } else {
         return !Stealth.inView(entity.method_5829()) ? "outside Stealth's view-angle" : "behind a block";
      }
   }

   private double placeReach() {
      return Math.min((Double)this.placeRange.get(), VanillaLimits.blockRange());
   }

   private double hitReach() {
      return Math.min((Double)this.hitRange.get(), VanillaLimits.entityRange());
   }

   private boolean sideAllowed(class_243 spot, class_243 knockback) {
      if (this.placementSide.get() == DoubleTap.PlacementSide.Any) {
         return true;
      } else {
         class_243 targetPos = this.target.method_73189();
         class_243 reference = this.placementSide.get() == DoubleTap.PlacementSide.KnockbackSide
            ? (knockback != null ? knockback : this.target.method_18798())
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

   private boolean shouldRotate() {
      return (Boolean)this.rotate.get() || Stealth.legitPlace();
   }

   private DoubleTap.Look cameraLook(BiPredicate<Double, Double> lands) {
      if (!TurnProgress.cameraNeeded()) {
         return null;
      } else {
         double yaw = this.mc.field_1724.method_36454();
         double pitch = this.mc.field_1724.method_36455();
         return lands.test(yaw, pitch) ? new DoubleTap.Look(yaw, pitch) : null;
      }
   }

   private boolean cameraHolds(double yaw, double pitch) {
      return !TurnProgress.cameraNeeded()
         ? false
         : Math.abs(class_3532.method_15338(yaw - this.mc.field_1724.method_36454())) > 0.001 || Math.abs(pitch - this.mc.field_1724.method_36455()) > 0.001;
   }

   private void notQueued(DoubleTap.Turn result) {
      switch (result) {
         case Busy:
            this.stall("another module is holding the rotation");
            break;
         case Camera:
            this.waitForCamera();
      }
   }

   private void waitForCamera() {
      this.cameraHold = this.mc.field_1724.method_6128()
         ? DoubleTap.CameraHold.Gliding
         : (
            TurnProgress.ownClickPending()
               ? DoubleTap.CameraHold.OwnClick
               : (TurnProgress.movementKeysHeld() ? DoubleTap.CameraHold.Moving : DoubleTap.CameraHold.CrosshairClick)
         );
      if (++this.cameraWaited > 40) {
         this.abort(this.cameraHold.gaveUp);
      }
   }

   private DoubleTap.Turn turnTowards(boolean rotating, double yaw, double pitch, Runnable action) {
      return this.turnTowards(rotating, yaw, pitch, action, false);
   }

   private DoubleTap.Turn turnTowards(boolean rotating, double yaw, double pitch, Runnable action, boolean settled) {
      long expected = this.cycleGeneration;
      Runnable current = this.activeAction(() -> {
         if (this.cycleGeneration == expected && this.stage != DoubleTap.Stage.Idle) {
            action.run();
         }
      });
      if (!rotating) {
         current.run();
         return DoubleTap.Turn.Queued;
      } else if (this.reactingTo != null) {
         this.turn.reset(TURN_OWNER);
         this.turn.wouldReach(TURN_OWNER, yaw, pitch, TURN_PRIORITY);
         return DoubleTap.Turn.Reacting;
      } else if (this.turn.heldByOther(TURN_OWNER)) {
         return this.cameraHolds(yaw, pitch) ? DoubleTap.Turn.Camera : DoubleTap.Turn.Busy;
      } else {
         boolean queued = settled ? this.turn.turnToSettled(TURN_OWNER, yaw, pitch, TURN_PRIORITY, current) : this.turn.turnTo(TURN_OWNER, yaw, pitch, TURN_PRIORITY, current);
         if (queued) {
            return DoubleTap.Turn.Queued;
         } else if (TurnProgress.cameraNeeded()) {
            return DoubleTap.Turn.Camera;
         } else {
            return this.turn.lastCallBlocked() ? DoubleTap.Turn.Busy : DoubleTap.Turn.Turning;
         }
      }
   }

   private boolean claimCrystal(boolean attack) {
      double cost = Stealth.actionCost();
      if (!this.crystalBudget.canAfford(cost)) {
         return false;
      } else if (attack ? Stealth.claimAttack() : Stealth.claimUse()) {
         this.crystalBudget.tryConsume(cost);
         return true;
      } else {
         return false;
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

   private class_243 predictedTargetPos(class_243 motion) {
      if ((Integer)this.leadTicks.get() <= 0) {
         return this.target.method_73189();
      } else {
         class_243 velocity = motion != null ? motion : this.target.method_18798();
         class_243 eyes = this.mc.field_1724.method_33571();
         double reach = this.placeReach() + 1.5;
         class_243[] path = Trajectory.path(this.target, velocity, (Integer)this.leadTicks.get());

         for (int i = path.length - 1; i >= 0; i--) {
            if (path[i].method_1022(eyes) <= reach) {
               return path[i];
            }
         }

         return this.target.method_73189();
      }
   }

   private class_243 expectedKnockback() {
      if (!this.hitDone) {
         return this.doHit.get() ? this.plannedKnockbackVelocity() : null;
      } else {
         int window = (Integer)this.leadTicks.get() + CrystalUtils.confirmTicks(EntityUtils.getPing(this.mc.field_1724));
         return this.hitVelocity != null && this.mc.field_1724.field_6012 - this.hitVelocityAge <= window ? this.hitVelocity : null;
      }
   }

   private class_243 spotMotion() {
      return this.predictKnockback.get() ? this.expectedKnockback() : null;
   }

   private class_1511 crystalAt(class_2338 base) {
      if (base == null) {
         return null;
      } else {
         class_243 expected = CrystalUtils.crystalPos(base);

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal
               && !crystal.method_31481()
               && !this.brokenCrystals.containsKey(crystal.method_5628())
               && crystal.method_73189().method_1025(expected) < 0.5) {
               return crystal;
            }
         }

         return null;
      }
   }

   private void placeObsidian(class_2338 pos) {
      if (!this.actionInView(class_243.method_24953(pos))) {
         this.sent = false;
         this.stall("the obsidian spot is outside max-place-angle (keep-everything-in-view)");
      } else {
         boolean rotating = this.shouldRotate();
         DoubleTap.Look camera = rotating
            ? this.cameraLook((y, p) -> LegitPlace.confirmPlacement(pos, class_1802.field_8281, y, p, VanillaLimits.blockRange()) != null)
            : null;
         double yaw;
         double pitch;
         if (camera != null) {
            yaw = camera.yaw();
            pitch = camera.pitch();
         } else if (rotating) {
            LegitPlace.Result r = LegitPlace.forBlock(pos, VanillaLimits.blockRange());
            if (r == null) {
               this.sent = false;
               this.stall("no face to place the obsidian against is in reach and view");
               return;
            }

            yaw = r.yaw();
            pitch = r.pitch();
         } else {
            yaw = LegitPlace.currentYaw();
            pitch = LegitPlace.currentPitch();
            if (LegitPlace.confirmPlacement(pos, class_1802.field_8281, yaw, pitch, VanillaLimits.blockRange()) == null) {
               this.sent = false;
               this.stall("your crosshair is not on a face that places the obsidian, and rotate is off");
               return;
            }
         }

         Runnable action = () -> {
            class_3965 hitResult = LegitPlace.confirmPlacement(pos, class_1802.field_8281, yaw, pitch, VanillaLimits.blockRange());
            if (hitResult == null) {
               this.sent = false;
               this.stall("the obsidian click no longer lands from here");
            } else if (!InvUtils.findInHotbar(new class_1792[]{class_1802.field_8281}).found()) {
               this.sent = false;
            } else {
               String refused = blockRefusal(hitResult.method_17777(), hitResult.method_17784());
               if (refused != null) {
                  this.sent = false;
                  this.stall("the obsidian spot is " + refused);
               } else if (!this.rightClick(class_1802.field_8281, hitResult, false)) {
                  this.sent = false;
               } else {
                  this.acted = true;
                  this.obsidianPlaced = true;
                  this.waited = 0;
               }
            }
         };
         DoubleTap.Turn result = this.turnTowards(rotating, yaw, pitch, action);
         if (result != DoubleTap.Turn.Queued) {
            this.sent = false;
         }

         this.notQueued(result);
      }
   }

   private boolean legacyRules() {
      return switch ((DoubleTap.Placement)this.placement.get()) {
         case Auto -> ServerVersion.needsLegacyCrystalPlacement();
         case Modern -> false;
         case Legacy -> true;
      };
   }

   private boolean occupied(class_2338 base, class_243 targetVelocity) {
      return this.obstructed(base.method_10084())
         || CrystalUtils.playerMovingInto(base.method_10084(), (Integer)this.lookahead.get(), this.target, targetVelocity);
   }

   private boolean obstructed(class_2338 above) {
      return !this.mc
         .field_1687
         .method_8333(null, CrystalUtils.crystalBox(above), class_1301.field_6155.and(entity -> !this.brokenCrystals.containsKey(entity.method_5628())))
         .isEmpty();
   }

   private int plannedWeaponSlot() {
      int selected = this.mc.field_1724.method_31548().method_67532();
      if ((Boolean)this.swapWeapon.get() && this.switchMode.get() != DoubleTap.SwitchMode.None) {
         int best = this.bestWeaponSlot();
         return best >= 0 ? best : selected;
      } else {
         return selected;
      }
   }

   private static boolean stabs(class_1799 stack) {
      return stack.method_57826(class_9334.field_63631);
   }

   private int hittingSlot() {
      int selected = this.mc.field_1724.method_31548().method_67532();
      if (!stabs(this.mc.field_1724.method_31548().method_5438(selected))) {
         return selected;
      } else if (this.switchMode.get() == DoubleTap.SwitchMode.None) {
         return -1;
      } else {
         int any = -1;

         for (int i = 0; i <= 8; i++) {
            class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
            if (!stabs(stack)) {
               if (stack.method_31574(class_1802.field_8301)) {
                  return i;
               }

               if (any == -1) {
                  any = i;
               }
            }
         }

         return any;
      }
   }

   private class_243 plannedKnockbackVelocity() {
      class_1799 weapon = this.mc.field_1724.method_31548().method_5438(this.plannedWeaponSlot());
      double yaw = LegitPlace.currentYaw();
      if (this.shouldRotate()) {
         DoubleTap.Look server = this.serverLook();
         boolean kept = server != null && LegitPlace.confirmEntity(this.target.method_5829(), server.yaw(), server.pitch(), this.hitReach()) != null;
         if (!kept) {
            yaw = Rotations.getYaw(this.target.method_5829().method_1005());
         }
      }

      return KnockbackPredictor.velocityAfterHit(this.target, KnockbackPredictor.strength(weapon), yaw);
   }

   private void placeCrystal(class_2338 base) {
      if (!this.actionInView(class_243.method_26410(base, 1.0))) {
         this.sent = false;
         this.stall("the obsidian is outside max-place-angle (keep-everything-in-view)");
      } else {
         boolean rotating = this.shouldRotate();
         DoubleTap.Look camera = rotating ? this.cameraLook((y, p) -> LegitPlace.confirmCrystal(base, y, p, VanillaLimits.blockRange()) != null) : null;
         double yaw;
         double pitch;
         if (camera != null) {
            yaw = camera.yaw();
            pitch = camera.pitch();
         } else if (rotating) {
            LegitPlace.Result initial = LegitPlace.forCrystal(base, VanillaLimits.blockRange());
            if (initial == null) {
               this.sent = false;
               this.stall("no face of the obsidian is in reach and view");
               return;
            }

            yaw = initial.yaw();
            pitch = initial.pitch();
         } else {
            yaw = LegitPlace.currentYaw();
            pitch = LegitPlace.currentPitch();
            if (LegitPlace.confirmCrystal(base, yaw, pitch, VanillaLimits.blockRange()) == null) {
               this.sent = false;
               this.stall("your crosshair is not on the obsidian, and rotate is off");
               return;
            }
         }

         Runnable action = () -> {
            if (this.occupied(base, this.spotMotion())) {
               this.sent = false;
            } else {
               class_3965 hitResult = LegitPlace.confirmCrystal(base, yaw, pitch, VanillaLimits.blockRange());
               if (hitResult == null) {
                  this.sent = false;
                  this.stall("the crystal click no longer lands from here");
               } else if (!InvUtils.findInHotbar(new class_1792[]{class_1802.field_8301}).found()) {
                  this.sent = false;
               } else {
                  String refused = blockRefusal(base, hitResult.method_17784());
                  if (refused != null) {
                     this.sent = false;
                     this.stall("the obsidian for the crystal is " + refused);
                  } else if (!this.rightClick(class_1802.field_8301, hitResult, true)) {
                     this.sent = false;
                  } else {
                     this.acted = true;
                     this.waited = 0;
                  }
               }
            }
         };
         DoubleTap.Turn result = this.turnTowards(rotating, yaw, pitch, action);
         if (result != DoubleTap.Turn.Queued) {
            this.sent = false;
         }

         this.notQueued(result);
      }
   }

   private void finish() {
      this.cycleGeneration++;
      if (this.returnSlot != -1) {
         if (this.mc.field_1724 != null
            && this.switchMode.get() == DoubleTap.SwitchMode.Hotbar
            && (Boolean)this.restoreSlot.get()
            && this.returnSlot != this.mc.field_1724.method_31548().method_67532()) {
            this.restoreTo = this.returnSlot;
            this.restoreFrom = this.mc.field_1724.method_31548().method_67532();
            this.restoreAt = this.ticks + Math.max(2, Stealth.pace(4));
         }

         this.returnSlot = -1;
      }

      this.enter(DoubleTap.Stage.Idle);
      this.obsidianPos = null;
      this.retries = 0;
      this.cameraWaited = 0;
      this.cameraHold = null;
      this.crystalsDone = 0;
      this.obsidianPlaced = false;
      this.settleTicks = 0;
      this.hitDone = false;
      this.hitVelocity = null;
      this.pruneBrokenCrystals();
   }

   private void restoreLater() {
      if (this.restoreTo != -1 && this.stage == DoubleTap.Stage.Idle && this.ticks >= this.restoreAt) {
         if (this.mc.field_1724.method_31548().method_67532() != this.restoreFrom || HotbarSwap.select(this.restoreTo)) {
            this.restoreTo = -1;
         }
      }
   }

   private boolean rightClick(class_1792 item, class_3965 hit, boolean crystal) {
      if (!this.clickAllowed()) {
         return false;
      } else if (!ClickGate.canUse()) {
         this.stall(clickBusy(false));
         return false;
      } else {
         DoubleTap.Held held = this.hold(item, hit);
         if (held == null) {
            this.stall("no hand a right click reaches could take the " + (crystal ? "crystal" : "obsidian") + " this tick");
            return false;
         } else if (switchedNow(held)) {
            return false;
         } else if (this.mc.field_1724.method_5998(held.hand()).method_31574(item) && (crystal ? this.claimCrystal(false) : Stealth.claimUse())) {
            VanillaClick.use(hit, (Boolean)this.swing.get());
            held.release();
            this.clicked();
            return true;
         } else {
            held.release();
            return false;
         }
      }
   }

   private static boolean switchedNow(DoubleTap.Held held) {
      if (!ClickGate.slotChangedThisTick()) {
         return false;
      } else {
         held.release();
         return true;
      }
   }

   private static String clickBusy(boolean attack) {
      if (ClickGate.slotChangedThisTick()) {
         return "the hotbar changed this tick, and a click waits a tick after a number key";
      } else {
         return attack ? "another click already went out this tick" : "another right click already went out this tick";
      }
   }

   private DoubleTap.Held hold(class_1792 item, class_3965 hit) {
      if (this.mc.field_1724.method_6047().method_31574(item)) {
         return HELD_MAIN;
      } else {
         boolean inOffhand = this.mc.field_1724.method_6079().method_31574(item);
         if (inOffhand && VanillaClick.reaches(hit, class_1268.field_5810)) {
            return new DoubleTap.Held(class_1268.field_5810, null);
         } else if (this.switchMode.get() == DoubleTap.SwitchMode.None) {
            return null;
         } else {
            FindItemResult slot = InvUtils.find(stack -> stack.method_31574(item), 0, 8);
            if (slot.found()) {
               return this.switchTo(slot.slot());
            } else if (!inOffhand) {
               return null;
            } else {
               int free = -1;
               if (this.returnSlot >= 0 && passesUse(this.mc.field_1724.method_31548().method_5438(this.returnSlot))) {
                  free = this.returnSlot;
               }

               for (int i = 0; free == -1 && i <= 8; i++) {
                  if (passesUse(this.mc.field_1724.method_31548().method_5438(i))) {
                     free = i;
                  }
               }

               if (free == -1) {
                  return null;
               } else {
                  DoubleTap.Held main = this.switchTo(free);
                  if (main == null) {
                     return null;
                  } else if (!VanillaClick.reaches(hit, class_1268.field_5810)) {
                     main.release();
                     return null;
                  } else {
                     return new DoubleTap.Held(class_1268.field_5810, main.silent());
                  }
               }
            }
         }
      }
   }

   private DoubleTap.Held switchTo(int slot) {
      if (slot == this.mc.field_1724.method_31548().method_67532()) {
         return HELD_MAIN;
      } else if (this.switchMode.get() == DoubleTap.SwitchMode.Silent) {
         HotbarSwap swap = HotbarSwap.silently(slot);
         return swap.ready() ? new DoubleTap.Held(class_1268.field_5808, swap) : null;
      } else {
         return HotbarSwap.select(slot) ? HELD_MAIN : null;
      }
   }

   private boolean inReachableHand(class_1792 item) {
      return this.mc.field_1724.method_6047().method_31574(item)
         ? true
         : this.mc.field_1724.method_6079().method_31574(item) && passesUse(this.mc.field_1724.method_6047());
   }

   private static boolean passesUse(class_1799 stack) {
      return stack.method_7960() || stack.method_31573(class_3489.field_42611) || stack.method_57826(class_9334.field_54274);
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
      } else if (this.cameraHold != null) {
         return this.stage + " (" + this.cameraHold.info + ")";
      } else if (this.stage != DoubleTap.Stage.Crystal && this.stage != DoubleTap.Stage.Break) {
         if (this.stage == DoubleTap.Stage.Gap && this.timing.get() == DoubleTap.Timing.Fixed) {
            return "gap " + Math.max(0, (this.paced >= 0 ? this.paced : (Integer)this.crystalGap.get()) - this.waited);
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

   private record BackInRange(long cycle, int crystal) {
   }

   private static enum CameraHold {
      Moving("moving", "you kept moving, and a look other than your camera's only goes out while no movement key is held"),
      Gliding("gliding", "you kept gliding, and while you glide the look is your flight path"),
      OwnClick("own click", "a click of your own kept waiting, and it goes out along your crosshair first"),
      CrosshairClick("crosshair click", "a click along your crosshair (Sword Place) kept waiting, and it goes out along your camera's look first");

      final String info;
      final String gaveUp;

      private CameraHold(String info, String gaveUp) {
         this.info = info;
         this.gaveUp = gaveUp;
      }
   }

   static enum Filter {
      NoBlock,
      NoRoom,
      OutOfReach,
      OutOfView,
      Hidden,
      BehindYou,
      TooClose,
      Lethal,
      NoClick;
   }

   private record ForeignCrystal(int id) {
   }

   private record Held(class_1268 hand, HotbarSwap silent) {
      void release() {
         if (this.silent != null) {
            this.silent.back();
         }
      }
   }

   static final class HitAim {
      private static final double MIN_JERK_PEAK = 1.875;
      private DoubleTap.Look aimed;
      private int turning;
      private int allowance = -1;

      void reset() {
         this.aimed = null;
         this.turning = 0;
         this.allowance = -1;
      }

      DoubleTap.Look choose(DoubleTap.Look server, BiPredicate<Double, Double> lands, Supplier<DoubleTap.Look> fresh) {
         if (server != null && lands.test(server.yaw(), server.pitch())) {
            return server;
         } else if (this.aimed != null && lands.test(this.aimed.yaw(), this.aimed.pitch())) {
            return this.aimed;
         } else {
            this.aimed = fresh.get();
            return this.aimed;
         }
      }

      static DoubleTap.Look preferMiddle(DoubleTap.Look middle, BiPredicate<Double, Double> lands, Supplier<DoubleTap.Look> other) {
         return middle != null && lands.test(middle.yaw(), middle.pitch()) ? middle : other.get();
      }

      boolean waitedTooLong(int turnTicks, int patience) {
         if (this.allowance < 0) {
            this.allowance = Math.max(1, turnTicks) + patience;
         }

         return ++this.turning > this.allowance;
      }

      static int turnTicks(double distance, double cap, double smooth) {
         if (!(cap <= 0.0) && !(distance <= cap)) {
            double straight = Math.ceil(distance / cap);
            return Math.max(1, (int)Math.ceil(straight * (1.0 + 0.875 * smooth)));
         } else {
            return 1;
         }
      }
   }

   record Look(double yaw, double pitch) {
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

   static final class Rejections {
      private final int[] counts = new int[DoubleTap.Filter.values().length];

      void add(DoubleTap.Filter filter) {
         this.counts[filter.ordinal()]++;
      }

      int count(DoubleTap.Filter filter) {
         return this.counts[filter.ordinal()];
      }

      DoubleTap.Filter named() {
         DoubleTap.Filter[] filters = DoubleTap.Filter.values();

         for (int i = filters.length - 1; i >= 0; i--) {
            if (this.counts[i] > 0) {
               return filters[i];
            }
         }

         return null;
      }
   }

   private record Spot(class_2338 pos, boolean isBase, float damage) {
      double score() {
         return this.damage + (this.isBase ? 0.001 : 0.0);
      }
   }

   private record SpotBlocked(long cycle, class_2338 spot) {
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

   private static enum Turn {
      Queued,
      Turning,
      Reacting,
      Busy,
      Camera;
   }
}
