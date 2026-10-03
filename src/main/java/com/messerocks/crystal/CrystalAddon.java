package com.messerocks.crystal;

import com.messerocks.crystal.hud.CrystalInfoHud;
import com.messerocks.crystal.modules.AnchorMacro;
import com.messerocks.crystal.modules.AutoAnchor;
import com.messerocks.crystal.modules.AutoBlock;
import com.messerocks.crystal.modules.AutoCrystal;
import com.messerocks.crystal.modules.AutoShieldBreak;
import com.messerocks.crystal.modules.CrystalDamageEsp;
import com.messerocks.crystal.modules.DoubleTap;
import com.messerocks.crystal.modules.FightStats;
import com.messerocks.crystal.modules.HotbarRefill;
import com.messerocks.crystal.modules.InventoryTotem;
import com.messerocks.crystal.modules.PopCounter;
import com.messerocks.crystal.modules.PopWindow;
import com.messerocks.crystal.modules.SchematicBuilder;
import com.messerocks.crystal.modules.SmartTotem;
import com.messerocks.crystal.modules.Stealth;
import com.messerocks.crystal.modules.SuicidePrevent;
import com.messerocks.crystal.modules.SwordPlace;
import com.messerocks.crystal.modules.TargetInfo;
import com.messerocks.crystal.modules.TierSpoof;
import com.mojang.logging.LogUtils;
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
      return "com.messerocks.crystal";
   }
}
