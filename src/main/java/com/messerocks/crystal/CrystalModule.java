package com.messerocks.crystal;

import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.class_11980;
import net.minecraft.class_2535;
import net.minecraft.class_2561;
import net.minecraft.class_2596;
import net.minecraft.class_2598;
import net.minecraft.class_2602;
import net.minecraft.class_2960;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_9145;

public abstract class CrystalModule extends Module {
   private final Setting<Boolean> chatOutput;
   private volatile long activationVersion;
   private WeakReference<Object> sessionPlayer = new WeakReference<>(null);
   private WeakReference<Object> sessionWorld = new WeakReference<>(null);

   public CrystalModule(Category category, String name, String description) {
      super(category, name, description);
      this.chatOutput = this.settings
         .getDefaultGroup()
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("chat-output"))
                     .description(
                        "Everything this module writes to chat: status, warnings and errors alike. Off changes nothing about what it does, it just stops telling you about it."
                     ))
                  .defaultValue(true))
               .build()
         );
   }

   protected boolean chatAllowed() {
      return (Boolean)this.chatOutput.get();
   }

   public void toggle() {
      this.activationVersion++;
      super.toggle();
   }

   public long activationVersion() {
      return this.activationVersion;
   }

   protected boolean sessionChanged() {
      if (this.sessionPlayer.get() == this.mc.field_1724 && this.sessionWorld.get() == this.mc.field_1687) {
         return false;
      } else {
         this.sessionPlayer = new WeakReference<>(this.mc.field_1724);
         this.sessionWorld = new WeakReference<>(this.mc.field_1687);
         return true;
      }
   }

   protected Runnable activeAction(Runnable action) {
      long version = this.activationVersion;
      class_746 player = this.mc.field_1724;
      class_638 world = this.mc.field_1687;
      return () -> {
         if (this.isActive()
            && this.activationVersion == version
            && this.mc.field_1724 == player
            && player != null
            && this.mc.field_1687 == world
            && world != null
            && this.mc.field_1761 != null) {
            action.run();
         }
      };
   }

   protected void receiveOnClient(Receive event, Runnable action) {
      class_2535 connection = event.connection;
      long version = this.activationVersion;
      Runnable task = () -> {
         if (this.isActive()
            && this.activationVersion == version
            && this.mc.field_1724 != null
            && this.mc.field_1687 != null
            && this.mc.method_1562() != null
            && this.mc.method_1562().method_48296() == connection) {
            action.run();
         }
      };
      class_11980 batcher = this.mc.method_74186();
      if (batcher.method_74447()) {
         task.run();
      } else if (connection.method_10744() instanceof class_2602 listener) {
         try {
            batcher.method_74448(listener, new CrystalModule.ClientTask(task));
         } catch (RejectedExecutionException var10) {
         }
      } else {
         this.mc.execute(task);
      }
   }

   public void info(class_2561 message) {
      if (this.chatAllowed()) {
         super.info(message);
      }
   }

   public void info(String message, Object... args) {
      if (this.chatAllowed()) {
         super.info(message, args);
      }
   }

   public void warning(String message, Object... args) {
      if (this.chatAllowed()) {
         super.warning(message, args);
      }
   }

   public void error(String message, Object... args) {
      if (this.chatAllowed()) {
         super.error(message, args);
      }
   }

   public void sendToggledMsg() {
      if (this.chatAllowed()) {
         super.sendToggledMsg();
      }
   }

   private record ClientTask(Runnable task) implements class_2596<class_2602> {
      private static final class_9145<CrystalModule.ClientTask> TYPE = new class_9145(
         class_2598.field_11942, class_2960.method_60655("crystal-addon", "client_task")
      );

      public class_9145<CrystalModule.ClientTask> method_65080() {
         return TYPE;
      }

      public void method_65081(class_2602 listener) {
         try {
            this.task.run();
         } catch (RuntimeException var3) {
            CrystalAddon.LOG.error("Crystal Addon packet task failed", var3);
         }
      }
   }
}
