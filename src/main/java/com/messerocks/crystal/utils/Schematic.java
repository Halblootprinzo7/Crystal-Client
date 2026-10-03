package com.messerocks.crystal.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.class_155;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2382;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_2758;
import net.minecraft.class_2769;
import net.minecraft.class_2771;
import net.minecraft.class_2960;
import net.minecraft.class_3341;
import net.minecraft.class_5544;
import net.minecraft.class_7923;

public final class Schematic {
   private final String name;
   private final String author;
   private final List<Schematic.Region> regions;
   private final List<Schematic.Entry> entries;
   private final class_3341 bounds;
   private final List<String> warnings;

   private Schematic(String name, String author, List<Schematic.Region> regions, List<String> warnings) {
      this.name = name;
      this.author = author;
      this.regions = regions;
      this.warnings = warnings;
      List<Schematic.Entry> all = new ArrayList<>();
      class_3341 box = null;

      for (Schematic.Region region : regions) {
         all.addAll(region.entries());
         if (region.box() != null) {
            box = box == null ? region.box() : class_3341.method_35413(List.of(box, region.box())).orElse(box);
         }
      }

      this.entries = all;
      this.bounds = box;
   }

   public String name() {
      return this.name;
   }

   public String author() {
      return this.author;
   }

   public class_2382 size() {
      return this.bounds == null ? class_2382.field_11176 : new class_2382(this.bounds.method_35414(), this.bounds.method_14660(), this.bounds.method_14663());
   }

   public class_3341 bounds() {
      return this.bounds;
   }

   public List<Schematic.Entry> entries() {
      return this.entries;
   }

   public List<Schematic.Region> regions() {
      return this.regions;
   }

   public int blockCount() {
      return this.entries.size();
   }

   public List<String> warnings() {
      return this.warnings;
   }

   public Schematic withRegionOverrides(Map<String, Schematic.RegionOverride> overrides) {
      if (overrides != null && !overrides.isEmpty()) {
         List<Schematic.Region> moved = new ArrayList<>(this.regions.size());

         for (Schematic.Region region : this.regions) {
            Schematic.RegionOverride override = overrides.get(region.name());
            if (override == null) {
               moved.add(region);
            } else if (override.enabled()) {
               class_2338 shift = override.position().method_10059(region.position());
               if (shift.equals(class_2338.field_10980)) {
                  moved.add(region);
               } else {
                  List<Schematic.Entry> shifted = new ArrayList<>(region.entries().size());

                  for (Schematic.Entry entry : region.entries()) {
                     shifted.add(new Schematic.Entry(entry.offset().method_10081(shift), entry.state()));
                  }

                  class_3341 box = region.box() == null ? null : region.box().method_19311(shift.method_10263(), shift.method_10264(), shift.method_10260());
                  moved.add(new Schematic.Region(region.name(), override.position(), box, shifted));
               }
            }
         }

         return new Schematic(this.name, this.author, moved, this.warnings);
      } else {
         return this;
      }
   }

