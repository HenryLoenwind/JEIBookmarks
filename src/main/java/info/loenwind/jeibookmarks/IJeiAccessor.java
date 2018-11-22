package info.loenwind.jeibookmarks;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mezz.jei.api.gui.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocus;

public interface IJeiAccessor {

  @Nullable
  IGuiProperties getGuiProperties();

  boolean isListDisplayed();

  @Nonnull
  Rectangle getDisplayArea();

  @Nonnull
  Set<Rectangle> getGuiAreas();

  <T> void renderIngredient(int iconX, int iconY, @Nonnull T ingredient);

  <T> void renderTooltip(int iconX, int iconY, @Nonnull T ingredient);

  @Nonnull
  <V> List<String> getIngredientTooltipSafe(@Nonnull IIngredientRenderer<V> ingredientRenderer, @Nonnull V ingredient);

  public <V> void show(@Nonnull IFocus<V> focus);

  @Nullable
  Object getIngredientUnderMouseForKey(int mouseX, int mouseY);

  @Nonnull
  Object copyIngdredient(@Nonnull IIngredientHelper<Object> ingredientHelper, @Nonnull Object ingredient);

  @Nonnull
  IIngredientHelper<Object> getIngredientHelper(@Nonnull Object ingredient);

  void drawHighlight(int iconX, int iconY, int width, int height);

}
