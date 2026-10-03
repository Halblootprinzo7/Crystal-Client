package com.messerocks.crystal.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.class_2338;

public final class LitematicaPlacement {
   private LitematicaPlacement() {
   }

   public static LitematicaPlacement.Placement find(Path configFolder, String schematicFileName) {
      if (!Files.isDirectory(configFolder)) {
         return null;
      } else {
         List<Path> files;
         try (Stream<Path> stream = Files.list(configFolder)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(LitematicaPlacement::modifiedAt).reversed()).toList();
         } catch (Exception var8) {
            return null;
         }

         for (Path file : files) {
            LitematicaPlacement.Placement placement = readFrom(file, schematicFileName);
            if (placement != null) {
               return placement;
            }
         }

         return null;
      }
   }

   private static long modifiedAt(Path path) {
      try {
         return Files.getLastModifiedTime(path).toMillis();
      } catch (Exception var2) {
         return 0L;
      }
   }

   private static LitematicaPlacement.Placement readFrom(Path file, String schematicFileName) {
      try {
         Object list;
         try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
               return null;
            }

            JsonElement placements = root.getAsJsonObject().get("placements");
            if (placements != null && placements.isJsonObject()) {
               JsonElement listx = placements.getAsJsonObject().get("placements");
               if (listx != null && listx.isJsonArray()) {
                  for (JsonElement element : listx.getAsJsonArray()) {
                     if (element.isJsonObject()) {
                        JsonObject entry = element.getAsJsonObject();
                        JsonElement schematic = entry.get("schematic");
                        if (schematic != null) {
                           String stored = schematic.getAsString().replace('\\', '/');
                           if (stored.endsWith("/" + schematicFileName) || stored.equals(schematicFileName)) {
                              class_2338 origin = readPos(entry.get("origin"));
                              if (origin != null) {
                                 return new LitematicaPlacement.Placement(
                                    origin,
                                    optString(entry, "rotation"),
                                    optString(entry, "mirror"),
                                    !entry.has("enabled") || entry.get("enabled").getAsBoolean()
                                 );
                              }
                           }
                        }
                     }
                  }

                  return null;
               }

               return null;
            }

            list = null;
         }

         return (LitematicaPlacement.Placement)list;
      } catch (Exception var15) {
         return null;
      }
   }

   private static class_2338 readPos(JsonElement element) {
      if (element != null && element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         return array.size() < 3 ? null : new class_2338(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
      } else {
         return null;
      }
   }

   private static String optString(JsonObject object, String key) {
      return object.has(key) ? object.get(key).getAsString() : "NONE";
   }

   public record Placement(class_2338 origin, String rotation, String mirror, boolean enabled) {
      public boolean isPlain() {
         return "NONE".equals(this.rotation) && "NONE".equals(this.mirror);
      }
   }
}
