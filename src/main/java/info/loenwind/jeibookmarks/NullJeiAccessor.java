package info.loenwind.jeibookmarks;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mezz.jei.api.gui.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocus;

public final class NullJeiAccessor implements IJeiAccessor {

  public static final @Nonnull IJeiAccessor instance = new NullJeiAccessor();

  private NullJeiAccessor() {
  }

  @Nonnull
  private static <P> P notnull(@Nullable P o) {
    if (o == null) {
      throw new NullPointerException("Houston we have a null! Please report that on our bugtracker unless you are using some old version. Thank you.");
    }
    return o;
  }

  @Override
  public @Nullable IGuiProperties getGuiProperties() {
    return null;
  }

  @Override
  public @Nonnull Rectangle getDisplayArea() {
    return new Rectangle();
  }

  @Override
  public @Nonnull Set<Rectangle> getGuiAreas() {
    return notnull(Collections.emptySet());
  }

  @Override
  public boolean isListDisplayed() {
    return false;
  }

  @Override
  public <T> void renderIngredient(int iconX, int iconY, @Nonnull T ingredient) {
  }

  @Override
  public <T> void renderTooltip(int iconX, int iconY, @Nonnull T ingredient) {
  }

  @Override
  public @Nonnull <V> List<String> getIngredientTooltipSafe(@Nonnull IIngredientRenderer<V> ingredientRenderer, @Nonnull V ingredient) {
    return notnull(Collections.emptyList());
  }

  @Override
  public <V> void show(@Nonnull IFocus<V> focus) {
  }

  @Override
  @Nullable
  public Object getIngredientUnderMouseForKey(int mouseX, int mouseY) {
    return null;
  }

  @Override
  @Nonnull
  public Object copyIngdredient(@Nonnull IIngredientHelper<Object> ingredientHelper, @Nonnull Object ingredient) {
    return ingredient;
  }

  @Override
  @Nonnull
  public IIngredientHelper<Object> getIngredientHelper(@Nonnull Object ingredient) {
    throw new NullPointerException();
  }

  @Override
  public void drawHighlight(int iconX, int iconY, int width, int height) {
  }

}
