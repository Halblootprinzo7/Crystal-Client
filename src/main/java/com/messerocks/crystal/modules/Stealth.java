package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.AimUtils;
import com.messerocks.crystal.utils.HumanSwap;
import com.messerocks.crystal.utils.TurnProgress;
import com.messerocks.crystal.utils.VanillaLimits;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.class_1297;
import net.minecraft.class_2338;
import net.minecraft.class_243;

public class Stealth extends CrystalModule {
   private final SettingGroup sgLimits = this.settings.getDefaultGroup();
   private final SettingGroup sgPacing = this.settings.createGroup("Pacing");
   private final SettingGroup sgHuman = this.settings.createGroup("Human");
   private final Setting<Boolean> serverReach = this.sgLimits
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("server-reach"))
                  .description("Use the reach the server actually allows, read from your interaction-range attributes. Anything beyond it is refused anyway."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> lineOfSight = this.sgLimits
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("require-line-of-sight"))
                  .description("Only act on what your eyes can see. Clicking through a wall is not something vanilla can do."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> legitPlace = this.sgLimits
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("legit-place"))
                  .description(
                     "Place against a face your eyes actually reach along the rotation you send, instead of always the block's top centre. This is the single thing that makes placing work behind Grim and Vulcan: a prediction anticheat reproduces your raycast and drops anything that does not line up. Costs a few spots you could reach through a corner, which those servers refuse anyway."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> maxTurn = this.sgLimits
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-turn-per-tick"))
               .description("Degrees the head may turn per tick, on the way to a target and on the way back to where you are looking. A mouse cannot jump. 0 disables the cap."))
            .defaultValue(45.0)
            .min(0.0)
            .sliderMax(180.0)
            .build()
      );
   private final Setting<Double> smoothness = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("aim-smoothness"))
               .description(
                  "How much the turn follows a human reaching curve instead of a straight ramp. 0 turns at exactly max-turn-per-tick until it snaps onto the target - inside the limit, but a constant speed no hand produces. 1 accelerates and decelerates like an arm, which takes about 1.9x as long for the same angle."
               ))
            .defaultValue(0.7)
            .min(0.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final Setting<Double> jitter = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("timing-jitter"))
               .description("Varies every delay by this fraction. An exact period is information you have no reason to give away."))
            .defaultValue(0.3)
            .min(0.0)
            .sliderMax(1.0)
            .build()
      );
   private final Setting<Double> globalRate = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-actions-per-second"))
               .description(
                  "Ceiling across all modules together. This is the honest lever: no packet is as telling as twenty placements a second, however clean each one looks. 0 removes the ceiling."
               ))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(40.0)
            .build()
      );
   private final Setting<Double> skipChance = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("miss-chance"))
               .description(
                  "Drops this fraction of actions outright. People miss; perfect conversion does not happen. Costs you real damage, which is the point."
               ))
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(0.3)
            .build()
      );
   private final Setting<Boolean> pauseInScreens = this.sgPacing
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("pause-in-screens"))
                  .description("Stop acting while a container or menu is open. Fighting through an open chest is not possible by hand."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> humanMode = this.sgHuman
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("human-mode"))
                  .description(
                     "Auto Crystal, the anchor macro and Sword Place may only do what a hand on a mouse and keyboard can: a hotbar switch that stands for a full tick before the item is used and another tick before switching back (no silent swaps), always looking at what they click, and a reaction time before Auto Crystal acts on anything new. Every Crystal module gets one click per tick between them all and only acts on what is in front of you. Slower, by design."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> reactionTime = this.sgHuman
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("reaction-time")
            .description(
               "Milliseconds before an automatic module acts on something new: a target that walks into range, a crystal that appears, a spot that opens up. Varied by timing-jitter. Spots you keep using are already known and wait for nothing. Key-driven modules are not delayed - the key press is your own reaction."
            )
            .defaultValue(150)
            .min(0)
            .sliderRange(0, 400)
            .visible(this.humanMode::get)
            .build()
      );
   private final Setting<Double> viewAngle = this.sgHuman
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("view-angle")
            .description(
               "Only act on what lies within this many degrees of where you are looking. 90 is everything in front of you; anything further means turning around for it."
            )
            .defaultValue(90.0)
            .min(10.0)
            .sliderRange(30.0, 180.0)
            .visible(this.humanMode::get)
            .build()
      );
   private double budget;
   private long lastRefill;
   private int lastClickTick = -1;

   public Stealth() {
      super(CrystalAddon.CATEGORY, "stealth", "Shared behaviour limits the other Crystal modules obey.");
   }

   public void onActivate() {
      double rate = (Double)this.globalRate.get();
      this.budget = rate <= 0.0 ? 0.0 : Math.min(1.0, rate);
      this.lastRefill = System.nanoTime();
   }

   @EventHandler
   private void onTick(Pre event) {
      long now = System.nanoTime();
      double elapsed = (now - this.lastRefill) / 1.0E9;
      this.lastRefill = now;
      double rate = (Double)this.globalRate.get();
      if (!(rate <= 0.0)) {
         this.budget = Math.min(this.budget + elapsed * rate, rate);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   private void onTickFirst(Pre event) {
      HumanSwap.observe();
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   private void onTickLast(Pre event) {
      TurnProgress.SHARED.easeBack();
      HumanSwap.tickReturn();
   }

   private static Stealth get() {
      Stealth module = (Stealth)Modules.get().get(Stealth.class);
      return module != null && module.isActive() ? module : null;
   }

   public static boolean allowsBlock(class_2338 pos, class_243 point) {
      Stealth stealth = get();
      if (stealth == null) {
         return true;
      } else if (!stealth.inView(point)) {
         return false;
      } else {
         return stealth.serverReach.get() && !VanillaLimits.canReachBlock(pos)
            ? false
            : !(Boolean)stealth.lineOfSight.get() || VanillaLimits.hasLineOfSight(point);
      }
   }

   public static boolean allowsEntity(class_1297 entity) {
      Stealth stealth = get();
      if (stealth == null) {
         return true;
      } else if (!stealth.inView(entity.method_5829().method_1005())) {
         return false;
      } else {
         return stealth.serverReach.get() && !VanillaLimits.canReachEntity(entity)
            ? false
            : !(Boolean)stealth.lineOfSight.get() || VanillaLimits.hasLineOfSight(entity.method_33571());
      }
   }

   private boolean inView(class_243 point) {
      return !(Boolean)this.humanMode.get() || AimUtils.withinCone(point, (Double)this.viewAngle.get());
   }

   public static boolean humanMode() {
      Stealth stealth = get();
      return stealth != null && (Boolean)stealth.humanMode.get();
   }

   // The reaction time for one new thing, already varied by timing-jitter. 0 outside human mode.
   public static long reactionNanos() {
      Stealth stealth = get();
      if (stealth != null && (Boolean)stealth.humanMode.get() && (Integer)stealth.reactionTime.get() > 0) {
         return (long)((Integer)stealth.reactionTime.get() * actionCost() * 1000000.0);
      } else {
         return 0L;
      }
   }

   public static double turnCap() {
      Stealth stealth = get();
      return stealth == null ? 0.0 : (Double)stealth.maxTurn.get();
   }

   public static boolean legitPlace() {
      Stealth stealth = get();
      return stealth != null && (Boolean)stealth.legitPlace.get();
   }

   public static double aimSmoothness() {
      Stealth stealth = get();
      return stealth == null ? 0.0 : (Double)stealth.smoothness.get();
   }

   public static double actionCost() {
      Stealth stealth = get();
      if (stealth == null) {
         return 1.0;
      } else {
         double jitter = (Double)stealth.jitter.get();
         return jitter <= 0.0 ? 1.0 : Math.max(0.1, 1.0 + (Math.random() * 2.0 - 1.0) * Math.min(1.0, jitter));
      }
   }

   public static int pace(int ticks) {
      Stealth stealth = get();
      return stealth == null ? ticks : VanillaLimits.jitter(ticks, (Double)stealth.jitter.get());
   }

   public static boolean claimAction() {
      Stealth stealth = get();
      if (stealth == null) {
         return true;
      } else if ((Boolean)stealth.pauseInScreens.get() && stealth.mc.field_1755 != null) {
         return false;
      } else if ((Boolean)stealth.humanMode.get() && stealth.mc.field_1724 != null && stealth.lastClickTick == stealth.mc.field_1724.field_6012) {
         return false;
      } else if (VanillaLimits.roll((Double)stealth.skipChance.get())) {
         return false;
      } else if ((Double)stealth.globalRate.get() > 0.0 && stealth.budget < 1.0) {
         return false;
      } else {
         if ((Double)stealth.globalRate.get() > 0.0) {
            stealth.budget--;
         }

         if ((Boolean)stealth.humanMode.get() && stealth.mc.field_1724 != null) {
            stealth.lastClickTick = stealth.mc.field_1724.field_6012;
            HumanSwap.used();
         }

         return true;
      }
   }

   public String getInfoString() {
      return this.globalRate.get() <= 0.0 ? "uncapped" : String.format("%.0f/%.0f", this.budget, this.globalRate.get());
   }
}