   public static Schematic load(Path file) throws Exception {
      class_2487 root = class_2507.method_30613(file, class_2505.method_53898());
      class_2487 metadata = root.method_68568("Metadata");
      String name = metadata.method_68564("Name", file.getFileName().toString());
      String author = metadata.method_68564("Author", "unknown");
      class_2487 regionsTag = root.method_68568("Regions");
      if (regionsTag.method_33133()) {
         throw new IllegalArgumentException("no regions in this file");
      } else {
         Schematic.Stats stats = new Schematic.Stats();
         List<Schematic.Region> regions = new ArrayList<>();

         for (String regionName : regionsTag.method_10541()) {
            regions.add(readRegion(regionName, regionsTag.method_68568(regionName), stats));
         }

         List<String> warnings = new ArrayList<>();
         Schematic schematic = new Schematic(name, author, regions, warnings);
         int totalBlocks = metadata.method_68083("TotalBlocks", -1);
         if (totalBlocks >= 0 && totalBlocks != schematic.blockCount()) {
            warnings.add(String.format("parsed %d blocks, the file says %d", schematic.blockCount(), totalBlocks));
         }

         if (stats.unknownBlocks > 0) {
            warnings.add(
               String.format("%d blocks use ids this game does not know and are left out: %s", stats.unknownBlocks, String.join(", ", stats.unknownIds))
            );
         }

         if (stats.badProperties > 0) {
            warnings.add(String.format("%d block property values were not understood, those blocks use defaults", stats.badProperties));
         }

         if (stats.badIndices > 0) {
            warnings.add(String.format("%d block entries point outside their palette and are left out", stats.badIndices));
         }

         class_2382 enclosing = readVec(metadata.method_68568("EnclosingSize"));
         class_2382 size = schematic.size();
         if (!enclosing.equals(class_2382.field_11176) && !enclosing.equals(size)) {
            warnings.add(
               String.format(
                  "regions cover %dx%dx%d, the file says %dx%dx%d",
                  size.method_10263(),
                  size.method_10264(),
                  size.method_10260(),
                  enclosing.method_10263(),
                  enclosing.method_10264(),
                  enclosing.method_10260()
               )
            );
         }

         int dataVersion = root.method_68083("MinecraftDataVersion", -1);
         int gameDataVersion = class_155.method_16673().comp_4026().comp_4038();
         if (dataVersion > gameDataVersion) {
            warnings.add(
               String.format("saved by a newer Minecraft (data version %d, this game is %d), some blocks may be missing", dataVersion, gameDataVersion)
            );
         }

         return schematic;
      }
   }

   private static Schematic.Region readRegion(String regionName, class_2487 region, Schematic.Stats stats) {
      class_2382 rawSize = readVec(region.method_68568("Size"));
      class_2338 position = new class_2338(readVec(region.method_68568("Position")));
      List<Schematic.Entry> out = new ArrayList<>();
      int width = Math.abs(rawSize.method_10263());
      int height = Math.abs(rawSize.method_10264());
      int length = Math.abs(rawSize.method_10260());
      if (width != 0 && height != 0 && length != 0) {
         int originX = cornerMin(position.method_10263(), rawSize.method_10263());
         int originY = cornerMin(position.method_10264(), rawSize.method_10264());
         int originZ = cornerMin(position.method_10260(), rawSize.method_10260());
         class_3341 box = new class_3341(originX, originY, originZ, originX + width - 1, originY + height - 1, originZ + length - 1);
         class_2499 paletteTag = region.method_68569("BlockStatePalette");
         List<class_2680> palette = new ArrayList<>(paletteTag.size());
         List<Boolean> known = new ArrayList<>(paletteTag.size());

         for (int i = 0; i < paletteTag.size(); i++) {
            class_2487 tag = paletteTag.method_68582(i);
            palette.add(readState(tag, stats));
            known.add(isKnown(tag));
         }

         if (palette.isEmpty()) {
            return new Schematic.Region(regionName, position, box, out);
         } else {
            long[] data = region.method_10565("BlockStates").orElse(new long[0]);
            if (data.length == 0) {
               return new Schematic.Region(regionName, position, box, out);
            } else {
               int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
               long mask = (1L << bits) - 1L;

               for (int y = 0; y < height; y++) {
                  for (int z = 0; z < length; z++) {
                     for (int x = 0; x < width; x++) {
                        long index = (long)y * width * length + (long)z * width + x;
                        int id = read(data, index, bits, mask);
                        if (id < 0 || id >= palette.size()) {
                           stats.badIndices++;
                        } else if (!known.get(id)) {
                           stats.unknownBlocks++;
                        } else {
                           class_2680 state = palette.get(id);
                           if (!state.method_26215()) {
                              out.add(new Schematic.Entry(new class_2338(originX + x, originY + y, originZ + z), state));
                           }
                        }
                     }
                  }
               }

               return new Schematic.Region(regionName, position, box, out);
            }
         }
      } else {
         return new Schematic.Region(regionName, position, null, out);
      }
   }

   static int cornerMin(int position, int size) {
      return position + Math.min(0, size + 1);
   }

