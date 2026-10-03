package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;

public class TierSpoof extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> tier = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("tier")).description("The number: 2 with high gives HT2.")).defaultValue(2))
            .min(1)
            .max(5)
            .sliderRange(1, 5)
            .build()
      );
   private final Setting<Boolean> high = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("high"))
                  .description("High tier (HT) rather than low tier (LT)."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> retired = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("retired"))
                  .description("Mark it retired, which prefixes an R (RHT2)."))
               .defaultValue(false))
            .build()
      );
   private final Setting<String> gamemode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)((meteordevelopment.meteorclient.settings.StringSetting.Builder)new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                     .name("gamemode"))
                  .description(
                     "Which gamemode the tier is filed under - that is what decides the icon next to it. Leave empty to file it under every mode at once, which leaves the icon up to TierTagger. Turn list-gamemodes on to see the ids your tierlist actually offers."
                  ))
               .defaultValue(""))
            .build()
      );
   private final Setting<Boolean> listModes = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("list-gamemodes"))
                  .description(
                     "Print the gamemode ids the loaded tierlist offers when this module is switched on. They differ per list - MCTiers and SubTiers have completely different sets."
                  ))
               .defaultValue(true))
            .build()
      );
   private int ticks;
   private long announcedForVersion = -1L;
   private UUID writtenPlayer;
   private Object originalTier;
   private Object lastWritten;

   public TierSpoof() {
      super(CrystalAddon.CATEGORY, "tier-spoof", "Show a tier of your choosing on your own nametag in TierTagger. Local only - nobody else sees it.");
   }

   public void onActivate() {
      if (!FabricLoader.getInstance().isModLoaded("tier-tagger")) {
         this.warning("TierTagger is not installed, nothing to write to.", new Object[0]);
         this.toggle();
      } else {
         this.ticks = 0;
         if (this.activationVersion() != this.announcedForVersion) {
            this.announcedForVersion = this.activationVersion();
            this.info("Local only: other players still see your real tier.", new Object[0]);
            if ((Boolean)this.listModes.get()) {
               List<String> ids = this.gamemodeIds();
               if (ids == TierSpoof.Modes.FALLBACK) {
                  this.info("TierTagger has not loaded its gamemode list, using the MCTiers ids: %s", new Object[]{String.join(", ", ids)});
               } else {
                  this.info("Gamemodes in this tierlist: %s", new Object[]{String.join(", ", ids)});
               }

               String chosen = ((String)this.gamemode.get()).trim();
               if (!chosen.isEmpty() && !ids.contains(chosen)) {
                  this.warning("'%s' is not one of them - the tier is still written, but TierTagger may not show it.", new Object[]{chosen});
               }
            }
         }
      }
   }

   public void onDeactivate() {
      try {
         this.restoreTier();
      } catch (Throwable var2) {
      }
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null) {
         if (this.ticks-- <= 0) {
            this.ticks = 20;

            try {
               this.write();
            } catch (Throwable var3) {
               this.error("Could not write the tier: %s. TierTagger's internals have probably changed.", new Object[]{var3.getClass().getSimpleName()});
               this.toggle();
            }
         }
      }
   }

   private void write() throws Exception {
      Map<UUID, Object> tiers = this.tiersMap();
      if (tiers != null) {
         UUID player = this.mc.field_1724.method_5667();
         if (!player.equals(this.writtenPlayer)) {
            this.restoreTier();
            this.writtenPlayer = player;
            this.originalTier = null;
         }

         Object ranking = this.buildRanking();
         Map<String, Object> byMode = new HashMap<>();
         String chosen = ((String)this.gamemode.get()).trim();
         if (chosen.isEmpty()) {
            for (String id : this.gamemodeIds()) {
               byMode.put(id, ranking);
            }
         } else {
            byMode.put(chosen, ranking);
         }

         Object entry = Optional.of(byMode);
         Object previous = this.lastWritten;
         tiers.compute(player, (uuid, current) -> {
            if (current != previous || previous == null) {
               this.originalTier = current;
            }

            return entry;
         });
         this.lastWritten = entry;
      }
   }

   private void restoreTier() throws Exception {
      if (this.writtenPlayer != null) {
         Map<UUID, Object> tiers = this.tiersMap();
         if (tiers != null && this.lastWritten != null) {
            if (this.originalTier instanceof Optional<?> optional && optional.isPresent()) {
               tiers.replace(this.writtenPlayer, this.lastWritten, this.originalTier);
            } else {
               tiers.remove(this.writtenPlayer, this.lastWritten);
            }
         }

         this.writtenPlayer = null;
         this.originalTier = null;
         this.lastWritten = null;
      }
   }

   private Map<UUID, Object> tiersMap() throws Exception {
      Class<?> cache = Class.forName("com.kevin.tiertagger.TierCache");
      Field field = cache.getDeclaredField("TIERS");
      field.setAccessible(true);
      return (Map<UUID, Object>)field.get(null);
   }

   private Object buildRanking() throws Exception {
      Class<?> type = Class.forName("com.kevin.tiertagger.model.PlayerInfo$Ranking");
      Constructor<?> ctor = type.getDeclaredConstructor(int.class, int.class, Integer.class, Integer.class, long.class, boolean.class);
      ctor.setAccessible(true);
      int pos = this.high.get() ? 0 : 1;
      return ctor.newInstance(this.tier.get(), pos, this.tier.get(), pos, System.currentTimeMillis() / 1000L, this.retired.get());
   }

   private List<String> gamemodeIds() {
      try {
         Class<?> cache = Class.forName("com.kevin.tiertagger.TierCache");
         Method getModes = cache.getMethod("getGamemodes");
         return TierSpoof.Modes.ids((List<?>)getModes.invoke(null));
      } catch (Throwable var3) {
         return TierSpoof.Modes.FALLBACK;
      }
   }

   public String getInfoString() {
      String label = (this.retired.get() ? "R" : "") + (this.high.get() ? "HT" : "LT") + this.tier.get();
      String chosen = ((String)this.gamemode.get()).trim();
      return chosen.isEmpty() ? label : label + " " + chosen;
   }

   static final class Modes {
      static final List<String> FALLBACK = List.of("vanilla", "uhc", "pot", "nethop", "smp", "sword", "axe", "mace");

      private Modes() {
      }

      static List<String> ids(List<?> modes) throws ReflectiveOperationException {
         if (modes != null && !modes.isEmpty()) {
            List<String> ids = new ArrayList<>(modes.size());

            for (Object mode : modes) {
               if (!isNone(mode) && mode.getClass().getMethod("id").invoke(mode) instanceof String s) {
                  ids.add(s);
               }
            }

            return ids.isEmpty() ? FALLBACK : ids;
         } else {
            return FALLBACK;
         }
      }

      static boolean isNone(Object mode) throws ReflectiveOperationException {
         Method method;
         try {
            method = mode.getClass().getMethod("isNone");
         } catch (NoSuchMethodException var3) {
            return false;
         }

         return Boolean.TRUE.equals(method.invoke(mode));
      }
   }
}
