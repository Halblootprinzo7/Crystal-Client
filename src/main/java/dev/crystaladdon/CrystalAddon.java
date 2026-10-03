package dev.crystaladdon;

import dev.crystaladdon.hud.CrystalInfoHud;
import dev.crystaladdon.modules.AnchorMacro;
import dev.crystaladdon.modules.AutoAnchor;
import dev.crystaladdon.modules.AutoBlock;
import dev.crystaladdon.modules.AutoCrystal;
import dev.crystaladdon.modules.AutoShieldBreak;
import dev.crystaladdon.modules.CrystalDamageEsp;
import dev.crystaladdon.modules.DoubleTap;
import dev.crystaladdon.modules.FightStats;
import dev.crystaladdon.modules.HotbarRefill;
import dev.crystaladdon.modules.InventoryTotem;
import dev.crystaladdon.modules.PopCounter;
import dev.crystaladdon.modules.PopWindow;
import dev.crystaladdon.modules.SchematicBuilder;
import dev.crystaladdon.modules.SmartTotem;
import dev.crystaladdon.modules.Stealth;
import dev.crystaladdon.modules.SuicidePrevent;
import dev.crystaladdon.modules.SwordPlace;
import dev.crystaladdon.modules.TargetInfo;
import dev.crystaladdon.modules.TierSpoof;
import dev.crystaladdon.utils.RevivedPlayers;
import dev.crystaladdon.utils.TurnProgress;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.class_1802;
import org.slf4j.Logger;

public class CrystalAddon extends MeteorAddon {
   public static final Logger LOG = LogUtils.getLogger();
   public static final Category CATEGORY = new Category("Crystal", class_1802.field_8301.method_7854());
   public static final HudGroup HUD_GROUP = new HudGroup("Crystal");

   public void onInitialize() {
      LOG.info("Initializing Crystal Addon");
      register(new AutoCrystal());
      register(new AutoAnchor());
      register(new AnchorMacro());
      register(new AutoBlock());
      register(new AutoShieldBreak());
      register(new DoubleTap());
      register(new SwordPlace());
      register(new PopWindow());
      register(new SmartTotem());
      register(new InventoryTotem());
      register(new SuicidePrevent());
      register(new Stealth());
      register(new TargetInfo());
      register(new CrystalDamageEsp());
      register(new HotbarRefill());
      register(new FightStats());
      register(new PopCounter());
      register(new SchematicBuilder());
      register(new TierSpoof());
      Hud.get().register(CrystalInfoHud.INFO);
      RevivedPlayers.init();
      MeteorClient.EVENT_BUS.subscribe(TurnProgress.SHARED);
   }

   private static void register(Module module) {
      Module existing = Modules.get().get(module.name);
      if (existing != null) {
         LOG.warn("Module name '{}' is already registered by {}. One of the two will be dropped by Meteor.", module.name, existing.getClass().getName());
      }

      Modules.get().add(module);
   }

   public void onRegisterCategories() {
      Modules.registerCategory(CATEGORY);
   }

   public String getPackage() {
      return "dev.crystaladdon";
   }
}