   static int read(long[] data, long index, int bits, long mask) {
      long startBit = index * bits;
      int startLong = (int)(startBit >> 6);
      int endLong = (int)((index + 1L) * bits - 1L >> 6);
      int offset = (int)(startBit & 63L);
      if (startLong < 0 || endLong >= data.length) {
         return -1;
      } else {
         return startLong == endLong ? (int)(data[startLong] >>> offset & mask) : (int)((data[startLong] >>> offset | data[endLong] << 64 - offset) & mask);
      }
   }

   private static boolean isKnown(class_2487 tag) {
      class_2960 id = class_2960.method_12829(tag.method_68564("Name", "minecraft:air"));
      return id != null && class_7923.field_41175.method_10250(id);
   }

   private static class_2680 readState(class_2487 tag, Schematic.Stats stats) {
      String rawId = tag.method_68564("Name", "minecraft:air");
      class_2960 id = class_2960.method_12829(rawId);
      if (id != null && class_7923.field_41175.method_10250(id)) {
         class_2248 block = (class_2248)class_7923.field_41175.method_63535(id);
         class_2680 state = block.method_9564();
         class_2487 properties = tag.method_68568("Properties");

         for (String key : properties.method_10541()) {
            class_2769<?> property = block.method_9595().method_11663(key);
            if (property == null) {
               stats.badProperties++;
            } else {
               class_2680 applied = apply(state, property, properties.method_68564(key, ""));
               if (applied == null) {
                  stats.badProperties++;
               } else {
                  state = applied;
               }
            }
         }

         return state;
      } else {
         if (stats.unknownIds.size() < 10) {
            stats.unknownIds.add(rawId);
         }

         return class_2246.field_10124.method_9564();
      }
   }

   private static <T extends Comparable<T>> class_2680 apply(class_2680 state, class_2769<T> property, String value) {
      return property.method_11900(value).map(parsed -> (class_2680)state.method_11657(property, parsed)).orElse(null);
   }

   private static class_2382 readVec(class_2487 tag) {
      return new class_2382(tag.method_68083("x", 0), tag.method_68083("y", 0), tag.method_68083("z", 0));
   }

   public static boolean satisfies(class_2680 current, class_2680 target) {
      if (current == target) {
         return true;
      } else if (current.method_26204() != target.method_26204()) {
         return false;
      } else {
         boolean candle = target.method_26204() instanceof class_5544;
         return propertiesMatch(
            target.method_28501(),
            p -> Schematic.NeighbourDerived.SET.contains(p) || p == class_2741.field_12548 && !candle,
            p -> current.method_28498(p) && current.method_11654(p).equals(target.method_11654(p))
         );
      }
   }

   public static boolean orientationMatches(class_2680 predicted, class_2680 target) {
      return predicted.method_26204() != target.method_26204()
         ? false
         : propertiesMatch(
            target.method_28501(),
            p -> !Schematic.PlacementDecided.SET.contains(p),
            p -> predicted.method_28498(p) && predicted.method_11654(p).equals(target.method_11654(p))
         );
   }

   public static boolean hasOrientation(class_2680 state) {
      for (class_2769<?> property : state.method_28501()) {
         if (Schematic.PlacementDecided.SET.contains(property)) {
            return true;
         }
      }

      return false;
   }

   public static int stackLevel(class_2680 state) {
      if (state.method_28498(class_2741.field_12485)) {
         return state.method_11654(class_2741.field_12485) == class_2771.field_12682 ? 2 : 1;
      } else {
         for (class_2758 property : Schematic.Stacked.SET) {
            if (state.method_28498(property)) {
               return (Integer)state.method_11654(property);
            }
         }

         return -1;
      }
   }

   public static boolean needsMore(class_2680 current, class_2680 target) {
      if (current.method_26204() != target.method_26204()) {
         return false;
      } else {
         return !moreNeeded(stackLevel(current), stackLevel(target)) ? false : matchesApartFromCount(current, target);
      }
   }

   public static boolean stacksCloser(class_2680 before, class_2680 after, class_2680 target) {
      if (!needsMore(before, target)) {
         return false;
      } else if (after.method_26204() != target.method_26204()) {
         return false;
      } else {
         return !closer(stackLevel(before), stackLevel(after), stackLevel(target)) ? false : matchesApartFromCount(after, target);
      }
   }

