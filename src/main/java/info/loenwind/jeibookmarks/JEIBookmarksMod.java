package info.loenwind.jeibookmarks;

import java.io.File;

import javax.annotation.Nonnull;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = JEIBookmarksMod.MODID, version = JEIBookmarksMod.VERSION, name = JEIBookmarksMod.MODID, dependencies = JEIBookmarksMod.DEPENDENCIES)
public class JEIBookmarksMod {

  public static final @Nonnull String MODID = "jeibookmarks";
  public static final @Nonnull String VERSION = "1.0.0";
  public static final @Nonnull String DEPENDENCIES = "after:jei@[4.13.1.224,4.14.0.0)";
  public static File configurationDirectory;

  @EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    configurationDirectory = event.getModConfigurationDirectory();
  }

}
