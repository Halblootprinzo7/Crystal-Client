package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import net.minecraft.class_2246;
import net.minecraft.class_2885;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import dev.crystaladdon.utils.KeyPriority;
import dev.crystaladdon.utils.ActionBudget;
import dev.crystaladdon.utils.AimUtils;
import dev.crystaladdon.utils.BlastShield;
import dev.crystaladdon.utils.ClickGate;
import dev.crystaladdon.utils.CrystalScore;
import dev.crystaladdon.utils.CrystalUtils;
import dev.crystaladdon.utils.DamageWindow;
import dev.crystaladdon.utils.HotbarSwap;
import dev.crystaladdon.utils.InventoryGuard;
import dev.crystaladdon.utils.KnockbackPredictor;
import dev.crystaladdon.utils.LegitPlace;
import dev.crystaladdon.utils.ReactionClock;
import dev.crystaladdon.utils.RevivedPlayers;
import dev.crystaladdon.utils.ServerVersion;
import dev.crystaladdon.utils.Trajectory;
import dev.crystaladdon.utils.TurnProgress;
import dev.crystaladdon.utils.VanillaClick;
import dev.crystaladdon.utils.VanillaLimits;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.systems.modules.world.PacketMine;
import meteordevelopment.meteorclient.systems.modules.world.VeinMiner;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1893;
import net.minecraft.class_1934;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_2879;
import net.minecraft.class_310;
import net.minecraft.class_3489;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_5134;
import net.minecraft.class_642;
import net.minecraft.class_6862;
import net.minecraft.class_9285;
import net.minecraft.class_9334;
import net.minecraft.class_1322.class_1323;
import net.minecraft.class_239.class_240;

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
   private final Setting<Integer> maxTargets = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-targets"))
                  .description(
                     "How many players in range the aura tries, in priority order. When the first gives it nothing to place or hit - behind a wall, too high, no spot worth it - the next one gets a look, instead of the aura standing still in a fight with several players."
                  ))
               .defaultValue(3))
            .range(1, 5)
            .sliderRange(1, 5)
            .build()
      );
   private final Setting<Boolean> predictMovement = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-movement"))
                  .description(
                     "Judge a crystal by where the target will stand when it goes off, not where the client draws them: other players are drawn a few ticks behind the position the server last sent, which is itself half a round trip old, and a placed crystal only explodes after it has spawned and been hit. Starts from the server's position, carries their pace on - walls and the floor stop it - and ignores a teleport."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> maxPredictTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("max-predict-ticks"))
                     .description(
                        "Cap on how far ahead the target's movement is carried on. The look-ahead follows your ping; a strafing target turns around, so further than a few ticks is guessing."
                     ))
                  .defaultValue(4))
               .range(0, 10)
               .sliderRange(0, 10)
               .visible(this.predictMovement::get))
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
                     "Hotbar really moves your selection onto the crystal. Silent goes back to the slot you had by itself, a few quiet ticks after the exchange is over - never in the tick of a click, the way a number key cannot either. With Stealth's same-tick-switch on (the default) the first click with the crystals may follow the switch in the same tick, as a number key and a click within one tick do in vanilla; off, the crystals are selected a tick before it."
                  ))
               .defaultValue(AutoCrystal.SwitchMode.Silent))
            .build()
      );
   private final Setting<Boolean> returnToWeapon = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("return-to-sword"))
                     .description(
                        "Go back to your sword once the aura has had nothing to do for Stealth's reaction-time - the target dead or gone is something you see happen, not something you answer in the same tick. Only while the selection is still where the aura left it: a slot you picked yourself meanwhile stays. Only Hotbar switch-mode needs this - Silent goes back on its own."
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
                  .description(
                     "Face the crystal before placing or breaking it. Off, the aura only acts where your own view already reaches: a crystal is placed or hit only when the ray from your eyes along the rotation the server has really lands on it. Aim mode Crosshair never turns to place; it turns only to hit a crystal the server's current look does not land on, and only while also-limit-breaking is off."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description(
                     "Render the hand swing client side. Off only hides the animation: the swing packet goes out exactly where vanilla's would either way."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> noMiningBases = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("no-mining-bases")
            .description(
               "A weapon or crystals cannot usefully mine obsidian or bedrock, so a left click on a crystal base with one of them in hand only swings instead of starting to mine it. Mining would pause placing for as long as it goes on. Hold a pickaxe to mine a base. Steps aside while Auto Tool, Packet Mine, Instant Rebreak or Vein Miner is on: they start from that very click."
            )
            .defaultValue(true)
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
                  .description(
                     "Placements per second, 1 to 50. Up to 20 that is one right click a tick at most; above 20 a click may follow another in the same tick - a hit and the new crystal on that obsidian. A slot switch and then the placement already share a tick at any speed with Stealth's same-tick-switch on. Stealth's max-actions-per-second caps all clicks together, raise it along with this."
                  ))
               .defaultValue(12.0)
               .range(1.0, 50.0)
               .sliderRange(1.0, 50.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Double> placeRange = this.sgPlace
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("place-range"))
                  .description(
                     "Maximum distance to place a crystal at, never beyond the block interaction range the server gives you (4.5 normally). Measured along the ray from your eyes to the face that gets clicked - a face nothing but that ray reaches, so there is no placing through a wall or around a corner."
                  ))
               .defaultValue(4.5)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.place::get))
            .build()
      );
   private final Setting<Boolean> crosshairPriority = this.sgPlace
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("crosshair-priority")
            .description(
               "Obsidian or bedrock under your crosshair is crystalled first whenever a crystal fits there, only needing face-place-min-damage on the target instead of min-damage, and without waiting out the reaction time: you are looking at it because that is where you want the crystal. Self-damage limits still apply. Off, only obsidian you placed yourself in the last two seconds is treated that way."
            )
            .defaultValue(true)
            .visible(this.place::get)
            .build()
      );
   private final Setting<Boolean> farPlace = this.sgPlace
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("far-place")
            .description(
               "Also place on obsidian that is in place reach (4.5) but where the crystal would stand beyond hit reach (3.0). Nobody can hit a crystal that far away: it explodes only once you walk within 3 blocks of it. Spots you can hit right away are always taken first, and no second far crystal is put down while one already stands out of reach."
            )
            .defaultValue(false)
            .visible(this.place::get)
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
   private final Setting<Double> selfDamageWeight = this.sgPlace
      .add(
         ((Builder)((Builder)new Builder().name("self-damage-weight"))
               .description(
                  "How hard your own damage counts when ranking spots and crystals: the score is the target's damage minus this times yours. 0 picks the hardest hit whatever it costs you, 1 counts a point of your health like one of theirs. A hit that kills or pops the target with a quarter to spare ranks first - their Resistance is invisible here - even below min-damage; among those the surer kill, then the cheaper one for you."
               ))
            .defaultValue(0.5)
            .min(0.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Boolean> allowSelfPop = this.sgPlace
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("allow-self-pop"))
                  .description(
                     "Keep crystalling when a crystal would take all your health while a totem is in your hand: it pops the totem instead of killing you. Off, the aura stops at that point, the way it did - right when you are about to pop and the exchange matters most. Without a totem in a hand such crystals are always refused, and max-self-damage still applies."
                  ))
               .defaultValue(true))
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
                  .description("Hits per second, 1 to 50. Up to 20 that is one hit a tick at most; above 20 a hit may also follow a right click, and a slot switch may follow the hit, in the same tick. A switch right before the hit already shares its tick at any speed with Stealth's same-tick-switch on. Stealth's max-actions-per-second caps all clicks together."))
               .defaultValue(12.0)
               .range(1.0, 50.0)
               .sliderRange(1.0, 50.0)
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
   private final Setting<Double> breakRange = this.sgBreak
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("break-range"))
                  .description(
                     "Maximum distance to hit a crystal at, never beyond the entity interaction range the server gives you (3.0 normally), measured along the ray from your eyes to the point of the hitbox it lands on. A crystal is an entity, so vanilla stops at getEntityInteractionRange - not at the 4.5 that applies to blocks."
                  ))
               .defaultValue(3.0)
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
   private final Setting<Boolean> antiWeakness = this.sgBreak
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("anti-weakness"))
                     .description(
                        "Under Weakness the crystal in your hand deals no damage, and a hit that deals none does not break the crystal. Select the hotbar weapon that still does - Stealth's reaction-time after the Weakness arrived, the moment a person would notice - and hit with it a tick later or more. The server only counts a weapon's damage from its first tick in your hand, so this swap is visible even in Silent; Silent goes back to your slot once the exchange is over. A spear in hand gets the same: it stabs instead of hitting. Nothing happens with switch-mode None."
                     ))
                  .defaultValue(true))
               .visible(this.doBreak::get))
            .build()
      );
   private final Setting<Integer> replaceDelay = this.sgBreak
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("replace-delay")
            .description(
               "Ticks after a crystal is hit before the next one goes on that obsidian - once the client no longer shows the old crystal in the way, as for any player. 0 places in the very same tick as the hit, along the same look and right through the crystal still drawn there, whenever the crystals are already in hand (or with place-speed above 20): faster than any vanilla client, and easier for an anticheat to notice."
            )
            .defaultValue(1)
            .range(0, 5)
            .sliderRange(0, 5)
            .visible(this.doBreak::get)
            .build()
      );
   private final Setting<Boolean> smartDelay = this.sgBreak
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("smart-delay"))
                     .description(
                        "Respect the target's damage window: for 10 ticks after a hit only the part of a new hit above the last one lands. After your own crystal, sword hit or a crystal you broke by hand the aura knows both the timing and the damage itself, a round trip before the client shows it: inside the window it only hits a crystal of yours that still adds something on top, or anyone else's that adds min-break-damage. After a hit from elsewhere it waits the window out. Off, crystals go off as soon as they can, which looks faster, but one inside the window deals only what it adds over the last hit - often nothing - so more crystals do not mean more damage."
                     ))
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
                     .description(
                        "Apply the same restriction to breaking, not just placing. In Crosshair mode, on: a crystal is only hit while your crosshair is on it, along the look the server has (the aura asks for your camera's look back if another rotation still holds it). Off: with rotate or Stealth force-rotate on, the aura may also turn the server-side look to hit any crystal within hit reach and the view angle that the current look misses; placing still only goes where you point."
                     ))
                  .defaultValue(false))
               .visible(() -> this.aimMode.get() != AutoCrystal.AimMode.Off))
            .build()
      );
   private final Setting<Boolean> heightFilter = this.sgHeight
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("require-higher-target"))
                  .description(
                     "Only place while the target is above you, going by the position the server last sent for them. Crystals already standing are still broken; otherwise the aura idles and the module stays on."
                  ))
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
                  .description(
                     "Ignore the damage requirement once the target is low. A drop in their health is something you see happen, so the aura goes by it only after Stealth's reaction-time."
                  ))
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
   private final AutoCrystal.HotbarLoan loan = new AutoCrystal.HotbarLoan();
   private static final Object TURN_OWNER = new Object();
   private final TurnProgress turn = TurnProgress.SHARED;
   private boolean workAvailable;
   private double renderDamage;
   private long lastDebugLog;
   private String idleReason;
   private boolean turnBusy;
   private boolean turnHeldForYou;
   private String placeSkip;
   private String breakSkip;
   private boolean weaponSwapped;
   private boolean weaponWaits;
   private boolean placeWaiting;
   private final AutoCrystal.Reacting<class_1657> reacting = new AutoCrystal.Reacting<>();
   private final Map<Integer, AutoCrystal.OwnHit> ownHits = new HashMap<>();
   private List<class_1657> candidates = List.of();
   private final Map<Integer, ArrayDeque<class_243>> trackedHistory = new HashMap<>();
   private static final double TELEPORT_SQ = 9.0;
   private List<class_2338> scanBases;
   private final Map<class_2338, Optional<AutoCrystal.Aim>> placeAimCache = new HashMap<>();
   private final Map<Integer, Optional<AutoCrystal.Aim>> hitAimCache = new HashMap<>();
   private final Map<class_2338, Boolean> hittableCache = new HashMap<>();
   private final Map<class_243, Float> selfDamageCache = new HashMap<>();
   private final Map<Integer, AutoCrystal.TargetFigures> figuresByTarget = new HashMap<>();
   private AutoCrystal.TargetFigures figures = new AutoCrystal.TargetFigures();
   private class_1657 cachedFor;
   private String loggedState;
   private String candidateState;
   private int candidateTicks;
   private final ActionBudget placeBudget = new ActionBudget();
   private final ActionBudget breakBudget = new ActionBudget();
   private final Set<class_2338> usedPositions = new HashSet<>();
   private final Set<Integer> hitCrystals = new HashSet<>();
   private final AutoCrystal.InFlight<Integer> attacked = new AutoCrystal.InFlight<>();
   private final AutoCrystal.InFlight<class_2338> placed = new AutoCrystal.InFlight<>();
   // Base -> {tick of the first click on it, clicks on it since}, kept longer than `placed`: a crystal whose spawn
   // arrives after the short in-flight window is still recognised as ours (no reaction wait, no min-break-damage)
   // instead of a stranger's.
   private final Map<class_2338, int[]> recentPlaces = new HashMap<>();
   // Ticks from a click to its crystal showing up, as last measured; slowly forgotten. The tab-list ping is updated
   // rarely, smoothed, and 0 without a tab entry, so on its own it can make the in-flight windows too short.
   private double spawnLag;
   private boolean sendingOwnHit;
   private boolean mining;
   private boolean scoredReady;
   private final ReactionClock reactions = new ReactionClock();
   private final AutoCrystal.OwnCrystals<class_2338> ownCrystals = new AutoCrystal.OwnCrystals<>();
   private final AutoCrystal.CrystalsSeen<class_2338> crystalsSeen = new AutoCrystal.CrystalsSeen<>();
   private final AutoCrystal.WorthMemory worth = new AutoCrystal.WorthMemory();
   private final Map<class_2338, Integer> hitBases = new HashMap<>();
   private final Map<class_2338, Integer> ownBases = new HashMap<>();
   private static final int OWN_BASE_TICKS = 40;
   private class_2338 replaceBase;
   private AutoCrystal.Aim replaceLook;
   private int replaceTick = Integer.MIN_VALUE;
   private final Map<Integer, AutoCrystal.SeenHealth> seenHealth = new HashMap<>();
   private int clientTicks;
   private final Predicate<class_1297> goneWhenPlanned = entity -> entity instanceof class_1511
      && (this.attacked.contains(entity.method_5628()) || this.hitCrystals.contains(entity.method_5628()));
   private final Predicate<class_1297> goneWhenSent = entity -> entity instanceof class_1511 && this.attacked.contains(entity.method_5628());
   private long tickNanos;
   private long worstTickNanos;
   private int timedTicks;
   private int slotAfterModules = -1;
   private float scoredDamage;

   public AutoCrystal() {
      super(CrystalAddon.CATEGORY, "auto-crystal", "Places and breaks end crystals on the best target.");
   }

   public void onDeactivate() {
      this.ownHits.clear();
      this.trackedHistory.clear();
      this.candidates = List.of();
      this.slotAfterModules = -1;
      if (this.loan.active()
         && this.mc.field_1724 != null
         && this.switchMode.get() != AutoCrystal.SwitchMode.None
         && this.loan.untouched(HotbarSwap.homeSlot())) {
         HotbarSwap.selectLater(this.loan.home(), this.mc.field_1724.method_31548().method_67532(), Stealth.reactionTicks());
      }

      this.loan.clear();
      this.turn.reset(TURN_OWNER);
      this.reactions.clear();
      this.ownCrystals.clear();
      this.crystalsSeen.clear();
      this.worth.clear();
      this.hitBases.clear();
      this.ownBases.clear();
      this.seenHealth.clear();
      this.loggedState = null;
      this.candidateState = null;
      this.workAvailable = false;
      this.weaponWaits = false;
      this.placeWaiting = false;
      this.reacting.reset();
      this.target = null;
      this.renderPos = null;
      this.renderDamage = 0.0;
      this.placeBudget.reset();
      this.breakBudget.reset();
      this.usedPositions.clear();
      this.hitCrystals.clear();
      this.attacked.clear();
      this.placed.clear();
      this.recentPlaces.clear();
      this.spawnLag = 0.0;
      this.hittableCache.clear();
      this.mining = false;
   }

   @EventHandler(
      priority = 9500
   )
   private void onTickStart(Pre event) {
      if (this.isActive() && this.mc.field_1724 != null) {
         if (this.slotAfterModules >= 0 && this.mc.field_1724.method_31548().method_67532() != this.slotAfterModules) {
            this.loan.clear();
         }
      }
   }

   @EventHandler(
      priority = -10000
   )
   private void onModulesDone(Pre event) {
      this.slotAfterModules = this.isActive() && this.mc.field_1724 != null ? this.mc.field_1724.method_31548().method_67532() : -1;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.isActive()) {
         long started = System.nanoTime();
         // replace-delay 0: crystals already hit are gone on the server before the next click arrives, so aiming does not
         // stop at them. Otherwise a crystal still drawn in the way blocks the click as it would for any player.
         if ((Integer)this.replaceDelay.get() == 0) {
            LegitPlace.passingThrough(this.goneWhenPlanned, () -> {
               this.tick();
               return null;
            });
         } else {
            this.tick();
         }
         long spent = System.nanoTime() - started;
         this.tickNanos += spent;
         this.worstTickNanos = Math.max(this.worstTickNanos, spent);
         if (++this.timedTicks >= 200) {
            double average = this.tickNanos / 1000000.0 / this.timedTicks;
            if ((Boolean)this.stateLog.get() && average > 2.0) {
               CrystalAddon.LOG
                  .info(
                     "[AutoCrystal] performance: {} ms per tick on average, worst {} ms, {} target(s), {} base(s)",
                     new Object[]{
                        String.format("%.2f", average),
                        String.format("%.1f", this.worstTickNanos / 1000000.0),
                        this.candidates.size(),
                        this.scanBases == null ? 0 : this.scanBases.size()
                     }
                  );
            }

            this.tickNanos = 0L;
            this.worstTickNanos = 0L;
            this.timedTicks = 0;
         }
      }
   }

   private void tick() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         if (this.sessionChanged()) {
            this.loan.clear();
            this.onDeactivate();
         }

         this.clientTicks++;
         this.ownBases.values().removeIf(at -> this.clientTicks - at > OWN_BASE_TICKS);
         this.reactions.tick();
         this.settleWorth();
         this.noteWeakness();
         int tabWindow = CrystalUtils.confirmTicks(EntityUtils.getPing(this.mc.field_1724));
         // A measured lag is forgotten at one tick per second, so one spike does not keep every window long.
         this.spawnLag = Math.max(0.0, this.spawnLag - 0.05);
         int window = Math.min(20, Math.max(tabWindow, (int)Math.ceil(this.spawnLag) + 1));
         // Long enough for a late spawn to still count as ours, short enough that a click the server refused does not
         // claim an opponent's crystal on that base much later.
         int adoptFor = Math.min(20, Math.max(10, 2 * window + 2));
         this.recentPlaces.values().removeIf(clicks -> this.clientTicks - clicks[0] > adoptFor);
         Set<Integer> brokenByUs = this.attacked.expire(this.clientTicks, window, id -> {
            class_1297 crystal = this.mc.field_1687.method_8469(id);
            return crystal == null || crystal.method_31481();
         });
         this.trackOwnWork(brokenByUs, tabWindow);
         this.placed.expire(this.clientTicks, window, base -> CrystalUtils.isObstructed(base.method_10084(), this.goneWhenSent));
         if (!Stealth.paused() && !this.mc.field_1724.method_29504()) {
            this.dropStaleMining();
            this.mining = this.mc.field_1761.method_2923();
            this.placeBudget.update((Double)this.placeSpeed.get(), ClickGate.perTick((Double)this.placeSpeed.get()));
            this.breakBudget.update((Double)this.breakSpeed.get(), ClickGate.perTick((Double)this.breakSpeed.get()));
            ClickGate.allowBurst(ClickGate.perTick(Math.max((Double)this.placeSpeed.get(), (Double)this.breakSpeed.get())));
            this.renderPos = null;
            this.renderDamage = 0.0;
            this.usedPositions.clear();
            this.hitCrystals.clear();
            this.workAvailable = false;
            this.turnBusy = false;
            this.turnHeldForYou = false;
            this.idleReason = null;
            this.placeSkip = null;
            this.breakSkip = null;
            this.weaponSwapped = false;
            this.weaponWaits = false;
            this.placeWaiting = false;
            this.reacting.reset();
            this.selfDamageCache.clear();
            this.figuresByTarget.clear();
            this.figures = new AutoCrystal.TargetFigures();
            this.cachedFor = null;
            this.scanBases = null;
            this.placeAimCache.clear();
            this.hitAimCache.clear();
            this.hittableCache.clear();
            this.candidates = List.of();
            this.recordTrackedPositions();
            this.ownHits.values().removeIf(hit -> this.clientTicks - hit.tick() > 50);
            String busy = this.busyReason();
            if (busy != null) {
               this.reactions.forget(AutoCrystal.Noticed.QUIET);
               this.logState("paused", () -> busy);
            } else {
               this.candidates = this.findTargets();
               if (this.candidates.isEmpty()) {
                  this.target = null;
                  this.workAvailable = false;
                  this.idleReason = this.ignoredPlayerReason();
                  this.turn.reset(TURN_OWNER);
                  String why = this.idleReason != null ? this.idleReason : "nobody in range";
                  this.logState("no-target", () -> why);
                  this.handBack(true);
               } else {
                  List<class_1657> inHeight = new ArrayList<>();

                  for (class_1657 candidate : this.candidates) {
                     this.aimAt(candidate);
                     if (this.heightOk()) {
                        inHeight.add(candidate);
                     }
                  }

                  class_1657 primary = inHeight.isEmpty() ? null : inHeight.get(0);
                  AutoCrystal.BreakOutcome breakOutcome = AutoCrystal.BreakOutcome.NOTHING;
                  if ((Boolean)this.doBreak.get()) {
                     // The height filter only holds back new crystals. One already standing is broken whatever the
                     // target's height, or it stays until the next knock-up and blocks every base around it.
                     for (class_1657 candidatex : this.candidates) {
                        this.aimAt(candidatex);
                        breakOutcome = this.tryBreak();
                        if (breakOutcome != AutoCrystal.BreakOutcome.NOTHING && breakOutcome != AutoCrystal.BreakOutcome.HELD) {
                           break;
                        }
                     }
                  }

                  if ((Boolean)this.place.get() && breakOutcome != AutoCrystal.BreakOutcome.TURNING && breakOutcome != AutoCrystal.BreakOutcome.SWAPPED) {
                     if (this.mining) {
                        // Vanilla sends no right click while a block is being mined; hits still go out.
                        this.placeSkip = "mining a block - no right click goes out until it stops";
                     } else {
                        for (class_1657 candidatexx : inHeight) {
                           this.aimAt(candidatexx);
                           if (this.tryPlace() || this.placeSkip != null || this.turnBusy) {
                              break;
                           }
                        }
                     }
                  }

                  if (primary == null && !this.workAvailable) {
                     this.aimAt(this.candidates.get(0));
                     this.turn.reset(TURN_OWNER);
                     this.handBack(false);
                     String name = this.target.method_5477().getString();
                     this.logState("height", () -> name + " is outside the height filter");
                  } else {
                     // Here primary is only null when a crystal is being broken for a target outside the height
                     // filter; the target stays the one that break was judged for.
                     if (!this.workAvailable) {
                        this.aimAt(primary);
                     }

                     if (!this.attacked.isEmpty() || !this.placed.isEmpty()) {
                        this.workAvailable = true;
                     }

                     if (this.workAvailable) {
                        this.reactions.forget(AutoCrystal.Noticed.QUIET);
                        // Not while you mine: a different stack in hand restarts the block (see handBack).
                        if (!this.turnHeldForYou && !this.turnBusy && !this.mining) {
                           this.keepCrystalsOut();
                        }
                     } else {
                        this.handBack(false);
                     }

                     String name = this.target.method_5477().getString();
                     class_1657 waitingFor = this.reacting.shown();
                     if (this.turnHeldForYou) {
                        this.logState(
                           "turn-yours", () -> name + ": waiting for you - no look but the camera's goes out while you walk, glide or click yourself"
                        );
                     } else if (this.turnBusy) {
                        this.logState("turn-busy", () -> name + ": another module holds the shared rotation");
                     } else if (waitingFor != null) {
                        String who = waitingFor.method_5477().getString();
                        this.logState("reacting", () -> who + ": waiting out Stealth's reaction-time");
                     } else if (this.workAvailable) {
                        this.logState("working", () -> name);
                     } else if (this.breakSkip != null) {
                        this.logState("no-spot:" + this.breakSkip, () -> name + ": " + this.breakSkip);
                     } else if (this.placeSkip != null) {
                        this.logState("no-spot:" + this.placeSkip, () -> name + ": " + this.placeSkip);
                     } else if ((Boolean)this.place.get()) {
                        this.logState("no-spot", () -> name + ": " + this.placementReport());
                     } else {
                        this.logState("no-spot", () -> name + ": place is off and nothing to break");
                     }

                     if ((Boolean)this.debug.get() && (Boolean)this.place.get() && this.renderPos == null && primary != null) {
                        this.logWhyNoPlacement();
                     }
                  }
               }
            }
         } else {
            this.turn.reset(TURN_OWNER);
            this.reactions.forget(AutoCrystal.Noticed.QUIET);
            this.target = null;
            this.renderPos = null;
            this.renderDamage = 0.0;
            this.reacting.reset();
            this.idleReason = this.mc.field_1724.method_29504() ? "paused (dead)" : "paused (screen open)";
         }
      }
   }

   private String busyReason() {
      if (KeyPriority.active()) {
         return "a key-driven module is at work";
      } else if (this.mc.field_1724.method_5765()) {
         return "riding";
      } else {
         // Mining is no full pause: vanilla's attack press has no mining check, only the use click waits for it
         // (see the place loop in tick()).
         return this.mc.field_1724.method_6115() ? "using an item" : null;
      }
   }

   // A left click on a block starts mining it, and vanilla only lets go of it later in this tick, when handleInputEvents
   // finds the button up. Until then the client counts as mining and no right click may go out, so the aura would lose
   // this tick to a click that has already ended. This is vanilla's own abort, only done before the aura acts; the
   // conditions are the ones under which handleBlockBreaking(false) gets to it.
   private void dropStaleMining() {
      if (this.mc.field_1761.method_2923()
         && !this.mc.field_1690.field_1886.method_1434()
         && this.mc.method_18506() == null
         && !this.mc.field_1724.method_6115()
         && !stabs(this.mc.field_1724.method_6047())) {
         this.mc.field_1761.method_2925();
      }
   }

   // no-mining-bases, read by OwnClickMixin before vanilla starts or continues mining the block under the crosshair:
   // a weapon or crystals in hand cannot usefully mine obsidian or bedrock, so the left click only swings.
   public static boolean sparesBase(class_310 mc) {
      AutoCrystal module = Modules.get() == null ? null : (AutoCrystal)Modules.get().get(AutoCrystal.class);
      if (module == null || !module.isActive() || !(Boolean)module.noMiningBases.get()) {
         return false;
      } else if (minesFromClick()) {
         // These start from the attack press itself (Meteor posts it from the start of attackBlock): left out, Auto
         // Tool never picks the pickaxe and Packet Mine never queues the block, and a surround could not be mined.
         return false;
      } else if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1724.method_68878()) {
         return false;
      } else if (mc.field_1765 instanceof class_3965 hit && hit.method_17783() == class_240.field_1332 && CrystalUtils.isBase(hit.method_17777())) {
         class_1799 hand = mc.field_1724.method_6047();
         return hand.method_31573(class_3489.field_42611)
            || hand.method_31573(class_3489.field_42612)
            || hand.method_31574(class_1802.field_49814)
            || hand.method_31574(class_1802.field_8547)
            || hand.method_31574(class_1802.field_8301);
      } else {
         return false;
      }
   }

   private static boolean minesFromClick() {
      Modules modules = Modules.get();
      return modules.isActive(AutoTool.class)
         || modules.isActive(PacketMine.class)
         || modules.isActive(InstantRebreak.class)
         || modules.isActive(VeinMiner.class);
   }

   private int ownStep() {
      int ticks = (Integer)this.replaceDelay.get();
      return Math.max(ticks, Stealth.pace(ticks));
   }

   private void trackOwnWork(Set<Integer> brokenByUs, int tabWindow) {
      for (class_2338 base : this.placed.keys()) {
         this.reactions.saw(base);
      }

      // Bases still in flight, and those clicked a little longer ago: a spawn that arrives after the in-flight window
      // is still our crystal, not a stranger's to react to.
      Set<class_2338> clicked = new HashSet<>(this.recentPlaces.keySet());
      clicked.addAll(this.placed.keys());

      for (class_2338 base : clicked) {
         class_2338 at = base.method_10084();

         // A crystal already hit is the old one still drawn after a replace click, not the one that click put there:
         // adopting it would use up the base's entry before our real crystal shows up.
         Predicate<class_1511> fresh = c -> !c.method_31481() && c.method_24515().equals(at) && !this.attacked.contains(c.method_5628());

         for (class_1511 crystal : this.mc.field_1687.method_8390(class_1511.class, new class_238(at), fresh)) {
            // Hittable the tick it shows up. You placed it to hit it; a player spamming the attack button lands the
            // first tick it exists, which is what a d-tap needs - the old two-to-three tick wait let the window close.
            if (this.ownCrystals.appeared(crystal.method_5628(), base, this.clientTicks)) {
               this.worth.placedForIt(crystal.method_5628());
               int[] clicks = this.recentPlaces.remove(base);
               if (clicks != null && clicks[1] == 1) {
                  // Only a base clicked once says how long a spawn takes: after a click the server refused, the one it
                  // took came later and the first would make the lag look longer. A sample still never exceeds about
                  // twice what the tab ping says, so one server hiccup does not hold every window long.
                  this.spawnLag = Math.max(this.spawnLag, Math.min(this.clientTicks - clicks[0], 2 * tabWindow + 2));
               }
            }
         }
      }

      Set<class_2338> standing = new HashSet<>();
      Set<Integer> ownGone = new HashSet<>();
      this.ownCrystals.sweep(id -> {
         class_1297 crystalx = this.mc.field_1687.method_8469(id);
         return crystalx == null || crystalx.method_31481();
      }, standing::add, ownGone::add);
      this.hitBases.values().removeIf(until -> this.clientTicks >= until);
      Map<Integer, class_2338> crystals = new HashMap<>();

      for (class_1297 entity : this.mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystalx && !crystalx.method_31481()) {
            crystals.put(crystalx.method_5628(), crystalx.method_24515().method_10074());
         }
      }

      // A crystal of ours that went - broken by someone else first, caught in a chain explosion, or its removal seen
      // only after the hit's window - changes nothing around its base that you did not expect: it stood there because
      // you put it there. Only a stranger's crystal vanishing resets the reaction time around it.
      this.crystalsSeen.update(crystals, id -> brokenByUs.contains(id) || ownGone.contains(id), base -> this.forgetAround(base, standing));

      for (class_2338 base : standing) {
         this.reactions.saw(base);
      }

      for (class_2338 base : this.hitBases.keySet()) {
         this.reactions.saw(base);
      }
   }

   private void forgetAround(class_2338 base, Set<class_2338> standing) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               class_2338 spot = base.method_10069(dx, dy, dz);
               if (!this.placed.contains(spot) && !this.hitBases.containsKey(spot) && !standing.contains(spot)) {
                  this.reactions.forget(spot);
               }
            }
         }
      }
   }

   private AutoCrystal.CrystalWait crystalWait(class_1511 crystal) {
      // A crystal of ours is never a surprise to react to, for as long as it stands - also after ticks in which it was
      // not looked at (a key-driven module at work, out of reach or view for a moment, the target outside the height
      // filter). WorthMemory alone forgot it after one such tick and made it wait a full reaction time.
      Integer ownFrom = this.ownCrystals.hittableFrom(crystal.method_5628());
      boolean answered = ownFrom != null || this.worth.worth(crystal.method_5628(), id -> this.reactions.ready(new AutoCrystal.CrystalWorth(id)));
      return AutoCrystal.CrystalWait.of(answered, ownFrom, this.clientTicks);
   }

   private void settleWorth() {
      this.worth.settle(id -> this.reactions.forget(new AutoCrystal.CrystalWorth(id)));
   }

   private boolean spotReady(class_2338 base) {
      // Obsidian you just placed yourself is no surprise to react to: you put it there to crystal it.
      Integer placedAt = this.ownBases.get(base);
      return placedAt != null && this.clientTicks - placedAt <= OWN_BASE_TICKS ? true : this.reactions.ready(base);
   }

   // Obsidian placed by this client - by hand, Sword Place or any other module. Read off the outgoing click: by the
   // time the packet is built the client has already predicted the block into the world.
   @EventHandler
   private void onClickSent(Send event) {
      if (event.packet instanceof class_2824 hit
         && this.mc.field_1724 != null
         && "ATTACK".equals(String.valueOf(((IPlayerInteractEntityC2SPacket)hit).meteor$getType()))
         && ((IPlayerInteractEntityC2SPacket)hit).meteor$getEntity() instanceof class_1657 victim) {
         // Your own melee hit opens the victim's damage window just like a crystal of ours does. Smart-delay treated it
         // as a hit from elsewhere and waited the whole half second out; knowing what it dealt, a crystal that clears it
         // by min-break-damage goes off right away, as after our own crystal.
         float raw = this.meleeRaw();
         if (raw > 0.0F) {
            this.recordOwnHit(victim.method_5628(), raw);
         }
      }

      if (!this.sendingOwnHit
         && event.packet instanceof class_2824 crystalHit
         && this.mc.field_1724 != null
         && this.mc.field_1687 != null
         && "ATTACK".equals(String.valueOf(((IPlayerInteractEntityC2SPacket)crystalHit).meteor$getType()))
         && ((IPlayerInteractEntityC2SPacket)crystalHit).meteor$getEntity() instanceof class_1511 struck
         && !struck.method_31481()
         && this.breaksCrystal(this.mc.field_1724.method_6047())) {
         // A crystal you broke by hand, or another module did (Double Tap), hurts the players around it like one of
         // ours. Unrecorded, smart-delay took that for a hit from elsewhere and waited the whole window out, and the
         // crystal vanishing reset the reaction time on every base around it. The aura's own hits are recorded in
         // attack(), against the predicted target.
         if (!this.attacked.contains(struck.method_5628())) {
            this.attacked.sent(struck.method_5628(), this.clientTicks);
            // In `attacked` the crystal no longer counts as in the way of a new one (goneWhenPlanned), so its base gets
            // the same replace-delay hold as after a hit of the aura's own.
            this.hitBases.merge(struck.method_24515().method_10074(), this.clientTicks + this.ownStep(), Math::max);
         }

         class_243 at = struck.method_73189();

         // All players in the world, not the candidates: those are empty while a key-driven module pauses the aura.
         for (class_1657 player : this.mc.field_1687.method_18456()) {
            if (player != this.mc.field_1724 && !player.method_31481()) {
               float raw = BlastShield.crystalRawDamage(player, at);
               if (raw > 0.0F) {
                  this.recordOwnHit(player.method_5628(), raw);
               }
            }
         }
      }

      if (event.packet instanceof class_2885 click && this.mc.field_1724 != null && this.mc.field_1687 != null) {
         class_1799 stack = this.mc.field_1724.method_5998(click.method_12546());
         class_2338 base = click.method_12543().method_17777().method_10062();
         if (stack.method_31574(class_1802.field_8301) && CrystalUtils.isBase(base)) {
            // Every crystal click goes through here, the aura's own included. One on a spot something visibly stands
            // in is refused by the server and tells nothing about how long a spawn takes.
            if (!CrystalUtils.isObstructed(base.method_10084(), this.goneWhenSent)) {
               this.recentPlaces.computeIfAbsent(base, b -> new int[]{this.clientTicks, 0})[1]++;
            }

            if (!this.placed.contains(base)) {
               // A crystal you put down yourself (right-click, a macro) is as much yours as one this aura placed: break
               // it the moment it appears instead of waiting out the reaction time for a stranger's crystal.
               this.placed.sent(base, this.clientTicks);
            }
         }

         if (stack.method_7960() || stack.method_31574(class_1802.field_8281)) {
            class_3965 hit = click.method_12543();
            class_2338 clicked = hit.method_17777().method_10062();
            class_2338 beside = clicked.method_10093(hit.method_17780());
            for (class_2338 pos : new class_2338[]{beside, clicked}) {
               if (this.mc.field_1687.method_8320(pos).method_27852(class_2246.field_10540)) {
                  this.ownBases.put(pos, this.clientTicks);
                  break;
               }
            }
         }
      }
   }

   private void noteHealth(class_1657 player) {
      float health = player.method_6032();
      float absorption = player.method_6067();
      AutoCrystal.SeenHealth seen = this.seenHealth.get(player.method_5628());
      AutoCrystal.HealthDrop drop = new AutoCrystal.HealthDrop(player.method_5628());
      if (seen == null || health + absorption >= seen.health() + seen.absorption() || this.reactions.ready(drop)) {
         this.seenHealth.put(player.method_5628(), new AutoCrystal.SeenHealth(health, absorption));
         this.reactions.forget(drop);
      }
   }

   private void noteWeakness() {
      if (this.mc.field_1724.method_6112(class_1294.field_5911) != null) {
         this.reactions.saw(AutoCrystal.Noticed.WEAKNESS);
      } else {
         this.reactions.forget(AutoCrystal.Noticed.WEAKNESS);
      }
   }

   private boolean weaknessUnnoticed() {
      return this.mc.field_1724.method_6112(class_1294.field_5911) != null && !this.reactions.ready(AutoCrystal.Noticed.WEAKNESS);
   }

   private AutoCrystal.SeenHealth seen(class_1657 player) {
      AutoCrystal.SeenHealth seen = this.seenHealth.get(player.method_5628());
      return seen != null ? seen : new AutoCrystal.SeenHealth(player.method_6032(), player.method_6067());
   }

   private float seenNeed() {
      AutoCrystal.SeenHealth seen = this.seen(this.target);
      return CrystalScore.need(seen.health(), seen.absorption());
   }

   private void logWhyNoPlacement() {
      long now = System.currentTimeMillis();
      if (now - this.lastDebugLog >= 1000L) {
         this.lastDebugLog = now;
         this.info(this.placementReport(), new Object[0]);
      }
   }

   private AutoCrystal.Gate rejection(class_2338 base, boolean legacy, double required, double selfHealth) {
      if (this.blockedByPending(base)) {
         return AutoCrystal.Gate.PENDING;
      } else if (!CrystalUtils.canPlace(base, legacy, this.goneWhenPlanned)) {
         return AutoCrystal.Gate.CANT_PLACE;
      } else {
         class_243 crystal = CrystalUtils.crystalPos(base);
         if (!this.withinReach(base)) {
            return AutoCrystal.Gate.REACH;
         } else if (!Stealth.inView(new class_238(base))) {
            return AutoCrystal.Gate.VIEW;
         } else if (!VanillaLimits.canReachBlock(base)) {
            return AutoCrystal.Gate.REACH;
         } else if (!this.aimAllows(crystal)) {
            return AutoCrystal.Gate.AIM;
         } else {
            float damage = this.targetDamage(crystal, true);
            float need = this.seenNeed();
            if (!(damage <= 0.0F) && (!(damage < required) || CrystalScore.lethal(damage, need))) {
               float selfDamage = this.selfDamage(crystal);
               if (!this.selfDamageOk(selfDamage, (Double)this.maxSelfDamage.get(), selfHealth, true)) {
                  return AutoCrystal.Gate.SELF_DAMAGE;
               } else if (this.placeAim(base) == null) {
                  return AutoCrystal.Gate.NO_FACE;
               } else {
                  return this.doBreak.get() && !this.farPlace.get() && !this.hittableAt(base) ? AutoCrystal.Gate.UNBREAKABLE : null;
               }
            } else {
               return AutoCrystal.Gate.LOW_DAMAGE;
            }
         }
      }
   }

   private String placementReport() {
      boolean legacy = this.legacyRules();
      double required = this.requiredDamage((Double)this.minDamage.get());
      double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
      String rules = legacy ? "legacy" : "modern";
      if (this.aimMode.get() == AutoCrystal.AimMode.Crosshair) {
         class_2338 looking = this.lookingAt();
         if (looking == null) {
            return "Crosshair: Fadenkreuz auf keinem Block in Reichweite | Regeln " + rules;
         } else if (!CrystalUtils.isBase(looking)) {
            return String.format(
               "Crosshair: Fadenkreuz auf %s %s, kein Obsidian/Bedrock | Regeln %s",
               this.mc.field_1687.method_8320(looking).method_26204().method_9518().getString(),
               looking.method_23854(),
               rules
            );
         } else {
            // Judged with the floor the crosshair fast path uses, or the report calls a base too weak that does get a crystal.
            AutoCrystal.Gate gate = this.rejection(looking, legacy, this.crosshairFloor(looking, required), selfHealth);
            return String.format("Crosshair: Basis %s %s | Regeln %s", looking.method_23854(), gate == null ? "brauchbar" : "verworfen: " + gate.label, rules);
         }
      } else {
         class_2338 origin = this.mc.field_1724.method_24515();
         int radius = (int)Math.ceil(this.placeReach());
         int bases = 0;
         int ok = 0;
         int[] rejected = new int[AutoCrystal.Gate.values().length];

         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  class_2338 base = origin.method_10069(x, y, z);
                  if (CrystalUtils.isBase(base)) {
                     bases++;
                     AutoCrystal.Gate gate = this.rejection(base, legacy, required, selfHealth);
                     if (gate == null) {
                        ok++;
                     } else {
                        rejected[gate.ordinal()]++;
                     }
                  }
               }
            }
         }

         StringBuilder text = new StringBuilder("Basen ").append(bases).append(" | verworfen: ");

         for (AutoCrystal.Gate gate : AutoCrystal.Gate.values()) {
            if (gate.ordinal() > 0) {
               text.append(", ");
            }

            text.append(gate.label).append(' ').append(rejected[gate.ordinal()]);
         }

         return text.append(" | brauchbar ").append(ok).append(" | Regeln ").append(rules).toString();
      }
   }

   private boolean legacyRules() {
      return switch ((AutoCrystal.Placement)this.placement.get()) {
         case Auto -> ServerVersion.needsLegacyCrystalPlacement();
         case Modern -> false;
         case Legacy -> true;
      };
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

   private void handBack(boolean fightOver) {
      if (this.mining) {
         // Another item in hand would restart the block you are mining; the slot goes back once you stop. Mining used
         // to pause the whole aura, which had the same effect.
         this.reactions.forget(AutoCrystal.Noticed.QUIET);
      } else if (this.loan.active() && this.mc.field_1724 != null) {
         AutoCrystal.SwitchMode mode = (AutoCrystal.SwitchMode)this.switchMode.get();
         if (mode == AutoCrystal.SwitchMode.None) {
            this.loan.clear();
         } else {
            boolean toWeapon = mode == AutoCrystal.SwitchMode.Hotbar && (Boolean)this.returnToWeapon.get();
            if (mode == AutoCrystal.SwitchMode.Hotbar && !toWeapon && !fightOver) {
               this.reactions.forget(AutoCrystal.Noticed.QUIET);
            } else if (!this.loan.untouched(HotbarSwap.homeSlot())) {
               this.reactions.forget(AutoCrystal.Noticed.QUIET);
            } else if (this.turn.heldByOther(TURN_OWNER)) {
               this.reactions.forget(AutoCrystal.Noticed.QUIET);
            } else if (this.reactions.ready(AutoCrystal.Noticed.QUIET)) {
               int slot = toWeapon ? this.bestWeaponSlot() : -1;
               if (slot == -1) {
                  slot = this.loan.home();
               }

               if (HotbarSwap.select(slot)) {
                  this.loan.clear();
               }
            }
         }
      }
   }

   private void tookHotbar(int home, int slot) {
      this.loan.took(home, slot);
      this.reactions.forget(AutoCrystal.Noticed.QUIET);
   }

   private void keepCrystalsOut() {
      if (this.switchMode.get() == AutoCrystal.SwitchMode.Silent) {
         int selected = this.mc.field_1724.method_31548().method_67532();
         if (this.mc.field_1724.method_31548().method_5438(selected).method_31574(class_1802.field_8301)) {
            HotbarSwap.silently(selected).back();
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
      // A crystal, totem or block scores the same as a bare fist; anything that does not beat that is no weapon.
      float bestDamage = this.target == null ? 0.0F : DamageUtils.getAttackDamage(this.mc.field_1724, this.target, class_1799.field_8037);

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

   private boolean heightOk() {
      if ((Boolean)this.heightFilter.get() && this.target != null) {
         double difference = this.heightDifference();
         return difference < this.minHeight.get() ? false : (Double)this.maxHeight.get() <= 0.0 || difference <= (Double)this.maxHeight.get();
      } else {
         return true;
      }
   }

   // Measured on the target's last server position, as predictedBox does: the drawn one is interpolated a few ticks
   // behind it, which opened every knock-up's window late and closed it late.
   private double heightDifference() {
      return serverPos(this.target).field_1351 - this.mc.field_1724.method_23318();
   }

   // Where the server last put this player. Only spawn and move packets write that position, so a player the client
   // made up itself - Meteor's fake player - still has the world origin there; its own position is all it has.
   private static class_243 serverPos(class_1657 player) {
      class_243 server = player.method_43389().method_60933();
      return !(player instanceof FakePlayerEntity) && server.method_1027() > 0.0 ? server : player.method_73189();
   }

   private boolean facePlacing() {
      if ((Boolean)this.facePlace.get() && this.target != null) {
         AutoCrystal.SeenHealth seen = this.seen(this.target);
         return seen.health() > 0.0F && seen.health() + seen.absorption() <= (Double)this.facePlaceHealth.get();
      } else {
         return false;
      }
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
      return Math.min((Double)this.placeRange.get(), VanillaLimits.blockRange());
   }

   private double breakReach() {
      return Math.min((Double)this.breakRange.get(), VanillaLimits.entityRange());
   }

   // Whether placing turns the server-side look first.
   private boolean shouldRotate() {
      return this.shouldRotate(false);
   }

   // Crosshair mode never turns to place - the crystal goes where you point - but with also-limit-breaking off it may
   // turn to hit a crystal its look no longer lands on, as the setting says.
   private boolean shouldRotate(boolean breaking) {
      return ((Boolean)this.rotate.get() || Stealth.legitPlace())
         && (this.aimMode.get() != AutoCrystal.AimMode.Crosshair || breaking && !(Boolean)this.aimAppliesToBreak.get());
   }

   private boolean crosshair() {
      return this.aimMode.get() == AutoCrystal.AimMode.Crosshair;
   }

   // Whether a click along this aim needs a turn first. In Crosshair mode, and while you mine, a hit along the look the
   // server already has goes out as it is: sending that look again through TurnProgress would only renew Meteor's hold
   // on a look the camera may already have left, and while mining such a look makes vanilla drop the block.
   private boolean turnsFor(AutoCrystal.Aim aim, boolean breaking) {
      return this.shouldRotate(breaking) && !((this.crosshair() || this.mining) && onServerLook(aim.yaw(), aim.pitch()));
   }

   private static boolean onServerLook(double yaw, double pitch) {
      return Math.abs(class_3532.method_15338(yaw - LegitPlace.currentYaw())) < 0.001 && Math.abs(pitch - LegitPlace.currentPitch()) < 0.001;
   }

   private AutoCrystal.Aim hitAim(class_238 box) {
      double reach = this.breakReach();
      class_243 eyes = this.mc.field_1724.method_33571();
      if (box.method_49271(eyes) >= reach * reach) {
         return null;
      } else {
         if (this.crosshair()) {
            // Along the look the server already has first: a hit there needs no turn.
            if (LegitPlace.confirmEntity(box, LegitPlace.currentYaw(), LegitPlace.currentPitch(), reach) != null) {
               return this.currentAim();
            }

            // While you mine no turn goes out at all: OwnClickMixin holds the mining back while the server is sent a
            // look other than the camera's, and vanilla then aborts the block and starts it over.
            if (this.mining || !this.shouldRotate(true)) {
               return null;
            }

            // Where your crosshair is on the crystal, the camera's look is the turn to make: it is the look the server
            // goes back to anyway, and a turn that lands on it does not leave the next crosshair placement behind.
            AutoCrystal.Aim camera = this.cameraAim();
            if (LegitPlace.confirmEntity(box, camera.yaw(), camera.pitch(), reach) != null) {
               return camera;
            }
         } else if (this.mining || !this.shouldRotate(true)) {
            return LegitPlace.confirmEntity(box, LegitPlace.currentYaw(), LegitPlace.currentPitch(), reach) == null ? null : this.currentAim();
         }

         if (box.method_1006(eyes)) {
            return this.currentAim();
         } else {
            LegitPlace.EntityResult result = LegitPlace.forEntity(box, reach);
            return result == null ? null : new AutoCrystal.Aim(result.yaw(), result.pitch());
         }
      }
   }

   private AutoCrystal.Aim currentAim() {
      return new AutoCrystal.Aim(LegitPlace.currentYaw(), LegitPlace.currentPitch());
   }

   private AutoCrystal.Aim cameraAim() {
      return new AutoCrystal.Aim(this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455());
   }

   // Crosshair mode clicks only along the look the server has. While that look is held somewhere else - after a turned
   // hit or another module's turn - something your crosshair is on cannot be clicked, and the aura sat blind until
   // Meteor's rotation hold ran out. Ask for the camera's look back instead, as Sword Place does; the click itself still
   // waits until the server has that look. Never while a key-driven module or another module's turn is at work: the
   // request refuses every look but the camera's for two ticks.
   private boolean requestCameraLook() {
      if (!this.crosshair() || KeyPriority.active() || this.turn.heldByOther(TURN_OWNER)) {
         return false;
      } else if (!this.turn.ownClickMismatched()) {
         // Only a look TurnProgress queued or is still easing back can be brought home by asking. The server already
         // looks along the camera, or a rotation outside TurnProgress holds it: asking changes nothing there, and
         // would only keep refusing every other module's turn for as long as the crosshair rests here.
         return false;
      } else {
         TurnProgress.requestCamera();
         this.workAvailable = true;
         return true;
      }
   }

   private AutoCrystal.BreakOutcome tryBreak() {
      if (!this.hitCrystals.isEmpty()) {
         return AutoCrystal.BreakOutcome.DONE;
      } else {
         boolean limitAim = (Boolean)this.aimAppliesToBreak.get() && this.aimMode.get() != AutoCrystal.AimMode.Off;
         double required = this.requiredDamage((Double)this.minBreakDamage.get());
         double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
         float need = this.seenNeed();
         float floor = 0.0F;
         boolean waitOutWindow = false;
         if ((Boolean)this.smartDelay.get()) {
            AutoCrystal.OwnHit own = this.ownHits.get(this.target.method_5628());
            int ownHitAgo = own == null ? Integer.MAX_VALUE : this.clientTicks - own.tick();
            DamageWindow.Judgement window = DamageWindow.judge(
               ownHitAgo, CrystalUtils.confirmTicks(EntityUtils.getPing(this.mc.field_1724)), DamageWindow.reduced(this.target), own == null ? 0.0F : own.raw()
            );
            floor = window.floor();
            waitOutWindow = window.hold();
         }

         class_238 predicted = this.predictedBox(false);
         class_243 eyes = this.mc.field_1724.method_33571();
         double reach = this.breakReach();
         class_1511 best = null;
         AutoCrystal.Aim bestAim = null;
         double bestScore = Double.NEGATIVE_INFINITY;
         float bestDamage = 0.0F;
         boolean waiting = false;
         boolean reactionWait = false;
         boolean cameraWanted = false;

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal
               && !crystal.method_31481()
               && !this.attacked.contains(crystal.method_5628())
               && !(crystal.method_5829().method_49271(eyes) >= reach * reach)
               && Stealth.inView(crystal.method_5829())) {
               class_243 pos = crystal.method_73189();
               // A crystal you put down yourself goes off as soon as you can hit it, whatever it now deals: the target
               // has moved since it was placed - a far-placed one is reached only after a step or two - and a crystal
               // left standing blocks every base around it. Only an open damage window is still waited out.
               boolean own = this.ownCrystals.hittableFrom(crystal.method_5628()) != null;
               float most = this.maxTargetDamage(pos, false);
               if (own || !(most <= 0.0F) && (!(most < required) || CrystalScore.lethal(most, need))) {
                  AutoCrystal.CrystalWait wait = this.crystalWait(crystal);
                  if (wait != AutoCrystal.CrystalWait.NONE) {
                     waiting = true;
                     if (wait == AutoCrystal.CrystalWait.REACTION) {
                        reactionWait = true;
                     }
                  } else if (!(CrystalScore.upperBound(most, need) <= bestScore)) {
                     float damage = this.targetDamage(pos, false);
                     if ((own || !(damage <= 0.0F) && (!(damage < required) || CrystalScore.lethal(damage, need)))
                        && !(CrystalScore.upperBound(damage, need) <= bestScore)
                        && Stealth.allowsEntity(crystal)) {
                        AutoCrystal.Aim aim = this.crystalAim(crystal);
                        boolean allowed = aim != null
                           && (!limitAim || (this.aimMode.get() == AutoCrystal.AimMode.Crosshair ? this.looksAtCrystal(crystal) : this.aimAllows(pos)));
                        // Your crosshair is on it, but the look the server holds is not (see requestCameraLook). It goes
                        // through the same checks as a hit, so the camera is only asked for a crystal that would be hit.
                        boolean cameraOnly = aim == null && this.crosshair() && this.looksAtCrystal(crystal);
                        if (allowed || cameraOnly) {
                           float counted = damage;
                           if (floor > 0.0F) {
                              float raw = predicted == null
                                 ? BlastShield.crystalRawDamage(this.target, pos)
                                 : BlastShield.crystalRawDamage(feetOf(predicted), predicted, pos);
                              counted = BlastShield.excessDamage(this.target, raw, floor);
                           }

                           boolean lethal = CrystalScore.lethal(counted, need);
                           boolean worth = own ? floor <= 0.0F || counted > 0.0F : !(counted <= 0.0F) && (!(counted < required) || lethal);
                           if (worth && !(CrystalScore.upperBound(counted, need) <= bestScore)) {
                              float selfDamage = this.selfDamage(pos);
                              if (this.selfDamageOk(selfDamage, (Double)this.maxBreakSelfDamage.get(), selfHealth, false)) {
                                 if (cameraOnly) {
                                    cameraWanted = true;
                                 } else {
                                    double score = CrystalScore.score(counted, selfDamage, (Double)this.selfDamageWeight.get(), need);
                                    if (!(score <= bestScore)) {
                                       bestScore = score;
                                       bestDamage = counted;
                                       best = crystal;
                                       bestAim = aim;
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

         if (best == null) {
            if (waiting) {
               this.workAvailable = true;
               if (reactionWait) {
                  this.reacting.waitingFor(this.target);
               }
            }

            // Not while smart-delay waits a hit from elsewhere out or nothing in the hotbar breaks a crystal: no hit
            // would follow, and the request keeps every other module's turn back.
            if (cameraWanted && !waitOutWindow && this.crystalHitSlot() >= 0) {
               this.requestCameraLook();
            }

            return AutoCrystal.BreakOutcome.NOTHING;
         } else {
            int hitSlot = this.crystalHitSlot();
            if (hitSlot < 0) {
               this.breakSkip = stabs(this.mc.field_1724.method_6047())
                  ? "a spear in hand stabs instead of hitting, and anti-weakness has nothing to switch to"
                  : "Weakness: nothing in the hotbar breaks a crystal";
               return AutoCrystal.BreakOutcome.BLOCKED;
            } else {
               this.workAvailable = true;
               if (waitOutWindow) {
                  return AutoCrystal.BreakOutcome.HELD;
               } else {
                  int selected = this.mc.field_1724.method_31548().method_67532();
                  if (hitSlot != selected) {
                     if (this.mining) {
                        // Another item in hand would restart the block you are mining; the hit waits until you stop.
                        return AutoCrystal.BreakOutcome.HELD;
                     } else if (this.weaknessUnnoticed()) {
                        this.reacting.waitingFor(this.target);
                        return AutoCrystal.BreakOutcome.HELD;
                     } else if (this.turn.heldByOther(TURN_OWNER)) {
                        this.turnBusy = true;
                        return AutoCrystal.BreakOutcome.HELD;
                     } else {
                        int home = HotbarSwap.homeSlot();
                        if (HotbarSwap.select(hitSlot)) {
                           this.tookHotbar(home, hitSlot);
                        }

                        this.weaponSwapped = true;
                        this.reacting.engaged();
                        return AutoCrystal.BreakOutcome.SWAPPED;
                     }
                  } else if (this.mining && this.turnsFor(bestAim, true)) {
                     // A turn would make vanilla drop the block you are mining (hitAim already keeps to the look the
                     // server has while you mine; this only makes sure).
                     return AutoCrystal.BreakOutcome.HELD;
                  } else if (!this.readyToAct(bestAim, true)) {
                     return AutoCrystal.BreakOutcome.TURNING;
                  } else if (!this.breakBudget.canAfford()) {
                     this.weaponWaits = true;
                     return AutoCrystal.BreakOutcome.DONE;
                  } else {
                     if (bestDamage > this.renderDamage) {
                        this.renderDamage = bestDamage;
                     }

                     this.hitCrystals.add(best.method_5628());
                     this.attack(best, bestAim);
                     if (!this.attacked.contains(best.method_5628())) {
                        this.weaponWaits = true;
                     }

                     return AutoCrystal.BreakOutcome.DONE;
                  }
               }
            }
         }
      }
   }

   private AutoCrystal.Aim crystalAim(class_1511 crystal) {
      return this.hitAimCache.computeIfAbsent(crystal.method_5628(), id -> {
         AutoCrystal.Aim shared = this.replaceAim(crystal);
         return Optional.ofNullable(shared != null ? shared : this.hitAim(crystal.method_5829()));
      }).orElse(null);
   }

   private void recordOwnHit(int id, float raw) {
      AutoCrystal.OwnHit own = this.ownHits.get(id);
      if (own == null || this.clientTicks - own.tick() >= 10) {
         this.ownHits.put(id, new AutoCrystal.OwnHit(this.clientTicks, raw));
      } else if (raw > own.raw()) {
         this.ownHits.put(id, new AutoCrystal.OwnHit(own.tick(), raw));
      }
   }

   private static class_243 feetOf(class_238 box) {
      return new class_243((box.field_1323 + box.field_1320) / 2.0, box.field_1322, (box.field_1321 + box.field_1324) / 2.0);
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
            if (!hurtable(mode)) {
               return player.method_5477().getString() + " ignored: " + mode.name().toLowerCase();
            }
         }
      }

      return null;
   }

   private List<class_1657> findTargets() {
      List<class_1657> targets = new ArrayList<>();
      List<class_1297> found = new ArrayList<>();
      int max = (Integer)this.maxTargets.get();
      TargetUtils.getList(
         found,
         entityx -> entityx instanceof class_1657 player && !player.method_29504() && player.method_6032() > 0.0F && this.isCandidate(player),
         (SortPriority)this.priority.get(),
         max
      );

      for (class_1297 entity : found) {
         if (!targets.contains(entity)) {
            targets.add((class_1657)entity);
         }
      }

      if (targets.size() < max) {
         TargetUtils.getList(
            found,
            entityx -> entityx instanceof class_1657 player
               && (player.method_29504() || player.method_6032() <= 0.0F)
               && RevivedPlayers.isRevived(player)
               && this.isCandidate(player),
            (SortPriority)this.priority.get(),
            max - targets.size()
         );

         for (class_1297 entityx : found) {
            if (!targets.contains(entityx)) {
               targets.add((class_1657)entityx);
            }
         }
      }

      return targets.size() > max ? targets.subList(0, max) : targets;
   }

   // Survival and adventure players both take explosion damage, and a player without a tab entry is still a player.
   // Only creative and spectator are out.
   private static boolean hurtable(class_1934 mode) {
      return mode != class_1934.field_9220 && mode != class_1934.field_9219;
   }

   private boolean isCandidate(class_1657 player) {
      if (player == this.mc.field_1724 || player.method_31481()) {
         return false;
      } else if (!PlayerUtils.isWithin(player, (Double)this.targetRange.get())) {
         return false;
      } else if (!Friends.get().shouldAttack(player)) {
         return false;
      } else {
         return player instanceof FakePlayerEntity fakePlayer ? !fakePlayer.noHit : hurtable(EntityUtils.getGameMode(player));
      }
   }

   private void aimAt(class_1657 candidate) {
      this.target = candidate;
      if (this.cachedFor != candidate) {
         this.cachedFor = candidate;
         this.figures = this.figuresByTarget.computeIfAbsent(candidate.method_5628(), id -> new AutoCrystal.TargetFigures());
      }
   }

   private boolean selfDamageOk(float selfDamage, double max, double selfHealth, boolean placing) {
      if (selfDamage > max) {
         return false;
      } else if (selfDamage < selfHealth) {
         return true;
      } else if (!(Boolean)this.allowSelfPop.get()) {
         return false;
      } else {
         return this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)
            ? true
            : !placing && this.mc.field_1724.method_6047().method_31574(class_1802.field_8288);
      }
   }

   private float selfDamage(class_243 crystal) {
      return this.selfDamageCache.computeIfAbsent(crystal, c -> DamageUtils.crystalDamage(this.mc.field_1724, c));
   }

   private float maxTargetDamage(class_243 crystal, boolean placing) {
      class_238 box = this.predictedBox(placing);
      class_243 feet = box == null ? this.target.method_73189() : feetOf(box);
      return BlastShield.crystalMaxDamage(this.target, feet, crystal);
   }

   private List<class_2338> basesForTarget() {
      if (this.figures.bases != null) {
         return this.figures.bases;
      } else {
         class_238 box = this.predictedBox(true);
         class_243 feet = box == null ? this.target.method_73189() : feetOf(box);
         List<class_2338> sorted = new ArrayList<>(this.scanBases());
         sorted.sort(Comparator.comparingDouble(base -> CrystalUtils.crystalPos(base).method_1025(feet)));
         this.figures.bases = sorted;
         return sorted;
      }
   }

   private float targetDamage(class_243 crystal, boolean placing) {
      Map<class_243, Float> cache = placing ? this.figures.place : this.figures.brk;
      return cache.computeIfAbsent(crystal, c -> {
         class_238 box = this.predictedBox(placing);
         if (box == null) {
            return DamageUtils.crystalDamage(this.target, c);
         } else {
            class_243 feet = new class_243((box.field_1323 + box.field_1320) / 2.0, box.field_1322, (box.field_1321 + box.field_1324) / 2.0);
            return DamageUtils.explosionDamage(this.target, feet, box, c, 12.0F, DamageUtils.HIT_FACTORY);
         }
      });
   }

   private class_238 predictedBox(boolean placing) {
      if (placing ? !this.figures.placeBoxDone : !this.figures.breakBoxDone) {
         class_238 box = null;
         if ((Boolean)this.predictMovement.get() && this.target != null) {
            class_243 server = serverPos(this.target);
            class_238 start = this.target.method_5829().method_997(server.method_1020(this.target.method_73189()));
            double ping = EntityUtils.getPing(this.mc.field_1724) / 50.0;
            int ticks = (int)Math.min((double)((Integer)this.maxPredictTicks.get()).intValue(), Math.ceil(placing ? 2.0 * ping + 2.0 : ping));
            class_243 perTick = this.trackedVelocity(this.target);
            if (ticks > 0 && perTick.method_1027() > 1.0E-6) {
               class_243 feet = Trajectory.walk(this.target, start, perTick, ticks);
               box = start.method_997(feet.method_1020(feetOf(start)));
            } else if (server.method_1025(this.target.method_73189()) > 1.0E-4) {
               box = start;
            }
         }

         if (placing) {
            this.figures.placeBoxDone = true;
            this.figures.placeBox = box;
         } else {
            this.figures.breakBoxDone = true;
            this.figures.breakBox = box;
         }

         return box;
      } else {
         return placing ? this.figures.placeBox : this.figures.breakBox;
      }
   }

   private void recordTrackedPositions() {
      double range = (Double)this.targetRange.get() + 4.0;
      Set<Integer> seen = new HashSet<>();

      for (class_1657 player : this.mc.field_1687.method_18456()) {
         if (player != this.mc.field_1724 && !player.method_31481() && !(this.mc.field_1724.method_5858(player) > range * range)) {
            ArrayDeque<class_243> history = this.trackedHistory.computeIfAbsent(player.method_5628(), id -> new ArrayDeque<>());
            history.addLast(serverPos(player));

            while (history.size() > 3) {
               history.removeFirst();
            }

            this.noteHealth(player);
            seen.add(player.method_5628());
         }
      }

      this.trackedHistory.keySet().retainAll(seen);
      this.seenHealth.keySet().retainAll(seen);
   }

   private class_243 trackedVelocity(class_1657 player) {
      ArrayDeque<class_243> history = this.trackedHistory.get(player.method_5628());
      if (history != null && history.size() >= 2) {
         class_243 previous = null;

         for (class_243 pos : history) {
            if (previous != null && pos.method_1025(previous) > 9.0) {
               return class_243.field_1353;
            }

            previous = pos;
         }

         class_243 first = history.getFirst();
         class_243 last = history.getLast();
         return last.method_1020(first).method_1021(1.0 / (history.size() - 1));
      } else {
         return class_243.field_1353;
      }
   }

   private boolean readyToAct(AutoCrystal.Aim aim, boolean breaking) {
      this.reacting.engaged();
      if (!this.turnsFor(aim, breaking)) {
         return true;
      } else if (this.turn.wouldReach(TURN_OWNER, aim.yaw(), aim.pitch())) {
         return true;
      } else {
         if (TurnProgress.cameraNeeded() && this.turn.lastCallBlocked()) {
            this.turnHeldForYou = true;
         } else if (this.turn.heldByOther(TURN_OWNER)) {
            this.turnBusy = true;
         }

         this.turn.turnTo(TURN_OWNER, aim.yaw(), aim.pitch(), 50, null);
         return false;
      }
   }

   private boolean spend(ActionBudget budget, double cost, boolean use) {
      if (!budget.canAfford(cost)) {
         return false;
      } else {
         // A hit may go out while you mine, as vanilla's attack press may; a right click may not.
         return (use ? Stealth.claimUse() : Stealth.claimAttack(true)) ? budget.tryConsume(cost) : false;
      }
   }

   private boolean looksAtCrystal(class_1511 crystal) {
      return LegitPlace.confirmEntity(crystal.method_5829(), this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455(), this.breakReach()) != null;
   }

   private boolean tryPlace() {
      if (this.weaponSwapped) {
         return false;
      } else if (!this.usedPositions.isEmpty()) {
         return true;
      } else {
         FindItemResult crystals = this.findCrystals(null);
         if (!crystals.found()) {
            this.placeSkip = this.mc.field_1724.method_6079().method_31574(class_1802.field_8301)
               ? "crystals only in the offhand, behind a main-hand item a right click would use first"
               : "no end crystals in the hotbar";
            return false;
         } else if (this.switchMode.get() == AutoCrystal.SwitchMode.None && crystals.getHand() == null) {
            this.placeSkip = "crystals are not in a hand and switch-mode is None";
            return false;
         } else {
            boolean legacy = this.legacyRules();
            double required = this.requiredDamage((Double)this.minDamage.get());
            double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
            this.placeWaiting = false;
            AutoCrystal.Spot spot = this.findPlacement(legacy, required, selfHealth);
            if (spot == null) {
               if (!this.placeWaiting) {
                  return false;
               } else {
                  this.workAvailable = true;
                  this.reacting.waitingFor(this.target);
                  return true;
               }
            } else {
               this.workAvailable = true;
               if (!this.readyToAct(spot.aim(), false)) {
                  return true;
               } else if (!this.placeBudget.canAfford()) {
                  return true;
               } else {
                  this.usedPositions.add(spot.base());
                  this.placeAt(spot.base(), spot.aim());
                  return true;
               }
            }
         }
      }
   }

   private AutoCrystal.Aim placeAim(class_2338 base) {
      double reach = this.placeReach();
      if (this.shouldRotate()) {
         // While a click of your own is about to go out, Sword Place has asked for the camera, or you glide, only the
         // camera's look may be sent. Where that look already lands on the base take it: a face point picked by
         // turning cost is never exactly the camera's, and would be refused for this tick.
         if (TurnProgress.cameraNeeded()) {
            AutoCrystal.Aim camera = this.cameraAim();
            if (LegitPlace.confirmCrystal(base, camera.yaw(), camera.pitch(), reach) != null) {
               return camera;
            }
         }

         LegitPlace.Result result = LegitPlace.forCrystal(base, reach);
         return result == null ? null : new AutoCrystal.Aim(result.yaw(), result.pitch());
      } else {
         return LegitPlace.confirmCrystal(base, LegitPlace.currentYaw(), LegitPlace.currentPitch(), reach) == null ? null : this.currentAim();
      }
   }

   private AutoCrystal.Aim cachedReachableAim(class_2338 base) {
      return this.placeAimCache.computeIfAbsent(base, b -> Optional.ofNullable(this.reachableAim(b))).orElse(null);
   }

   private AutoCrystal.Aim reachableAim(class_2338 base) {
      AutoCrystal.Aim aim = this.placeAim(base);
      if (aim == null) {
         return null;
      } else {
         return this.doBreak.get() && !this.farPlace.get() && !this.hittableAt(base) ? null : aim;
      }
   }

   // Whether the crystal on this base could be hit from where you stand right now: by some look within hit reach, not
   // necessarily the current one, in every aim mode. Crosshair mode used to ask for the current look here, which the
   // side face of fresh obsidian never passes - the crystal's box is above that face - so it refused to place until you
   // aimed at the top. The hit itself still goes only along a look the server has (hitAim).
   private boolean hittableAt(class_2338 base) {
      return !(Boolean)this.doBreak.get() || this.hittableCache.computeIfAbsent(base, b -> {
         class_238 box = CrystalUtils.crystalHitbox(b);
         double reach = this.breakReach();
         class_243 eyes = this.mc.field_1724.method_33571();
         return box.method_49271(eyes) < reach * reach && (box.method_1006(eyes) || LegitPlace.forEntity(box, reach) != null);
      });
   }

   // far-place: a crystal goes down beyond hit reach only while none of the ones already standing is waiting there to
   // be walked up to - one crystal ahead of you is the play, a row of them is just thrown away.
   private boolean farPlacingAllowed() {
      if (!(Boolean)this.farPlace.get()) {
         return false;
      } else {
         class_243 eyes = this.mc.field_1724.method_33571();
         double hit = this.breakReach();
         double around = this.placeReach() + 2.0;

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal && !crystal.method_31481()) {
               double distance = crystal.method_5829().method_49271(eyes);
               if (distance >= hit * hit && distance < around * around && this.maxTargetDamage(crystal.method_73189(), false) > 0.0F) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private AutoCrystal.Spot findPlacement(boolean legacy, double required, double selfHealth) {
      if (this.aimMode.get() == AutoCrystal.AimMode.Crosshair) {
         // The same shortcuts as in the other aim modes: the same-tick replace at replace-delay 0 along the hit's look,
         // and the block under your crosshair (crosshair-priority, or obsidian you just placed) with the lower damage
         // floor and no reaction wait. Neither turns: both only accept the look the server already has.
         AutoCrystal.Spot again = this.sameTickReplace(legacy, required, selfHealth);
         // A hit may have turned away from the crosshair (also-limit-breaking off), so the same-tick replace is only
         // taken on the block you point at: the crystal still goes where you point, never where the hit looked.
         if (again != null && again.base().equals(this.lookingAt())) {
            return again;
         }

         AutoCrystal.Spot yours = this.ownBaseInCrosshair(legacy, required, selfHealth);
         if (yours != null) {
            return yours;
         }

         class_2338 looking = this.lookingAt();
         if (looking == null || this.blockedByPending(looking)) {
            return null;
         } else {
            AutoCrystal.Spot spot = this.acceptable(looking, legacy, required, selfHealth);
            if (spot == null && !this.placeWaiting && this.cameraWouldPlace(looking, legacy, required, selfHealth) && this.requestCameraLook()) {
               if (this.renderPos == null) {
                  this.renderPos = looking;
               }
            }

            return spot;
         }
      } else {
         AutoCrystal.Spot again = this.sameTickReplace(legacy, required, selfHealth);
         if (again != null) {
            return again;
         }

         AutoCrystal.Spot yours = this.ownBaseInCrosshair(legacy, required, selfHealth);
         if (yours != null) {
            return yours;
         }

         // Spots whose crystal can be hit from here come first; with far-place on, a spot only in place reach is taken
         // when there is no such spot at all, not even one still waiting out the reaction time.
         AutoCrystal.SpotPick<AutoCrystal.Spot> near = new AutoCrystal.SpotPick<>();
         AutoCrystal.SpotPick<AutoCrystal.Spot> far = new AutoCrystal.SpotPick<>();
         boolean farAllowed = this.farPlacingAllowed();

         for (class_2338 base : this.basesForTarget()) {
            if (!this.blockedByPending(base)) {
               boolean hittable = this.hittableAt(base);
               if (hittable || farAllowed) {
                  AutoCrystal.SpotPick<AutoCrystal.Spot> into = hittable ? near : far;
                  double score = this.placementScore(base, legacy, required, selfHealth, into.beat(), true, true);
                  if (!Double.isNaN(score)) {
                     float damage = this.scoredDamage;
                     AutoCrystal.Aim aim = this.cachedReachableAim(base);
                     if (aim != null) {
                        into.offer(new AutoCrystal.Spot(base, aim), score, damage, this.scoredReady);
                     }
                  }
               }
            }
         }

         AutoCrystal.SpotPick<AutoCrystal.Spot> pick = near.best() == null && near.waiting() == null ? far : near;

         if (pick.waiting() != null) {
            this.placeWaiting = true;
         }

         AutoCrystal.Spot shown = pick.best() != null ? pick.best() : pick.waiting();
         if (shown != null) {
            if (this.renderPos == null) {
               this.renderPos = shown.base();
            }

            float damage = pick.best() != null ? pick.bestDamage() : pick.waitingDamage();
            if (damage > this.renderDamage) {
               this.renderDamage = damage;
            }
         }

         return pick.best();
      }
   }

   // Obsidian you just placed and are now looking at is the spot you want crystalled - that is why you put it there.
   // It goes first, needs no reaction, and only has to clear the face-place damage floor instead of min-damage: right
   // after a hit the target is flying away, and the predicted damage on your block easily drops under the full floor.
   // Self-damage limits still apply as usual.
   private AutoCrystal.Spot ownBaseInCrosshair(boolean legacy, double required, double selfHealth) {
      class_2338 looking = this.lookingAt();
      if (looking == null || !this.crosshairFirst(looking) || this.blockedByPending(looking)) {
         return null;
      } else if (new class_238(looking).method_49271(this.mc.field_1724.method_33571()) >= this.placeReach() * this.placeReach()
         || !VanillaLimits.canReachBlock(looking)
         || !Stealth.inView(new class_238(looking))) {
         return null;
      } else if (!this.hittableAt(looking) && !this.farPlacingAllowed()) {
         // Placing reaches 4.5 blocks, hitting a crystal only 3: a crystal put down beyond that just stands there until
         // you walk up to it. Only far-place asks for that, and then only one at a time.
         return null;
      } else {
         double score = this.placementScore(looking, legacy, this.crosshairFloor(looking, required), selfHealth, Double.NEGATIVE_INFINITY, true);
         if (Double.isNaN(score)) {
            return null;
         } else {
            AutoCrystal.Aim aim = this.placeAim(looking);
            if (aim == null) {
               return null;
            } else {
               this.renderPos = looking;
               if (this.scoredDamage > this.renderDamage) {
                  this.renderDamage = this.scoredDamage;
               }

               return new AutoCrystal.Spot(looking, aim);
            }
         }
      }
   }

   // The block under the crosshair that ownBaseInCrosshair takes first: obsidian you placed in the last two seconds, or
   // any base while crosshair-priority is on.
   private boolean crosshairFirst(class_2338 base) {
      Integer placedAt = this.ownBases.get(base);
      boolean own = placedAt != null && this.clientTicks - placedAt <= OWN_BASE_TICKS;
      return own || (Boolean)this.crosshairPriority.get() && CrystalUtils.isBase(base);
   }

   private double crosshairFloor(class_2338 base, double required) {
      return this.crosshairFirst(base) ? Math.min(required, (Double)this.facePlaceMinDamage.get()) : required;
   }

   // Crosshair mode: the base under the crosshair would take a crystal along the camera's look - damage, self-damage,
   // reach, reaction - and only the look the server still holds misses it (see requestCameraLook).
   private boolean cameraWouldPlace(class_2338 base, boolean legacy, double required, double selfHealth) {
      AutoCrystal.Aim camera = this.cameraAim();
      if (onServerLook(camera.yaw(), camera.pitch()) || LegitPlace.confirmCrystal(base, camera.yaw(), camera.pitch(), this.placeReach()) == null) {
         return false;
      } else if (Double.isNaN(this.placementScore(base, legacy, this.crosshairFloor(base, required), selfHealth, Double.NEGATIVE_INFINITY))) {
         return false;
      } else if (this.crosshairFirst(base)) {
         return this.hittableAt(base) || this.farPlacingAllowed();
      } else {
         return (this.hittableAt(base) || (Boolean)this.farPlace.get()) && this.spotReady(base);
      }
   }

   // replace-delay 0: the crystal just hit this tick is replaced at once, along the look the hit went out with - the
   // only look this tick allows - which reachable crystal aims are picked to share (see replaceAim).
   private AutoCrystal.Spot sameTickReplace(boolean legacy, double required, double selfHealth) {
      if ((Integer)this.replaceDelay.get() != 0 || this.replaceTick != this.clientTicks || this.replaceBase == null || this.replaceLook == null) {
         return null;
      } else {
         class_2338 base = this.replaceBase;
         AutoCrystal.Aim aim = this.replaceLook;
         if (Double.isNaN(this.placementScore(base, legacy, required, selfHealth, Double.NEGATIVE_INFINITY))) {
            return null;
         } else {
            return LegitPlace.confirmCrystal(base, aim.yaw(), aim.pitch(), this.placeReach()) == null ? null : new AutoCrystal.Spot(base, aim);
         }
      }
   }

   private AutoCrystal.Aim replaceAim(class_1511 crystal) {
      // While you mine nothing is placed, and the hit takes the look the server has (hitAim).
      if ((Integer)this.replaceDelay.get() != 0 || !(Boolean)this.place.get() || !this.shouldRotate() || this.mining) {
         return null;
      } else {
         class_2338 base = crystal.method_24515().method_10074();
         if (!CrystalUtils.isBase(base)) {
            return null;
         } else {
            AutoCrystal.Aim aim = LegitPlace.passingThrough(entity -> entity == crystal, () -> this.placeAim(base));
            return aim != null && LegitPlace.confirmEntity(crystal.method_5829(), aim.yaw(), aim.pitch(), this.breakReach()) != null ? aim : null;
         }
      }
   }

   private boolean blockedByPending(class_2338 base) {
      for (class_2338 used : this.usedPositions) {
         if (nextTo(used, base)) {
            return true;
         }
      }

      for (class_2338 sent : this.placed.keys()) {
         if (nextTo(sent, base)) {
            return true;
         }
      }

      for (Entry<class_2338, Integer> hit : this.hitBases.entrySet()) {
         if (this.clientTicks < hit.getValue() && nextTo(hit.getKey(), base)) {
            return true;
         }
      }

      return false;
   }

   private static boolean nextTo(class_2338 crystalBase, class_2338 base) {
      return Math.abs(crystalBase.method_10263() - base.method_10263()) <= 1
         && Math.abs(crystalBase.method_10264() - base.method_10264()) <= 1
         && Math.abs(crystalBase.method_10260() - base.method_10260()) <= 1;
   }

   private AutoCrystal.Spot acceptable(class_2338 base, boolean legacy, double required, double selfHealth) {
      double score = this.placementScore(base, legacy, required, selfHealth, Double.NEGATIVE_INFINITY);
      if (Double.isNaN(score)) {
         return null;
      } else {
         float damage = this.scoredDamage;
         AutoCrystal.Aim aim = this.cachedReachableAim(base);
         if (aim == null) {
            return null;
         } else {
            if (this.renderPos == null) {
               this.renderPos = base;
            }

            if (damage > this.renderDamage) {
               this.renderDamage = damage;
            }

            if (!this.spotReady(base)) {
               this.placeWaiting = true;
               return null;
            } else {
               return new AutoCrystal.Spot(base, aim);
            }
         }
      }
   }

   private boolean withinReach(class_2338 base) {
      class_243 eyes = this.mc.field_1724.method_33571();
      double place = this.placeReach();
      if (new class_238(base).method_49271(eyes) >= place * place) {
         return false;
      } else if (!(Boolean)this.doBreak.get() || (Boolean)this.farPlace.get()) {
         return true;
      } else {
         double hit = this.breakReach();
         return CrystalUtils.crystalHitbox(base).method_49271(eyes) < hit * hit;
      }
   }

   private double placementScore(class_2338 base, boolean legacy, double required, double selfHealth, double beat) {
      return this.placementScore(base, legacy, required, selfHealth, beat, false);
   }

   private double placementScore(class_2338 base, boolean legacy, double required, double selfHealth, double beat, boolean prefiltered) {
      return this.placementScore(base, legacy, required, selfHealth, beat, prefiltered, false);
   }

   // clock: the base scan. Every base that clears the damage floor keeps its reaction clock running (into scoredReady),
   // also while a better one is ahead: a spot in view and worth a crystal is seen, so when the best one gets blocked the
   // next is ready instead of only then starting its reaction time. The cut-offs below still save the costly part.
   private double placementScore(class_2338 base, boolean legacy, double required, double selfHealth, double beat, boolean prefiltered, boolean clock) {
      if (!CrystalUtils.canPlace(base, legacy, this.goneWhenPlanned)) {
         return Double.NaN;
      } else {
         class_243 crystal = CrystalUtils.crystalPos(base);
         if (!prefiltered && !this.usableBase(base, crystal)) {
            return Double.NaN;
         } else {
            float need = this.seenNeed();
            float most = this.maxTargetDamage(crystal, true);
            if (!(most <= 0.0F) && (!(most < required) || CrystalScore.lethal(most, need))) {
               if (clock) {
                  this.scoredReady = this.spotReady(base);
               }

               if (CrystalScore.upperBound(most, need) <= beat) {
                  return Double.NaN;
               } else {
                  float damage = this.targetDamage(crystal, true);
                  if (!(damage <= 0.0F) && (!(damage < required) || CrystalScore.lethal(damage, need))) {
                     if (CrystalScore.upperBound(damage, need) <= beat) {
                        return Double.NaN;
                     } else {
                        float self = this.selfDamage(crystal);
                        if (!this.selfDamageOk(self, (Double)this.maxSelfDamage.get(), selfHealth, true)) {
                           return Double.NaN;
                        } else {
                           double score = CrystalScore.score(damage, self, (Double)this.selfDamageWeight.get(), need);
                           if (score <= beat) {
                              return Double.NaN;
                           } else {
                              this.scoredDamage = damage;
                              return score;
                           }
                        }
                     }
                  } else {
                     return Double.NaN;
                  }
               }
            } else {
               return Double.NaN;
            }
         }
      }
   }

   // Line of sight is not checked to the top of the block: a crystal goes on top whichever face of the obsidian is
   // clicked, so a base whose top you cannot see (above eye level, or covered) is fine as long as some face is in
   // reach and sight. reachableAim() looks for that face; this only keeps out what is out of reach or behind you.
   private boolean usableBase(class_2338 base, class_243 crystal) {
      return this.withinReach(base) && VanillaLimits.canReachBlock(base) && Stealth.inView(new class_238(base)) && this.aimAllows(crystal);
   }

   private List<class_2338> scanBases() {
      if (this.scanBases != null) {
         return this.scanBases;
      } else {
         this.scanBases = new ArrayList<>();
         class_2338 origin = this.mc.field_1724.method_24515();
         int radius = (int)Math.ceil(this.placeReach());

         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  class_2338 base = origin.method_10069(x, y, z);
                  if (CrystalUtils.isBase(base) && this.usableBase(base, CrystalUtils.crystalPos(base))) {
                     this.scanBases.add(base);
                  }
               }
            }
         }

         return this.scanBases;
      }
   }

   private boolean attack(class_1511 crystal, AutoCrystal.Aim aim) {
      Runnable action = () -> {
         if ((Boolean)this.doBreak.get() && !crystal.method_31481() && Stealth.allowsEntity(crystal)) {
            if (LegitPlace.confirmEntity(crystal.method_5829(), aim.yaw(), aim.pitch(), this.breakReach()) != null) {
               float selfDamage = DamageUtils.crystalDamage(this.mc.field_1724, crystal.method_73189());
               if (this.selfDamageOk(selfDamage, (Double)this.maxBreakSelfDamage.get(), EntityUtils.getTotalHealth(this.mc.field_1724), false)) {
                  if (this.crystalHitSlot() == this.mc.field_1724.method_31548().method_67532()) {
                     if (this.spend(this.breakBudget, Stealth.actionCost(), false)) {
                        HotbarSwap.syncSelected();
                        // onClickSent records crystal hits from other sources; this one is recorded below.
                        this.sendingOwnHit = true;

                        try {
                           this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(crystal, this.mc.field_1724.method_5715()));
                        } finally {
                           this.sendingOwnHit = false;
                        }

                        // The server resets its attack cooldown on this hit; keep the client's in step, like vanilla's
                        // attackEntity, or the next sword hit looks charged here and lands weak there.
                        this.mc.field_1724.method_7350();
                        if ((Boolean)this.swing.get()) {
                           this.mc.field_1724.method_6104(class_1268.field_5808);
                        } else {
                           this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
                        }

                        KnockbackPredictor.afterAttack();
                        this.attacked.sent(crystal.method_5628(), this.clientTicks);
                        this.hitBases.put(crystal.method_24515().method_10074(), this.clientTicks + ownStep());
                        this.replaceBase = crystal.method_24515().method_10074();
                        this.replaceLook = aim;
                        this.replaceTick = this.clientTicks;
                        class_243 at = crystal.method_73189();

                        for (class_1657 player : this.candidates) {
                           if (!player.method_31481()) {
                              float raw;
                              if (player == this.target) {
                                 class_238 box = this.predictedBox(false);
                                 raw = box == null ? BlastShield.crystalRawDamage(player, at) : BlastShield.crystalRawDamage(feetOf(box), box, at);
                              } else {
                                 raw = BlastShield.crystalRawDamage(player, at);
                              }

                              if (raw > 0.0F) {
                                 this.recordOwnHit(player.method_5628(), raw);
                              }
                           }
                        }

                        this.placeAimCache.clear();
                        this.hitAimCache.clear();
                        this.hittableCache.clear();
                     }
                  }
               }
            }
         }
      };
      return this.turnAndRun(aim, action, true);
   }

   private FindItemResult findCrystals(class_3965 hit) {
      class_1799 main = this.mc.field_1724.method_6047();
      if (main.method_31574(class_1802.field_8301)) {
         return new FindItemResult(this.mc.field_1724.method_31548().method_67532(), main.method_7947());
      } else {
         class_1799 off = this.mc.field_1724.method_6079();
         return off.method_31574(class_1802.field_8301) && !InventoryGuard.offhandInFlight() && VanillaClick.reaches(hit, class_1268.field_5810)
            ? new FindItemResult(40, off.method_7947())
            : InvUtils.find(stack -> stack.method_31574(class_1802.field_8301), 0, 8);
      }
   }

   private int crystalHitSlot() {
      int selected = this.mc.field_1724.method_31548().method_67532();
      if (this.breaksCrystal(this.mc.field_1724.method_31548().method_5438(selected))) {
         return selected;
      } else if ((Boolean)this.antiWeakness.get() && this.switchMode.get() != AutoCrystal.SwitchMode.None) {
         int best = -1;
         double bestDamage = 0.0;

         for (int i = 0; i <= 8; i++) {
            class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
            if (this.breaksCrystal(stack)) {
               double damage = this.attackDamage(stack);
               if (best == -1 || damage > bestDamage) {
                  best = i;
                  bestDamage = damage;
               }
            }
         }

         return best;
      } else {
         return -1;
      }
   }

   private boolean breaksCrystal(class_1799 stack) {
      return stabs(stack) ? false : this.attackDamage(stack) > 0.0 || Utils.getEnchantmentLevel(stack, class_1893.field_9118) > 0;
   }

   private static boolean stabs(class_1799 stack) {
      return stack.method_57826(class_9334.field_63631);
   }

   // What the hit about to go out deals before armour, the amount vanilla compares the next hit against inside the
   // damage window: attack damage for the charge, sharpness, and a critical hit when one is possible. Errs high, so a
   // crystal is never counted for more than it adds.
   private float meleeRaw() {
      float charge = this.mc.field_1724.method_7261(0.5F);
      double damage = this.mc.field_1724.method_45325(class_5134.field_23721) * (0.2 + charge * charge * 0.8);
      int sharpness = Utils.getEnchantmentLevel(this.mc.field_1724.method_6047(), class_1893.field_9118);
      if (sharpness > 0) {
         damage += (1.0 + 0.5 * (sharpness - 1)) * charge;
      }

      if (charge > 0.9F && !this.mc.field_1724.method_24828()) {
         damage *= 1.5;
      }

      return (float)damage;
   }

   private double attackDamage(class_1799 stack) {
      double damage = 1.0;
      class_9285 modifiers = (class_9285)stack.method_58694(class_9334.field_49636);
      if (modifiers != null) {
         double[] added = new double[]{0.0};
         modifiers.method_57482(class_1304.field_6173, (attribute, modifier) -> {
            if (attribute == class_5134.field_23721 && modifier.comp_2450() == class_1323.field_6328) {
               added[0] += modifier.comp_2449();
            }
         });
         damage += added[0];
      }

      class_1293 strength = this.mc.field_1724.method_6112(class_1294.field_5910);
      if (strength != null) {
         damage += 3.0 * (strength.method_5578() + 1);
      }

      class_1293 weakness = this.mc.field_1724.method_6112(class_1294.field_5911);
      if (weakness != null) {
         damage -= 4.0 * (weakness.method_5578() + 1);
      }

      return Math.max(0.0, damage);
   }

   private void placeAt(class_2338 base, AutoCrystal.Aim aim) {
      double reach = this.placeReach();
      Runnable action = () -> {
         class_3965 hitResult = LegitPlace.confirmCrystal(base, aim.yaw(), aim.pitch(), reach);
         if (hitResult != null) {
            FindItemResult crystals = this.findCrystals(hitResult);
            if (crystals.found()) {
               if ((Boolean)this.place.get() && this.target != null && !this.target.method_31481()) {
                  if (this.switchMode.get() != AutoCrystal.SwitchMode.None || crystals.getHand() != null) {
                     if (CrystalUtils.canPlace(base, this.legacyRules(), this.goneWhenSent)) {
                        float selfDamage = DamageUtils.crystalDamage(this.mc.field_1724, CrystalUtils.crystalPos(base));
                        if (this.selfDamageOk(selfDamage, (Double)this.maxSelfDamage.get(), EntityUtils.getTotalHealth(this.mc.field_1724), true)) {
                           double cost = Stealth.actionCost();
                           if (this.placeBudget.canAfford(cost) && ClickGate.canUse()) {
                              HotbarSwap silent = null;
                              if (crystals.getHand() == null) {
                                 if (this.turn.heldByOther(TURN_OWNER)) {
                                    this.turnBusy = true;
                                    return;
                                 }

                                 if (this.weaponWaits && !this.breaksCrystal(this.mc.field_1724.method_31548().method_5438(crystals.slot()))) {
                                    return;
                                 }

                                 if (this.switchMode.get() == AutoCrystal.SwitchMode.Silent) {
                                    silent = HotbarSwap.silently(crystals.slot());
                                    if (!silent.ready()) {
                                       return;
                                    }
                                 } else {
                                    int from = HotbarSwap.homeSlot();
                                    if (!HotbarSwap.select(crystals.slot())) {
                                       return;
                                    }

                                    this.tookHotbar(from, crystals.slot());
                                 }

                                 if (ClickGate.slotChangedThisTick()) {
                                    if (silent != null) {
                                       silent.back();
                                    }

                                    return;
                                 }
                              }

                              if (!this.spend(this.placeBudget, cost, true)) {
                                 if (silent != null) {
                                    silent.back();
                                 }
                              } else {
                                 // The client refuses a crystal where the one just hit still stands (a same-tick
                                 // replace), but the click still goes out and the server, having taken the hit first,
                                 // places it. What counts is whether a click left.
                                 int usesBefore = ClickGate.usesThisTick();
                                 class_1268 acted = VanillaClick.use(hitResult, (Boolean)this.swing.get());
                                 if (acted != null || ClickGate.usesThisTick() > usesBefore) {
                                    this.placed.sent(base, this.clientTicks);
                                 }

                                 if (silent != null) {
                                    silent.back();
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      };
      this.turnAndRun(aim, action, false);
   }

   private boolean turnAndRun(AutoCrystal.Aim aim, Runnable action, boolean breaking) {
      Runnable current = this.activeAction(action);
      if (!this.turnsFor(aim, breaking)) {
         current.run();
         return true;
      } else {
         return this.turn.turnTo(TURN_OWNER, aim.yaw(), aim.pitch(), 50, current);
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
      if (this.target == null || this.mc.field_1724 == null) {
         return this.idleReason;
      } else if (!this.heightOk()) {
         boolean low = this.heightDifference() < (Double)this.minHeight.get();
         return this.target.method_5477().getString() + (low ? " (too low)" : " (too high)");
      } else if (this.turnHeldForYou) {
         return this.target.method_5477().getString() + " (waits for you)";
      } else if (this.turnBusy) {
         return this.target.method_5477().getString() + " (turn busy)";
      } else {
         class_1657 waitingFor = this.reacting.shown();
         if (waitingFor != null) {
            return waitingFor.method_5477().getString() + " (reacting)";
         } else {
            return this.popWindow() != null
               ? this.target.method_5477().getString() + " pop"
               : String.format("%s %.1f", this.target.method_5477().getString(), this.renderDamage);
         }
      }
   }

   private record Aim(double yaw, double pitch) {
   }

   public static enum AimMode {
      Off,
      Angle,
      Fov,
      Crosshair;
   }

   private static enum BreakOutcome {
      NOTHING,
      HELD,
      BLOCKED,
      SWAPPED,
      TURNING,
      DONE;
   }

   static enum CrystalWait {
      NONE,
      OWN_STEP,
      REACTION;

      static AutoCrystal.CrystalWait of(boolean answered, Integer hittableFrom, int now) {
         if (!answered) {
            return REACTION;
         } else {
            return hittableFrom != null && now < hittableFrom ? OWN_STEP : NONE;
         }
      }
   }

   private record CrystalWorth(int id) {
   }

   static final class CrystalsSeen<B> {
      private Map<Integer, B> last = new HashMap<>();

      void update(Map<Integer, B> now, IntPredicate brokenByUs, Consumer<B> freed) {
         for (Entry<Integer, B> entry : this.last.entrySet()) {
            if (!now.containsKey(entry.getKey()) && !brokenByUs.test(entry.getKey())) {
               freed.accept(entry.getValue());
            }
         }

         this.last = now;
      }

      void clear() {
         this.last = new HashMap<>();
      }
   }

   private static enum Gate {
      PENDING("wartet auf Server"),
      CANT_PLACE("canPlace"),
      REACH("Reichweite/Sicht"),
      VIEW("ausserhalb view-angle"),
      AIM("Aim-Modus"),
      LOW_DAMAGE("Schaden<min"),
      SELF_DAMAGE("Selbstschaden"),
      NO_FACE("keine Flaeche im Winkel"),
      UNBREAKABLE("danach nicht schlagbar");

      final String label;

      private Gate(String label) {
         this.label = label;
      }
   }

   private record HealthDrop(int id) {
   }

   static final class HotbarLoan {
      private int home = -1;
      private int held = -1;

      void took(int from, int slot) {
         if (this.home == -1 && from != slot && from >= 0) {
            this.home = from;
         }

         this.held = slot;
      }

      boolean active() {
         return this.home != -1;
      }

      int home() {
         return this.home;
      }

      boolean untouched(int current) {
         return this.active() && current == this.held;
      }

      void clear() {
         this.home = -1;
         this.held = -1;
      }
   }

   static final class InFlight<K> {
      private final Map<K, Integer> sentAt = new HashMap<>();

      void sent(K key, int tick) {
         this.sentAt.put(key, tick);
      }

      boolean contains(K key) {
         return this.sentAt.containsKey(key);
      }

      Set<K> keys() {
         return this.sentAt.keySet();
      }

      boolean isEmpty() {
         return this.sentAt.isEmpty();
      }

      void clear() {
         this.sentAt.clear();
      }

      Set<K> expire(int tick, int window, Predicate<K> resolved) {
         Set<K> answered = new HashSet<>();
         this.sentAt.entrySet().removeIf(entry -> {
            if (resolved.test(entry.getKey())) {
               answered.add(entry.getKey());
               return true;
            } else {
               return tick - entry.getValue() > window;
            }
         });
         return answered;
      }
   }

   private static enum Noticed {
      QUIET,
      WEAKNESS;
   }

   static final class OwnCrystals<B> {
      private final Map<Integer, AutoCrystal.OwnCrystals.Own<B>> byId = new HashMap<>();

      boolean appeared(int id, B base, int from) {
         return this.byId.putIfAbsent(id, new AutoCrystal.OwnCrystals.Own<>(base, from)) == null;
      }

      Integer hittableFrom(int id) {
         AutoCrystal.OwnCrystals.Own<B> own = this.byId.get(id);
         return own == null ? null : own.hittableFrom();
      }

      void sweep(IntPredicate gone, Consumer<B> standing, IntConsumer removed) {
         this.byId.entrySet().removeIf(entry -> {
            if (gone.test(entry.getKey())) {
               removed.accept(entry.getKey());
               return true;
            } else {
               standing.accept(entry.getValue().base());
               return false;
            }
         });
      }

      void clear() {
         this.byId.clear();
      }

      private record Own<B>(B base, int hittableFrom) {
      }
   }

   private record OwnHit(int tick, float raw) {
   }

   public static enum Placement {
      Auto,
      Modern,
      Legacy;
   }

   static final class Reacting<P> {
      private P waitingFor;
      private boolean engaged;

      void reset() {
         this.waitingFor = null;
         this.engaged = false;
      }

      void waitingFor(P player) {
         if (this.waitingFor == null) {
            this.waitingFor = player;
         }
      }

      void engaged() {
         this.engaged = true;
      }

      P shown() {
         return this.engaged ? null : this.waitingFor;
      }
   }

   private record SeenHealth(float health, float absorption) {
   }

   private record Spot(class_2338 base, AutoCrystal.Aim aim) {
   }

   static final class SpotPick<S> {
      private S best;
      private double bestScore = Double.NEGATIVE_INFINITY;
      private float bestDamage;
      private S waiting;
      private double waitingScore = Double.NEGATIVE_INFINITY;
      private float waitingDamage;

      double beat() {
         return this.bestScore;
      }

      boolean wouldTake(double score, boolean ready) {
         return ready ? score > this.bestScore : score > this.waitingScore && score > this.bestScore;
      }

      void offer(S spot, double score, float damage, boolean ready) {
         if (this.wouldTake(score, ready)) {
            if (ready) {
               this.best = spot;
               this.bestScore = score;
               this.bestDamage = damage;
            } else {
               this.waiting = spot;
               this.waitingScore = score;
               this.waitingDamage = damage;
            }
         }
      }

      S best() {
         return this.best;
      }

      float bestDamage() {
         return this.bestDamage;
      }

      S waiting() {
         return this.waiting != null && this.waitingScore > this.bestScore ? this.waiting : null;
      }

      float waitingDamage() {
         return this.waitingDamage;
      }
   }

   public static enum SwitchMode {
      None,
      Hotbar,
      Silent;
   }

   private static final class TargetFigures {
      final Map<class_243, Float> place = new HashMap<>();
      final Map<class_243, Float> brk = new HashMap<>();
      class_238 placeBox;
      class_238 breakBox;
      boolean placeBoxDone;
      boolean breakBoxDone;
      List<class_2338> bases;
   }

   public static enum Weapon {
      Sword,
      Axe,
      MostDamage;
   }

   static final class WorthMemory {
      private final Set<Integer> known = new HashSet<>();
      private final Set<Integer> now = new HashSet<>();
      private final Set<Integer> clocked = new HashSet<>();

      void placedForIt(int id) {
         this.known.add(id);
      }

      boolean worth(int id, IntPredicate clock) {
         this.now.add(id);
         if (this.known.contains(id)) {
            return true;
         } else {
            this.clocked.add(id);
            return clock.test(id);
         }
      }

      void settle(IntConsumer forget) {
         this.known.retainAll(this.now);
         this.clocked.removeIf(id -> {
            if (this.now.contains(id)) {
               return false;
            } else {
               forget.accept(id);
               return true;
            }
         });
         this.now.clear();
      }

      void clear() {
         this.known.clear();
         this.now.clear();
         this.clocked.clear();
      }
   }
}
