package com.messerocks.crystal;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.class_2561;

public abstract class CrystalModule extends Module {
   private final Setting<Boolean> chatOutput = this.settings
      .getDefaultGroup()
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("chat-output"))
                  .description(
                     "Everything this module writes to chat: status, warnings and errors alike. Off changes nothing about what it does, it just stops telling you about it."
                  ))
               .defaultValue(true))
            .build()
      );

   public CrystalModule(Category category, String name, String description) {
      super(category, name, description);
   }

   protected boolean chatAllowed() {
      return (Boolean)this.chatOutput.get();
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
}
