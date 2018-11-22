package info.loenwind.jeibookmarks;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mezz.jei.Internal;
import mezz.jei.api.IIngredientListOverlay;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.input.InputHandler;
import mezz.jei.runtime.JeiRuntime;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.Log;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindMethodException;

public class JeiAccessor implements IJeiAccessor {

  public static JeiAccessor create(@Nonnull IJeiRuntime jeiRuntime, @Nonnull IModRegistry jeiRegistry) {
    if (!(jeiRuntime instanceof JeiRuntime)) {
      return null;
    }
    IIngredientListOverlay ingredientListOverlay = jeiRuntime.getIngredientListOverlay();
    if (!(ingredientListOverlay instanceof IngredientListOverlay)) {
      return null;
    }

    return new JeiAccessor(jeiRegistry, (JeiRuntime) jeiRuntime, (IngredientListOverlay) ingredientListOverlay);
  }

  @Nonnull
  private static <P> P notnull(@Nullable P o) {
    if (o == null) {
      throw new NullPointerException("Houston we have a null! Please report that on our bugtracker unless you are using some old version. Thank you.");
    }
    return o;
  }

  private final @Nonnull IModRegistry jeiRegistry;

  private final @Nonnull JeiRuntime jeiRuntime;

  private final @Nonnull IngredientListOverlay ingredientListOverlay;

  private InputHandler inputHandler;
  private Method getIngredientUnderMouseForKey;

  private JeiAccessor(@Nonnull IModRegistry jeiRegistry, @Nonnull JeiRuntime jeiRuntime, @Nonnull IngredientListOverlay ingredientListOverlay) {
    this.jeiRegistry = jeiRegistry;
    this.jeiRuntime = jeiRuntime;
    this.ingredientListOverlay = ingredientListOverlay;
  }

  @Override
  public @Nullable IGuiProperties getGuiProperties() {
    return jeiRuntime.getGuiProperties(Minecraft.getMinecraft().currentScreen);
  }

  @Override
  public boolean isListDisplayed() {
    return ingredientListOverlay.isListDisplayed();
  }

  @Override
  @SuppressWarnings("unchecked")
  public @Nonnull Rectangle getDisplayArea() {
    Object value = ReflectionHelper.getPrivateValue(IngredientListOverlay.class, ingredientListOverlay, "displayArea");
    return value instanceof Rectangle ? (Rectangle) value : new Rectangle();
  }

  // mezz.jei.gui.overlay.IngredientGridAll.getGuiAreas()
  @Override
  public @Nonnull Set<Rectangle> getGuiAreas() {
    final GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
    if (currentScreen instanceof GuiContainer) {
      final GuiContainer guiContainer = (GuiContainer) currentScreen;
      final Set<Rectangle> allGuiExtraAreas = new HashSet<>();
      final List<IAdvancedGuiHandler<GuiContainer>> activeAdvancedGuiHandlers = jeiRuntime.getActiveAdvancedGuiHandlers(guiContainer);
      for (IAdvancedGuiHandler<GuiContainer> advancedGuiHandler : activeAdvancedGuiHandlers) {
        final List<Rectangle> guiExtraAreas = advancedGuiHandler.getGuiExtraAreas(guiContainer);
        if (guiExtraAreas != null) {
          allGuiExtraAreas.addAll(guiExtraAreas);
        }
      }
      return allGuiExtraAreas;
    }
    return notnull(Collections.emptySet());
  }

  // mezz.jei.gui.recipes.RecipeCategoryTab.renderIngredient(Minecraft, int, int, T)
  @Override
  public <T> void renderIngredient(int iconX, int iconY, @Nonnull T ingredient) {
    IIngredientRegistry ingredientRegistry = jeiRegistry.getIngredientRegistry();
    IIngredientRenderer<T> ingredientRenderer = ingredientRegistry.getIngredientRenderer(ingredient);
    GlStateManager.enableDepth();
    ingredientRenderer.render(Minecraft.getMinecraft(), iconX, iconY, ingredient);
    GlStateManager.enableAlpha();
    // GlStateManager.disableDepth();
  }

