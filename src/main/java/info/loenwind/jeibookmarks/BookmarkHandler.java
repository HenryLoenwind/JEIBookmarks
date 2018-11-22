package info.loenwind.jeibookmarks;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IFocus.Mode;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.config.Constants;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.Focus;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.ingredients.GuiItemStackGroup;
import mezz.jei.input.IPaged;
import mezz.jei.input.MouseHelper;
import mezz.jei.util.MathUtil;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@JEIPlugin
@EventBusSubscriber(modid = JEIBookmarksMod.MODID, value = Side.CLIENT)
public class BookmarkHandler implements IModPlugin {

  private static final @Nonnull String MARKER_OTHER = "O";
  private static final @Nonnull String MARKER_STACK = "T";
  private static final @Nonnull String BOOKMARK_FILE = "jeibookmarks.ini";

  private static @Nonnull IJeiAccessor jeiAccessor = NullJeiAccessor.instance;

  private static IModRegistry jeiRegistry = null;
  private static final @Nonnull List<Object> list = new ArrayList<>();
  private static boolean hasRendered = false;
  private static final @Nonnull List<Bookmark> screen = new ArrayList<>();
  private static @Nonnull Rectangle bookmarkArea = new Rectangle();
  private static @Nonnull Rectangle naviArea = new Rectangle();
  private static @Nonnull Rectangle textArea = new Rectangle();
  private static @Nonnull Set<Rectangle> exclusionAreas = notnull(Collections.emptySet());
  private static final @Nonnull Pager pager = new Pager();
  private static PageNavigation navi;
  private static final @Nonnull KeyBinding keyBinding = new KeyBinding("key.jeibookmarks.bookmark", Keyboard.KEY_A,
      Constants.MOD_ID + " (" + Constants.NAME + ')');

  public BookmarkHandler() {
    ClientRegistry.registerKeyBinding(keyBinding);
  }

  @Override
  public void onRuntimeAvailable(@Nonnull IJeiRuntime jeiRuntimeIn) {
    if (jeiRegistry != null) {
      JeiAccessor accessor = JeiAccessor.create(jeiRuntimeIn, jeiRegistry);
      if (accessor != null) {
        jeiAccessor = accessor;
        navi = new PageNavigation(pager, false);
        loadBookmarks();
        System.out.println("Bookmark Plugin Loaded");
      }
    }
  }

  @Override
  public void register(@Nonnull IModRegistry registry) {
    jeiRegistry = registry;
  }

  static final class Pager implements IPaged {
    int page = 1;
    int pages = 1;
    int perPage = 0;

    private void update() {
      navi.updatePageState();
    }

    void setItemsPerPage(int items) {
      perPage = items;
      if (list.isEmpty() || items < 1) {
        page = pages = 1;
      } else {
        pages = (int) Math.ceil(list.size() / (double) items);
        if (page > pages) {
          page = pages;
        }
      }
      update();
    }

    int getSkip() {
      return (page - 1) * perPage;
    }

    @Override
    public boolean nextPage() {
      page++;
      if (page > pages) {
        page = 1;
      }
      update();
      return true;
    }

    @Override
    public boolean previousPage() {
      page--;
      if (page < 1) {
        page = pages;
      }
      update();
      return true;
    }

    @Override
    public boolean hasNext() {
      return page < pages;
    }

    @Override
    public boolean hasPrevious() {
      return page > 1;
    }

    @Override
    public int getPageCount() {
      return pages;
    }

    @Override
    public int getPageNumber() {
      return page - 1;
    }

  }

  private static class Bookmark {
    final @Nonnull Rectangle area;
    final @Nonnull Object ingredient;

    Bookmark(@Nonnull Rectangle area, @Nonnull Object ingredient) {
      this.area = area;
      this.ingredient = ingredient;
    }
  }

  private static final int BORDER_PADDING = 2;
  private static final int NAVIGATION_HEIGHT = 20;
  private static final int SEARCH_HEIGHT = 16;
  private static final int INGREDIENT_PADDING = 1;
  private static final int INGREDIENT_WIDTH = GuiItemStackGroup.getWidth(INGREDIENT_PADDING);
  private static final int INGREDIENT_HEIGHT = GuiItemStackGroup.getHeight(INGREDIENT_PADDING);

  @SubscribeEvent
  public static void onDrawBackgroundEventPost(@Nonnull GuiScreenEvent.BackgroundDrawnEvent event) {
    hasRendered = render();
    if (naviArea.width > 0) {
      navi.draw(Minecraft.getMinecraft(), event.getMouseX(), event.getMouseY(), 0);
    }
    if (textArea.width > 0) {
      drawKeyHint();
    }
  }