   private static boolean matchesApartFromCount(class_2680 state, class_2680 target) {
      return propertiesMatch(
         target.method_28501(),
         p -> !Schematic.PlacementDecided.SET.contains(p) || isCount(p),
         p -> state.method_28498(p) && state.method_11654(p).equals(target.method_11654(p))
      );
   }

   private static boolean isCount(class_2769<?> property) {
      return property == class_2741.field_12485 || Schematic.Stacked.SET.contains(property);
   }

   static boolean moreNeeded(int current, int target) {
      return current >= 0 && target >= 0 && current < target;
   }

   static boolean closer(int before, int after, int target) {
      return moreNeeded(before, target) && after > before && after <= target;
   }

   static <P> boolean propertiesMatch(Collection<P> properties, Predicate<P> ignored, Predicate<P> equal) {
      for (P property : properties) {
         if (!ignored.test(property) && !equal.test(property)) {
            return false;
         }
      }

      return true;
   }

   public static boolean isSecondHalf(class_2680 state) {
      return state.method_28498(class_2741.field_12533) && state.method_11654(class_2741.field_12533) == class_2756.field_12609
         ? true
         : state.method_28498(class_2741.field_12483) && state.method_11654(class_2741.field_12483) == class_2742.field_12560;
   }

   public static List<Path> findSchematics(Path root) {
      List<Path> found = new ArrayList<>();
      if (!Files.isDirectory(root)) {
         return found;
      } else {
         try (Stream<Path> stream = Files.walk(root, 3)) {
            stream.filter(p -> p.toString().endsWith(".litematic")).forEach(found::add);
         } catch (Exception var7) {
         }

         found.sort((a, b) -> {
            try {
               return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (Exception var3) {
               return 0;
            }
         });
         return found;
      }
   }

   public static List<String> schematicNames(Path root) {
      return findSchematics(root).stream().map(p -> p.getFileName().toString()).toList();
   }

   public record Entry(class_2338 offset, class_2680 state) {
   }

   private static final class NeighbourDerived {
      private static final Set<class_2769<?>> SET = Set.of(
         class_2741.field_12508,
         class_2741.field_12503,
         class_2741.field_12541,
         class_2741.field_12514,
         class_2741.field_12512,
         class_2741.field_12489,
         class_2741.field_12487,
         class_2741.field_12540,
         class_2741.field_12527,
         class_2741.field_12519,
         class_2741.field_12546,
         class_2741.field_22175,
         class_2741.field_22174,
         class_2741.field_22176,
         class_2741.field_22177,
         class_2741.field_12495,
         class_2741.field_12523,
         class_2741.field_12551,
         class_2741.field_12504,
         class_2741.field_12484,
         class_2741.field_12511,
         class_2741.field_12522,
         class_2741.field_12515,
         class_2741.field_12528,
         class_2741.field_12524,
         class_2741.field_12499,
         class_2741.field_12491,
         class_2741.field_12493,
         class_2741.field_12506,
         class_2741.field_17393,
         class_2741.field_12544
      );
   }

   private static final class PlacementDecided {
      private static final Set<class_2769<?>> SET = Set.of(
         class_2741.field_12525,
         class_2741.field_12481,
         class_2741.field_12545,
         class_2741.field_28062,
         class_2741.field_12496,
         class_2741.field_12529,
         class_2741.field_12518,
         class_2741.field_12485,
         class_2741.field_12555,
         class_2741.field_12520,
         class_2741.field_12532,
         class_2741.field_23333,
         class_2741.field_16561,
         class_2741.field_17104
      );
   }

   public record Region(String name, class_2338 position, class_3341 box, List<Schematic.Entry> entries) {
   }

   public record RegionOverride(class_2338 position, boolean enabled) {
   }

   private static final class Stacked {
      private static final List<class_2758> SET = List.of(
         class_2741.field_12536, class_2741.field_27220, class_2741.field_12543, class_2741.field_12509, class_2741.field_42835, class_2741.field_55829
      );
   }

   private static final class Stats {
      int unknownBlocks;
      int badProperties;
      int badIndices;
      final Set<String> unknownIds = new LinkedHashSet<>();
   }
}
