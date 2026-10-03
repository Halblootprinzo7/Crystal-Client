package com.messerocks.crystal.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2382;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class Schematic {
   private final String name;
   private final String author;
   private final class_2382 size;
   private final List<Schematic.Entry> entries;

   private Schematic(String name, String author, class_2382 size, List<Schematic.Entry> entries) {
      this.name = name;
      this.author = author;
      this.size = size;
      this.entries = entries;
   }

   public String name() {
      return this.name;
   }

   public String author() {
      return this.author;
   }

   public class_2382 size() {
      return this.size;
   }

   public List<Schematic.Entry> entries() {
      return this.entries;
   }

   public int blockCount() {
      return this.entries.size();
   }

   public static Schematic load(Path file) throws Exception {
      class_2487 root = class_2507.method_30613(file, class_2505.method_53898());
      class_2487 metadata = root.method_68568("Metadata");
      String name = metadata.method_68564("Name", file.getFileName().toString());
      String author = metadata.method_68564("Author", "unknown");
      class_2487 regions = root.method_68568("Regions");
      if (regions.method_33133()) {
         throw new IllegalArgumentException("no regions in this file");
      } else {
         List<Schematic.Entry> entries = new ArrayList<>();
         int maxX = 0;
         int maxY = 0;
         int maxZ = 0;

         for (String regionName : regions.method_10541()) {
            class_2382 extent = readRegion(regions.method_68568(regionName), entries);
            maxX = Math.max(maxX, extent.method_10263());
            maxY = Math.max(maxY, extent.method_10264());
            maxZ = Math.max(maxZ, extent.method_10260());
         }

         return new Schematic(name, author, new class_2382(maxX, maxY, maxZ), entries);
      }
   }

   private static class_2382 readRegion(class_2487 region, List<Schematic.Entry> out) {
      class_2382 rawSize = readVec(region.method_68568("Size"));
      class_2382 position = readVec(region.method_68568("Position"));
      int width = Math.abs(rawSize.method_10263());
      int height = Math.abs(rawSize.method_10264());
      int length = Math.abs(rawSize.method_10260());
      if (width != 0 && height != 0 && length != 0) {
         int originX = position.method_10263() + Math.min(0, rawSize.method_10263() + 1);
         int originY = position.method_10264() + Math.min(0, rawSize.method_10264() + 1);
         int originZ = position.method_10260() + Math.min(0, rawSize.method_10260() + 1);
         List<class_2680> palette = readPalette(region.method_68569("BlockStatePalette"));
         if (palette.isEmpty()) {
            return class_2382.field_11176;
         } else {
            long[] data = region.method_10565("BlockStates").orElse(new long[0]);
            if (data.length == 0) {
               return class_2382.field_11176;
            } else {
               int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
               long mask = (1L << bits) - 1L;

               for (int y = 0; y < height; y++) {
                  for (int z = 0; z < length; z++) {
                     for (int x = 0; x < width; x++) {
                        long index = (long)y * width * length + (long)z * width + x;
                        int id = read(data, index, bits, mask);
                        if (id >= 0 && id < palette.size()) {
                           class_2680 state = palette.get(id);
                           if (!state.method_26215()) {
                              out.add(new Schematic.Entry(new class_2338(originX + x, originY + y, originZ + z), state));
                           }
                        }
                     }
                  }
               }

               return new class_2382(Math.abs(originX) + width, Math.abs(originY) + height, Math.abs(originZ) + length);
            }
         }
      } else {
         return class_2382.field_11176;
      }
   }

   private static int read(long[] data, long index, int bits, long mask) {
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

   private static List<class_2680> readPalette(class_2499 list) {
      List<class_2680> palette = new ArrayList<>(list.size());

      for (int i = 0; i < list.size(); i++) {
         palette.add(readState(list.method_68582(i)));
      }

      return palette;
   }

   private static class_2680 readState(class_2487 tag) {
      class_2960 id = class_2960.method_12829(tag.method_68564("Name", "minecraft:air"));
      if (id == null) {
         return class_2246.field_10124.method_9564();
      } else {
         class_2248 block = (class_2248)class_7923.field_41175.method_63535(id);
         class_2680 state = block.method_9564();
         class_2487 properties = tag.method_68568("Properties");

         for (String key : properties.method_10541()) {
            class_2769<?> property = block.method_9595().method_11663(key);
            if (property != null) {
               state = apply(state, property, properties.method_68564(key, ""));
            }
         }

         return state;
      }
   }

   private static <T extends Comparable<T>> class_2680 apply(class_2680 state, class_2769<T> property, String value) {
      return property.method_11900(value).map(parsed -> (class_2680)state.method_11657(property, parsed)).orElse(state);
   }

   private static class_2382 readVec(class_2487 tag) {
      return new class_2382(tag.method_68083("x", 0), tag.method_68083("y", 0), tag.method_68083("z", 0));
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
}
