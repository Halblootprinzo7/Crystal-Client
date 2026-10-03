package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.TurnProgress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_10590;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_2824;
import net.minecraft.class_2879;
import net.minecraft.class_9334;

public class AutoShieldBreak extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range")).description("How close the blocking enemy has to be. Vanilla melee reach is 3."))
            .defaultValue(3.0)
            .min(1.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Boolean> requireCharged = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("require-charged"))
                  .description(
                     "Wait for the attack meter to fill before swinging. Off by default: an uncharged hit disables the shield just as well, and switching to the axe resets the meter, so waiting costs about a second."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Double> charge = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("charge")).description("How full the attack meter has to be."))
               .defaultValue(0.9)
               .min(0.1)
               .sliderRange(0.1, 1.0)
               .visible(this.requireCharged::get))
            .build()
      );
   private final Setting<Integer> retryDelay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("retry-delay"))
                  .description("Ticks before hitting the same player again, so one disable does not soak up three swings."))
               .defaultValue(20))
            .min(0)
            .sliderRange(0, 60)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Face the target before swinging."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> swapBack = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swap-back"))
                  .description("Return to the item you were holding after the hit."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> pauseWhileBlocking = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-while-blocking"))
                  .description("Stay out of the way while Auto Block is holding your own shield up. Without this the two modules fight over your main hand."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-friends"))
                  .description("Never break a friend's shield."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Swing your arm visibly. Off sends the swing packet without the client-side animation."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say whose shield was broken."))
               .defaultValue(false))
            .build()
      );
   private static final Object TURN_OWNER = new Object();
   private final TurnProgress turn = TurnProgress.SHARED;
   private final Map<UUID, Integer> lastHit = new HashMap<>();
   private int tick;
   private int returnSlot = -1;
   private class_1657 target;

   public AutoShieldBreak() {
      super(CrystalAddon.CATEGORY, "auto-shield-break", "Hits a blocking enemy with an axe to put their shield on cooldown.");
   }

   public void onDeactivate() {
      this.restoreSlot();
      this.lastHit.clear();
      this.turn.reset(TURN_OWNER);
      this.target = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.tick++;
         if ((Boolean)this.pauseWhileBlocking.get() && this.ownShieldUp()) {
            this.restoreSlot();
            this.target = null;
         } else {
            this.target = this.findBlockingEnemy();
            if (this.target == null) {
               this.restoreSlot();
               this.turn.reset(TURN_OWNER);
            } else {
               FindItemResult axe = InvUtils.findInHotbar(AutoShieldBreak::disablesBlocking);
               if (!axe.found() || !axe.isHotbar() || axe.isOffhand()) {
                  this.target = null;
               } else if (Stealth.allowsEntity(this.target)) {
                  if (axe.slot() != this.mc.field_1724.method_31548().method_67532()) {
                     if (this.returnSlot == -1) {
                        this.returnSlot = this.mc.field_1724.method_31548().method_67532();
                     }

                     InvUtils.swap(axe.slot(), false);
                  }

                  if (!(Boolean)this.requireCharged.get() || !(this.mc.field_1724.method_7261(0.0F) < (Double)this.charge.get())) {
                     if (Stealth.claimAction()) {
                        class_1657 victim = this.target;
                        Runnable attack = () -> {
                           this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(victim, this.mc.field_1724.method_5715()));
                           if ((Boolean)this.swing.get()) {
                              this.mc.field_1724.method_6104(class_1268.field_5808);
                           } else {
                              this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
                           }

                           this.mc.field_1724.method_7350();
                           this.lastHit.put(victim.method_5667(), this.tick);
                           if ((Boolean)this.chatInfo.get()) {
                              this.info("Broke %s's shield.", new Object[]{victim.method_5477().getString()});
                           }

                           if ((Boolean)this.swapBack.get()) {
                              this.restoreSlot();
                           }
                        };
                        if (!(Boolean)this.rotate.get()) {
                           attack.run();
                        } else {
                           this.turn.turnTo(TURN_OWNER, Rotations.getYaw(victim), Rotations.getPitch(victim, Target.Body), 60, attack);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean disablesBlocking(class_1799 stack) {
      class_10590 weapon = (class_10590)stack.method_58694(class_9334.field_55878);
      return weapon != null && weapon.comp_3602() > 0.0F;
   }

   private class_1657 findBlockingEnemy() {
      double bestSq = (Double)this.range.get() * (Double)this.range.get();
      class_1657 best = null;

      for (class_1657 player : this.mc.field_1687.method_18456()) {
         if (player != this.mc.field_1724
            && player.method_5805()
            && (!(Boolean)this.ignoreFriends.get() || Friends.get().shouldAttack(player))
            && player.method_6039()) {
            Integer hit = this.lastHit.get(player.method_5667());
            if (hit == null || this.tick - hit >= (Integer)this.retryDelay.get()) {
               double distSq = this.mc.field_1724.method_5858(player);
               if (distSq <= bestSq) {
                  bestSq = distSq;
                  best = player;
               }
            }
         }
      }

      return best;
   }

   private boolean ownShieldUp() {
      AutoBlock autoBlock = (AutoBlock)Modules.get().get(AutoBlock.class);
      return autoBlock != null && autoBlock.isBlocking();
   }

   private void restoreSlot() {
      if (this.returnSlot != -1 && this.mc.field_1724 != null) {
         InvUtils.swap(this.returnSlot, false);
         this.returnSlot = -1;
      }
   }

   public String getInfoString() {
      return this.target == null ? null : this.target.method_5477().getString();
   }
}