  private static void drawKeyHint() {
    FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
    String str = (keyBinding.getKeyCode() != Keyboard.KEY_NONE) ? Translator.translateToLocalFormatted("jeibookmarks.key", keyBinding.getDisplayName())
        : Translator.translateToLocal("jeibookmarks.nokey");
    int width = fontRenderer.getStringWidth(str);
    int txtX = Math.max((int) (textArea.getCenterX() - width / 2), BORDER_PADDING);
    int txtY = (int) (textArea.getCenterY() - 8 / 2);
    fontRenderer.drawString(str, txtX, txtY, Color.white.getRGB(), true);
  }

  @SubscribeEvent
  public static void onDrawScreenEventPost(@Nonnull GuiScreenEvent.DrawScreenEvent.Post event) {
    if (!hasRendered) {
      hasRendered = render();
      if (naviArea.width > 0) {
        navi.draw(Minecraft.getMinecraft(), event.getMouseX(), event.getMouseY(), 0);
      }
      if (textArea.width > 0) {
        drawKeyHint();
      }
    }
    if (hasRendered) {
      hasRendered = false;
      Bookmark bookmarkUnderMouse = getBookmarkUnderMouse(event.getMouseX(), event.getMouseY());
      if (bookmarkUnderMouse != null) {
        jeiAccessor.drawHighlight(bookmarkUnderMouse.area.x, bookmarkUnderMouse.area.y, INGREDIENT_WIDTH, INGREDIENT_HEIGHT);
        jeiAccessor.renderTooltip(event.getMouseX(), event.getMouseY(), bookmarkUnderMouse.ingredient);
      }
    }
  }

  private static @Nullable Bookmark getBookmarkUnderMouse(int mouseX, int mouseY) {
    if (!screen.isEmpty() && bookmarkArea.contains(mouseX, mouseY)) {
      for (Bookmark entry : screen) {
        if (entry.area.contains(mouseX, mouseY)) {
          return entry;
        }
      }
    }
    return null;
  }

  private static boolean render() {
    screen.clear();
    naviArea.setBounds(0, 0, 0, 0);
    textArea.setBounds(0, 0, 0, 0);

    IGuiProperties guiProperties = jeiAccessor.getGuiProperties();
    if (guiProperties == null) {
      return false;
    }
    if (!jeiAccessor.isListDisplayed()) {
      return false;
    }

    if (!computeBounds(guiProperties)) {
      return false;
    }

    if (list.isEmpty()) {
      return false;
    }

    GlStateManager.disableLighting();
    List<Rectangle> slots = getSlots();
    if (slots.isEmpty()) {
      return false;
    }
    pager.setItemsPerPage(slots.size());

    int skip = pager.getSkip();
    for (Object object : list) {
      if (object != null) {
        if (skip-- <= 0) {
          Rectangle area = slots.remove(0);
          jeiAccessor.renderIngredient(area.x + INGREDIENT_PADDING, area.y + INGREDIENT_PADDING, object);
          screen.add(new Bookmark(area, object));
          if (slots.isEmpty()) {
            return true;
          }
        }
      }
    }
    if (skip > 0) {
      // weird...
      pager.previousPage();
    }

    return true;
  }

  private static boolean computeBounds(@Nonnull IGuiProperties guiProperties) {
    exclusionAreas = jeiAccessor.getGuiAreas();
    bookmarkArea = jeiAccessor.getDisplayArea();
    bookmarkArea = new Rectangle(bookmarkArea);
    bookmarkArea.width = guiProperties.getGuiLeft();
    bookmarkArea.x = 2 * BORDER_PADDING;
    bookmarkArea.height -= NAVIGATION_HEIGHT + SEARCH_HEIGHT + 4;
    naviArea = new Rectangle(bookmarkArea);

    bookmarkArea.y += BORDER_PADDING + NAVIGATION_HEIGHT;
    textArea = new Rectangle(bookmarkArea);
    textArea.y = (int) bookmarkArea.getMaxY();
    textArea.height = SEARCH_HEIGHT;

    int yCenteringOffset = (bookmarkArea.height - ((bookmarkArea.height / INGREDIENT_HEIGHT) * INGREDIENT_HEIGHT)) / 2;
    bookmarkArea.y += yCenteringOffset;
    bookmarkArea.height -= yCenteringOffset;

    int guiLeft = guiProperties.getGuiLeft() - BORDER_PADDING;
    if (guiProperties.getGuiClass() == mezz.jei.gui.recipes.RecipesGui.class && exclusionAreas.isEmpty()) {
      // JEI doesn't define an exclusion area for its own side-tabs at the moment
      guiLeft -= INGREDIENT_WIDTH + 4;
    }

    while (bookmarkArea.getMaxX() > (guiLeft)) {
      bookmarkArea.width--;
      if (bookmarkArea.width <= INGREDIENT_WIDTH) {
        return false;
      }
    }

    bookmarkArea.width = (bookmarkArea.width / INGREDIENT_WIDTH) * INGREDIENT_WIDTH;

    naviArea.width = bookmarkArea.width;
    naviArea.height = NAVIGATION_HEIGHT;
    textArea.width = bookmarkArea.width;
    navi.updateBounds(naviArea);
    navi.updatePageState();
    return true;
  }

