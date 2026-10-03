package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.RevivedPlayers;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_2535;
import net.minecraft.class_2540;
import net.minecraft.class_2663;
import net.minecraft.class_9334;

public class PopWindow extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> window = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("window")).description("How many ticks the window stays open after a pop.")).defaultValue(20))
            .min(1)
            .sliderMax(60)
            .build()
      );
   private final Setting<Double> crystalMinDamage = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("crystal-min-damage"))
               .description("Damage requirement Auto Crystal uses inside the window."))
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Double> anchorMinDamage = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("anchor-min-damage"))
               .description("Damage requirement Auto Anchor uses inside the window."))
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Boolean> ignoreRatio = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-damage-ratio"))
                  .description("Also ignore Auto Anchor's min-damage-ratio inside the window. Your own safety limit still applies."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> closeOnRetotem = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("close-on-retotem"))
                  .description("Close the window as soon as the target has a totem in hand again - a weak hit would only pop them a second time."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> retotemGrace = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("retotem-grace"))
                     .description("Ticks to wait before believing the totem you see in their hands: their old totem is still shown for a moment after the pop."))
                  .defaultValue(3))
               .min(0)
               .sliderMax(10)
               .visible(this.closeOnRetotem::get))
            .build()
      );
   private final Setting<Boolean> extendWhileNaked = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("extend-while-naked"))
                  .description("Keep the window open as long as they still have no totem in hand, up to max-window."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Integer> maxWindow = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("max-window")).description("Hard limit for an extended window.")).defaultValue(60))
               .range(20, 200)
               .sliderRange(20, 200)
               .visible(this.extendWhileNaked::get))
            .build()
      );
   private final Setting<Integer> selfWindowTicks = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("self-window-ticks"))
                  .description(
                     "At most how long your own pop counts as a dangerous moment, for modules that ask - Suicide Prevent uses it. The moment ends earlier, a few ticks after a totem is back in your hand, so this length is what you get while you are still without one."
                  ))
               .defaultValue(40))
            .range(10, 100)
            .sliderRange(10, 100)
            .build()
      );
   private final Setting<Boolean> countSelf = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("count-own-pops"))
                  .description("Also open an attack window when you pop. Only useful for testing that detection works."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say in chat when a window opens."))
               .defaultValue(true))
            .build()
      );
   private final Map<UUID, PopWindow.Window> open = new HashMap<>();
   private final Map<Integer, class_1657> leftThisTick = new HashMap<>();
   private int clock;
   private int selfPoppedAt = -1073741824;

   public PopWindow() {
      super(CrystalAddon.CATEGORY, "pop-window", "After the target pops a totem, lets Auto Crystal and Auto Anchor drop their damage floor for a moment.");
   }

   public void onDeactivate() {
      this.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.clear();
   }

   private void clear() {
      this.open.clear();
      this.leftThisTick.clear();
      this.selfPoppedAt = -1073741824;
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      if (event.entity instanceof class_1657 player) {
         this.leftThisTick.put(player.method_5628(), player);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.sessionChanged()) {
            this.clear();
         }

         this.clock++;
         this.leftThisTick.clear();
         this.open
            .entrySet()
            .removeIf(
               entry -> {
                  PopWindow.Window state = entry.getValue();
                  class_1657 player = this.mc.field_1687.method_18470(entry.getKey());
                  if (player == null) {
                     return this.clock >= state.deadline;
                  } else if (!player.method_5805() && !RevivedPlayers.isRevived(player)) {
                     return true;
                  } else {
                     boolean holdsTotem = hasTotem(player);
                     if ((Boolean)this.extendWhileNaked.get() && !holdsTotem) {
                        state.deadline = Math.max(state.deadline, Math.min(this.clock + 1, state.opened + (Integer)this.maxWindow.get()));
                     }

                     return !PopWindow.Timing.keepOpen(
                        this.clock, state.opened, state.deadline, holdsTotem, (Boolean)this.closeOnRetotem.get(), (Integer)this.retotemGrace.get()
                     );
                  }
               }
            );
      }
   }

   private static boolean hasTotem(class_1657 player) {
      return player.method_6079().method_57826(class_9334.field_54274) || player.method_6047().method_57826(class_9334.field_54274);
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (event.packet instanceof class_2663 packet) {
         if (packet.method_11470() == 35) {
            int id = entityId(packet);
            class_2535 connection = event.connection;
            this.mc.execute(() -> {
               if (this.isActive() && this.mc.field_1724 != null && this.mc.field_1687 != null) {
                  if (this.mc.method_1562() != null && this.mc.method_1562().method_48296() == connection) {
                     this.openWindow(id);
                  }
               }
            });
         }
      }
   }

   private static int entityId(class_2663 packet) {
      class_2540 buf = new class_2540(Unpooled.buffer(8));

      int var2;
      try {
         class_2663.field_47924.encode(buf, packet);
         var2 = buf.readInt();
      } finally {
         buf.release();
      }

      return var2;
   }

   private void openWindow(int entityId) {
      class_1297 entity = this.mc.field_1687.method_8469(entityId);
      if (entity == null) {
         entity = (class_1297)this.leftThisTick.get(entityId);
      }

      if (entity instanceof class_1657 player) {
         if (player == this.mc.field_1724) {
            this.selfPoppedAt = this.clock;
            if (!(Boolean)this.countSelf.get()) {
               return;
            }
         }

         int first = this.clock + 1;
         PopWindow.Window state = new PopWindow.Window(first, first + (Integer)this.window.get());
         if (player != this.mc.field_1724) {
            int reaction = Stealth.reactionTicks();
            state.usableFrom = this.clock + 1 + reaction;
            state.deadline += reaction;
         }

         this.open.put(player.method_5667(), state);
         if ((Boolean)this.chatInfo.get()) {
            this.info("%s popped, window open for %d ticks.", new Object[]{player.method_5477().getString(), this.window.get()});
         }
      }
   }

   public boolean isOpenFor(class_1657 target) {
      if (this.isActive() && target != null) {
         PopWindow.Window state = this.open.get(target.method_5667());
         return state != null && this.clock >= state.usableFrom;
      } else {
         return false;
      }
   }

   public boolean selfWindowOpen() {
      return this.isActive() && this.mc.field_1724 != null
         ? PopWindow.Timing.selfOpen(this.clock - this.selfPoppedAt, (Integer)this.selfWindowTicks.get(), hasTotem(this.mc.field_1724))
         : false;
   }

   public double crystalMinDamage() {
      return (Double)this.crystalMinDamage.get();
   }

   public double anchorMinDamage() {
      return (Double)this.anchorMinDamage.get();
   }

   public boolean ignoreRatio() {
      return (Boolean)this.ignoreRatio.get();
   }

   public String getInfoString() {
      return this.open.isEmpty() ? null : this.open.size() + " open";
   }

   static final class Timing {
      static final int SELF_RETOTEM_GRACE = 5;

      private Timing() {
      }

      static boolean keepOpen(int now, int opened, int deadline, boolean holdsTotem, boolean closeOnRetotem, int grace) {
         return now >= deadline ? false : !closeOnRetotem || !holdsTotem || now - opened < grace;
      }

      static boolean selfOpen(int since, int windowTicks, boolean holdsTotem) {
         return since >= 0 && since < windowTicks ? since <= 5 || !holdsTotem : false;
      }
   }

   private static final class Window {
      final int opened;
      int deadline;
      int usableFrom = Integer.MIN_VALUE;

      Window(int opened, int deadline) {
         this.opened = opened;
         this.deadline = deadline;
      }
   }
}
