package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.LitematicaPlacement;
import com.messerocks.crystal.utils.Schematic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting.Builder;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_5321;
import net.minecraft.class_9288;
import net.minecraft.class_9334;

public class SchematicBuilder extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBuild = this.settings.createGroup("Build");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<String> file = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("schematic")).description("File from your Litematica schematics folder."))
                  .supplier(() -> Schematic.schematicNames(this.schematicsFolder()).toArray(new String[0]))
                  .defaultValue(""))
               .onChanged(v -> this.loaded = null))
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
   private final Setting<Double> range = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("How far you can place from."))
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Double> speed = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("blocks-per-second"))
               .description("Placements per second. 0 removes the throttle, which servers rarely appreciate."))
            .defaultValue(8.0)
            .min(0.0)
            .sliderMax(40.0)
            .build()
      );
   private final Setting<Integer> maxPerTick = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-per-tick"))
                  .description("Ceiling per tick, so a pause cannot bank placements into one burst."))
               .defaultValue(1))
            .min(1)
            .sliderMax(8)
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
                  .description("Face each block as it is placed."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> swing = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Render the hand swing."))
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
   private final Setting<Boolean> walk = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("walk-to-work"))
                  .description(
                     "Walk to the next unbuilt part on your own once nothing is left in reach. Needs Baritone, which is what actually does the pathing."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> walkWithin = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("stop-walking-within"))
                  .description("How close to the next block to get before building resumes."))
               .defaultValue(3.5)
               .min(1.0)
               .sliderMax(6.0)
               .visible(this.walk::get))
            .build()
      );
   private final Setting<Integer> repathInterval = this.sgBuild
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("repath-interval"))
                     .description("Ticks before a stalled walk is retargeted. Baritone gives up quietly on unreachable spots."))
                  .defaultValue(60))
               .min(20)
               .sliderMax(200)
               .visible(this.walk::get))
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
   private class_2338 anchor;
   private final List<Schematic.Entry> pending = new ArrayList<>();
   private final ActionBudget budget = new ActionBudget();
   private int rescanTimer;
   private int placedThisSession;
   private String missingReport;
   private String lastScan;
   private final List<Schematic.Entry> remaining = new ArrayList<>();
   private class_2338 walkTarget;
   private int walkTimer;
   private int idleTicks;
   private String lastStallReason;
   private class_5321<class_1937> dimension;
   private double lastWalkDistance = Double.MAX_VALUE;
   private final List<class_2338> unreachable = new ArrayList<>();

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
      switch ((SchematicBuilder.OriginMode)this.originMode.get()) {
         case Litematica:
            LitematicaPlacement.Placement placement = LitematicaPlacement.find(this.litematicaConfigFolder(), (String)this.file.get());
            if (placement == null) {
               this.error("Litematica has no placement for %s. Place it there first, or switch origin to AtFeet.", new Object[]{this.file.get()});
               this.toggle();
               return false;
            } else {
               if (!placement.isPlain()) {
                  this.error(
                     "That placement is rotated (%s) or mirrored (%s), which this builder does not apply.",
                     new Object[]{placement.rotation(), placement.mirror()}
                  );
                  this.toggle();
                  return false;
               }

               this.anchor = placement.origin();
               if ((Boolean)this.chatProgress.get()) {
                  this.info(
                     "Using Litematica's placement at %d %d %d.",
                     new Object[]{this.anchor.method_10263(), this.anchor.method_10264(), this.anchor.method_10260()}
                  );
               }

               return true;
            }
         case AtFeet:
            if (this.mc.field_1724 == null) {
               return false;
            }

            this.anchor = this.mc.field_1724.method_24515();
            return true;
         case Manual:
            this.anchor = (class_2338)this.manualOrigin.get();
            return true;
         default:
            return false;
      }
   }

   private void resetForNewWorld() {
      this.anchor = null;
      this.pending.clear();
      this.remaining.clear();
      this.unreachable.clear();
      this.stopWalking();
      this.rescanTimer = 0;
      this.idleTicks = 0;
      this.lastStallReason = null;
   }

   public void onActivate() {
      this.loaded = null;
      this.dimension = null;
      this.pending.clear();
      this.placedThisSession = 0;
      this.rescanTimer = 0;
      this.missingReport = null;
      this.idleTicks = 0;
      this.lastStallReason = null;
      this.unreachable.clear();
      this.lastWalkDistance = Double.MAX_VALUE;
      this.anchor = null;
   }

   public void onDeactivate() {
      this.budget.reset();
      this.pending.clear();
      this.remaining.clear();
      this.unreachable.clear();
      this.stopWalking();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         class_5321<class_1937> currentDim = this.mc.field_1687.method_27983();
         if (this.dimension != null && !this.dimension.equals(currentDim)) {
            if ((Boolean)this.chatProgress.get()) {
               this.info("Dimension changed, re-reading the placement.", new Object[0]);
            }

            this.resetForNewWorld();
         }

         this.dimension = currentDim;
         if (this.loaded != null || this.load()) {
            if (this.anchor != null || this.resolveAnchor()) {
               this.budget.update((Double)this.speed.get(), (Integer)this.maxPerTick.get());
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

               boolean placed = false;

               while (this.budget.tryConsume() && this.placeNext()) {
                  placed = true;
               }

               if (placed) {
                  this.idleTicks = 0;
                  this.lastStallReason = null;
                  this.stopWalking();
               } else if (this.remaining.isEmpty()) {
                  if ((Boolean)this.chatProgress.get() && this.placedThisSession > 0) {
                     this.info("Schematic complete: %d blocks placed.", new Object[]{this.placedThisSession});
                  }

                  this.toggle();
               } else {
                  this.idleTicks++;
                  if ((Boolean)this.walk.get()) {
                     this.walkToWork();
                  }

                  if (this.idleTicks == 60) {
                     this.explainStall();
                  }
               }
            }
         }
      }
   }

   private void explainStall() {
      String reason;
      if (this.remaining.isEmpty()) {
         reason = this.missingReport != null
            ? "every remaining block needs an item you have none of loose: " + this.missingReport
            : "nothing left to build here - check the origin, the outline shows where it sits";
      } else if (!(Boolean)this.walk.get()) {
         reason = String.format("%d blocks left but none within %.1f. Turn walk-to-work on, or move closer", this.remaining.size(), this.range.get());
      } else if (PathManagers.get().getName().equals("none")) {
         reason = "walk-to-work needs Baritone and none is loaded. Install it, or walk there yourself";
      } else if (this.walkTarget != null) {
         reason = String.format(
            "walking to %d %d %d, %d blocks left",
            this.walkTarget.method_10263(),
            this.walkTarget.method_10264(),
            this.walkTarget.method_10260(),
            this.remaining.size()
         );
      } else if (!this.unreachable.isEmpty()) {
         reason = String.format("%d areas were unreachable and got skipped, nothing else in range", this.unreachable.size());
      } else {
         reason = "no reachable spot and no path target - the build site may be walled in";
      }

      if (!reason.equals(this.lastStallReason)) {
         this.warning("Not building: %s.", new Object[]{reason});
         this.info(
            "Scan: %s | origin %d %d %d", new Object[]{this.lastScan, this.anchor.method_10263(), this.anchor.method_10264(), this.anchor.method_10260()}
         );
         this.lastStallReason = reason;
      }
   }

   private void walkToWork() {
      class_2338 next = this.nextWorkTarget();
      if (next == null) {
         this.stopWalking();
      } else {
         double horizontal = horizontalDistanceSq(this.mc.field_1724.method_24515(), next);
         if (horizontal <= (Double)this.walkWithin.get() * (Double)this.walkWithin.get()) {
            this.stopWalking();
            this.rescanTimer = 0;
         } else if (next.equals(this.walkTarget)) {
            boolean closer = horizontal < this.lastWalkDistance - 1.0;
            if (closer) {
               this.lastWalkDistance = horizontal;
               this.walkTimer = (Integer)this.repathInterval.get();
            } else if (PathManagers.get().isPathing() && this.walkTimer > 0) {
               this.walkTimer--;
            } else if (this.walkTimer <= 0) {
               this.unreachable.add(this.walkTarget);
               if ((Boolean)this.chatProgress.get()) {
                  this.info(
                     "Cannot reach %d %d %d, skipping that area.",
                     new Object[]{this.walkTarget.method_10263(), this.walkTarget.method_10264(), this.walkTarget.method_10260()}
                  );
               }

               this.walkTarget = null;
            } else {
               this.walkTimer--;
            }
         } else {
            this.walkTarget = next;
            this.lastWalkDistance = horizontal;
            this.walkTimer = (Integer)this.repathInterval.get();
            PathManagers.get().moveTo(next, true);
            if ((Boolean)this.chatProgress.get()) {
               this.info(
                  "Walking to %d %d %d, %d blocks left.", new Object[]{next.method_10263(), next.method_10264(), next.method_10260(), this.remaining.size()}
               );
            }
         }
      }
   }

   private static double horizontalDistanceSq(class_2338 a, class_2338 b) {
      double dx = a.method_10263() - b.method_10263();
      double dz = a.method_10260() - b.method_10260();
      return dx * dx + dz * dz;
   }

   private class_2338 nextWorkTarget() {
      class_2338 me = this.mc.field_1724.method_24515();
      class_2338 best = null;
      int bestLayer = Integer.MAX_VALUE;
      double bestDistance = Double.MAX_VALUE;

      for (Schematic.Entry entry : this.remaining) {
         class_2338 world = this.anchor.method_10081(entry.offset());
         if (!this.isNearUnreachable(world)) {
            int layer = world.method_10264();
            double distance = horizontalDistanceSq(me, world);
            boolean better = this.bottomUp.get() ? layer < bestLayer || layer == bestLayer && distance < bestDistance : distance < bestDistance;
            if (better) {
               bestLayer = layer;
               bestDistance = distance;
               best = world;
            }
         }
      }

      return best;
   }

   private boolean isNearUnreachable(class_2338 pos) {
      for (class_2338 bad : this.unreachable) {
         if (bad.method_19771(pos, 4.0)) {
            return true;
         }
      }

      return false;
   }

   private void stopWalking() {
      if (this.walkTarget != null) {
         this.walkTarget = null;
         this.walkTimer = 0;
         this.lastWalkDistance = Double.MAX_VALUE;
         if (PathManagers.get().isPathing()) {
            PathManagers.get().stop();
         }
      }
   }

   private boolean load() {
      String wanted = (String)this.file.get();
      if (wanted != null && !wanted.isBlank()) {
         for (Path path : Schematic.findSchematics(this.schematicsFolder())) {
            if (path.getFileName().toString().equals(wanted)) {
               try {
                  this.loaded = Schematic.load(path);
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
                     if (!BaritoneUtils.IS_AVAILABLE && (Boolean)this.walk.get()) {
                        this.warning("walk-to-work is on but no Baritone is loaded - it will not move on its own.", new Object[0]);
                     }
                  }

                  return true;
               } catch (Exception var5) {
                  this.error("Could not read %s: %s", new Object[]{wanted, var5.getMessage()});
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

   private void rescan() {
      this.pending.clear();
      this.remaining.clear();
      class_243 eyes = this.mc.field_1724.method_33571();
      double reachSq = (Double)this.range.get() * (Double)this.range.get();
      Map<class_1792, Integer> missing = new HashMap<>();
      int alreadyRight = 0;
      int occupied = 0;
      int noItem = 0;

      for (Schematic.Entry entry : this.loaded.entries()) {
         class_2338 world = this.anchor.method_10081(entry.offset());
         class_2680 current = this.mc.field_1687.method_8320(world);
         if (current.equals(entry.state())) {
            alreadyRight++;
         } else if (!current.method_45474()) {
            occupied++;
         } else if (!placeable(entry.state())) {
            noItem++;
         } else {
            class_1792 item = entry.state().method_26204().method_8389();
            if (!InvUtils.find(stack -> stack.method_31574(item)).found()) {
               missing.merge(item, 1, Integer::sum);
               if ((Boolean)this.onlyWhatIHave.get()) {
                  continue;
               }
            }

            this.remaining.add(entry);
            if (!(eyes.method_1025(class_243.method_24953(world)) > reachSq) && BlockUtils.canPlaceBlock(world, true, entry.state().method_26204())) {
               this.pending.add(entry);
            }
         }
      }

      this.sortWork(eyes);
      this.reportMissing(missing);
      this.lastScan = String.format(
         "%d done, %d blocked, %d no-item, %d left, %d in reach", alreadyRight, occupied, noItem, this.remaining.size(), this.pending.size()
      );
   }

   private void sortWork(class_243 eyes) {
      Comparator<Schematic.Entry> byDistance = Comparator.comparingDouble(e -> eyes.method_1025(class_243.method_24953(this.anchor.method_10081(e.offset()))));
      this.pending.sort(this.bottomUp.get() ? Comparator.<Schematic.Entry>comparingInt(e -> e.offset().method_10264()).thenComparing(byDistance) : byDistance);
   }

   private static boolean placeable(class_2680 state) {
      return state.method_26204().method_8389() instanceof class_1747;
   }

   private int countHeld(class_1792 item) {
      int count = 0;

      for (int i = 0; i < this.mc.field_1724.method_31548().method_5439(); i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (!stack.method_7960()) {
            if (stack.method_31574(item)) {
               count += stack.method_7947();
            } else if ((Boolean)this.countShulkers.get()) {
               count += countInside(stack, item);
            }
         }
      }

      return count;
   }

   private int countInShulkers(class_1792 item) {
      if (!(Boolean)this.countShulkers.get()) {
         return 0;
      } else {
         int count = 0;

         for (int i = 0; i < this.mc.field_1724.method_31548().method_5439(); i++) {
            count += countInside(this.mc.field_1724.method_31548().method_5438(i), item);
         }

         return count;
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
               Map<class_1792, Integer> needed = new HashMap<>();
               int noItem = 0;

               for (Schematic.Entry entry : this.loaded.entries()) {
                  if (!placeable(entry.state())) {
                     noItem++;
                  } else {
                     needed.merge(entry.state().method_26204().method_8389(), 1, Integer::sum);
                  }
               }

               this.info("--- %s: %d block types ---", new Object[]{this.loaded.name(), needed.size()});
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
                  this.info("%d blocks have no placeable item (fluids, doors' upper halves and the like) and are skipped.", new Object[]{noItem});
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

   private boolean placeNext() {
      class_243 eyes = this.mc.field_1724.method_33571();

      while (!this.pending.isEmpty()) {
         Schematic.Entry entry = this.pending.remove(0);
         class_2338 world = this.anchor.method_10081(entry.offset());
         if (!this.mc.field_1687.method_8320(world).equals(entry.state())
            && this.mc.field_1687.method_8320(world).method_45474()
            && !(eyes.method_1025(class_243.method_24953(world)) > (Double)this.range.get() * (Double)this.range.get())
            && BlockUtils.canPlaceBlock(world, true, entry.state().method_26204())) {
            FindItemResult item = this.findPlaceable(entry.state());
            if (item.found() && BlockUtils.place(world, item, (Boolean)this.rotate.get(), 50, (Boolean)this.swing.get(), true, true)) {
               this.placedThisSession++;
               return true;
            }
         }
      }

      return false;
   }

   private FindItemResult findPlaceable(class_2680 state) {
      class_1792 item = state.method_26204().method_8389();
      if (!(item instanceof class_1747)) {
         return new FindItemResult(-1, 0);
      } else {
         FindItemResult hotbar = InvUtils.findInHotbar(new class_1792[]{item});
         if (hotbar.found()) {
            return hotbar;
         } else {
            FindItemResult anywhere = InvUtils.find(stack -> stack.method_31574(item));
            if (!anywhere.found()) {
               return anywhere;
            } else {
               if (this.mc.field_1724.field_7512 == this.mc.field_1724.field_7498) {
                  for (int i = 0; i <= 8; i++) {
                     if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) {
                        InvUtils.move().from(anywhere.slot()).toHotbar(i);
                        break;
                     }
                  }
               }

               return new FindItemResult(-1, 0);
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.loaded != null && this.anchor != null) {
         if ((Boolean)this.renderBounds.get()) {
            event.renderer
               .box(
                  this.anchor.method_10263(),
                  this.anchor.method_10264(),
                  this.anchor.method_10260(),
                  this.anchor.method_10263() + this.loaded.size().method_10263(),
                  this.anchor.method_10264() + this.loaded.size().method_10264(),
                  this.anchor.method_10260() + this.loaded.size().method_10260(),
                  (Color)this.boundsColor.get(),
                  (Color)this.boundsColor.get(),
                  ShapeMode.Lines,
                  0
               );
         }

         if ((Boolean)this.renderNext.get()) {
            int shown = Math.min((Integer)this.renderCount.get(), this.pending.size());

            for (int i = 0; i < shown; i++) {
               class_2338 world = this.anchor.method_10081(this.pending.get(i).offset());
               event.renderer.box(world, (Color)this.queueSideColor.get(), (Color)this.queueLineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
            }
         }
      }
   }

   public String getInfoString() {
      if (this.loaded == null) {
         return null;
      } else {
         return this.pending.isEmpty() ? this.placedThisSession + " placed" : String.format("%d left, %d placed", this.pending.size(), this.placedThisSession);
      }
   }

   public static enum OriginMode {
      Litematica,
      AtFeet,
      Manual;
   }
}
