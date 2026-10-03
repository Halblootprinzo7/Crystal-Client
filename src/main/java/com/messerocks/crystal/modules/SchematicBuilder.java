package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.mixin.BlockItemInvoker;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.ClickGate;
import com.messerocks.crystal.utils.HotbarSwap;
import com.messerocks.crystal.utils.InventoryGuard;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.LitematicaPlacement;
import com.messerocks.crystal.utils.ReactionClock;
import com.messerocks.crystal.utils.Schematic;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaClick;
import com.messerocks.crystal.utils.VanillaLimits;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2478;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_3341;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_7743;
import net.minecraft.class_9288;
import net.minecraft.class_9334;
import net.minecraft.class_2350.class_2351;

public class SchematicBuilder extends CrystalModule {
   private static final int MAX_FAILED_CLICKS = 2;
   private static final int MAX_SEARCHES_PER_TICK = 32;
   private static final Object TURN_OWNER = new Object();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBuild = this.settings.createGroup("Build");
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<SchematicBuilder.Mode> mode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<SchematicBuilder.Mode>()
            .name("mode")
            .description(
               "Automatic finds the next missing block itself and turns to it. Crosshair is a semi-automatic printer: it never turns and only places what your own crosshair lands on - a real face of a real block, never into the air - and only when the block that would go there is the one the schematic wants, oriented the way it wants it, from where you look. Swaps to the right item from the hotbar on its own."
            )
            .defaultValue(SchematicBuilder.Mode.Automatic)
            .build()
      );
   private final Setting<String> file = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("schematic")).description("File from your Litematica schematics folder."))
                  .supplier(() -> Schematic.schematicNames(this.schematicsFolder()).toArray(new String[0]))
                  .defaultValue(""))
               .onChanged(v -> {
                  this.loaded = null;
                  this.resetForNewWorld();
               }))
            .build()
      );
   private final Setting<SchematicBuilder.OriginMode> originMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("origin"))
                     .description("Litematica reads the spot from its own placement, so the build lands exactly on the ghost preview you already see."))
                  .defaultValue(SchematicBuilder.OriginMode.Litematica))
               .onChanged(v -> this.anchor = null))
            .build()
      );
   private final Setting<class_2338> manualOrigin = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BlockPosSetting.Builder)((meteordevelopment.meteorclient.settings.BlockPosSetting.Builder)((meteordevelopment.meteorclient.settings.BlockPosSetting.Builder)((meteordevelopment.meteorclient.settings.BlockPosSetting.Builder)((meteordevelopment.meteorclient.settings.BlockPosSetting.Builder)new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                           .name("manual-origin"))
                        .description("Corner the schematic is built from."))
                     .defaultValue(class_2338.field_10980))
                  .visible(() -> this.originMode.get() == SchematicBuilder.OriginMode.Manual))
               .onChanged(v -> this.anchor = null))
            .build()
      );
   private final Setting<String> placementName = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                           .name("placement-name"))
                        .description(
                           "Build the Litematica placement with this name. Empty takes the one selected in Litematica, or the first one of this schematic."
                        ))
                     .defaultValue(""))
                  .visible(() -> this.originMode.get() == SchematicBuilder.OriginMode.Litematica))
               .onChanged(v -> this.anchor = null))
            .build()
      );
   private final Setting<Boolean> followPlacement = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("follow-placement"))
                     .description("When you move, switch or edit the placement in Litematica while building, start over at the new spot."))
                  .defaultValue(true))
               .visible(() -> this.originMode.get() == SchematicBuilder.OriginMode.Litematica))
            .build()
      );
   private final Setting<Boolean> allowOtherWorld = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("allow-other-world-placement"))
                     .description(
                        "Without Litematica running there is no telling which of its config files is this world. On takes the newest one anyway - usually the world you last left."
                     ))
                  .defaultValue(false))
               .visible(() -> this.originMode.get() == SchematicBuilder.OriginMode.Litematica))
            .build()
      );
   private final Setting<Double> range = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("How far you place from. Never further than the server's block reach, which is also where your own crosshair ends."))
            .defaultValue(4.5)
            .min(1.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Double> speed = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("blocks-per-second"))
               .description(
                  "Placements per second, at most one a tick. Each one is priced a little differently by Stealth's timing-jitter, so they never come on an exact beat. Holding right click in vanilla gives 5; 10 to 15 is a fast builder clicking - the top of what a hand does. Stealth's max-actions-per-second caps it as well."
               ))
            .defaultValue(8.0)
            .range(0.5, 15.0)
            .sliderRange(0.5, 15.0)
            .build()
      );
   private final Setting<Boolean> bottomUp = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("bottom-up"))
                  .description("Work layer by layer from the ground. Off goes purely by distance, which will ask for blocks with nothing under them."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description(
                     "Turn to each block as it is placed. A click only ever goes out along the rotation the server really has, so off builds just what your own look lands on. Stealth's force-rotate turns it back on."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<SchematicBuilder.Orientation> orientation = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("orientation"))
                  .description(
                     "Exact only clicks where the face, the spot on it and the look towards that spot make vanilla put down the state the schematic has, so stairs, slabs, logs, furnaces and pistons come out right. The look is always the one towards the spot clicked, so a stair that has to face away from you waits until you stand where it can. Ignore takes the click needing the least head movement, whatever orientation that gives."
                  ))
               .defaultValue(SchematicBuilder.Orientation.Exact))
            .build()
      );
   private final Setting<Boolean> swing = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Show the arm swing on screen. The swing packet goes out either way, wherever vanilla sends one."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> rescanInterval = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("rescan-interval"))
                  .description("Ticks between rescans of what is still missing. The scan is the expensive part."))
               .defaultValue(20))
            .min(1)
            .sliderMax(100)
            .build()
      );
   private final Setting<Boolean> onlyWhatIHave = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("skip-missing-items"))
                  .description("Skip blocks you have no item for instead of stalling on them."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> strictStates = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("strict-states"))
                  .description(
                     "Count a block as right only when every property matches, including what neighbours decide (stair corners, fence sides, leaf distance). For checking a finished build; normally this keeps the builder from ever finishing."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> chatProgress = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("progress-in-chat"))
                  .description("Report progress and what is missing."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Keybind> listBind = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)((meteordevelopment.meteorclient.settings.KeybindSetting.Builder)new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                     .name("material-list-bind"))
                  .description("Prints what the whole schematic needs and what you are short of."))
               .defaultValue(Keybind.none()))
            .action(this::printMaterialList)
            .build()
      );
   private final Setting<Boolean> countShulkers = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("count-shulkers"))
                  .description("Count blocks sitting inside shulker boxes you are carrying as stock. They still have to be unpacked before they can be placed."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> debug = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug"))
                  .description("Report the scan breakdown every rescan, so a stalled build says why."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> pauseInCombat = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-in-combat"))
                  .description("Stop building while AutoCrystal has a target, so the builder does not swap your hotbar or turn your head mid-fight."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> pausePlayerRange = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("pause-player-range"))
               .description("Pause while a player who is not a friend is this close. 0 turns it off."))
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(64.0)
            .build()
      );
   private final Setting<Double> pauseHealth = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("pause-below-health"))
               .description("Pause while health plus absorption is below this. 0 turns it off."))
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Integer> resumeDelay = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("resume-delay"))
                  .description("Ticks to wait after the reason for a pause is gone, so building does not flicker on between two hits."))
               .defaultValue(40))
            .min(0)
            .sliderMax(200)
            .build()
      );
   private final Setting<Integer> buildSlot = this.sgSafety
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("build-slot"))
                  .description(
                     "Hotbar slot (1-9) to swap backpack materials into when no hotbar slot is free. Whatever is there goes to where the material came from. 0 only ever uses free slots."
                  ))
               .defaultValue(0))
            .range(0, 9)
            .sliderRange(0, 9)
            .build()
      );
   private final Setting<Boolean> renderBounds = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-bounds"))
                  .description("Outline the whole schematic."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> renderNext = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-queue"))
                  .description("Outline the blocks that are up next."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> renderCount = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("queue-shown"))
                     .description("How many upcoming blocks to outline."))
                  .defaultValue(16))
               .min(1)
               .sliderMax(64)
               .visible(this.renderNext::get))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("shape-mode"))
                  .description("How shapes are rendered."))
               .defaultValue(ShapeMode.Lines))
            .build()
      );
   private final Setting<SettingColor> boundsColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("bounds-color"))
               .description("Colour of the schematic outline."))
            .defaultValue(new SettingColor(120, 160, 255, 120))
            .build()
      );
   private final Setting<SettingColor> queueSideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("queue-side-color"))
               .description("Side colour of queued blocks."))
            .defaultValue(new SettingColor(120, 255, 160, 40))
            .build()
      );
   private final Setting<SettingColor> queueLineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("queue-line-color"))
               .description("Line colour of queued blocks."))
            .defaultValue(new SettingColor(120, 255, 160, 200))
            .build()
      );
   private Schematic loaded;
   private Schematic building;
   private class_2338 anchor;
   // Crosshair mode: the schematic by world position, so the block under your crosshair is looked up the moment you
   // look at it instead of waiting for the next rescan to put it on the list.
   private Map<class_2338, Schematic.Entry> byWorld;
   private Schematic byWorldFor;
   private class_2338 byWorldAt;
   private LitematicaPlacement.Placement anchoredPlacement;
   private int followTimer;
   private final List<Schematic.Entry> pending = new ArrayList<>();
   private final ActionBudget budget = new ActionBudget();
   private int rescanTimer;
   private int placedThisSession;
   private int attemptsSinceScan;
   private int pendingIndex;
   private boolean movedMaterial;
   private Object buildWorld;
   private String missingReport;
   private SchematicBuilder.Tally tally = new SchematicBuilder.Tally();
   private String lastScan;
   private final List<Schematic.Entry> remaining = new ArrayList<>();
   private int idleTicks;
   private String lastStallReason;
   private class_2338 scanStand;
   private final Map<class_2338, Integer> failedClicks = new HashMap<>();
   private final Set<class_2338> stackOnto = new HashSet<>();
   private final Set<class_2338> wrongSide = new HashSet<>();
   private final Set<class_2338> outOfView = new HashSet<>();
   private class_243 scanLook;
   private boolean planOutOfView;
   private final TurnProgress turn = TurnProgress.SHARED;
   private SchematicBuilder.Job job;
   private double nextCost = Double.NaN;
   private SchematicBuilder.Miss planMiss = SchematicBuilder.Miss.NO_CLICK;
   private SchematicBuilder.Outcome outcome = SchematicBuilder.Outcome.WAIT;
   private class_2338 signPos;
   private int signPlacedAge;
   private final ReactionClock signClock = new ReactionClock();
   private int ticks;
   private int holdUntil;
   private static final int SETTLE_TICKS = 4;
   private int searchesThisTick;
   private int inReach;
   private String pauseReason;
   private int resumeTicks;
   private static final int MOVED_RESCAN = 5;
   private static final double TURNED_RESCAN = 15.0;
   private static final Map<class_2248, Boolean> PLACEABLE = new HashMap<>();

   public SchematicBuilder() {
      super(CrystalAddon.CATEGORY, "schematic-builder", "Builds a Litematica schematic on its own.");
   }

   private Path schematicsFolder() {
      return this.mc.field_1697.toPath().resolve("schematics");
   }

   private Path litematicaConfigFolder() {
      return this.mc.field_1697.toPath().resolve("config").resolve("litematica");
   }

   private boolean resolveAnchor() {
      LitematicaPlacement.Placement placement = null;
      class_2338 resolved;
      switch ((SchematicBuilder.OriginMode)this.originMode.get()) {
         case Litematica:
            placement = this.findPlacement();
            if (placement == null) {
               return false;
            }

            if (!placement.isPlain()) {
               this.error(
                  "That placement or one of its regions is rotated (%s) or mirrored (%s), which this builder does not apply.",
                  new Object[]{placement.rotation(), placement.mirror()}
               );
               this.toggle();
               return false;
            }

            resolved = placement.origin();
            break;
         case AtFeet:
            if (this.mc.field_1724 == null) {
               return false;
            }

            resolved = this.mc.field_1724.method_24515();
            break;
         case Manual:
            resolved = (class_2338)this.manualOrigin.get();
            break;
         default:
            return false;
      }

      Schematic result = placement == null ? this.loaded : this.loaded.withRegionOverrides(placement.regionOverrides());
      if (result.blockCount() == 0) {
         this.error("Nothing to build: every region of that placement is switched off in Litematica.", new Object[0]);
         this.toggle();
         return false;
      } else {
         this.anchor = resolved;
         this.anchoredPlacement = placement;
         this.building = result;
         this.followTimer = 20;
         this.pending.clear();
         this.pendingIndex = 0;
         this.rescanTimer = 0;
         this.dropJob();
         if (placement != null && (Boolean)this.chatProgress.get()) {
            String name = placement.name().isEmpty() ? "" : " \"" + placement.name() + "\"";
            this.info(
               "Using Litematica's placement%s at %d %d %d%s.",
               new Object[]{
                  name,
                  this.anchor.method_10263(),
                  this.anchor.method_10264(),
                  this.anchor.method_10260(),
                  placement.source() == LitematicaPlacement.Source.Live ? "" : " (from its config file)"
               }
            );
         }

         return true;
      }
   }

   private LitematicaPlacement.Placement findPlacement() {
      String name = (String)this.placementName.get();
      String named = name.isBlank() ? "" : " named \"" + name.trim() + "\"";
      if (LitematicaPlacement.Live.available()) {
         try {
            LitematicaPlacement.Placement live = LitematicaPlacement.Live.find((String)this.file.get(), name);
            if (live == null) {
               this.error("Litematica has no enabled placement of %s%s in this world. Place it first, or switch origin.", new Object[]{this.file.get(), named});
               this.toggle();
            }

            return live;
         } catch (IllegalStateException var5) {
            CrystalAddon.LOG.warn("Reading Litematica's placements failed, using its config file.", var5);
            this.warning(
               "Could not read Litematica's placements directly, using its config file - a placement moved this session is not in there yet.", new Object[0]
            );
         }
      }

      String worldFile = LitematicaPlacement.currentWorldFileName();
      LitematicaPlacement.Placement placement = worldFile == null
         ? null
         : LitematicaPlacement.findInWorldFile(this.litematicaConfigFolder().resolve(worldFile), (String)this.file.get(), name);
      if (placement != null) {
         return placement;
      } else {
         if ((Boolean)this.allowOtherWorld.get()) {
            placement = LitematicaPlacement.find(this.litematicaConfigFolder(), (String)this.file.get(), name);
            if (placement != null) {
               this.warning("Taking the placement from Litematica's newest config file, which may be another world.", new Object[0]);
               return placement;
            }
         }

         if (worldFile == null && !(Boolean)this.allowOtherWorld.get()) {
            this.error(
               "Without Litematica running there is no telling which placement belongs to this world. Turn on allow-other-world-placement, or switch origin.",
               new Object[0]
            );
         } else {
            this.error(
               "Litematica has no placement of %s%s for this world. Place it there first, or switch origin to AtFeet.", new Object[]{this.file.get(), named}
            );
         }

         this.toggle();
         return null;
      }
   }

   private boolean placementMoved() {
      if (this.originMode.get() == SchematicBuilder.OriginMode.Litematica && (Boolean)this.followPlacement.get()) {
         if (this.anchoredPlacement == null || this.anchoredPlacement.source() != LitematicaPlacement.Source.Live) {
            return false;
         } else if (--this.followTimer > 0) {
            return false;
         } else {
            this.followTimer = 20;

            LitematicaPlacement.Placement now;
            try {
               now = LitematicaPlacement.Live.find((String)this.file.get(), (String)this.placementName.get());
            } catch (IllegalStateException var3) {
               return false;
            }

            if (this.anchoredPlacement.sameSpot(now)) {
               return false;
            } else {
               if (now != null && (Boolean)this.chatProgress.get()) {
                  this.info("Placement moved, following it.", new Object[0]);
               }

               this.resetForNewWorld();
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void resetForNewWorld() {
      this.anchor = null;
      this.anchoredPlacement = null;
      this.building = null;
      this.pending.clear();
      this.remaining.clear();
      this.failedClicks.clear();
      this.stackOnto.clear();
      this.wrongSide.clear();
      this.outOfView.clear();
      this.scanStand = null;
      this.scanLook = null;
      this.inReach = 0;
      this.dropJob();
      this.nextCost = Double.NaN;
      this.signPos = null;
      this.signClock.clear();
      this.holdUntil = 0;
      this.rescanTimer = 0;
      this.idleTicks = 0;
      this.lastStallReason = null;
      this.pendingIndex = 0;
      this.attemptsSinceScan = 0;
      this.tally = new SchematicBuilder.Tally();
      this.budget.reset();
   }

   public void onActivate() {
      this.loaded = null;
      this.buildWorld = null;
      this.placedThisSession = 0;
      this.missingReport = null;
      this.ticks = 0;
      this.pauseReason = null;
      this.resumeTicks = 0;
      this.resetForNewWorld();
   }

   public void onDeactivate() {
      this.resetForNewWorld();
      this.buildWorld = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.resetForNewWorld();
      this.buildWorld = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         this.ticks++;
         this.signClock.tick();
         if (this.signPos != null) {
            if (this.mc.field_1755 instanceof class_7743 editor) {
               if (this.signClock.ready(this.signPos)) {
                  this.signClock.forget(this.signPos);
                  this.signPos = null;
                  this.holdUntil = this.ticks + Stealth.pace(4);
                  editor.method_25419();
               }
            } else {
               int since = this.mc.field_1724.field_6012 - this.signPlacedAge;
               if (since < 0 || since > 40) {
                  this.signPos = null;
               }
            }
         }

         if (this.mc.field_1755 == null && InventoryGuard.inventoryFree()) {
            if (this.buildWorld != null && this.buildWorld != this.mc.field_1687) {
               if ((Boolean)this.chatProgress.get()) {
                  this.info("World changed, re-reading the placement.", new Object[0]);
               }

               this.resetForNewWorld();
            }

            this.buildWorld = this.mc.field_1687;
            if (this.loaded != null || this.load()) {
               if (this.anchor != null || this.resolveAnchor()) {
                  if (!this.placementMoved()) {
                     if (!this.holdForSafety()) {
                        this.budget.update((Double)this.speed.get(), 1);
                        if (!this.outOfView.isEmpty() && this.rescanTimer > 5 && this.turnedSinceScan()) {
                           this.rescanTimer = 5;
                        }

                        if (this.rescanTimer > 0) {
                           this.rescanTimer--;
                        } else {
                           this.rescan();
                           this.rescanTimer = (Integer)this.rescanInterval.get();
                           if ((Boolean)this.debug.get()) {
                              this.info(
                                 "Scan: %s | origin %d %d %d",
                                 new Object[]{this.lastScan, this.anchor.method_10263(), this.anchor.method_10264(), this.anchor.method_10260()}
                              );
                           }
                        }

                        if (this.ticks < this.holdUntil) {
                           this.idleTicks = 0;
                        } else if (InventoryGuard.offhandInFlight()) {
                           this.idleTicks = 0;
                        } else {
                           this.movedMaterial = false;
                           this.searchesThisTick = 0;
                           SchematicBuilder.ClickStep step = this.placeNext();
                           if (step != SchematicBuilder.ClickStep.NONE) {
                              this.idleTicks = 0;
                              this.lastStallReason = null;
                           } else if (!this.movedMaterial) {
                              if (this.pendingIndex < this.pending.size()) {
                                 this.idleTicks = 0;
                              } else if (this.attemptsSinceScan > 0) {
                                 this.rescanTimer = 0;
                              } else if (this.tally.complete()) {
                                 if ((Boolean)this.chatProgress.get() && this.placedThisSession > 0) {
                                    this.info("Schematic complete: %d blocks placed%s.", new Object[]{this.placedThisSession, this.tally.leftOutSummary()});
                                 }

                                 this.toggle();
                              } else {
                                 this.idleTicks++;
                                 if (!this.mc.field_1724.method_24515().equals(this.scanStand) && this.rescanTimer > 5) {
                                    this.rescanTimer = 5;
                                 }

                                 // Idle is normal for the crosshair printer: it waits for you to look at the next block.
                                 if (this.idleTicks % 60 == 0 && this.mode.get() != SchematicBuilder.Mode.Crosshair) {
                                    this.explainStall();
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         } else {
            this.dropJob();
         }
      }
   }

   private boolean turnedSinceScan() {
      return this.scanLook == null || SchematicBuilder.FaceAim.turnedBy(this.scanLook, this.mc.field_1724.method_5828(1.0F), 15.0);
   }

   private boolean holdForSafety() {
      String reason = this.safetyReason();
      if (reason != null) {
         if (this.pauseReason == null && (Boolean)this.chatProgress.get()) {
            this.info("Pausing: %s.", new Object[]{reason});
         }

         this.pauseReason = reason;
         this.resumeTicks = (Integer)this.resumeDelay.get();
         this.dropJob();
         this.budget.reset();
         return true;
      } else if (this.resumeTicks > 0) {
         this.resumeTicks--;
         return true;
      } else {
         this.pauseReason = null;
         return false;
      }
   }

   private String safetyReason() {
      if ((Boolean)this.pauseInCombat.get()) {
         AutoCrystal autoCrystal = (AutoCrystal)Modules.get().get(AutoCrystal.class);
         if (autoCrystal != null && autoCrystal.getTarget() != null) {
            return "in combat";
         }
      }

      double health = (Double)this.pauseHealth.get();
      if (health > 0.0 && this.mc.field_1724.method_6032() + this.mc.field_1724.method_6067() < health) {
         return "low health";
      } else {
         double playerRange = (Double)this.pausePlayerRange.get();
         if (playerRange > 0.0) {
            for (class_1657 player : this.mc.field_1687.method_18456()) {
               if (player != this.mc.field_1724
                  && !player.method_7325()
                  && !Friends.get().isFriend(player)
                  && player.method_5858(this.mc.field_1724) <= playerRange * playerRange) {
                  return player.method_5477().getString() + " nearby";
               }
            }
         }

         return null;
      }
   }

   private void explainStall() {
      int wrongWay = this.wrongWayLeft();
      String reason;
      if (this.remaining.isEmpty()) {
         if (this.tally.noSlot > 0) {
            reason = String.format("the hotbar is full and %d blocks' items are only in the backpack. Free a hotbar slot or set build-slot", this.tally.noSlot);
         } else if (this.missingReport != null) {
            reason = "every remaining block needs an item you have none of loose: " + this.missingReport;
         } else {
            reason = this.tally.occupied + " blocks are taken by something else, which this builder does not break";
         }
      } else if (this.remainingOnlyWhereYouStand()) {
         reason = "the blocks left are where you are standing - step aside";
      } else if (!this.outOfView.isEmpty()) {
         reason = String.format(
            "%d blocks in reach are outside your view (Stealth's view-angle), and only what is in front of you is built - turn towards them, building picks up as they come into view",
            this.outOfView.size()
         );
      } else if (wrongWay > 0) {
         reason = String.format(
            "%d blocks cannot be put down the way the schematic has them from where you stand - which way they face comes from the way you look while clicking. Move to another side of them%s, or set orientation to Ignore",
            wrongWay,
            this.standHint()
         );
      } else if (!this.rotating()) {
         reason = String.format(
            "%d blocks left, and with rotate off only the one your own look lands on is placed - walk to them and look at them, or turn rotate on",
            this.remaining.size()
         );
      } else if (this.inReach > 0) {
         reason = String.format(
            "%d blocks in reach have no face you can see from here to click against. Move a little, or build what they rest on first", this.inReach
         );
      } else {
         reason = String.format("%d blocks left but none within %.1f - walk closer, building picks up as you get there", this.remaining.size(), this.reach());
      }

      if (!reason.equals(this.lastStallReason)) {
         this.warning("Not building: %s.", new Object[]{reason});
         this.info(
            "Scan: %s | origin %d %d %d", new Object[]{this.lastScan, this.anchor.method_10263(), this.anchor.method_10264(), this.anchor.method_10260()}
         );
         this.lastStallReason = reason;
      }
   }

   private boolean remainingOnlyWhereYouStand() {
      for (Schematic.Entry entry : this.remaining) {
         if (!this.occupiedByMe(this.anchor.method_10081(entry.offset()))) {
            return false;
         }
      }

      return !this.remaining.isEmpty();
   }

   private int wrongWayLeft() {
      int count = 0;

      for (Schematic.Entry entry : this.remaining) {
         if (this.wrongSide.contains(this.anchor.method_10081(entry.offset()))) {
            count++;
         }
      }

      return count;
   }

   private String standHint() {
      int asked = 0;

      for (Schematic.Entry entry : this.remaining) {
         class_2338 world = this.anchor.method_10081(entry.offset());
         if (this.wrongSide.contains(world)) {
            if (++asked > 8) {
               break;
            }

            class_2338 stand = this.standSpot(world, entry.state());
            if (stand != null) {
               return String.format(
                  " (the one at %d %d %d from around %d %d %d)",
                  world.method_10263(),
                  world.method_10264(),
                  world.method_10260(),
                  stand.method_10263(),
                  stand.method_10264(),
                  stand.method_10260()
               );
            }
         }
      }

      return "";
   }

   private class_2338 standSpot(class_2338 pos, class_2680 target) {
      if (!(target.method_26204().method_8389() instanceof class_1747 blockItem)) {
         return null;
      } else {
         class_1799 var26 = new class_1799(blockItem);
         class_2680 current = this.mc.field_1687.method_8320(pos);
         boolean stacking = this.stackOnto.contains(pos);
         boolean sixteen = target.method_28498(class_2741.field_12532);
         int looks = sixteen ? 16 : 4;
         int back = sixteen ? 3 : 2;

         for (int i = 0; i < looks; i++) {
            float yaw = i * 360.0F / looks;

            for (class_2350 toNeighbour : class_2350.values()) {
               class_2338 neighbour = pos.method_10093(toNeighbour);
               if (this.clickable(neighbour, pos)) {
                  class_243 face = class_243.method_24953(pos)
                     .method_1031(toNeighbour.method_10148() * 0.5, toNeighbour.method_10164() * 0.5, toNeighbour.method_10165() * 0.5);
                  double[] heights = toNeighbour.method_10166() == class_2351.field_11052 ? new double[]{0.0} : new double[]{-0.25, 0.25};

                  for (double dy : heights) {
                     class_3965 hit = new class_3965(face.method_1031(0.0, dy, 0.0), toNeighbour.method_10153(), neighbour, false);
                     class_2680 predicted = this.predict(pos, blockItem, var26, class_1268.field_5808, hit, yaw, 30.0);
                     if (predicted != null && this.fits(current, predicted, target, stacking)) {
                        return SchematicBuilder.FaceAim.standBack(pos, yaw, back);
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private boolean occupiedByMe(class_2338 pos) {
      return this.mc.field_1724.method_5829().method_994(new class_238(pos));
   }

   private boolean load() {
      String wanted = (String)this.file.get();
      if (wanted != null && !wanted.isBlank()) {
         for (Path path : Schematic.findSchematics(this.schematicsFolder())) {
            if (path.getFileName().toString().equals(wanted)) {
               try {
                  this.loaded = Schematic.load(path);
                  this.building = null;
                  this.anchor = null;
                  this.pending.clear();
                  this.rescanTimer = 0;
                  if ((Boolean)this.chatProgress.get()) {
                     this.info(
                        "Loaded %s by %s: %d blocks, %dx%dx%d.",
                        new Object[]{
                           this.loaded.name(),
                           this.loaded.author(),
                           this.loaded.blockCount(),
                           this.loaded.size().method_10263(),
                           this.loaded.size().method_10264(),
                           this.loaded.size().method_10260()
                        }
                     );

                     for (String warning : this.loaded.warnings()) {
                        this.warning("%s: %s.", new Object[]{wanted, warning});
                     }
                  }

                  return true;
               } catch (Exception var6) {
                  this.error("Could not read %s: %s", new Object[]{wanted, var6.getMessage()});
                  this.toggle();
                  return false;
               }
            }
         }

         this.error("No schematic called %s in %s.", new Object[]{wanted, this.schematicsFolder()});
         this.toggle();
         return false;
      } else {
         List<Path> available = Schematic.findSchematics(this.schematicsFolder());
         if (available.isEmpty()) {
            this.error("No .litematic files under %s.", new Object[]{this.schematicsFolder()});
         } else {
            this.error("Pick one in the schematic setting. %d found, e.g. %s", new Object[]{available.size(), available.get(0).getFileName()});
         }

         this.toggle();
         return false;
      }
   }

   private boolean matches(class_2680 current, class_2680 target) {
      return this.strictStates.get() ? current.equals(target) : Schematic.satisfies(current, target);
   }

   private void rescan() {
      this.pending.clear();
      this.remaining.clear();
      this.stackOnto.clear();
      this.outOfView.clear();
      this.pendingIndex = 0;
      this.attemptsSinceScan = 0;
      this.inReach = 0;
      this.scanLook = this.mc.field_1724.method_5828(1.0F);
      class_2338 stand = this.mc.field_1724.method_24515();
      if (!stand.equals(this.scanStand)) {
         this.failedClicks.clear();
         this.wrongSide.clear();
      }

      this.scanStand = stand;
      class_243 eyes = this.mc.field_1724.method_33571();
      double reachSq = this.reach() * this.reach();
      SchematicBuilder.Tally counts = new SchematicBuilder.Tally();
      Map<class_1792, Integer> missing = new HashMap<>();
      Set<class_1792> inHotbar = new HashSet<>();
      Set<class_1792> inBackpack = new HashSet<>();
      boolean hotbarRoom = (Integer)this.buildSlot.get() > 0;

      for (int i = 0; i <= 35; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_7960()) {
            if (i <= 8) {
               hotbarRoom = true;
            }
         } else {
            (i <= 8 ? inHotbar : inBackpack).add(stack.method_7909());
         }
      }

      if (!this.mc.field_1724.method_6079().method_7960()) {
         inHotbar.add(this.mc.field_1724.method_6079().method_7909());
      }

      for (Schematic.Entry entry : this.building.entries()) {
         class_2680 target = entry.state();
         if (!Schematic.isSecondHalf(target)) {
            class_2338 world = this.anchor.method_10081(entry.offset());
            class_2680 current = this.mc.field_1687.method_8320(world);
            if (this.matches(current, target)) {
               counts.done++;
            } else {
               boolean stacking = Schematic.needsMore(current, target);
               if (!stacking) {
                  if (current.method_26204() == target.method_26204()) {
                     counts.wrongState++;
                     continue;
                  }

                  if (!current.method_45474()) {
                     counts.occupied++;
                     continue;
                  }
               }

               if (!placeable(target)) {
                  counts.noItem++;
               } else {
                  class_1792 item = target.method_26204().method_8389();
                  if (!inHotbar.contains(item)) {
                     if (inBackpack.contains(item)) {
                        if (!hotbarRoom) {
                           counts.noSlot++;
                           continue;
                        }
                     } else {
                        missing.merge(item, 1, Integer::sum);
                        if ((Boolean)this.onlyWhatIHave.get()) {
                           counts.missingItem++;
                           continue;
                        }
                     }
                  }

                  this.remaining.add(entry);
                  if (stacking) {
                     this.stackOnto.add(world);
                  }

                  if (!(eyes.method_1025(class_243.method_24953(world)) > reachSq)) {
                     this.inReach++;
                     if (this.failedClicks.getOrDefault(world, 0) < 2 && (stacking || BlockUtils.canPlaceBlock(world, true, target.method_26204()))) {
                        if (!cellInView(world)) {
                           this.outOfView.add(world);
                        } else {
                           this.pending.add(entry);
                        }
                     }
                  }
               }
            }
         }
      }

      counts.remaining = this.remaining.size();
      this.tally = counts;
      this.sortWork(eyes);
      this.reportMissing(missing);
      this.lastScan = String.format(
         "%d done, %d wrong-state, %d blocked, %d no-item, %d no-hotbar-slot, %d left (%d needing another click on what is there), %d in reach, %d more in reach but out of view",
         counts.done,
         counts.wrongState,
         counts.occupied,
         counts.noItem,
         counts.noSlot,
         this.remaining.size(),
         this.stackOnto.size(),
         this.pending.size(),
         this.outOfView.size()
      );
   }

   private static boolean cellInView(class_2338 pos) {
      class_238 cell = new class_238(pos);
      if (Stealth.inView(cell)) {
         return true;
      } else {
         for (class_2350 side : class_2350.values()) {
            if (Stealth.inView(SchematicBuilder.FaceAim.faceCentre(cell, side.method_10148(), side.method_10164(), side.method_10165()))) {
               return true;
            }
         }

         return false;
      }
   }

   private void sortWork(class_243 eyes) {
      Comparator<Schematic.Entry> byDistance = Comparator.comparingDouble(e -> eyes.method_1025(class_243.method_24953(this.anchor.method_10081(e.offset()))));
      this.pending.sort(this.bottomUp.get() ? Comparator.<Schematic.Entry>comparingInt(e -> e.offset().method_10264()).thenComparing(byDistance) : byDistance);
   }

   private static boolean placeable(class_2680 state) {
      return PLACEABLE.computeIfAbsent(state.method_26204(), SchematicBuilder::itemPlaces);
   }

   private static boolean itemPlaces(class_2248 block) {
      if (block.method_8389() instanceof class_1747 item) {
         HashMap var3 = new HashMap();
         item.method_7713(var3, item);
         return var3.containsKey(block);
      } else {
         return false;
      }
   }

   private int countHeld(class_1792 item) {
      int count = 0;

      for (int slot = 0; slot <= 35; slot++) {
         count += this.countIn(this.mc.field_1724.method_31548().method_5438(slot), item);
      }

      return count + this.countIn(this.mc.field_1724.method_6079(), item);
   }

   private int countIn(class_1799 stack, class_1792 item) {
      if (stack.method_7960()) {
         return 0;
      } else if (stack.method_31574(item)) {
         return stack.method_7947();
      } else {
         return this.countShulkers.get() ? countInside(stack, item) : 0;
      }
   }

   private int countInShulkers(class_1792 item) {
      if (!(Boolean)this.countShulkers.get()) {
         return 0;
      } else {
         int count = 0;

         for (int slot = 0; slot <= 35; slot++) {
            count += countInside(this.mc.field_1724.method_31548().method_5438(slot), item);
         }

         return count + countInside(this.mc.field_1724.method_6079(), item);
      }
   }

   private static int countInside(class_1799 container, class_1792 item) {
      class_9288 contents = (class_9288)container.method_58694(class_9334.field_49622);
      if (contents == null) {
         return 0;
      } else {
         int count = 0;

         for (class_1799 stack : contents.method_59714()) {
            if (stack.method_31574(item)) {
               count += stack.method_7947();
            }
         }

         return count;
      }
   }

   private void printMaterialList() {
      if (this.isActive()) {
         if (this.loaded != null || this.load()) {
            if (this.mc.field_1724 != null) {
               Schematic source = this.building != null ? this.building : this.loaded;
               Map<class_1792, Integer> needed = new HashMap<>();
               int noItem = 0;

               for (Schematic.Entry entry : source.entries()) {
                  if (!Schematic.isSecondHalf(entry.state())) {
                     if (!placeable(entry.state())) {
                        noItem++;
                     } else {
                        needed.merge(entry.state().method_26204().method_8389(), 1, Integer::sum);
                     }
                  }
               }

               this.info("--- %s: %d block types ---", new Object[]{source.name(), needed.size()});
               int shortTypes = 0;
               List<Entry<class_1792, Integer>> sorted = new ArrayList<>(needed.entrySet());
               sorted.sort(Entry.<class_1792, Integer>comparingByValue().reversed());

               for (Entry<class_1792, Integer> line : sorted) {
                  int have = this.countHeld(line.getKey());
                  int need = line.getValue();
                  if (have < need) {
                     if (++shortTypes <= 12) {
                        this.info(
                           "%s: need %d, have %d (short %d, %.1f stacks)",
                           new Object[]{line.getKey().toString(), need, have, need - have, (need - have) / 64.0}
                        );
                     }
                  }
               }

               if (shortTypes == 0) {
                  this.info("You have everything.", new Object[0]);
               } else if (shortTypes > 12) {
                  this.info("...and %d more types short.", new Object[]{shortTypes - 12});
               }

               if (noItem > 0) {
                  this.info("%d blocks have no placeable item (fluids, piston heads and the like) and are skipped.", new Object[]{noItem});
               }
            }
         }
      }
   }

   private void reportMissing(Map<class_1792, Integer> missing) {
      if (missing.isEmpty()) {
         this.missingReport = null;
      } else {
         String report = missing.entrySet()
            .stream()
            .sorted(Entry.<class_1792, Integer>comparingByValue().reversed())
            .limit(3L)
            .map(e -> e.getValue() + "x " + e.getKey().toString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
         if ((Boolean)this.chatProgress.get() && !report.equals(this.missingReport)) {
            this.warning("Not in reach of an item: %s.", new Object[]{report});
            int packed = missing.keySet().stream().mapToInt(this::countInShulkers).sum();
            if (packed > 0) {
               this.warning("%d of those are in shulker boxes - unpack them, placing needs loose stock.", new Object[]{packed});
            } else {
               this.warning("Press the material-list bind for the full list.", new Object[0]);
            }
         }

         this.missingReport = report;
      }
   }

   private boolean rotating() {
      return this.mode.get() == SchematicBuilder.Mode.Crosshair ? false : (Boolean)this.rotate.get() || Stealth.legitPlace();
   }

   // The look the server has is the one a click goes along. In Crosshair mode that has to be your crosshair: while
   // another module holds a server-side look elsewhere, nothing is placed.
   private boolean serverLooksWhereYouLook() {
      double yaw = class_3532.method_15338(LegitPlace.currentYaw() - this.mc.field_1724.method_36454());
      double pitch = LegitPlace.currentPitch() - this.mc.field_1724.method_36455();
      return Math.abs(yaw) < 0.01 && Math.abs(pitch) < 0.01;
   }

   private double reach() {
      return Math.min((Double)this.range.get(), VanillaLimits.blockRange());
   }

   private SchematicBuilder.ClickStep placeNext() {
      if (this.job != null && this.mode.get() == SchematicBuilder.Mode.Crosshair) {
         // A job is a block the automatic builder turned towards; the crosshair printer does not turn.
         this.dropJob();
      }

      if (this.job != null) {
         if (!this.stillWanted(this.job)) {
            this.dropJob();
         } else {
            if (Stealth.inView(this.job.plan().hit().method_17784())) {
               return this.advance(this.job);
            }

            this.outOfView.add(this.job.pos());
            this.dropJob();
         }
      }

      if (!this.rotating()) {
         return this.placeAlongLook();
      } else {
         class_243 eyes = this.mc.field_1724.method_33571();

         while (this.pendingIndex < this.pending.size()) {
            Schematic.Entry entry = this.pending.get(this.pendingIndex++);
            class_2338 world = this.anchor.method_10081(entry.offset());
            boolean stacking = this.stackOnto.contains(world);
            if (this.workable(world, entry.state(), stacking, eyes)) {
               if (this.searchesThisTick >= 32) {
                  this.pendingIndex--;
                  return SchematicBuilder.ClickStep.NONE;
               }

               FindItemResult item = this.findPlaceable(entry.state());
               if (!item.found()) {
                  if (this.movedMaterial) {
                     this.pendingIndex--;
                     return SchematicBuilder.ClickStep.NONE;
                  }
               } else {
                  this.searchesThisTick++;
                  SchematicBuilder.Plan plan = this.plan(world, entry.state(), item, stacking, null);
                  if (plan != null) {
                     this.job = new SchematicBuilder.Job(world, entry.state(), stacking, plan);
                     return this.advance(this.job);
                  }

                  if (this.planMiss == SchematicBuilder.Miss.OUT_OF_VIEW) {
                     this.outOfView.add(world);
                  } else if (this.planMiss == SchematicBuilder.Miss.ORIENTATION) {
                     this.wrongSide.add(world);
                  }

                  if (this.planMiss.heldAgainst()) {
                     this.failedClicks.merge(world, 1, Integer::sum);
                  }
               }
            }
         }

         return SchematicBuilder.ClickStep.NONE;
      }
   }

   private SchematicBuilder.ClickStep placeAlongLook() {
      if (this.mode.get() == SchematicBuilder.Mode.Crosshair && !this.serverLooksWhereYouLook()) {
         return SchematicBuilder.ClickStep.NONE;
      }

      class_3965 look = LegitPlace.along(LegitPlace.currentYaw(), LegitPlace.currentPitch(), this.reach());
      if (look == null) {
         return SchematicBuilder.ClickStep.NONE;
      } else {
         class_2338 clicked = look.method_17777();
         class_2338 beyond = clicked.method_10093(look.method_17780());
         class_243 eyes = this.mc.field_1724.method_33571();
         boolean crosshair = this.mode.get() == SchematicBuilder.Mode.Crosshair;

         for (Schematic.Entry entry : crosshair ? this.targetsAt(clicked, beyond) : this.pending) {
            class_2338 world = this.anchor.method_10081(entry.offset());
            if (world.equals(clicked) || world.equals(beyond)) {
               boolean stacking = crosshair
                  ? Schematic.needsMore(this.mc.field_1687.method_8320(world), entry.state())
                  : this.stackOnto.contains(world);
               if (this.workable(world, entry.state(), stacking, eyes)) {
                  FindItemResult item = this.findPlaceable(entry.state());
                  if (!item.found()) {
                     if (this.movedMaterial) {
                        return SchematicBuilder.ClickStep.NONE;
                     }
                  } else {
                     SchematicBuilder.Plan plan = this.plan(world, entry.state(), item, stacking, look);
                     if (plan != null) {
                        if (!this.canSpend()) {
                           return SchematicBuilder.ClickStep.TURNING;
                        }
                        return switch (this.click(new SchematicBuilder.Job(world, entry.state(), stacking, plan))) {
                           case SENT -> {
                              this.attemptsSinceScan++;
                              yield SchematicBuilder.ClickStep.QUEUED;
                           }
                           case WAIT -> SchematicBuilder.ClickStep.TURNING;
                           case DROP -> SchematicBuilder.ClickStep.NONE;
                        };
                     }
                  }
               }
            }
         }

         return SchematicBuilder.ClickStep.NONE;
      }
   }

   private List<Schematic.Entry> targetsAt(class_2338 clicked, class_2338 beyond) {
      if (this.byWorld == null || this.byWorldFor != this.building || !this.anchor.equals(this.byWorldAt)) {
         this.byWorld = new HashMap<>();
         this.byWorldFor = this.building;
         this.byWorldAt = this.anchor;

         for (Schematic.Entry entry : this.building.entries()) {
            if (!Schematic.isSecondHalf(entry.state())) {
               this.byWorld.put(this.anchor.method_10081(entry.offset()), entry);
            }
         }
      }

      List<Schematic.Entry> found = new ArrayList<>(2);
      // The block beyond the face first: that is where a normal click puts the block. The clicked block itself only
      // counts for blocks that are clicked onto (slabs, candles, snow layers stacking up).
      for (class_2338 pos : new class_2338[]{beyond, clicked}) {
         Schematic.Entry entry = this.byWorld.get(pos);
         if (entry != null) {
            found.add(entry);
         }
      }

      return found;
   }

   private boolean workable(class_2338 world, class_2680 target, boolean stacking, class_243 eyes) {
      if (this.failedClicks.getOrDefault(world, 0) >= 2) {
         return false;
      } else {
         class_2680 current = this.mc.field_1687.method_8320(world);
         if (this.matches(current, target)) {
            return false;
         } else {
            double reach = this.reach();
            if (eyes.method_1025(class_243.method_24953(world)) > reach * reach) {
               return false;
            } else {
               return stacking ? Schematic.needsMore(current, target) : current.method_45474() && BlockUtils.canPlaceBlock(world, true, target.method_26204());
            }
         }
      }
   }

   private boolean stillWanted(SchematicBuilder.Job current) {
      if (!this.rotating()) {
         return false;
      } else if (!this.workable(current.pos(), current.target(), current.stacking(), this.mc.field_1724.method_33571())) {
         return false;
      } else if (!this.held(current.target().method_26204().method_8389()).found()) {
         return false;
      } else {
         class_3965 planned = current.plan().hit();
         class_3965 ray = LegitPlace.along(current.plan().yaw(), current.plan().pitch(), this.reach());
         return ray != null && ray.method_17777().equals(planned.method_17777()) && ray.method_17780() == planned.method_17780();
      }
   }

   private SchematicBuilder.ClickStep advance(SchematicBuilder.Job current) {
      SchematicBuilder.Plan plan = current.plan();
      if (!this.turn.heldByOther(TURN_OWNER)) {
         this.readyHand(this.held(current.target().method_26204().method_8389()), plan.hit());
      }

      if (this.turn.wouldReachSettled(TURN_OWNER, plan.yaw(), plan.pitch(), 50) && this.canSpend()) {
         this.outcome = SchematicBuilder.Outcome.WAIT;
         if (this.turn.turnToSettled(TURN_OWNER, plan.yaw(), plan.pitch(), 50, this.clickAction(current))) {
            if (this.outcome == SchematicBuilder.Outcome.SENT) {
               this.job = null;
               this.attemptsSinceScan++;
               return SchematicBuilder.ClickStep.QUEUED;
            }

            if (this.outcome == SchematicBuilder.Outcome.DROP) {
               this.job = null;
            }
         }

         return SchematicBuilder.ClickStep.TURNING;
      } else {
         this.turn.turnTo(TURN_OWNER, plan.yaw(), plan.pitch(), 50, null);
         return SchematicBuilder.ClickStep.TURNING;
      }
   }

   private void dropJob() {
      if (this.job != null) {
         this.job = null;
         this.turn.reset(TURN_OWNER);
      }
   }

   private boolean canSpend() {
      if (Double.isNaN(this.nextCost)) {
         this.nextCost = Stealth.actionCost();
      }

      return this.budget.canAfford(this.nextCost) && ClickGate.canUse() && !Stealth.handsBusy() && !this.playerClicking();
   }

   private boolean spend() {
      if (Double.isNaN(this.nextCost)) {
         this.nextCost = Stealth.actionCost();
      }

      if (!this.budget.canAfford(this.nextCost)) {
         return false;
      } else if (!Stealth.claimUse()) {
         return false;
      } else if (!this.budget.tryConsume(this.nextCost)) {
         return false;
      } else {
         this.nextCost = Double.NaN;
         return true;
      }
   }

   private boolean playerClicking() {
      return this.mc.field_1690.field_1904.method_1434() || ((KeyBindingAccessor)this.mc.field_1690.field_1904).meteor$getTimesPressed() > 0;
   }

   private Runnable clickAction(SchematicBuilder.Job current) {
      return this.activeAction(() -> this.outcome = this.click(current));
   }

   private SchematicBuilder.Outcome click(SchematicBuilder.Job current) {
      class_2338 pos = current.pos();
      class_2680 target = current.target();
      boolean stacking = current.stacking();
      SchematicBuilder.Plan plan = current.plan();
      if (target.method_26204().method_8389() instanceof class_1747 wanted) {
         class_2680 var29 = this.mc.field_1687.method_8320(pos);
         if (this.matches(var29, target)) {
            return SchematicBuilder.Outcome.DROP;
         } else {
            boolean spotOk = stacking ? Schematic.needsMore(var29, target) : var29.method_45474() && BlockUtils.canPlaceBlock(pos, true, target.method_26204());
            FindItemResult item = this.held(wanted);
            class_3965 hit = LegitPlace.along(plan.yaw(), plan.pitch(), this.reach());
            boolean rayOk = hit != null && hit.method_17777().equals(plan.hit().method_17777()) && this.clickable(hit.method_17777(), pos);
            if (spotOk && item.found() && rayOk && !Stealth.inView(hit.method_17784())) {
               this.outOfView.add(pos);
               return SchematicBuilder.Outcome.DROP;
            } else {
               boolean hitOk = rayOk && Stealth.allowsBlock(hit.method_17777(), hit.method_17784());
               if (spotOk && item.found() && hitOk) {
                  class_1268 hand = item.slot() == 40 ? class_1268.field_5810 : class_1268.field_5808;
                  class_1799 stack = hand == class_1268.field_5810
                     ? this.mc.field_1724.method_6079()
                     : this.mc.field_1724.method_31548().method_5438(item.slot());
                  class_2680 predicted = this.predict(pos, wanted, stack, hand, hit, plan.yaw(), plan.pitch());
                  if (predicted != null && this.fits(var29, predicted, target, stacking)) {
                     SchematicBuilder.Outcome notYet = this.readyHand(item, hit);
                     if (notYet != null) {
                        return notYet;
                     } else if (!this.playerClicking() && this.spend()) {
                        int levelBefore = Schematic.stackLevel(var29);
                        float wasYaw = this.mc.field_1724.method_36454();
                        float wasPitch = this.mc.field_1724.method_36455();

                        class_1268 acted;
                        try {
                           this.mc.field_1724.method_36456((float)plan.yaw());
                           this.mc.field_1724.method_36457((float)plan.pitch());
                           acted = VanillaClick.use(hit, (Boolean)this.swing.get());
                        } finally {
                           this.mc.field_1724.method_36456(wasYaw);
                           this.mc.field_1724.method_36457(wasPitch);
                        }

                        class_2680 now = this.mc.field_1687.method_8320(pos);
                        boolean landed = acted == hand && now.method_27852(target.method_26204()) && (!stacking || Schematic.stackLevel(now) > levelBefore);
                        if (landed) {
                           this.placedThisSession++;
                           this.failedClicks.remove(pos);
                           this.wrongSide.remove(pos);
                           if (target.method_26204() instanceof class_2478) {
                              this.signPos = pos;
                              this.signPlacedAge = this.mc.field_1724.field_6012;
                              this.signClock.forget(pos);
                           }

                           for (class_2350 face : class_2350.values()) {
                              this.failedClicks.remove(pos.method_10093(face));
                           }
                        } else {
                           this.failedClicks.merge(pos, 1, Integer::sum);
                        }

                        return SchematicBuilder.Outcome.SENT;
                     } else {
                        return SchematicBuilder.Outcome.WAIT;
                     }
                  } else {
                     this.failedClicks.merge(pos, 1, Integer::sum);
                     return SchematicBuilder.Outcome.DROP;
                  }
               } else {
                  this.failedClicks.merge(pos, 1, Integer::sum);
                  return SchematicBuilder.Outcome.DROP;
               }
            }
         }
      } else {
         return SchematicBuilder.Outcome.DROP;
      }
   }

   private SchematicBuilder.Outcome readyHand(FindItemResult item, class_3965 hit) {
      if (!item.found()) {
         return SchematicBuilder.Outcome.DROP;
      } else if (Stealth.handsBusy() || this.mc.field_1761.method_2923()) {
         return SchematicBuilder.Outcome.WAIT;
      } else if (InventoryGuard.offhandInFlight()) {
         return SchematicBuilder.Outcome.WAIT;
      } else {
         boolean offhand = item.slot() == 40;
         int slot;
         if (!offhand) {
            slot = item.slot();
         } else {
            if (VanillaClick.reaches(hit, class_1268.field_5810)) {
               return null;
            }

            if ((slot = this.emptyHotbarSlot()) < 0) {
               return SchematicBuilder.Outcome.DROP;
            }
         }

         if (this.mc.field_1724.method_31548().method_67532() != slot && !HotbarSwap.select(slot)) {
            return SchematicBuilder.Outcome.WAIT;
         } else if (ClickGate.slotChangedThisTick()) {
            // Waits a tick after the switch - unless Stealth's same-tick-switch lets the click follow it at once.
            return SchematicBuilder.Outcome.WAIT;
         } else {
            return offhand && !VanillaClick.reaches(hit, class_1268.field_5810) ? SchematicBuilder.Outcome.DROP : null;
         }
      }
   }

   private SchematicBuilder.Plan plan(class_2338 pos, class_2680 target, FindItemResult item, boolean stacking, class_3965 look) {
      this.planMiss = look != null ? SchematicBuilder.Miss.NOT_LOOKING : SchematicBuilder.Miss.NO_CLICK;
      if (target.method_26204().method_8389() instanceof class_1747 blockItem) {
         class_1268 hand = item.isOffhand() ? class_1268.field_5810 : class_1268.field_5808;
         class_1799 stack = hand == class_1268.field_5810 ? this.mc.field_1724.method_6079() : this.mc.field_1724.method_31548().method_5438(item.slot());
         class_2680 current = this.mc.field_1687.method_8320(pos);
         if (look == null) {
            double reach = this.reach();
            this.planOutOfView = false;
            SchematicBuilder.Plan found = this.search(pos, current, target, blockItem, stack, hand, stacking, reach, false);
            if (found == null) {
               found = this.search(pos, current, target, blockItem, stack, hand, stacking, reach, true);
            }

            if (found == null && this.planOutOfView) {
               this.planMiss = SchematicBuilder.Miss.OUT_OF_VIEW;
            }

            return found;
         } else if (this.clickable(look.method_17777(), pos) && Stealth.allowsBlock(look.method_17777(), look.method_17784())) {
            double yaw = LegitPlace.currentYaw();
            double pitch = LegitPlace.currentPitch();
            class_2680 predicted = this.predict(pos, blockItem, stack, hand, look, yaw, pitch);
            return predicted != null && this.fits(current, predicted, target, stacking) ? new SchematicBuilder.Plan(look, yaw, pitch) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private SchematicBuilder.Plan search(
      class_2338 pos,
      class_2680 current,
      class_2680 target,
      class_1747 blockItem,
      class_1799 stack,
      class_1268 hand,
      boolean stacking,
      double reach,
      boolean wide
   ) {
      for (SchematicBuilder.Aim aim : this.aims(pos, current, target, reach, wide)) {
         class_3965 wish = new class_3965(aim.point(), aim.side(), aim.clicked(), false);
         class_2680 predicted = this.predict(pos, blockItem, stack, hand, wish, aim.yaw(), aim.pitch());
         if (predicted != null) {
            if (!this.fits(current, predicted, target, stacking)) {
               this.planMiss = SchematicBuilder.Miss.ORIENTATION;
            } else {
               class_3965 hit = LegitPlace.along(aim.yaw(), aim.pitch(), reach);
               if (hit != null && hit.method_17777().equals(aim.clicked()) && hit.method_17780() == aim.side()) {
                  if (!Stealth.inView(hit.method_17784())) {
                     if (!this.planOutOfView) {
                        class_2680 exact = this.predict(pos, blockItem, stack, hand, hit, aim.yaw(), aim.pitch());
                        this.planOutOfView = exact != null && this.fits(current, exact, target, stacking);
                     }
                  } else if (Stealth.allowsBlock(hit.method_17777(), hit.method_17784())) {
                     class_2680 exact = this.predict(pos, blockItem, stack, hand, hit, aim.yaw(), aim.pitch());
                     if (exact != null && this.fits(current, exact, target, stacking)) {
                        return new SchematicBuilder.Plan(hit, aim.yaw(), aim.pitch());
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private List<SchematicBuilder.Aim> aims(class_2338 pos, class_2680 current, class_2680 target, double reach, boolean wide) {
      class_243 eyes = this.mc.field_1724.method_33571();
      class_243 place = class_243.method_24954(pos);
      boolean half = target.method_28498(class_2741.field_12518) || target.method_28498(class_2741.field_12485);
      boolean hinge = target.method_28498(class_2741.field_12520);
      List<SchematicBuilder.Aim> out = new ArrayList<>();

      for (class_2350 toNeighbour : class_2350.values()) {
         class_2338 neighbour = pos.method_10093(toNeighbour);
         if (this.clickable(neighbour, pos)) {
            this.addAims(out, neighbour, toNeighbour.method_10153(), eyes, place, reach, half, hinge, wide);
         }
      }

      if (!current.method_26215()) {
         for (class_2350 side : class_2350.values()) {
            this.addAims(out, pos, side, eyes, place, reach, half, hinge, wide);
         }
      }

      out.sort(Comparator.comparingDouble(SchematicBuilder.Aim::cost));
      return out;
   }

   private void addAims(
      List<SchematicBuilder.Aim> out,
      class_2338 block,
      class_2350 side,
      class_243 eyes,
      class_243 place,
      double reach,
      boolean half,
      boolean hinge,
      boolean wide
   ) {
      double reachSq = reach * reach;

      for (class_238 local : this.mc.field_1687.method_8320(block).method_26218(this.mc.field_1687, block).method_1090()) {
         class_238 box = local.method_996(block);
         if (!(box.method_49271(eyes) > reachSq)) {
            for (class_243 point : SchematicBuilder.FaceAim.points(
               box, side.method_10148(), side.method_10164(), side.method_10165(), eyes, place, half, hinge, wide
            )) {
               if (!(eyes.method_1025(point) > reachSq)) {
                  double yaw = Rotations.getYaw(point);
                  double pitch = Rotations.getPitch(point);
                  out.add(new SchematicBuilder.Aim(block, side, point, yaw, pitch, turnCost(yaw, pitch)));
               }
            }
         }
      }
   }

   private boolean clickable(class_2338 clicked, class_2338 pos) {
      class_2680 state = this.mc.field_1687.method_8320(clicked);
      return state.method_26215() ? false : clicked.equals(pos) || !LegitPlace.isInteractive(state);
   }

   private static double turnCost(double yaw, double pitch) {
      return Math.abs(class_3532.method_15338(yaw - LegitPlace.currentYaw())) + Math.abs(pitch - LegitPlace.currentPitch());
   }

   private boolean fits(class_2680 current, class_2680 predicted, class_2680 target, boolean stacking) {
      if (stacking) {
         return Schematic.stacksCloser(current, predicted, target);
      } else {
         return this.orientation.get() == SchematicBuilder.Orientation.Ignore
            ? predicted.method_27852(target.method_26204())
            : Schematic.orientationMatches(predicted, target) || Schematic.needsMore(predicted, target);
      }
   }

   private class_2680 predict(class_2338 pos, class_1747 blockItem, class_1799 stack, class_1268 hand, class_3965 hit, double yaw, double pitch) {
      float wasYaw = this.mc.field_1724.method_36454();
      float wasPitch = this.mc.field_1724.method_36455();
      this.mc.field_1724.method_36456((float)yaw);
      this.mc.field_1724.method_36457((float)pitch);

      Object var14;
      try {
         class_1750 context = new class_1750(this.mc.field_1724, hand, stack, hit);
         if (!context.method_7716() || !context.method_8037().equals(pos)) {
            return null;
         }

         class_1750 adjusted = blockItem.method_16356(context);
         if (adjusted != null && adjusted.method_8037().equals(pos)) {
            return ((BlockItemInvoker)blockItem).crystal$getPlacementState(adjusted);
         }

         var14 = null;
      } catch (RuntimeException var18) {
         CrystalAddon.LOG.debug("Simulating a placement of {} failed", blockItem, var18);
         return null;
      } finally {
         this.mc.field_1724.method_36456(wasYaw);
         this.mc.field_1724.method_36457(wasPitch);
      }

      return (class_2680)var14;
   }

   private FindItemResult findPlaceable(class_2680 state) {
      class_1792 item = state.method_26204().method_8389();
      if (!(item instanceof class_1747)) {
         return new FindItemResult(-1, 0);
      } else {
         FindItemResult ready = this.held(item);
         if (ready.found()) {
            return ready;
         } else {
            FindItemResult backpack = InvUtils.find(stack -> stack.method_31574(item), 9, 35);
            if (!backpack.found()) {
               return backpack;
            } else {
               int target = this.hotbarTarget();
               if (target < 0) {
                  return new FindItemResult(-1, 0);
               } else {
                  this.movedMaterial = true;
                  if (InventoryGuard.swapToHotbar(backpack.slot(), target)) {
                     this.holdUntil = this.ticks + Stealth.pace(4);
                  }

                  return new FindItemResult(-1, 0);
               }
            }
         }
      }
   }

   private FindItemResult held(class_1792 item) {
      class_1799 main = this.mc.field_1724.method_6047();
      if (main.method_31574(item)) {
         return new FindItemResult(this.mc.field_1724.method_31548().method_67532(), main.method_7947());
      } else {
         FindItemResult hotbar = InvUtils.find(stack -> stack.method_31574(item), 0, 8);
         if (hotbar.found()) {
            return hotbar;
         } else {
            class_1799 off = this.mc.field_1724.method_6079();
            return !off.method_31574(item) || !VanillaClick.reaches(null, class_1268.field_5810) && this.emptyHotbarSlot() < 0
               ? new FindItemResult(-1, 0)
               : new FindItemResult(40, off.method_7947());
         }
      }
   }

   private int hotbarTarget() {
      int empty = this.emptyHotbarSlot();
      return empty >= 0 ? empty : (Integer)this.buildSlot.get() - 1;
   }

   private int emptyHotbarSlot() {
      for (int i = 0; i <= 8; i++) {
         if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) {
            return i;
         }
      }

      return -1;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.building != null && this.anchor != null && this.buildWorld == this.mc.field_1687) {
         class_3341 bounds = this.building.bounds();
         if ((Boolean)this.renderBounds.get() && bounds != null) {
            event.renderer
               .box(
                  this.anchor.method_10263() + bounds.method_35415(),
                  this.anchor.method_10264() + bounds.method_35416(),
                  this.anchor.method_10260() + bounds.method_35417(),
                  this.anchor.method_10263() + bounds.method_35418() + 1,
                  this.anchor.method_10264() + bounds.method_35419() + 1,
                  this.anchor.method_10260() + bounds.method_35420() + 1,
                  (Color)this.boundsColor.get(),
                  (Color)this.boundsColor.get(),
                  ShapeMode.Lines,
                  0
               );
         }

         if ((Boolean)this.renderNext.get()) {
            int shown = Math.min((Integer)this.renderCount.get(), this.pending.size() - this.pendingIndex);

            for (int i = 0; i < shown; i++) {
               class_2338 world = this.anchor.method_10081(this.pending.get(i + this.pendingIndex).offset());
               event.renderer.box(world, (Color)this.queueSideColor.get(), (Color)this.queueLineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
            }
         }
      }
   }

   public String getInfoString() {
      if (this.pauseReason != null) {
         return "paused: " + this.pauseReason;
      } else if (this.loaded == null) {
         return null;
      } else {
         String info = String.format("%d unfinished, %d placed", this.tally.unfinished(), this.placedThisSession);
         return this.outOfView.isEmpty() ? info : info + String.format(", %d out of view", this.outOfView.size());
      }
   }

   private record Aim(class_2338 clicked, class_2350 side, class_243 point, double yaw, double pitch, double cost) {
   }

   private static enum ClickStep {
      QUEUED,
      TURNING,
      NONE;
   }

   static final class FaceAim {
      private static final double OFF_CENTRE = 0.6;

      private FaceAim() {
      }

      static boolean turnedTowards(class_238 box, int nx, int ny, int nz, class_243 eyes) {
         class_243 centre = faceCentre(box, nx, ny, nz);
         return (eyes.field_1352 - centre.field_1352) * nx + (eyes.field_1351 - centre.field_1351) * ny + (eyes.field_1350 - centre.field_1350) * nz > 1.0E-6;
      }

      static class_243 faceCentre(class_238 box, int nx, int ny, int nz) {
         class_243 centre = box.method_1005();
         return new class_243(
            nx == 0 ? centre.field_1352 : (nx > 0 ? box.field_1320 : box.field_1323),
            ny == 0 ? centre.field_1351 : (ny > 0 ? box.field_1325 : box.field_1322),
            nz == 0 ? centre.field_1350 : (nz > 0 ? box.field_1324 : box.field_1321)
         );
      }

      static List<class_243> points(class_238 box, int nx, int ny, int nz, class_243 eyes, class_243 place, boolean half, boolean hinge, boolean wide) {
         List<class_243> out = new ArrayList<>();
         if (!turnedTowards(box, nx, ny, nz, eyes)) {
            return out;
         } else {
            class_243 centre = faceCentre(box, nx, ny, nz);
            if (!wide) {
               add(out, centre);
               add(
                  out,
                  new class_243(
                     nx != 0 ? centre.field_1352 : inside(eyes.field_1352, box.field_1323, box.field_1320),
                     ny != 0 ? centre.field_1351 : inside(eyes.field_1351, box.field_1322, box.field_1325),
                     nz != 0 ? centre.field_1350 : inside(eyes.field_1350, box.field_1321, box.field_1324)
                  )
               );
               if (half && ny == 0) {
                  add(out, new class_243(centre.field_1352, inside(place.field_1351 + 0.25, box.field_1322, box.field_1325), centre.field_1350));
                  add(out, new class_243(centre.field_1352, inside(place.field_1351 + 0.75, box.field_1322, box.field_1325), centre.field_1350));
               }

               if (hinge) {
                  if (nx == 0) {
                     add(out, new class_243(inside(place.field_1352 + 0.25, box.field_1323, box.field_1320), centre.field_1351, centre.field_1350));
                     add(out, new class_243(inside(place.field_1352 + 0.75, box.field_1323, box.field_1320), centre.field_1351, centre.field_1350));
                  }

                  if (nz == 0) {
                     add(out, new class_243(centre.field_1352, centre.field_1351, inside(place.field_1350 + 0.25, box.field_1321, box.field_1324)));
                     add(out, new class_243(centre.field_1352, centre.field_1351, inside(place.field_1350 + 0.75, box.field_1321, box.field_1324)));
                  }
               }

               return out;
            } else {
               double ax = box.method_17939() / 2.0 * 0.6;
               double ay = box.method_17940() / 2.0 * 0.6;
               double az = box.method_17941() / 2.0 * 0.6;

               for (int du = -1; du <= 1; du++) {
                  for (int dv = -1; dv <= 1; dv++) {
                     if (du != 0 || dv != 0) {
                        if (nx != 0) {
                           add(out, centre.method_1031(0.0, du * ay, dv * az));
                        } else if (ny != 0) {
                           add(out, centre.method_1031(du * ax, 0.0, dv * az));
                        } else {
                           add(out, centre.method_1031(du * ax, dv * ay, 0.0));
                        }
                     }
                  }
               }

               return out;
            }
         }
      }

      private static void add(List<class_243> out, class_243 point) {
         for (class_243 seen : out) {
            if (seen.method_1025(point) < 1.0E-8) {
               return;
            }
         }

         out.add(point);
      }

      static double inside(double value, double min, double max) {
         double inset = Math.min(0.05, (max - min) / 4.0);
         return Math.max(min + inset, Math.min(max - inset, value));
      }

      static class_2338 standBack(class_2338 pos, float yaw, int back) {
         double radians = Math.toRadians(yaw);
         return pos.method_10069((int)Math.round(back * Math.sin(radians)), 0, (int)Math.round(-back * Math.cos(radians)));
      }

      static boolean turnedBy(class_243 before, class_243 now, double degrees) {
         double length = before.method_1033() * now.method_1033();
         if (length < 1.0E-9) {
            return false;
         } else {
            double cos = class_3532.method_15350(before.method_1026(now) / length, -1.0, 1.0);
            return Math.toDegrees(Math.acos(cos)) >= degrees;
         }
      }
   }

   private record Job(class_2338 pos, class_2680 target, boolean stacking, SchematicBuilder.Plan plan) {
   }

   static enum Miss {
      NO_CLICK,
      ORIENTATION,
      NOT_LOOKING,
      OUT_OF_VIEW;

      boolean heldAgainst() {
         return this == NO_CLICK || this == ORIENTATION;
      }
   }

   public static enum Mode {
      Automatic,
      Crosshair;
   }

   public static enum Orientation {
      Exact,
      Ignore;
   }

   public static enum OriginMode {
      Litematica,
      AtFeet,
      Manual;
   }

   private static enum Outcome {
      SENT,
      WAIT,
      DROP;
   }

   private record Plan(class_3965 hit, double yaw, double pitch) {
   }

   public static final class Tally {
      public int done;
      public int wrongState;
      public int occupied;
      public int noItem;
      public int missingItem;
      public int noSlot;
      public int remaining;

      public int unfinished() {
         return this.occupied + this.missingItem + this.noSlot + this.remaining;
      }

      public boolean complete() {
         return this.unfinished() == 0;
      }

      public String leftOutSummary() {
         List<String> parts = new ArrayList<>(2);
         if (this.noItem > 0) {
            parts.add(this.noItem + " without an item skipped");
         }

         if (this.wrongState > 0) {
            parts.add(this.wrongState + " in a different state than the schematic");
         }

         return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
      }
   }
}
