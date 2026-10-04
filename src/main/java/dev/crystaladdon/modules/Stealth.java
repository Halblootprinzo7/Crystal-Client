package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.utils.AimUtils;
import dev.crystaladdon.utils.ClickGate;
import dev.crystaladdon.utils.LegitPlace;
import dev.crystaladdon.utils.TurnProgress;
import dev.crystaladdon.utils.VanillaLimits;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_10185;
import net.minecraft.class_1297;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_490;
import net.minecraft.class_9334;

public class Stealth extends CrystalModule {
   private final SettingGroup sgLimits = this.settings.getDefaultGroup();
   private final SettingGroup sgPacing = this.settings.createGroup("Pacing");
   private final Setting<Boolean> legitPlace = this.sgLimits
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("force-rotate"))
                  .description(
                     "Turn onto the face or hitbox being clicked, whatever the module's own rotate setting says. Every click is only made where the ray from your eyes along the rotation the server has really lands, either way; off, a module with rotate off waits until your own view gets there."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> maxTurn = this.sgLimits
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-turn-per-tick"))
               .description(
                  "Degrees the head may turn per tick at the fastest point of a turn. A turn within this lands in one tick; a wider one is spread over several, and the click waits for its end - at 45 and aim-smoothness 0.7 a 46 to 90 degree turn takes 4 ticks. 90 is already a very fast flick; up to 180 turns onto anything in front of you within a single tick."
               ))
            .defaultValue(45.0)
            .range(5.0, 180.0)
            .sliderRange(5.0, 180.0)
            .build()
      );
   private final Setting<Double> viewAngle = this.sgLimits
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("view-angle"))
               .description(
                  "Only act on what lies within this many degrees of where you are looking. 90 is everything in front of you; anything further means turning around for it, which a module does not do for you."
               ))
            .defaultValue(90.0)
            .range(45.0, 120.0)
            .sliderRange(45.0, 120.0)
            .build()
      );
   private final Setting<Integer> reaction = this.sgLimits
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("reaction-time"))
                  .description(
                     "Ticks between something happening - a pop, a crystal appearing, an enemy raising a shield - and the first action that answers it. People need about 150 ms (3 ticks) at their very best; 0 answers in the same tick."
                  ))
               .defaultValue(4))
            .range(0, 20)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Double> smoothness = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("aim-smoothness"))
               .description(
                  "How much the turn follows a human reaching curve instead of a straight ramp. 0 turns at constant top speed and arrives soonest; 1 accelerates and decelerates like an arm, which takes about 1.9x as long for the same angle. Only turns wider than max-turn-per-tick are affected - anything within it lands in one tick either way."
               ))
            .defaultValue(0.7)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final Setting<Double> jitter = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("timing-jitter"))
               .description("Varies every delay by this fraction. An exact period is something no hand produces."))
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final Setting<Double> globalRate = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-actions-per-second"))
               .description(
                  "Clicks per second across all modules together, left and right button counted together, so keep it at least the crystal aura's place-speed plus break-speed. After a pause up to one second's worth is saved up, so a short burst is not slowed. 20 is one click a tick on average; 40 lets both buttons click every tick; above that only a module whose own speed is set above 20 clicks more than once a tick."
               ))
            .defaultValue(20.0)
            .range(1.0, 100.0)
            .sliderRange(1.0, 100.0)
            .build()
      );
   private final Setting<Boolean> sameTickSwitch = this.sgPacing
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("same-tick-switch")
            .description(
               "Let a module select a hotbar slot and click with it in the same tick. A player does the same when a number key and a mouse button go down within one 50 ms tick: vanilla handles the hotbar keys before the use and attack buttons, so the switch goes out first and the click right after it. On, Sword Place, the crystal aura and the anchor macro save a tick at every item switch; off, every click waits a tick after a slot change."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> afterInventory = this.sgPacing
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("after-inventory"))
                  .description(
                     "Ticks after an inventory click - a refill, a totem move, your own - before the next world click or hotbar switch, yours included. 4 is about the 200 ms a hand needs from the inventory back to the fight; 1 only keeps the click out of the tick of the inventory click itself."
                  ))
               .defaultValue(4))
            .range(1, 4)
            .sliderRange(1, 4)
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
   private static double budget = 1.0;
   private static long lastRefill = -1L;
   private static final class_310 mc = class_310.method_1551();

   public Stealth() {
      super(CrystalAddon.CATEGORY, "stealth", "Limits every Crystal module obeys. They apply whether this is on or off; the settings tune the human side.");
   }

   private static Stealth settings() {
      return (Stealth)Modules.get().get(Stealth.class);
   }

   public static boolean allowsBlock(class_2338 pos, class_243 point) {
      return VanillaLimits.canReachBlock(pos) && inView(point) && VanillaLimits.hasLineOfSight(point);
   }

   public static boolean allowsEntity(class_1297 entity) {
      return VanillaLimits.canReachEntity(entity) && inView(entity.method_5829()) && canSeeAnyOf(entity);
   }

   public static double viewAngle() {
      return (Double)settings().viewAngle.get();
   }

   public static boolean inView(class_243 point) {
      return AimUtils.withinCone(point, (Double)settings().viewAngle.get());
   }

   public static boolean inView(class_238 box) {
      if (inView(box.method_1005())) {
         return true;
      } else {
         for (int i = 0; i < 8; i++) {
            class_243 corner = new class_243(
               (i & 1) == 0 ? box.field_1323 : box.field_1320, (i & 2) == 0 ? box.field_1322 : box.field_1325, (i & 4) == 0 ? box.field_1321 : box.field_1324
            );
            if (inView(corner)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean canSeeAnyOf(class_1297 entity) {
      if (mc.field_1724 == null) {
         return false;
      } else if (VanillaLimits.hasLineOfSight(entity.method_33571())) {
         return true;
      } else {
         class_238 box = entity.method_5829();
         class_243 eyes = mc.field_1724.method_33571();
         double reach = Math.sqrt(box.method_49271(eyes)) + box.method_17939() + box.method_17940() + box.method_17941();
         return LegitPlace.forEntity(box, reach) != null;
      }
   }

   public static double turnCap() {
      return (Double)settings().maxTurn.get();
   }

   public static boolean legitPlace() {
      return (Boolean)settings().legitPlace.get();
   }

   public static boolean sameTickSwitch() {
      return (Boolean)settings().sameTickSwitch.get();
   }

   public static int afterInventoryTicks() {
      return (Integer)settings().afterInventory.get();
   }

   public static double aimSmoothness() {
      return (Double)settings().smoothness.get();
   }

   public static double actionCost() {
      double jitter = (Double)settings().jitter.get();
      return Math.max(0.1, 1.0 + (Math.random() * 2.0 - 1.0) * Math.min(1.0, jitter));
   }

   public static int pace(int ticks) {
      return VanillaLimits.jitter(ticks, (Double)settings().jitter.get());
   }

   public static int paceAtLeast(int min, int ticks) {
      return Math.max(min, pace(ticks));
   }

   public static int reactionTicks() {
      int base = (Integer)settings().reaction.get();
      double spread = (Double)settings().jitter.get();
      return base + (int)Math.round(Math.random() * base * spread);
   }

   public static boolean claimAction() {
      if (!canAct()) {
         return false;
      } else if (VanillaLimits.roll((Double)settings().skipChance.get())) {
         return false;
      } else {
         budget--;
         return true;
      }
   }

   private static boolean canAct() {
      if (mc.field_1755 != null || mc.method_18506() != null) {
         return false;
      } else if (mc.field_1724 == null || mc.field_1724.method_6115() || mc.field_1724.method_29504()) {
         return false;
      } else if (mc.field_1724.method_5765()) {
         return false;
      } else {
         return (TurnProgress.ownClickPending() || TurnProgress.cameraRequested() || mc.field_1724.method_6128()) && TurnProgress.SHARED.ownClickMismatched()
            ? false
            : budget >= 1.0;
      }
   }

   public static boolean canUse() {
      return mc.field_1761 != null && !mc.field_1761.method_2923() ? ClickGate.canUse() && canAct() : false;
   }

   private static boolean attackItemHits() {
      if (mc.field_1724 == null) {
         return false;
      } else {
         class_1799 stack = mc.field_1724.method_6047();
         return stack.method_57826(class_9334.field_63631) ? false : !mc.field_1724.method_75202(stack, 0);
      }
   }

   public static boolean canAttack() {
      if (mc.field_1761 == null || mc.field_1761.method_2923()) {
         return false;
      } else {
         return !attackItemHits() ? false : ClickGate.canAttack() && canAct();
      }
   }

   public static boolean claimUse() {
      return mc.field_1761 != null && !mc.field_1761.method_2923() ? ClickGate.canUse() && claimAction() : false;
   }

   public static boolean claimAttack() {
      if (mc.field_1761 == null || mc.field_1761.method_2923()) {
         return false;
      } else {
         return !attackItemHits() ? false : ClickGate.canAttack() && claimAction();
      }
   }

   public static boolean allowsInventoryClick() {
      if (mc.field_1724 == null) {
         return false;
      } else if (mc.field_1755 instanceof class_490) {
         return true;
      } else if (mc.field_1755 != null) {
         return false;
      } else {
         class_10185 input = mc.field_1724.field_3913.field_54155;
         boolean sneakHeld = input.comp_3164() && !(Boolean)mc.field_1690.method_42449().method_41753();
         return !input.comp_3159()
            && !input.comp_3160()
            && !input.comp_3161()
            && !input.comp_3162()
            && !input.comp_3163()
            && !sneakHeld
            && !mc.field_1724.method_5624()
            && !mc.field_1724.method_6115();
      }
   }

   public static boolean handsBusy() {
      return mc.field_1724 != null && mc.field_1724.method_6115();
   }

   public static boolean paused() {
      return mc.field_1755 != null;
   }

   public String getInfoString() {
      return String.format("%.0f/%.0f", budget, this.globalRate.get());
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(Stealth.Ticker.class);
      ClickGate.init();
   }

   private static final class Ticker {
      @EventHandler
      private static void onTick(Pre event) {
         long now = System.nanoTime();
         double elapsed = Stealth.lastRefill < 0L ? 0.0 : (now - Stealth.lastRefill) / 1.0E9;
         Stealth.lastRefill = now;
         double rate = (Double)Stealth.settings().globalRate.get();
         Stealth.budget = Math.min(Stealth.budget + elapsed * rate, Math.max(1.0, rate));
      }

      @EventHandler(
         priority = -200
      )
      private static void onTickLate(Pre event) {
         TurnProgress.SHARED.easeBack();
      }
   }
}
