package com.ghostslotecho.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class GhostSlotConfigScreen {

    public static Screen create(Screen parent) {
        GhostSlotConfig config = GhostSlotConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("title.ghostslotecho.config"));

        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("category.ghostslotecho.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder.startIntSlider(
                        Text.translatable("option.ghostslotecho.echo_duration"), config.echoDurationSeconds, 1, 120)
                .setDefaultValue(20)
                .setTooltip(Text.translatable("option.ghostslotecho.echo_duration.tooltip"))
                .setSaveConsumer(newValue -> config.echoDurationSeconds = newValue)
                .build());

        general.addEntry(entryBuilder.startIntSlider(
                        Text.translatable("option.ghostslotecho.ghost_opacity"), (int) (config.ghostOpacity * 100), 5, 80)
                .setDefaultValue(25)
                .setTooltip(Text.translatable("option.ghostslotecho.ghost_opacity.tooltip"))
                .setSaveConsumer(newValue -> config.ghostOpacity = newValue / 100.0f)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.translatable("option.ghostslotecho.persist_on_close"), config.persistOnClose)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("option.ghostslotecho.persist_on_close.tooltip"))
                .setSaveConsumer(newValue -> config.persistOnClose = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.translatable("option.ghostslotecho.enable_smart_quick_move"), config.enableSmartQuickMove)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("option.ghostslotecho.enable_smart_quick_move.tooltip"))
                .setSaveConsumer(newValue -> config.enableSmartQuickMove = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.translatable("option.ghostslotecho.enable_pinning"), config.enablePinning)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("option.ghostslotecho.enable_pinning.tooltip"))
                .setSaveConsumer(newValue -> config.enablePinning = newValue)
                .build());

        builder.setSavingRunnable(GhostSlotConfig::save);

        return builder.build();
    }
}