  // mezz.jei.render.IngredientRenderer.drawTooltip(Minecraft, int, int)
  @Override
  public <T> void renderTooltip(int iconX, int iconY, @Nullable T ingredient) {
    if (ingredient == null) {
      return;
    }
    IIngredientRegistry ingredientRegistry = jeiRegistry.getIngredientRegistry();
    IIngredientRenderer<T> ingredientRenderer = ingredientRegistry.getIngredientRenderer(ingredient);
    List<String> tooltip = getIngredientTooltipSafe(ingredientRenderer, ingredient);
    FontRenderer fontRenderer = ingredientRenderer.getFontRenderer(Minecraft.getMinecraft(), ingredient);

    if (ingredient instanceof ItemStack) {
      ItemStack itemStack = (ItemStack) ingredient;
      TooltipRenderer.drawHoveringText(itemStack, Minecraft.getMinecraft(), tooltip, iconX, iconY, fontRenderer);
    } else {
      TooltipRenderer.drawHoveringText(Minecraft.getMinecraft(), tooltip, iconX, iconY, fontRenderer);
    }
  }

  // mezz.jei.render.IngredientRenderer.getIngredientTooltipSafe(Minecraft, IIngredientListElement<V>)
  @Override
  public @Nonnull <V> List<String> getIngredientTooltipSafe(@Nonnull IIngredientRenderer<V> ingredientRenderer, @Nonnull V ingredient) {
    try {
      ITooltipFlag.TooltipFlags tooltipFlag = Minecraft.getMinecraft().gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED
          : ITooltipFlag.TooltipFlags.NORMAL;
      return ingredientRenderer.getTooltip(Minecraft.getMinecraft(), ingredient, tooltipFlag);
    } catch (RuntimeException | LinkageError e) {
      Log.get().error("Tooltip crashed.", e);
    }

    List<String> tooltip = new ArrayList<>();
    tooltip.add(TextFormatting.RED + Translator.translateToLocal("jei.tooltip.error.crash"));
    return tooltip;
  }

  // mezz.jei.render.IngredientRenderer.drawHighlight()
  @Override
  public void drawHighlight(int iconX, int iconY, int width, int height) {
    GlStateManager.disableLighting();
    GlStateManager.disableDepth();
    GlStateManager.colorMask(true, true, true, false);
    GuiUtils.drawGradientRect(0, iconX, iconY, iconX + width, iconY + height, 0x80FFFFFF, 0x80FFFFFF);
    GlStateManager.colorMask(true, true, true, true);
    GlStateManager.enableDepth();
  }

  @Override
  public <V> void show(@Nonnull IFocus<V> focus) {
    jeiRuntime.getRecipesGui().show(focus);
  }

  @Override
  @Nullable
  public Object getIngredientUnderMouseForKey(int mouseX, int mouseY) {
    if (inputHandler == null) {
      Object o = ReflectionHelper.getPrivateValue(mezz.jei.Internal.class, null, "inputHandler");
      if (!(o instanceof InputHandler)) {
        return null;
      }
      inputHandler = (InputHandler) o;
    }

    if (getIngredientUnderMouseForKey == null) {
      try {
        getIngredientUnderMouseForKey = ReflectionHelper.findMethod(InputHandler.class, "getIngredientUnderMouseForKey", "getIngredientUnderMouseForKey",
            int.class, int.class);
      } catch (UnableToFindMethodException e) {
        return null;
      }
    }

    try {
      Object invoke = getIngredientUnderMouseForKey.invoke(inputHandler, mouseX, mouseY);
      if (invoke instanceof IClickedIngredient) {
        return ((IClickedIngredient<?>) invoke).getValue();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  @Nonnull
  public Object copyIngdredient(@Nonnull IIngredientHelper<Object> ingredientHelper, @Nonnull Object ingredient) {
    return LegacyUtil.getIngredientCopy(ingredient, ingredientHelper);
  }

  @Override
  @Nonnull
  public IIngredientHelper<Object> getIngredientHelper(@Nonnull Object ingredient) {
    return Internal.getIngredientRegistry().getIngredientHelper(ingredient);
  }
}