  private static @Nonnull List<Rectangle> getSlots() {
    List<Rectangle> result = new ArrayList<>();
    int x = (int) bookmarkArea.getMinX(), y = (int) bookmarkArea.getMinY();
    while (true) {
      Rectangle area = new Rectangle(x, y, INGREDIENT_WIDTH, INGREDIENT_HEIGHT);
      if (!MathUtil.intersects(exclusionAreas, area)) {
        result.add(area);
      }
      x += INGREDIENT_WIDTH;
      if (x + INGREDIENT_WIDTH > bookmarkArea.getMaxX()) {
        x = (int) bookmarkArea.getMinX();
        y += INGREDIENT_HEIGHT;
        if (y + INGREDIENT_HEIGHT > bookmarkArea.getMaxY()) {
          return result;
        }
      }
    }
  }

  private static boolean bookmarkItemUnderMouse(int mouseX, int mouseY) {
    Object underMouse = jeiAccessor.getIngredientUnderMouseForKey(mouseX, mouseY);
    if (underMouse != null) {
      // copy it so we don't keep a reference to e.g. a living itemstack in the player's inventory
      IIngredientHelper<Object> ingredientHelper = jeiAccessor.getIngredientHelper(underMouse);
      Object copy = jeiAccessor.copyIngdredient(ingredientHelper, underMouse);
      boolean hasProperEquals = copy.equals(underMouse);
      underMouse = copy;
      // Normalize it
      if (underMouse instanceof ItemStack) {
        ((ItemStack) underMouse).setCount(1);
      } else if (underMouse instanceof FluidStack) {
        ((FluidStack) underMouse).amount = 1000;
      }
      // Dupe-check it
      if (!list.contains(underMouse)) {
        if (!hasProperEquals) { // e.g. ItemStack
          for (Object existing : list) {
            if (existing != null && existing.getClass() == underMouse.getClass()) {
              if (ingredientHelper.getUniqueId(existing).equals(ingredientHelper.getUniqueId(underMouse))) {
                return false;
              }
            }
          }
        }
        list.add(underMouse);
        saveBookmarks();
        return true;
      }
    }
    return false;
  }

  @SubscribeEvent
  public static void onGuiKeyboardEvent(@Nonnull GuiScreenEvent.KeyboardInputEvent.Post event) {
    if (Keyboard.getEventKeyState()) {
      int eventKey = Keyboard.getEventKey();
      int mouseX = MouseHelper.getX();
      int mouseY = MouseHelper.getY();
      if (keyBinding.isActiveAndMatches(eventKey) && bookmarkItemUnderMouse(mouseX, mouseY)) {
        event.setCanceled(true);
      } else if (!screen.isEmpty() && bookmarkArea.contains(mouseX, mouseY)) {
        Bookmark bookmarkUnderMouse = getBookmarkUnderMouse(mouseX, mouseY);
        if (bookmarkUnderMouse != null) {
          if (keyBinding.isActiveAndMatches(eventKey)) {
            list.remove(bookmarkUnderMouse.ingredient);
            saveBookmarks();
            event.setCanceled(true);
          } else if (KeyBindings.showRecipe.isActiveAndMatches(eventKey)) {
            jeiAccessor.show(new Focus<>(Mode.OUTPUT, bookmarkUnderMouse.ingredient));
            event.setCanceled(true);
          } else if (KeyBindings.showUses.isActiveAndMatches(eventKey)) {
            jeiAccessor.show(new Focus<>(Mode.INPUT, bookmarkUnderMouse.ingredient));
            event.setCanceled(true);
          }
        } else if (KeyBindings.nextPage.isActiveAndMatches(eventKey)) {
          pager.nextPage();
          event.setCanceled(true);
        } else if (KeyBindings.previousPage.isActiveAndMatches(eventKey)) {
          pager.previousPage();
          event.setCanceled(true);
        }
      }
    }
  }

