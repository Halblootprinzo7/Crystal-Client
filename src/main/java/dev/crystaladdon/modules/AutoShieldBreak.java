package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.utils.ClickGate;
import dev.crystaladdon.utils.HotbarSwap;
import dev.crystaladdon.utils.KnockbackPredictor;
import dev.crystaladdon.utils.LegitPlace;
import dev.crystaladdon.utils.ReactionClock;
import dev.crystaladdon.utils.RevivedPlayers;
import dev.crystaladdon.utils.TurnProgress;
import dev.crystaladdon.utils.VanillaLimits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_10590;
import net.minecraft.class_10707;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_2879;
import net.minecraft.class_3532;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_9334;
import net.minecraft.class_10707.class_10708;

public class AutoShieldBreak extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range"))
               .description(
                  "How close the blocking enemy has to be, measured to their hitbox. Never more than the server's entity reach - 3 in vanilla - whatever this says."
               ))
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
                  .description("Ticks before hitting the same player again, so one disable does not soak up three swings. Spread by Stealth's timing-jitter."))
               .defaultValue(20))
            .min(5)
            .sliderRange(5, 60)
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
                  .description("Return to the item you were holding a few ticks after the hit - never in the tick of the hit itself."))
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
                  .description("Swing your arm visibly. Off only hides the animation; the swing packet goes out either way, as vanilla's does."))
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
   private final Map<UUID, Integer> nextHit = new HashMap<>();
   private final ReactionClock reactions = new ReactionClock();
   static final int EDGE_GRACE = 2;
   private final Map<UUID, Integer> awaySince = new HashMap<>();
   private int tick;
   private class_1657 target;
   private int returnSlot = -1;
   private int axeSlot = -1;
   private int restoreAt = -1;
   private boolean struck;

   public AutoShieldBreak() {
      super(CrystalAddon.CATEGORY, "auto-shield-break", "Hits a blocking enemy with an axe to put their shield on cooldown.");
   }

   public void onDeactivate() {
      if (this.returnSlot != -1 && this.mc.field_1724 != null && HotbarSwap.stillOn(this.axeSlot)) {
         HotbarSwap.selectLater(this.returnSlot, this.axeSlot, Stealth.reactionTicks());
      }

      this.returnSlot = -1;
      this.axeSlot = -1;
      this.restoreAt = -1;
      this.nextHit.clear();
      this.reactions.clear();
      this.awaySince.clear();
      this.turn.reset(TURN_OWNER);
      this.target = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.sessionChanged()) {
            this.returnSlot = -1;
            this.onDeactivate();
            this.tick = 0;
         }

         if (Stealth.paused()) {
            this.turn.reset(TURN_OWNER);
         } else {
            this.tick++;
            this.reactions.tick();
            if ((Boolean)this.pauseWhileBlocking.get() && this.ownShieldUp()) {
               this.target = null;
            } else {
               this.target = this.findBlockingEnemy();
               if (this.target == null) {
                  this.turn.reset(TURN_OWNER);
                  this.putAxeAway();
               } else {
                  FindItemResult axe = InvUtils.find(AutoShieldBreak::disablesBlocking, 0, 8);
                  if (!axe.found()) {
                     this.target = null;
                     this.putAxeAway();
                  } else if (!Stealth.allowsEntity(this.target)) {
                     this.putAxeAway();
                  } else {
                     boolean rotating = this.shouldRotate();
                     double reach = this.reach();
                     boolean alongCamera = rotating
                        && TurnProgress.cameraNeeded()
                        && LegitPlace.confirmEntity(this.target.method_5829(), this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455(), reach)
                           != null;
                     double yaw;
                     double pitch;
                     if (alongCamera) {
                        yaw = this.mc.field_1724.method_36454();
                        pitch = this.mc.field_1724.method_36455();
                     } else if (rotating) {
                        // Only looks in view: a box passes allowsEntity with one corner in the cone, and its cheapest
                        // point can then lie behind you - with the camera pitched down, a player right behind your back.
                        LegitPlace.EntityResult aim = LegitPlace.forEntity(this.target.method_5829(), reach, Stealth::allowsLook);
                        if (aim == null) {
                           this.putAxeAway();
                           return;
                        }

                        yaw = aim.yaw();
                        pitch = aim.pitch();
                     } else {
                        yaw = LegitPlace.currentYaw();
                        pitch = LegitPlace.currentPitch();
                        // The look the server has may be held anywhere after another module's turn.
                        if (!Stealth.allowsLook(yaw, LegitPlace.confirmEntity(this.target.method_5829(), yaw, pitch, reach))) {
                           this.putAxeAway();
                           return;
                        }
                     }

                     if (rotating && !alongCamera && TurnProgress.cameraNeeded()) {
                        this.putAxeAway();
                     } else {
                        boolean charging = (Boolean)this.requireCharged.get() && this.mc.field_1724.method_7261(0.0F) < (Double)this.charge.get();
                        boolean holdingAxe = axe.slot() == this.mc.field_1724.method_31548().method_67532();
                        if (rotating && !this.turn.wouldReach(TURN_OWNER, yaw, pitch, 60)) {
                           boolean refused = this.turn.lastCallBlocked();
                           if (!refused) {
                              this.turn.turnTo(TURN_OWNER, yaw, pitch, 60, null);
                           }

                           if (charging && holdingAxe && !refused) {
                              this.restoreAt = -1;
                           } else {
                              this.putAxeAway();
                           }
                        } else if (!this.hitCanGoOut()) {
                           this.putAxeAway();
                        } else {
                           if (!holdingAxe) {
                              int home = HotbarSwap.homeSlot();
                              if (!HotbarSwap.select(axe.slot())) {
                                 return;
                              }

                              if (this.returnSlot == -1) {
                                 this.returnSlot = home;
                              }

                              this.axeSlot = axe.slot();
                              this.restoreAt = -1;
                              if (ClickGate.slotChangedThisTick()) {
                                 if (rotating && !alongCamera) {
                                    this.turn.turnTo(TURN_OWNER, yaw, pitch, 60, null);
                                 }

                                 return;
                              }
                           }

                           if (charging) {
                              this.restoreAt = -1;
                              if (rotating) {
                                 this.turn.turnTo(TURN_OWNER, yaw, pitch, 60, null);
                              }
                           } else {
                              class_1657 victim = this.target;
                              class_746 player = this.mc.field_1724;
                              class_638 world = this.mc.field_1687;
                              this.struck = false;
                              Runnable attack = this.activeAction(
                                 () -> {
                                    if (this.isActive()
                                       && this.mc.field_1724 == player
                                       && this.mc.field_1687 == world
                                       && inPlay(victim)
                                       && this.shieldFacesMe(victim)
                                       && Stealth.allowsEntity(victim)
                                       && disablesBlocking(this.mc.field_1724.method_6047())) {
                                       if (!(Boolean)this.requireCharged.get() || !(this.mc.field_1724.method_7261(0.0F) < (Double)this.charge.get())) {
                                          // Judged on the point this very look hits, and only when no other clicker sent a
                                          // click this tick along a different look of its own.
                                          if (Stealth.allowsLook(yaw, LegitPlace.confirmEntity(victim.method_5829(), yaw, pitch, this.reach()))
                                             && this.turn.clickLookFree(yaw, pitch)) {
                                             if (Stealth.claimAttack()) {
                                                HotbarSwap.syncSelected();
                                                // The hit goes along this look: make sure it is the one this tick's movement
                                                // packet carries.
                                                this.turn.noteOwnClick(yaw, pitch);
                                                this.mc.field_1724.field_3944.method_52787(class_2824.method_34206(victim, this.mc.field_1724.method_5715()));
                                                if ((Boolean)this.swing.get()) {
                                                   this.mc.field_1724.method_6104(class_1268.field_5808);
                                                } else {
                                                   this.mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
                                                }

                                                KnockbackPredictor.afterAttack();
                                                this.struck = true;
                                                this.nextHit.put(victim.method_5667(), this.tick + Stealth.pace((Integer)this.retryDelay.get()));
                                                if ((Boolean)this.chatInfo.get()) {
                                                   this.info("Broke %s's shield.", new Object[]{victim.method_5477().getString()});
                                                }

                                                if ((Boolean)this.swapBack.get()) {
                                                   if (this.returnSlot != -1) {
                                                      this.restoreAt = this.tick + Math.max(2, Stealth.pace(4));
                                                   }
                                                } else {
                                                   this.returnSlot = -1;
                                                   this.axeSlot = -1;
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              );
                              if (!rotating) {
                                 attack.run();
                              } else {
                                 this.turn.turnTo(TURN_OWNER, yaw, pitch, 60, attack);
                              }

                              if (!this.struck) {
                                 this.putAxeAway();
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

   private boolean hitCanGoOut() {
      return ClickGate.canAttack() && Stealth.canUse();
   }

   private void putAxeAway() {
      if (this.returnSlot != -1 && this.restoreAt < 0) {
         this.restoreAt = this.tick + Stealth.reactionTicks();
      }

      this.restoreSlot();
   }

   private double reach() {
      return Math.min((Double)this.range.get(), VanillaLimits.entityRange());
   }

   private boolean shouldRotate() {
      return (Boolean)this.rotate.get() || Stealth.legitPlace();
   }

   private boolean shieldFacesMe(class_1657 player) {
      return this.facesMe(player, player.method_62821());
   }

   private boolean facesMe(class_1657 player, class_1799 blocking) {
      if (blocking != null && !blocking.method_7960()) {
         class_10707 blocks = (class_10707)blocking.method_58694(class_9334.field_56396);
         if (blocks == null) {
            return false;
         } else {
            float widest = -1.0F;

            for (class_10708 reduction : blocks.comp_3588()) {
               widest = Math.max(widest, reduction.comp_3638());
            }

            return widest < 0.0F
               ? false
               : AutoShieldBreak.BlockingArc.contains(
                  this.mc.field_1724.method_23317() - player.method_23317(),
                  this.mc.field_1724.method_23321() - player.method_23321(),
                  player.method_5791(),
                  widest
               );
         }
      } else {
         return false;
      }
   }

   private static boolean inPlay(class_1657 player) {
      return !player.method_31481() && (player.method_6032() > 0.0F || RevivedPlayers.isRevived(player));
   }

   private static boolean disablesBlocking(class_1799 stack) {
      if (stack.method_57826(class_9334.field_63631)) {
         return false;
      } else {
         class_10590 weapon = (class_10590)stack.method_58694(class_9334.field_55878);
         return weapon != null && weapon.comp_3602() > 0.0F;
      }
   }

   private static boolean raisingShield(class_1657 player) {
      return player.method_6115() && player.method_6030().method_57826(class_9334.field_56396);
   }

   private class_1657 findBlockingEnemy() {
      double reach = this.reach();
      double bestSq = reach * reach;
      class_1657 best = null;
      this.awaySince.values().removeIf(since -> this.tick - since > 2);

      for (class_1657 player : this.mc.field_1687.method_18456()) {
         if (player != this.mc.field_1724 && inPlay(player) && (!(Boolean)this.ignoreFriends.get() || Friends.get().shouldAttack(player))) {
            UUID id = player.method_5667();
            double distSq = player.method_5829().method_49271(this.mc.field_1724.method_33571());
            boolean raising = raisingShield(player);
            boolean visible = raising && Stealth.inView(player.method_5829());
            boolean facing = raising && this.facesMe(player, player.method_6030());
            boolean news = shieldNews(raising, visible, facing, distSq, reach);
            int awayFor = !news && visible ? this.tick - this.awaySince.computeIfAbsent(id, k -> this.tick) : 0;
            AutoShieldBreak.Clock step = clockStep(raising, visible, facing, distSq, reach, awayFor);
            if (step == AutoShieldBreak.Clock.Forget) {
               this.reactions.forget(id);
               this.awaySince.remove(id);
            } else if (step != AutoShieldBreak.Clock.Hold) {
               this.awaySince.remove(id);
               boolean seen = this.reactions.ready(id);
               if (this.shieldFacesMe(player)) {
                  Integer allowed = this.nextHit.get(id);
                  if ((allowed == null || this.tick >= allowed) && seen && !(distSq > bestSq)) {
                     bestSq = distSq;
                     best = player;
                  }
               }
            }
         }
      }

      return best;
   }

   static boolean shieldNews(boolean raising, boolean inView, boolean facing, double distSq, double reach) {
      return raising && inView && facing && distSq <= reach * reach;
   }

   static AutoShieldBreak.Clock clockStep(boolean raising, boolean inView, boolean facing, double distSq, double reach, int awayFor) {
      if (!raising || !inView) {
         return AutoShieldBreak.Clock.Forget;
      } else if (shieldNews(raising, inView, facing, distSq, reach)) {
         return AutoShieldBreak.Clock.Run;
      } else {
         return awayFor < 2 ? AutoShieldBreak.Clock.Hold : AutoShieldBreak.Clock.Forget;
      }
   }

   private boolean ownShieldUp() {
      AutoBlock autoBlock = (AutoBlock)Modules.get().get(AutoBlock.class);
      return autoBlock != null && autoBlock.isBlocking();
   }

   private void restoreSlot() {
      if (this.returnSlot != -1 && this.restoreAt >= 0 && this.tick >= this.restoreAt) {
         int selected = this.mc.field_1724.method_31548().method_67532();
         if (selected != this.axeSlot) {
            if (!this.ownShieldUp()) {
               this.returnSlot = -1;
               this.axeSlot = -1;
               this.restoreAt = -1;
            }
         } else if (HotbarSwap.select(this.returnSlot)) {
            this.returnSlot = -1;
            this.axeSlot = -1;
            this.restoreAt = -1;
         }
      }
   }

   public String getInfoString() {
      return this.target == null ? null : this.target.method_5477().getString();
   }

   static final class BlockingArc {
      private BlockingArc() {
      }

      static boolean contains(double towardsAttackerX, double towardsAttackerZ, float headYaw, float maxAngle) {
         float yaw = -headYaw * (float) (Math.PI / 180.0);
         class_243 facing = new class_243(class_3532.method_15374(yaw), 0.0, class_3532.method_15362(yaw));
         class_243 towards = new class_243(towardsAttackerX, 0.0, towardsAttackerZ).method_1029();
         double angle = Math.acos(towards.method_1026(facing));
         return !(angle > (float) (Math.PI / 180.0) * maxAngle);
      }
   }

   static enum Clock {
      Run,
      Hold,
      Forget;
   }
}
