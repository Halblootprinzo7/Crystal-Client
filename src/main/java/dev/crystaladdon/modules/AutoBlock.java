package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.mixin.MinecraftClientAccessor;
import dev.crystaladdon.utils.BlastShield;
import dev.crystaladdon.utils.ComboDetector;
import dev.crystaladdon.utils.HotbarSwap;
import dev.crystaladdon.utils.InventoryGuard;
import dev.crystaladdon.utils.LegitPlace;
import dev.crystaladdon.utils.ReactionClock;
import dev.crystaladdon.utils.RevivedPlayers;
import dev.crystaladdon.utils.TotemRules;
import dev.crystaladdon.utils.TurnProgress;
import dev.crystaladdon.utils.VanillaClick;
import dev.crystaladdon.utils.VanillaLimits;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntSupplier;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Sent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_10707;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1301;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1675;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2885;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_9334;
import net.minecraft.class_1269.class_9860;
import net.minecraft.class_1269.class_9861;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class AutoBlock extends CrystalModule {
   private static final int RELEASE_GAP = 2;
   private static final int RESELECT_TICKS = 3;
   private static final int LET_GO_PATIENCE = 20;
   private static final Object COMBO = new Object();
   private static final Object LOWER = new Object();
   private final SettingGroup sgCombo = this.settings.getDefaultGroup();
   private final SettingGroup sgLive = this.settings.createGroup("Live crystal");
   private final SettingGroup sgArm = this.settings.createGroup("Pre-arm");
   private final SettingGroup sgDisabled = this.settings.createGroup("Disabled shield");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> comboHits = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("combo-hits"))
                  .description(
                     "How many crystals have to go off near you inside the window before it counts as a combo. Two means the shield is up from the second crystal onwards - the first one can never be blocked."
                  ))
               .defaultValue(2))
            .min(1)
            .sliderRange(1, 6)
            .build()
      );
   private final Setting<Integer> comboWindow = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("combo-window"))
                  .description("Ticks the pops have to fall within to count as one combo. 40 is two seconds."))
               .defaultValue(40))
            .min(5)
            .sliderRange(10, 100)
            .build()
      );
   private final Setting<Double> comboRange = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("combo-range"))
               .description("How close a crystal has to go off to count as aimed at you."))
            .defaultValue(8.0)
            .min(1.0)
            .sliderRange(2.0, 16.0)
            .build()
      );
   private final Setting<Double> minPopDamage = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("min-pop-damage"))
               .description(
                  "Only count a crystal that went off if it would have dealt at least this much to you, so pops behind cover or in someone else's fight do not raise the shield."
               ))
            .defaultValue(3.0)
            .min(0.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Boolean> countOwn = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("count-own-crystals"))
                  .description("Also count crystals you placed or hit yourself. Off stops your own aura's pops from looking like a combo on you."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Integer> hold = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("hold-after-last-pop"))
                  .description(
                     "Ticks to keep blocking after the last crystal went off, so a pause in the rhythm does not catch you mid-lower. Counted from when the combo was noticed if that is later; the shield comes down a reaction time after it runs out."
                  ))
               .defaultValue(30))
            .min(0)
            .sliderRange(0, 80)
            .build()
      );
   private final Setting<AutoBlock.HandMode> hand = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("hand"))
                  .description(
                     "Main hand swaps your hotbar to the shield. Offhand leaves your weapon selected, so you swing the moment the block drops - no swing goes out while any shield is up, that is vanilla. Only while the offhand is free: a totem there, or Smart Totem running, keeps it for the totem. An offhand shield only goes up behind an empty hand, a sword or a totem in the main hand - anything else would take the right click first."
                  ))
               .defaultValue(AutoBlock.HandMode.MainHand))
            .build()
      );
   private final Setting<Boolean> mainHandFallback = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("main-hand-fallback"))
                     .description(
                        "In Offhand mode, block with the main hand instead when the offhand is reserved for the totem. Off means no block at all in that case."
                     ))
                  .defaultValue(false))
               .visible(() -> this.hand.get() == AutoBlock.HandMode.Offhand))
            .build()
      );
   private final Setting<Integer> shieldSlot = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("shield-slot"))
                  .description(
                     "Hotbar slot (1-9) to swap the shield into when it is only in your inventory and the hotbar is full. 0 only uses an empty hotbar slot."
                  ))
               .defaultValue(0))
            .range(0, 9)
            .sliderRange(0, 9)
            .build()
      );
   private final Setting<Boolean> dontInterruptEating = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("dont-interrupt-eating"))
                  .description(
                     "Do not swap away or cancel food, a golden apple or a potion you are using right now. Off lets go of it first and raises the shield a moment later, never in the same tick."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> faceThreat = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("face-the-blast"))
                  .description("Turn towards what is being blocked. A shield only stops what comes from in front, so without this the block is decorative."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say when the shield goes up and comes down."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> blockLive = this.sgLive
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("block-standing-crystals"))
                  .description(
                     "Also block for a crystal that is standing next to you. This is the one chance to catch the first crystal too - it only works when the enemy leaves it up for longer than your reaction time instead of popping it instantly."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> liveRange = this.sgLive
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("live-range"))
                  .description("How far to look for a crystal that is already placed."))
               .defaultValue(8.0)
               .min(1.0)
               .sliderRange(2.0, 16.0)
               .visible(this.blockLive::get))
            .build()
      );
   private final Setting<Double> minDamage = this.sgLive
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("min-damage"))
                  .description("Only block for a standing crystal that would deal at least this much, so a crystal you already have cover from is ignored."))
               .defaultValue(4.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.blockLive::get))
            .build()
      );
   private final Setting<Boolean> preArm = this.sgArm
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pre-arm"))
                  .description(
                     "Hold the shield up whenever an enemy is close at all, before anything has happened - a reaction time after they come into range. At the price of blocking nearly all the time in a fight."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Double> preArmRange = this.sgArm
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("pre-arm-range"))
                  .description("How close an enemy has to be for pre-arm."))
               .defaultValue(6.0)
               .min(1.0)
               .sliderRange(1.0, 12.0)
               .visible(this.preArm::get))
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgArm
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("ignore-friends"))
                     .description("Do not treat friends as someone who would crystal you."))
                  .defaultValue(true))
               .visible(this.preArm::get))
            .build()
      );
   private final Setting<Boolean> swapBackWhileDisabled = this.sgDisabled
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swap-back-while-disabled"))
                  .description(
                     "While an axe has disabled the shield, give the main hand back to what you held before, so you can fight for those seconds. The shield comes back as soon as it is ready."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> disabledAlert = this.sgDisabled
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("disabled-alert"))
                  .description("Say in chat when the shield gets disabled and when it is ready again."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Outline what the shield is up for."))
               .defaultValue(true))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shape-mode"))
                     .description("How the shape is rendered."))
                  .defaultValue(ShapeMode.Lines))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> liveColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("crystal-color"))
                  .description("Outline colour for a crystal that is standing there now."))
               .defaultValue(new SettingColor(80, 180, 255, 200))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> comboColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("combo-color"))
                  .description("Outline colour for where the last crystal of a combo went off."))
               .defaultValue(new SettingColor(255, 90, 90, 180))
               .visible(this.render::get))
            .build()
      );
   private final ComboDetector combo = new ComboDetector();
   private final ReactionClock clock = new ReactionClock();
   private final AutoBlock.Sightings sightings = new AutoBlock.Sightings();
   private final TotemRules.InventoryReach reach = new TotemRules.InventoryReach();
   private int ticks;
   private class_1511 live;
   private float liveDamage;
   private boolean liveSeen;
   private boolean comboLatched;
   private boolean comboWaiting;
   private boolean comboArmed;
   private int comboArmedAt;
   private class_243 comboAim;
   private class_1657 armedBy;
   private boolean enemySeen;
   private boolean blocking;
   private int returnSlot = -1;
   private int heldSlot = -1;
   private int restoreAt = -1;
   private int reselectSlot = -1;
   private int reselectFrom = -1;
   private int reselectAt = -1;
   private int releasedAt = -1073741824;
   private int releaseGap;
   private final AutoBlock.AxeHit axeHit = new AutoBlock.AxeHit();
   private class_1799 shieldUpLastTick;
   private boolean keyLetGo;
   private boolean keepLetGo;
   private boolean letGoSpent;
   private boolean raising;
   private boolean hasShield;
   private boolean disabled;
   private FindItemResult readyShield = new FindItemResult(-1, 0);
   private boolean warned;
   private static final double FACING_SLACK = 45.0;

   public AutoBlock() {
      super(CrystalAddon.CATEGORY, "auto-block", "Raises a shield once it notices you are being crystal comboed.");
   }

   public void onDeactivate() {
      this.releaseKey();
      this.restoreKey();
      if (this.mc.field_1724 != null) {
         int selected = this.mc.field_1724.method_31548().method_67532();
         if (this.returnSlot != -1 && HotbarSwap.stillOn(this.heldSlot)) {
            HotbarSwap.selectLater(this.returnSlot, this.heldSlot, Stealth.reactionTicks());
         } else if (this.reselectSlot != -1 && HotbarSwap.stillOn(this.reselectFrom)) {
            HotbarSwap.selectLater(this.reselectSlot, this.reselectFrom, Stealth.reactionTicks());
         }
      }

      this.returnSlot = -1;
      this.heldSlot = -1;
      this.restoreAt = -1;
      this.clearReselect();
      this.releasedAt = -1073741824;
      this.axeHit.reset();
      this.shieldUpLastTick = null;
      this.keepLetGo = false;
      this.letGoSpent = false;
      this.combo.reset();
      this.clock.clear();
      this.sightings.reset();
      this.reach.reset();
      this.live = null;
      this.liveSeen = false;
      this.comboLatched = false;
      this.comboWaiting = false;
      this.comboArmed = false;
      this.comboAim = null;
      this.armedBy = null;
      this.enemySeen = false;
      this.hasShield = false;
      this.disabled = false;
      this.readyShield = new FindItemResult(-1, 0);
      this.warned = false;
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (event.entity instanceof class_1511 crystal) {
            boolean var7 = this.combo.isOwn(crystal.method_5628());
            this.combo.forget(crystal.method_5628());
            if (!var7 || (Boolean)this.countOwn.get()) {
               double range = (Double)this.comboRange.get();
               if (!(this.mc.field_1724.method_5858(crystal) > range * range)) {
                  float damage = BlastShield.crystalDamage(this.mc.field_1724, crystal.method_73189());
                  if (!(damage < (Double)this.minPopDamage.get())) {
                     this.combo.record(crystal.method_73189(), damage);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onSent(Sent event) {
      if (this.mc.method_18854() && this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (!this.raising) {
            if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.meteor$getEntity() instanceof class_1511 crystal) {
               this.combo.markAttack(crystal.method_5628());
            } else if (event.packet instanceof class_2885 packet) {
               class_1799 stack = this.mc.field_1724.method_5998(packet.method_12546());
               if (!stack.method_31574(class_1802.field_8301) && !stack.method_7960()) {
                  return;
               }

               class_2338 clicked = packet.method_12543().method_17777();
               class_2680 state = this.mc.field_1687.method_8320(clicked);
               if (!state.method_27852(class_2246.field_10540) && !state.method_27852(class_2246.field_9987)) {
                  return;
               }

               this.combo.markPlacement(clicked.method_10084().method_10063());
            }
         }
      }
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (event.entity instanceof class_1511 crystal) {
         this.combo.onCrystalAdded(crystal.method_5628(), crystal.method_24515().method_10063());
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         if (this.sessionChanged()) {
            this.returnSlot = -1;
            this.clearReselect();
            this.onDeactivate();
         }

         this.ticks++;
         this.clock.tick();
         this.keepLetGo = false;
         this.combo.tick((Integer)this.comboWindow.get());
         this.comboLatched = this.combo.updateLatch((Integer)this.comboHits.get(), (Integer)this.hold.get());
         this.updateShieldState();
         this.axeHit.update(this.ticks, this.shieldJustDisabled(), this.disabledShieldInUse(), Stealth::reactionTicks);
         this.updateCombo();
         this.liveSeen = false;
         this.enemySeen = false;
         this.live = this.blockLive.get() ? this.findLiveThreat() : null;
         this.armedBy = this.preArm.get() ? this.readyEnemy((Double)this.preArmRange.get()) : null;
         boolean armed = this.comboArmed || this.live != null || this.armedBy != null;
         if (Stealth.paused()) {
            this.releaseKey();
            this.settleKey(false);
         } else {
            boolean threat = this.comboLatched || this.comboWaiting || this.liveSeen || this.enemySeen;
            if (armed) {
               this.clock.forget(LOWER);
               this.startBlocking();
            } else if (this.blocking && (threat || !this.clock.ready(LOWER))) {
               if (threat) {
                  this.clock.forget(LOWER);
               }

               this.keepBlocking();
            } else {
               this.clock.forget(LOWER);
               this.stopBlocking();
               this.warned = false;
            }

            this.settleKey(armed);
            if (!armed) {
               this.letGoSpent = false;
            }

            this.handBack();
         }
      }
   }

   private void updateShieldState() {
      this.hasShield = InvUtils.find(AutoBlock::isShield).found();
      this.readyShield = InvUtils.find(this::isReadyShield);
      boolean nowDisabled = this.hasShield && !this.readyShield.found();
      if (nowDisabled != this.disabled) {
         this.disabled = nowDisabled;
         if ((Boolean)this.disabledAlert.get()) {
            if (this.disabled) {
               this.warning("Shield disabled.", new Object[0]);
            } else {
               this.info("Shield ready.", new Object[0]);
            }
         }
      }
   }

   private static boolean isShield(class_1799 stack) {
      return stack.method_57826(class_9334.field_56396);
   }

   private boolean isReadyShield(class_1799 stack) {
      return isShield(stack) && !this.mc.field_1724.method_7357().method_7904(stack);
   }

   private boolean disabledShieldInUse() {
      if (!this.mc.field_1724.method_6115()) {
         return false;
      } else {
         class_1799 active = this.mc.field_1724.method_6030();
         return isShield(active) && !this.isReadyShield(active);
      }
   }

   private boolean shieldJustDisabled() {
      return this.disabledShieldInUse() ? true : this.shieldUpLastTick != null && this.mc.field_1724.method_7357().method_7904(this.shieldUpLastTick);
   }

   @EventHandler(
      priority = -200
   )
   private void onTickPost(Post event) {
      if (this.mc.field_1724 != null && this.mc.field_1724.method_6115()) {
         class_1799 active = this.mc.field_1724.method_6030();
         this.shieldUpLastTick = isShield(active) ? active : null;
      } else {
         this.shieldUpLastTick = null;
      }
   }

   private void updateCombo() {
      if (this.comboLatched && !this.comboArmed) {
         this.comboWaiting = true;
      }

      if (this.comboWaiting && this.clock.ready(COMBO)) {
         this.comboWaiting = false;
         this.comboArmed = true;
         this.comboArmedAt = this.ticks;
      }

      if (this.comboArmed && !this.comboLatched && this.ticks - this.comboArmedAt > Math.max(1, (Integer)this.hold.get())) {
         this.comboArmed = false;
      }

      if (!this.comboLatched && !this.comboArmed && !this.comboWaiting) {
         this.clock.forget(COMBO);
         this.comboAim = null;
      }

      class_243 last = this.combo.lastPos();
      if ((this.comboLatched || this.comboArmed) && last != null && this.clock.ready(last)) {
         this.comboAim = last;
      }
   }

   private class_1511 findLiveThreat() {
      double range = (Double)this.liveRange.get();
      double rangeSq = range * range;
      class_1511 worst = null;
      float worstDamage = 0.0F;
      class_238 area = class_238.method_30048(this.mc.field_1724.method_73189(), range * 2.0, range * 2.0, range * 2.0);

      for (class_1511 crystal : this.mc.field_1687.method_8390(class_1511.class, area, entity -> !entity.method_31481())) {
         if (this.sightings.noticed(crystal.method_5628(), Stealth.inView(crystal.method_5829()))
            && !(this.mc.field_1724.method_5858(crystal) > rangeSq)
            && ((Boolean)this.countOwn.get() || !this.combo.isOwn(crystal.method_5628()))) {
            float damage = BlastShield.crystalDamage(this.mc.field_1724, crystal.method_73189());
            if (!(damage < (Double)this.minDamage.get())) {
               this.liveSeen = true;
               if (this.clock.ready(crystal.method_5628()) && damage > worstDamage) {
                  worstDamage = damage;
                  worst = crystal;
               }
            }
         }
      }

      this.sightings.endTick();
      this.liveDamage = worstDamage;
      return worst;
   }

   private class_1657 readyEnemy(double within) {
      double rangeSq = within * within;
      double bestSq = rangeSq;
      class_1657 best = null;

      for (class_1657 player : this.mc.field_1687.method_18456()) {
         if (player != this.mc.field_1724
            && !player.method_7325()
            && (player.method_5805() || RevivedPlayers.isRevived(player))
            && (!(Boolean)this.ignoreFriends.get() || Friends.get().shouldAttack(player))) {
            double distSq = this.mc.field_1724.method_5858(player);
            if (!(distSq > rangeSq)) {
               this.enemySeen = true;
               if (this.clock.ready(player.method_5628()) && distSq <= bestSq) {
                  bestSq = distSq;
                  best = player;
               }
            }
         }
      }

      return best;
   }

   private class_243 facingTarget() {
      if (this.live != null) {
         return this.live.method_73189();
      } else if (this.comboArmed && this.comboAim != null) {
         return this.comboAim;
      } else {
         return this.armedBy == null ? null : this.armedBy.method_33571();
      }
   }

   private void keepBlocking() {
      if (this.mc.field_1724.method_6115() && this.isReadyShield(this.mc.field_1724.method_6030())) {
         this.restoreAt = -1;
         this.hold(this.mc.field_1724.method_6058());
      } else {
         this.stopBlocking();
      }
   }

   private void startBlocking() {
      if (!this.hasShield) {
         this.stopBlocking();
      } else if (this.disabled) {
         this.lowerKey();
         if ((Boolean)this.swapBackWhileDisabled.get()) {
            this.scheduleRestore();
         }
      } else if (!this.disabledShieldInUse() && this.axeHit.answerable(this.ticks)) {
         boolean shieldUp = this.mc.field_1724.method_6115() && this.isReadyShield(this.mc.field_1724.method_6030());
         boolean usingOther = this.mc.field_1724.method_6115() && !shieldUp;
         if (usingOther && (Boolean)this.dontInterruptEating.get()) {
            this.releaseKey();
         } else {
            this.restoreAt = -1;
            class_1268 soon = shieldUp ? this.mc.field_1724.method_6058() : this.raiseHandSoon();
            if ((Boolean)this.faceThreat.get()) {
               if (soon != null) {
                  this.face(this.facingTarget());
               } else {
                  TurnProgress.SHARED.reset(this);
               }
            }

            if (shieldUp) {
               this.hold(this.mc.field_1724.method_6058());
            } else if (!usingOther) {
               if (this.ticks - this.releasedAt < this.releaseGap) {
                  if (soon != null) {
                     this.keepLetGo = true;
                  }
               } else if (!this.bringToHand(this.readyShield)) {
                  this.notRaised(soon);
               } else {
                  class_1268 shieldHand = null;
                  if (this.isReadyShield(this.mc.field_1724.method_6047())) {
                     shieldHand = class_1268.field_5808;
                  } else if (this.isReadyShield(this.mc.field_1724.method_6079()) && !InventoryGuard.offhandInFlight()) {
                     shieldHand = class_1268.field_5810;
                  }

                  if (shieldHand != null && this.raise(shieldHand)) {
                     this.hold(shieldHand);
                  } else {
                     this.notRaised(soon);
                  }
               }
            } else {
               if (soon != null && !this.letGoSpent) {
                  this.letGo();
               } else {
                  this.releaseKey();
               }
            }
         }
      } else {
         this.lowerKey();
      }
   }

   private class_1268 raiseHandSoon() {
      if (this.readyShield.found() && !this.mc.field_1724.method_5765()) {
         class_1268 shieldHand = this.shieldHandSoon();
         if (shieldHand == null) {
            return null;
         } else {
            return this.clickReaches(this.crosshair(LegitPlace.currentYaw(), LegitPlace.currentPitch()), shieldHand) ? shieldHand : null;
         }
      } else {
         return null;
      }
   }

   private class_1268 shieldHandSoon() {
      if (this.isReadyShield(this.mc.field_1724.method_6047())) {
         return class_1268.field_5808;
      } else {
         boolean offhandReady = this.isReadyShield(this.mc.field_1724.method_6079()) && !InventoryGuard.offhandInFlight();
         if (this.readyShield.isOffhand()) {
            return offhandReady ? class_1268.field_5810 : null;
         } else {
            if (this.hand.get() == AutoBlock.HandMode.Offhand) {
               if (InventoryGuard.offhandInFlight()) {
                  return null;
               }

               if (offhandReady) {
                  return class_1268.field_5810;
               }

               if (!this.offhandReserved() || !(Boolean)this.mainHandFallback.get()) {
                  return null;
               }
            }

            return this.readyShield.isHotbar() ? class_1268.field_5808 : null;
         }
      }
   }

   private boolean clickReaches(class_239 target, class_1268 shieldHand) {
      if (target instanceof class_3966 hit && !this.passesClick(hit.method_17782())) {
         return false;
      } else {
         class_3965 block = target instanceof class_3965 hit ? hit : null;
         return block != null && LegitPlace.isInteractive(this.mc.field_1687.method_8320(block.method_17777()))
            ? false
            : VanillaClick.reaches(block, shieldHand);
      }
   }

   private void notRaised(class_1268 soon) {
      this.releaseKey();
      if (this.keyLetGo && soon != null && this.ticks - this.releasedAt <= 20) {
         this.keepLetGo = true;
      }
   }

   private void hold(class_1268 shieldHand) {
      this.keyLetGo = false;
      this.letGoSpent = false;
      if (!this.blocking) {
         this.blocking = true;
         if ((Boolean)this.chatInfo.get()) {
            if (this.comboArmed) {
               this.info("Shield up, %d crystals in %d ticks.", new Object[]{this.combo.count(), this.comboWindow.get()});
            } else if (this.live != null) {
               this.info("Shield up, a crystal next to you is worth %.1f.", new Object[]{this.liveDamage});
            } else {
               this.info("Shield up, enemy close.", new Object[0]);
            }
         }
      }

      this.mc.field_1690.field_1904.method_23481(true);
   }

   private void letGo() {
      this.blocking = false;
      this.mc.field_1690.field_1904.method_23481(false);
      this.keyLetGo = true;
      this.keepLetGo = true;
      this.releasedAt = this.ticks;
      this.releaseGap = Math.max(1, Stealth.pace(2));
   }

   private boolean raise(class_1268 shieldHand) {
      double yaw = LegitPlace.currentYaw();
      double pitch = LegitPlace.currentPitch();
      class_239 target = this.crosshair(yaw, pitch);
      if (!this.clickReaches(target, shieldHand)) {
         return false;
      } else {
         class_3966 entity = target instanceof class_3966 hit ? hit : null;
         class_3965 block = target instanceof class_3965 hitx ? hitx : null;
         if (!Stealth.claimUse()) {
            return false;
         } else {
            float viewYaw = this.mc.field_1724.method_36454();
            float viewPitch = this.mc.field_1724.method_36455();
            this.mc.field_1724.method_36456((float)yaw);
            this.mc.field_1724.method_36457((float)pitch);
            this.raising = true;

            class_1268 acted;
            try {
               acted = entity != null ? this.useOnEntityCrosshair(entity) : VanillaClick.use(block, true);
            } finally {
               this.raising = false;
               this.mc.field_1724.method_36456(viewYaw);
               this.mc.field_1724.method_36457(viewPitch);
            }

            return acted == shieldHand && this.mc.field_1724.method_6115() && this.mc.field_1724.method_6058() == shieldHand;
         }
      }
   }

   private boolean passesClick(class_1297 entity) {
      return entity instanceof class_1511 || entity instanceof class_1657 && entity != this.mc.field_1724;
   }

   private class_1268 useOnEntityCrosshair(class_3966 hit) {
      class_1297 entity = hit.method_17782();
      if (!this.mc.field_1687.method_8621().method_11952(entity.method_24515())) {
         return null;
      } else {
         ((MinecraftClientAccessor)this.mc).crystal$setItemUseCooldown(4);

         for (class_1268 hand : class_1268.values()) {
            class_1799 stack = this.mc.field_1724.method_5998(hand);
            if (!stack.method_45435(this.mc.field_1687.method_45162())) {
               return null;
            }

            if (this.mc.field_1724.method_56094(entity, 0.0)) {
               class_1269 result = this.mc.field_1761.method_2917(this.mc.field_1724, entity, hit, hand);
               if (!result.method_23665()) {
                  result = this.mc.field_1761.method_2905(this.mc.field_1724, entity, hand);
               }

               if (result instanceof class_9860 success) {
                  if (success.comp_2909() == class_9861.field_52427) {
                     this.mc.field_1724.method_6104(hand);
                  }

                  return hand;
               }
            }

            if (!stack.method_7960() && this.mc.field_1761.method_2919(this.mc.field_1724, hand) instanceof class_9860 success) {
               if (success.comp_2909() == class_9861.field_52427) {
                  this.mc.field_1724.method_6104(hand);
               }

               return hand;
            }
         }

         return null;
      }
   }

   private class_239 crosshair(double yaw, double pitch) {
      class_243 eyes = this.mc.field_1724.method_33571();
      class_243 direction = class_243.method_1030((float)pitch, (float)yaw);
      double blockRange = VanillaLimits.blockRange();
      double entityRange = VanillaLimits.entityRange();
      double reach = Math.max(blockRange, entityRange);
      class_3965 block = this.mc
         .field_1687
         .method_17742(new class_3959(eyes, eyes.method_1019(direction.method_1021(reach)), class_3960.field_17559, class_242.field_1348, this.mc.field_1724));
      boolean blockHit = block != null && block.method_17783() == class_240.field_1332;
      double limitSq = blockHit ? block.method_17784().method_1025(eyes) : reach * reach;
      class_243 stretch = direction.method_1021(Math.sqrt(limitSq));
      class_238 search = this.mc.field_1724.method_5829().method_18804(stretch).method_1009(1.0, 1.0, 1.0);
      class_3966 entity = class_1675.method_18075(this.mc.field_1724, eyes, eyes.method_1019(stretch), search, class_1301.field_52443, limitSq);
      if (entity != null && entity.method_17784().method_1025(eyes) < limitSq) {
         return entity.method_17784().method_24802(eyes, entityRange) ? entity : null;
      } else {
         return blockHit && block.method_17784().method_24802(eyes, blockRange) ? block : null;
      }
   }

   private void face(class_243 threat) {
      if (threat != null) {
         double yaw = Rotations.getYaw(threat);
         boolean serverFacing = Math.abs(class_3532.method_15338(yaw - LegitPlace.currentYaw())) <= 45.0;
         boolean viewFacing = Math.abs(class_3532.method_15338(yaw - this.mc.field_1724.method_36454())) <= 45.0;
         if (serverFacing && viewFacing) {
            TurnProgress.SHARED.reset(this);
         } else {
            TurnProgress.SHARED.turnTo(this, yaw, this.mc.field_1724.method_36455(), 100, null);
         }
      }
   }

   private boolean bringToHand(FindItemResult shield) {
      if (shield.isOffhand()) {
         return true;
      } else {
         if (this.hand.get() == AutoBlock.HandMode.Offhand) {
            if (this.isReadyShield(this.mc.field_1724.method_6079())) {
               return true;
            }

            if (this.isReadyShield(this.mc.field_1724.method_6047())) {
               return true;
            }

            if (InventoryGuard.offhandInFlight()) {
               return false;
            }

            if (!this.offhandReserved()) {
               if (shield.isHotbar()) {
                  int selected = this.mc.field_1724.method_31548().method_67532();
                  int home = this.homeSlot();
                  if (InventoryGuard.swapWithOffhand(shield.slot()) && shield.slot() != selected && this.reselectSlot == -1) {
                     this.reselectSlot = home;
                     this.reselectFrom = shield.slot();
                     this.reselectAt = -1;
                  }

                  return false;
               }

               if (this.inventoryReached(shield.slot(), 40) && InventoryGuard.swapToOffhand(shield.slot())) {
                  this.reach.reset();
               }

               return false;
            }

            if (!(Boolean)this.mainHandFallback.get()) {
               this.warnOnce("The offhand is kept for the totem - not blocking. Enable main-hand-fallback to block with the main hand.");
               return false;
            }
         }

         if (this.isReadyShield(this.mc.field_1724.method_6047())) {
            return true;
         } else if (shield.isHotbar()) {
            int selected = this.mc.field_1724.method_31548().method_67532();
            if (shield.slot() != selected) {
               int home = this.homeSlot();
               if (!HotbarSwap.select(shield.slot())) {
                  return false;
               }

               if (this.returnSlot == -1) {
                  this.returnSlot = home;
               }

               this.heldSlot = shield.slot();
            }

            return true;
         } else {
            int target = -1;

            for (int i = 0; i <= 8; i++) {
               if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) {
                  target = i;
                  break;
               }
            }

            if (target == -1 && (Integer)this.shieldSlot.get() > 0) {
               target = (Integer)this.shieldSlot.get() - 1;
            }

            if (target == -1) {
               this.warnOnce("The shield is only in your inventory and the hotbar is full - set shield-slot to let it swap in.");
               return false;
            } else {
               if (this.inventoryReached(shield.slot(), target) && InventoryGuard.swapToHotbar(shield.slot(), target)) {
                  this.reach.reset();
               }

               return false;
            }
         }
      }
   }

   private boolean offhandReserved() {
      SmartTotem totem = (SmartTotem)Modules.get().get(SmartTotem.class);
      return this.mc.field_1724.method_6079().method_31574(class_1802.field_8288) || totem != null && totem.isActive();
   }

   private int homeSlot() {
      if (this.reselectSlot >= 0 && SmartTotem.stillOn(this.reselectFrom)) {
         return this.reselectSlot;
      } else {
         SmartTotem totem = (SmartTotem)Modules.get().get(SmartTotem.class);
         int totemBack = totem == null ? -1 : totem.pendingReselect();
         return totemBack >= 0 ? totemBack : HotbarSwap.homeSlot();
      }
   }

   public int pendingReturn() {
      if (!this.isActive() || this.mc.field_1724 == null) {
         return -1;
      } else if (this.reselectSlot >= 0 && SmartTotem.stillOn(this.reselectFrom)) {
         return this.reselectSlot;
      } else {
         return this.returnSlot >= 0 && SmartTotem.stillOn(this.heldSlot) ? this.returnSlot : -1;
      }
   }

   private boolean inventoryReached(int from, int button) {
      return this.mc.field_1755 == null
         && InventoryGuard.inventoryFree()
         && this.reach.advance(from * 100 + button, SmartTotem.inventoryCouldBeOpen(2), () -> Stealth.pace(6));
   }

   private void warnOnce(String message) {
      if (!this.warned) {
         this.warned = true;
         this.warning(message, new Object[0]);
      }
   }

   private void releaseKey() {
      if (this.blocking) {
         this.mc.field_1690.field_1904.method_23481(Input.isPressed(this.mc.field_1690.field_1904));
         this.blocking = false;
         TurnProgress.SHARED.reset(this);
         if ((Boolean)this.chatInfo.get()) {
            this.info("Shield down.", new Object[0]);
         }
      }
   }

   private void settleKey(boolean armed) {
      if (this.keyLetGo && !this.keepLetGo && this.ticks - this.releasedAt >= this.releaseGap) {
         this.restoreKey();
         if (armed) {
            this.letGoSpent = true;
         }
      }
   }

   private void restoreKey() {
      if (this.keyLetGo) {
         this.keyLetGo = false;
         this.mc.field_1690.field_1904.method_23481(Input.isPressed(this.mc.field_1690.field_1904));
      }
   }

   private void scheduleRestore() {
      if (this.returnSlot != -1 && this.restoreAt < 0) {
         this.restoreAt = this.ticks + Stealth.reactionTicks();
      }
   }

   private void handBack() {
      int selected = this.mc.field_1724.method_31548().method_67532();
      boolean mainHandInUse = this.mc.field_1724.method_6115() && this.mc.field_1724.method_6058() == class_1268.field_5808;
      if (this.reselectSlot >= 0 && !InventoryGuard.offhandInFlight()) {
         if (this.reselectAt < 0) {
            int delay = Math.max(1, Stealth.pace(3));
            this.reselectAt = this.ticks + delay;
            if (selected == this.reselectFrom) {
               HotbarSwap.selectLater(this.reselectSlot, this.reselectFrom, delay);
            }
         } else if (this.ticks >= this.reselectAt) {
            if (!SmartTotem.stillOn(this.reselectFrom)) {
               this.clearReselect();
            } else if (!mainHandInUse && HotbarSwap.select(this.reselectSlot)) {
               this.clearReselect();
            }
         }
      }

      if (this.returnSlot >= 0 && this.restoreAt >= 0 && this.ticks >= this.restoreAt) {
         if (!SmartTotem.stillOn(this.heldSlot)) {
            this.clearReturn();
         } else if (!mainHandInUse && HotbarSwap.select(this.returnSlot)) {
            this.clearReturn();
         }
      }
   }

   private void clearReselect() {
      this.reselectSlot = -1;
      this.reselectFrom = -1;
      this.reselectAt = -1;
   }

   private void clearReturn() {
      this.returnSlot = -1;
      this.heldSlot = -1;
      this.restoreAt = -1;
   }

   private void stopBlocking() {
      this.lowerKey();
      this.scheduleRestore();
      this.reach.reset();
   }

   private void lowerKey() {
      if (this.blocking && this.axeHit.keepKey(this.ticks)) {
         this.mc.field_1690.field_1904.method_23481(true);
      } else {
         this.releaseKey();
      }
   }

   public boolean isBlocking() {
      return this.isActive() && this.blocking;
   }

   public boolean shieldDisabled() {
      return this.isActive() && this.disabled;
   }

   public boolean canBlockNow() {
      return this.isActive() && this.hasShield && !this.disabled;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get()) {
         if (this.live != null) {
            event.renderer.box(this.live.method_5829(), (Color)this.liveColor.get(), (Color)this.liveColor.get(), (ShapeMode)this.shapeMode.get(), 0);
         } else {
            class_243 last = this.combo.lastPos();
            if (this.comboLatched && last != null) {
               event.renderer
                  .box(
                     last.field_1352 - 0.5,
                     last.field_1351 - 0.5,
                     last.field_1350 - 0.5,
                     last.field_1352 + 0.5,
                     last.field_1351 + 1.5,
                     last.field_1350 + 0.5,
                     (Color)this.comboColor.get(),
                     (Color)this.comboColor.get(),
                     (ShapeMode)this.shapeMode.get(),
                     0
                  );
            }
         }
      }
   }

   public String getInfoString() {
      boolean threat = this.comboLatched || this.live != null || this.armedBy != null;
      if (this.disabled && threat) {
         return "disabled";
      } else {
         String prefix = "";
         if (this.mc.field_1724 != null && this.mc.field_1724.method_6115()) {
            class_10707 component = (class_10707)this.mc.field_1724.method_6030().method_58694(class_9334.field_56396);
            if (component != null && this.mc.field_1724.method_6048() < component.method_67197()) {
               prefix = "arming ";
            }
         }

         if (this.comboLatched) {
            return String.format("%scombo x%d %.1f", prefix, this.combo.count(), this.combo.damageInWindow());
         } else if (this.live != null) {
            return String.format("%s%.1f", prefix, this.liveDamage);
         } else {
            return this.blocking ? prefix + "holding" : null;
         }
      }
   }

   static final class AxeHit {
      private boolean seen;
      private boolean inUse;
      private int answerAt = Integer.MIN_VALUE;

      void update(int tick, boolean hit, boolean stillInUse, IntSupplier reaction) {
         if (hit && !this.seen) {
            this.answerAt = tick + reaction.getAsInt();
         }

         this.seen = hit;
         this.inUse = hit && stillInUse;
      }

      boolean answerable(int tick) {
         return tick >= this.answerAt;
      }

      boolean keepKey(int tick) {
         return this.inUse && tick < this.answerAt;
      }

      void reset() {
         this.seen = false;
         this.inUse = false;
         this.answerAt = Integer.MIN_VALUE;
      }
   }

   public static enum HandMode {
      MainHand,
      Offhand;
   }

   static final class Sightings {
      private final Set<Integer> seen = new HashSet<>();
      private final Set<Integer> present = new HashSet<>();

      boolean noticed(int id, boolean inView) {
         this.present.add(id);
         if (inView) {
            this.seen.add(id);
         }

         return this.seen.contains(id);
      }

      void endTick() {
         this.seen.retainAll(this.present);
         this.present.clear();
      }

      void reset() {
         this.seen.clear();
         this.present.clear();
      }
   }
}