  @SubscribeEvent
  public static void onGuiMouseEvent(@Nonnull GuiScreenEvent.MouseInputEvent.Pre event) {
    if (!Mouse.getEventButtonState() && navi != null) {
      int button = Mouse.getEventButton();
      int mouseX = MouseHelper.getX();
      int mouseY = MouseHelper.getY();
      Bookmark bookmarkUnderMouse = getBookmarkUnderMouse(mouseX, mouseY);
      if (button == 0) {
        if (bookmarkUnderMouse != null) {
          jeiAccessor.show(new Focus<>(Mode.OUTPUT, bookmarkUnderMouse.ingredient));
          event.setCanceled(true);
        } else if (navi.isMouseOver() && navi.handleMouseClickedButtons(mouseX, mouseY)) {
          event.setCanceled(true);
        }
      } else if (button == 1) {
        if (bookmarkUnderMouse != null) {
          jeiAccessor.show(new Focus<>(Mode.INPUT, bookmarkUnderMouse.ingredient));
          event.setCanceled(true);
        }
      } else if (Mouse.getEventDWheel() != 0
          && (bookmarkArea.contains(mouseX, mouseY) || naviArea.contains(mouseX, mouseY) || textArea.contains(mouseX, mouseY))) {
        if (Mouse.getEventDWheel() < 0) {
          pager.nextPage();
        } else {
          pager.previousPage();
        }
        event.setCanceled(true);
      }
    }
  }

  private static void saveBookmarks() {
    IIngredientRegistry ingredientRegistry = jeiRegistry.getIngredientRegistry();
    List<String> strings = new ArrayList<>();
    for (Object object : list) {
      if (object instanceof ItemStack) {
        strings.add(MARKER_STACK + ((ItemStack) object).writeToNBT(new NBTTagCompound()).toString());
      } else if (object != null) {
        IIngredientHelper<Object> ingredientHelper = ingredientRegistry.getIngredientHelper(object);
        strings.add(MARKER_OTHER + ingredientHelper.getUniqueId(object));
      }
    }
    File f = new File(JEIBookmarksMod.configurationDirectory, BOOKMARK_FILE);
    try (FileWriter writer = new FileWriter(f)) {
      IOUtils.writeLines(strings, "\n", writer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void loadBookmarks() {
    File f = new File(JEIBookmarksMod.configurationDirectory, BOOKMARK_FILE);
    if (!f.exists()) {
      return;
    }
    List<String> strings;
    try (FileReader reader = new FileReader(f)) {
      strings = IOUtils.readLines(reader);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    Map<String, Object> map = new HashMap<>();
    for (String s : strings) {
      if (s.startsWith(MARKER_OTHER)) {
        map.put(s.substring(1), null);
      }
    }

    mapNonItemStackIngredient(map);

    list.clear();
    for (String s : strings) {
      if (s.startsWith(MARKER_STACK)) {
        try {
          list.add(new ItemStack(JsonToNBT.getTagFromJson(notnull(s.substring(1)))));
        } catch (NBTException e) {
          e.printStackTrace();
        }
      } else if (s.startsWith(MARKER_OTHER)) {
        Object object = map.get(s.substring(1));
        if (object != null) {
          list.add(object);
        }
      }
    }

  }

  @SuppressWarnings({ "rawtypes", "unchecked" }) // doesn't compile with generics
  private static void mapNonItemStackIngredient(Map<String, Object> map) {
    IIngredientRegistry ingredientRegistry = jeiRegistry.getIngredientRegistry();
    for (IIngredientType ingredientType : ingredientRegistry.getRegisteredIngredientTypes()) {
      if (ingredientType != null && ingredientType != VanillaTypes.ITEM) {
        IIngredientHelper ingredientHelper = ingredientRegistry.getIngredientHelper(ingredientType);
        for (Object o : ingredientRegistry.getAllIngredients(ingredientType)) {
          if (o != null) {
            String id = ingredientHelper.getUniqueId(o);
            if (map.containsKey(id)) {
              map.put(id, o);
            }
          }
        }
      }
    }
  }

  @Nonnull
  private static <P> P notnull(@Nullable P o) {
    if (o == null) {
      throw new NullPointerException("Houston we have a null! Please report that on our bugtracker unless you are using some old version. Thank you.");
    }
    return o;
  }

}
