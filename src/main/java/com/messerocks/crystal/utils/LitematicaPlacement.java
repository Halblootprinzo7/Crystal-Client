package com.messerocks.crystal.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.messerocks.crystal.CrystalAddon;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2338;
import net.minecraft.class_2415;
import net.minecraft.class_2470;

public final class LitematicaPlacement {
   private static Method storageFileName;
   private static boolean storageFileNameResolved;

   private LitematicaPlacement() {
   }

   public static LitematicaPlacement.Placement find(Path configFolder, String schematicFileName) {
      return find(configFolder, schematicFileName, "");
   }

   public static LitematicaPlacement.Placement find(Path configFolder, String schematicFileName, String placementName) {
      if (!Files.isDirectory(configFolder)) {
         return null;
      } else {
         List<Path> files;
         try (Stream<Path> stream = Files.list(configFolder)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(LitematicaPlacement::modifiedAt).reversed()).toList();
         } catch (Exception var9) {
            return null;
         }

         for (Path file : files) {
            LitematicaPlacement.Placement placement = readFrom(file, schematicFileName, placementName, LitematicaPlacement.Source.AnyFile);
            if (placement != null) {
               return placement;
            }
         }

         return null;
      }
   }

   public static LitematicaPlacement.Placement findInWorldFile(Path file, String schematicFileName, String placementName) {
      return file != null && Files.isRegularFile(file) ? readFrom(file, schematicFileName, placementName, LitematicaPlacement.Source.WorldFile) : null;
   }

   public static String currentWorldFileName() {
      if (!FabricLoader.getInstance().isModLoaded("malilib")) {
         return null;
      } else {
         if (!storageFileNameResolved) {
            storageFileNameResolved = true;

            try {
               Class<?> utils = Class.forName("fi.dy.masa.malilib.util.StringUtils", true, LitematicaPlacement.class.getClassLoader());
               storageFileName = utils.getMethod("getStorageFileName", boolean.class, String.class, String.class, String.class);
            } catch (Throwable var2) {
               CrystalAddon.LOG.warn("Could not find malilib's storage file name, Litematica's config file cannot be matched to this world.", var2);
            }
         }

         if (storageFileName == null) {
            return null;
         } else {
            try {
               return (String)storageFileName.invoke(null, false, "litematica_", ".json", "default");
            } catch (Throwable var1) {
               return null;
            }
         }
      }
   }

   private static long modifiedAt(Path path) {
      try {
         return Files.getLastModifiedTime(path).toMillis();
      } catch (Exception var2) {
         return 0L;
      }
   }

   private static LitematicaPlacement.Placement readFrom(Path file, String schematicFileName, String placementName, LitematicaPlacement.Source source) {
      try {
         Object manager;
         try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
               return null;
            }

            JsonElement placements = root.getAsJsonObject().get("placements");
            if (placements != null && placements.isJsonObject()) {
               JsonObject managerx = placements.getAsJsonObject();
               JsonElement list = managerx.get("placements");
               if (list != null && list.isJsonArray()) {
                  int selected = managerx.has("selected") && managerx.get("selected").isJsonPrimitive() ? managerx.get("selected").getAsInt() : -1;
                  List<LitematicaPlacement.Placement> candidates = new ArrayList<>();
                  int selectedCandidate = -1;
                  JsonArray array = list.getAsJsonArray();

                  for (int i = 0; i < array.size(); i++) {
                     LitematicaPlacement.Placement placement;
                     try {
                        placement = parsePlacement(array.get(i), schematicFileName, source);
                     } catch (RuntimeException var17) {
                        placement = null;
                     }

                     if (placement != null) {
                        if (i == selected) {
                           selectedCandidate = candidates.size();
                        }

                        candidates.add(placement);
                     }
                  }

                  int chosen = choose(candidates.stream().map(LitematicaPlacement.Placement::name).toList(), selectedCandidate, placementName);
                  return chosen < 0 ? null : candidates.get(chosen);
               }

               return null;
            }

            manager = null;
         }

         return (LitematicaPlacement.Placement)manager;
      } catch (Exception var19) {
         return null;
      }
   }

   private static LitematicaPlacement.Placement parsePlacement(JsonElement element, String schematicFileName, LitematicaPlacement.Source source) {
      if (!element.isJsonObject()) {
         return null;
      } else {
         JsonObject entry = element.getAsJsonObject();
         if (entry.has("enabled") && !entry.get("enabled").getAsBoolean()) {
            return null;
         } else {
            JsonElement schematic = entry.get("schematic");
            if (schematic != null && schematic.isJsonPrimitive()) {
               if (!matchesFile(schematic.getAsString(), schematicFileName)) {
                  return null;
               } else {
                  class_2338 origin = readPos(entry.get("origin"));
                  if (origin == null) {
                     return null;
                  } else {
                     Map<String, LitematicaPlacement.SubRegion> subRegions = new LinkedHashMap<>();
                     JsonElement regions = entry.get("placements");
                     if (regions != null && regions.isJsonArray()) {
                        for (JsonElement regionElement : regions.getAsJsonArray()) {
                           if (regionElement.isJsonObject()) {
                              JsonObject regionEntry = regionElement.getAsJsonObject();
                              JsonElement inner = regionEntry.get("placement");
                              if (inner != null && inner.isJsonObject()) {
                                 JsonObject region = inner.getAsJsonObject();
                                 String name = regionEntry.has("name") ? regionEntry.get("name").getAsString() : optString(region, "name", "");
                                 class_2338 pos = readPos(region.get("pos"));
                                 if (pos != null && !name.isEmpty()) {
                                    subRegions.put(
                                       name,
                                       new LitematicaPlacement.SubRegion(
                                          name,
                                          pos,
                                          optString(region, "rotation", "NONE"),
                                          optString(region, "mirror", "NONE"),
                                          !region.has("enabled") || region.get("enabled").getAsBoolean()
                                       )
                                    );
                                 }
                              }
                           }
                        }
                     }

                     return new LitematicaPlacement.Placement(
                        origin,
                        optString(entry, "rotation", "NONE"),
                        optString(entry, "mirror", "NONE"),
                        true,
                        optString(entry, "name", ""),
                        source,
                        subRegions
                     );
                  }
               }
            } else {
               return null;
            }
         }
      }
   }

   static int choose(List<String> names, int selected, String wantedName) {
      if (names.isEmpty()) {
         return -1;
      } else if (wantedName != null && !wantedName.isBlank()) {
         String wanted = wantedName.trim();

         for (int i = 0; i < names.size(); i++) {
            if (wanted.equalsIgnoreCase(Objects.requireNonNullElse(names.get(i), "").trim())) {
               return i;
            }
         }

         return -1;
      } else {
         return selected >= 0 && selected < names.size() ? selected : 0;
      }
   }

   static boolean matchesFile(String storedPath, String schematicFileName) {
      if (storedPath != null && schematicFileName != null && !schematicFileName.isBlank()) {
         String stored = storedPath.replace('\\', '/');
         return stored.endsWith("/" + schematicFileName) || stored.equals(schematicFileName);
      } else {
         return false;
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

   private static String optString(JsonObject object, String key, String fallback) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
   }

   public static final class Live {
      private static boolean resolved;
      private static boolean available;
      private static Method getManager;
      private static Method getAll;
      private static Method getSelected;
      private static Method getFile;
      private static Method getOrigin;
      private static Method getRotation;
      private static Method getMirror;
      private static Method isEnabled;
      private static Method getName;
      private static Method getSubRegions;
      private static Method regionName;
      private static Method regionPos;
      private static Method regionRotation;
      private static Method regionMirror;
      private static Method regionEnabled;

      private Live() {
      }

      public static boolean available() {
         if (resolved) {
            return available;
         } else {
            resolved = true;
            if (!FabricLoader.getInstance().isModLoaded("litematica")) {
               return false;
            } else {
               try {
                  ClassLoader loader = LitematicaPlacement.class.getClassLoader();
                  Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager", true, loader);
                  Class<?> manager = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager", true, loader);
                  Class<?> placement = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement", true, loader);
                  Class<?> subRegion = Class.forName("fi.dy.masa.litematica.schematic.placement.SubRegionPlacement", true, loader);
                  getManager = dataManager.getMethod("getSchematicPlacementManager");
                  getAll = manager.getMethod("getAllSchematicsPlacements");
                  getSelected = manager.getMethod("getSelectedSchematicPlacement");
                  getFile = placement.getMethod("getSchematicFile");
                  getOrigin = placement.getMethod("getOrigin");
                  getRotation = placement.getMethod("getRotation");
                  getMirror = placement.getMethod("getMirror");
                  isEnabled = placement.getMethod("isEnabled");
                  getName = placement.getMethod("getName");
                  getSubRegions = placement.getMethod("getAllSubRegionsPlacements");
                  regionName = subRegion.getMethod("getName");
                  regionPos = subRegion.getMethod("getPos");
                  regionRotation = subRegion.getMethod("getRotation");
                  regionMirror = subRegion.getMethod("getMirror");
                  regionEnabled = subRegion.getMethod("isEnabled");
                  available = true;
               } catch (Throwable var5) {
                  CrystalAddon.LOG.warn("Litematica is loaded but its placements could not be read live, falling back to its config file.", var5);
               }

               return available;
            }
         }
      }

      public static LitematicaPlacement.Placement find(String schematicFileName, String placementName) {
         if (!available()) {
            return null;
         } else {
            try {
               Object manager = getManager.invoke(null);
               if (manager == null) {
                  return null;
               } else {
                  Object selected = getSelected.invoke(manager);
                  Collection<?> all = (Collection<?>)getAll.invoke(manager);
                  List<LitematicaPlacement.Placement> candidates = new ArrayList<>();
                  int selectedCandidate = -1;

                  for (Object placement : all) {
                     if ((Boolean)isEnabled.invoke(placement)) {
                        Path file = (Path)getFile.invoke(placement);
                        if (file != null && file.getFileName() != null && file.getFileName().toString().equals(schematicFileName)) {
                           class_2338 origin = (class_2338)getOrigin.invoke(placement);
                           if (origin != null) {
                              if (placement == selected) {
                                 selectedCandidate = candidates.size();
                              }

                              candidates.add(
                                 new LitematicaPlacement.Placement(
                                    origin.method_10062(),
                                    rotationName(getRotation.invoke(placement)),
                                    mirrorName(getMirror.invoke(placement)),
                                    true,
                                    Objects.requireNonNullElse((String)getName.invoke(placement), ""),
                                    LitematicaPlacement.Source.Live,
                                    readSubRegions(placement)
                                 )
                              );
                           }
                        }
                     }
                  }

                  int chosen = LitematicaPlacement.choose(
                     candidates.stream().map(LitematicaPlacement.Placement::name).toList(), selectedCandidate, placementName
                  );
                  return chosen < 0 ? null : candidates.get(chosen);
               }
            } catch (Throwable var11) {
               throw new IllegalStateException("reading Litematica's placements failed: " + var11, var11);
            }
         }
      }

      private static Map<String, LitematicaPlacement.SubRegion> readSubRegions(Object placement) throws Exception {
         Map<String, LitematicaPlacement.SubRegion> regions = new LinkedHashMap<>();

         for (Object region : (Collection)getSubRegions.invoke(placement)) {
            String name = (String)regionName.invoke(region);
            class_2338 pos = (class_2338)regionPos.invoke(region);
            if (name != null && pos != null) {
               regions.put(
                  name,
                  new LitematicaPlacement.SubRegion(
                     name,
                     pos.method_10062(),
                     rotationName(regionRotation.invoke(region)),
                     mirrorName(regionMirror.invoke(region)),
                     (Boolean)regionEnabled.invoke(region)
                  )
               );
            }
         }

         return regions;
      }

      private static String rotationName(Object rotation) {
         if (rotation == null || rotation == class_2470.field_11467) {
            return "NONE";
         } else {
            return rotation == class_2470.field_11464 ? "CLOCKWISE_180" : ((class_2470)rotation).method_15434().toUpperCase(Locale.ROOT);
         }
      }

      private static String mirrorName(Object mirror) {
         return mirror != null && mirror != class_2415.field_11302 ? ((class_2415)mirror).method_15434().toUpperCase(Locale.ROOT) : "NONE";
      }
   }

   public record Placement(
      class_2338 origin,
      String rotation,
      String mirror,
      boolean enabled,
      String name,
      LitematicaPlacement.Source source,
      Map<String, LitematicaPlacement.SubRegion> subRegions
   ) {
      public Placement(class_2338 origin, String rotation, String mirror, boolean enabled) {
         this(origin, rotation, mirror, enabled, "", LitematicaPlacement.Source.AnyFile, Map.of());
      }

      public boolean isPlain() {
         if ("NONE".equals(this.rotation) && "NONE".equals(this.mirror)) {
            for (LitematicaPlacement.SubRegion region : this.subRegions.values()) {
               if (region.enabled() && !region.isPlain()) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }

      public boolean worldMatched() {
         return this.source != LitematicaPlacement.Source.AnyFile;
      }

      public Map<String, Schematic.RegionOverride> regionOverrides() {
         Map<String, Schematic.RegionOverride> overrides = new LinkedHashMap<>();

         for (LitematicaPlacement.SubRegion region : this.subRegions.values()) {
            overrides.put(region.name(), new Schematic.RegionOverride(region.pos(), region.enabled()));
         }

         return overrides;
      }

      public boolean sameSpot(LitematicaPlacement.Placement other) {
         return other != null
            && this.origin.equals(other.origin)
            && this.rotation.equals(other.rotation)
            && this.mirror.equals(other.mirror)
            && this.subRegions.equals(other.subRegions);
      }
   }

   public static enum Source {
      Live,
      WorldFile,
      AnyFile;
   }

   public record SubRegion(String name, class_2338 pos, String rotation, String mirror, boolean enabled) {
      public boolean isPlain() {
         return "NONE".equals(this.rotation) && "NONE".equals(this.mirror);
      }
   }
}
