package dev.crystaladdon.utils;

import java.lang.reflect.Method;

public final class ServerVersion {
   private static final int PROTOCOL_1_13 = 393;
   private static Method getTargetVersion;
   private static Method getVersion;
   private static boolean resolved;
   private static boolean available;

   private ServerVersion() {
   }

   private static void resolve() {
      if (!resolved) {
         resolved = true;

         try {
            Class<?> translator = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
            getTargetVersion = translator.getMethod("getTargetVersion");
            Class<?> version = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            getVersion = version.getMethod("getVersion");
            available = true;
         } catch (Throwable var2) {
            available = false;
         }
      }
   }

   public static boolean isTranslating() {
      resolve();
      return available;
   }

   public static int protocol() {
      resolve();
      if (!available) {
         return -1;
      } else {
         try {
            Object version = getTargetVersion.invoke(null);
            return version == null ? -1 : (Integer)getVersion.invoke(version);
         } catch (Throwable var1) {
            return -1;
         }
      }
   }

   public static boolean needsLegacyCrystalPlacement() {
      int protocol = protocol();
      return protocol > 0 && protocol < 393;
   }

   public static String describe() {
      int protocol = protocol();
      if (!isTranslating()) {
         return "native";
      } else {
         return protocol < 0 ? "unknown" : (protocol < 393 ? "legacy" : "modern") + " (protocol " + protocol + ")";
      }
   }
}
