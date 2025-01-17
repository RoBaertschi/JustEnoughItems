package mezz.jei.common.util;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IJeiClientConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.TooltipFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SafeIngredientUtil {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Set<IIngredientRenderer<?>> CRASHING_INGREDIENT_BATCH_RENDERERS = new HashSet<>();
	private static final Set<Object> CRASHING_INGREDIENT_RENDERERS = new HashSet<>();
	private static final Set<Object> CRASHING_INGREDIENT_TOOLTIPS = new HashSet<>();

	private SafeIngredientUtil() {
	}

	public static <T> void getTooltip(ITooltipBuilder tooltip, IIngredientManager ingredientManager, IIngredientRenderer<T> ingredientRenderer, ITypedIngredient<T> typedIngredient) {
		Minecraft minecraft = Minecraft.getInstance();
		TooltipFlag.Default tooltipFlag = minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
		tooltipFlag = tooltipFlag.asCreative();
		getTooltip(tooltip, ingredientManager, ingredientRenderer, typedIngredient, tooltipFlag);
	}

	public static <T> void getTooltip(
		ITooltipBuilder tooltip,
		IIngredientManager ingredientManager,
		IIngredientRenderer<T> ingredientRenderer,
		ITypedIngredient<T> typedIngredient,
		TooltipFlag.Default tooltipFlag
	) {
		T ingredient = typedIngredient.getIngredient();

		if (CRASHING_INGREDIENT_TOOLTIPS.contains(ingredient)) {
			getTooltipErrorTooltip(tooltip);
			return;
		}

		tooltip.setIngredient(typedIngredient);
		try {
			ingredientRenderer.getTooltip(tooltip, ingredient, tooltipFlag);
			if (CRASHING_INGREDIENT_RENDERERS.contains(ingredient)) {
				getRenderErrorTooltip(tooltip);
			}
		} catch (RuntimeException | LinkageError e) {
			CRASHING_INGREDIENT_TOOLTIPS.add(ingredient);
			ErrorUtil.logIngredientCrash(e, "Caught an error getting an Ingredient's tooltip", ingredientManager, typedIngredient.getType(), ingredient);
			getTooltipErrorTooltip(tooltip);
		}
	}

	private static void getTooltipErrorTooltip(ITooltipBuilder tooltip) {
		MutableComponent crash = Component.translatable("jei.tooltip.error.crash");
		tooltip.add(crash.withStyle(ChatFormatting.RED));
	}

	private static void getRenderErrorTooltip(ITooltipBuilder tooltip) {
		MutableComponent crash = Component.translatable("jei.tooltip.error.render.crash");
		tooltip.add(crash.withStyle(ChatFormatting.RED));
	}

	public static <T> void renderBatch(
		GuiGraphics guiGraphics,
		IIngredientType<T> ingredientType,
		IIngredientRenderer<T> ingredientRenderer,
		List<BatchRenderElement<T>> elements
	) {
		if (CRASHING_INGREDIENT_BATCH_RENDERERS.contains(ingredientRenderer)) {
			for (BatchRenderElement<T> element : elements) {
				render(guiGraphics, ingredientRenderer, ingredientType, element);
			}
			return;
		}

		try {
			ingredientRenderer.renderBatch(guiGraphics, elements);
		} catch (RuntimeException | LinkageError e) {
			CRASHING_INGREDIENT_BATCH_RENDERERS.add(ingredientRenderer);
			LOGGER.error(
				"Caught an error while rendering a batch of Ingredients with ingredient renderer: {}",
				ingredientRenderer.getClass(),
				e
			);
		}
	}

	public static <T> void render(
		GuiGraphics guiGraphics,
		IIngredientRenderer<T> ingredientRenderer,
		ITypedIngredient<T> typedIngredient,
		int x,
		int y
	) {
		render(guiGraphics, ingredientRenderer, typedIngredient.getType(), typedIngredient.getIngredient(), x, y);
	}

	public static <T> void render(
		GuiGraphics guiGraphics,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientType<T> ingredientType,
		BatchRenderElement<T> element
	) {
		render(guiGraphics, ingredientRenderer, ingredientType, element.ingredient(), element.x(), element.y());
	}

	public static <T> void render(
		GuiGraphics guiGraphics,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientType<T> ingredientType,
		T ingredient,
		int x,
		int y
	) {
		if (CRASHING_INGREDIENT_RENDERERS.contains(ingredient)) {
			renderError(guiGraphics);
			return;
		}

		try {
			ingredientRenderer.render(guiGraphics, ingredient, x, y);
		} catch (RuntimeException | LinkageError e) {
			CRASHING_INGREDIENT_RENDERERS.add(ingredient);

			IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
			if (shouldCatchRenderErrors()) {
				ErrorUtil.logIngredientCrash(e, "Caught an error rendering an Ingredient", ingredientManager, ingredientType, ingredient);
				renderError(guiGraphics);
			} else {
				CrashReport crashReport = ErrorUtil.createIngredientCrashReport(e, "Rendering ingredient", ingredientManager, ingredientType, ingredient);
				throw new ReportedException(crashReport);
			}
		}
	}

	private static boolean shouldCatchRenderErrors() {
		return Internal.getOptionalJeiClientConfigs()
			.map(IJeiClientConfigs::getClientConfig)
			.map(IClientConfig::isCatchRenderErrorsEnabled)
			.orElse(false);
	}

	private static void renderError(GuiGraphics guiGraphics) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		guiGraphics.drawString(font, "ERR", 0, 0, 0xFFFF0000, false);
		guiGraphics.drawString(font, "OR", 0, 8, 0xFFFF0000, false);
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}

}
