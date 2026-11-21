package dev.urinth.sunflare;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.caffeinemc.mods.sodium.client.gui.options.OptionFlag;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpact;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;

/**
 * The "URinth" page of Sodium's video settings: one category that holds every setting of this mod, as Sodium's own rows, so it has
 * the same look, width and tooltips as the other pages (and as Reese's Sodium Options, which draws the same pages in a list).
 * This is the only class that uses Sodium's option classes; it is loaded the first time Sodium makes its screen, never when Sodium
 * is not installed. What each row offers is in {@link PageChoices}.
 */
public final class UrinthPage {
    private UrinthPage() {}

    /** Makes the page for this mod's settings. Changes are written to the settings file when the player presses Apply. */
    public static OptionPage build(FlareConfig config) {
        OptionStorage<FlareConfig> storage = new OptionStorage<>() {
            @Override
            public FlareConfig getData() {
                return config;
            }

            @Override
            public void save() {
                config.save();
            }
        };

        OptionImpl<FlareConfig, PageChoices.Flare> flare = OptionImpl.createBuilder(PageChoices.Flare.class, storage)
            .setName(Component.literal(PageChoices.FLARE_NAME))
            .setTooltip(Component.literal(PageChoices.FLARE_TIP))
            .setControl(option -> new CyclingControl<>(option, PageChoices.Flare.class, names(PageChoices.Flare.values())))
            .setBinding((c, v) -> c.setMode(v.ordinal()), c -> PageChoices.Flare.of(c.mode()))
            .setImpact(OptionImpact.LOW)
            .build();

        OptionImpl<FlareConfig, PageChoices.Beams> beams = OptionImpl.createBuilder(PageChoices.Beams.class, storage)
            .setName(Component.literal(PageChoices.BEAMS_NAME))
            .setTooltip(Component.literal(PageChoices.BEAMS_TIP))
            .setControl(option -> new CyclingControl<>(option, PageChoices.Beams.class, names(PageChoices.Beams.values())))
            .setBinding((c, v) -> c.setBeams(v.ordinal()), c -> PageChoices.Beams.of(c.beams()))
            .setImpact(OptionImpact.VARIES)
            .build();

        OptionImpl<FlareConfig, PageChoices.Style> style = OptionImpl.createBuilder(PageChoices.Style.class, storage)
            .setName(Component.literal(PageChoices.STYLE_NAME))
            .setTooltip(Component.literal(PageChoices.STYLE_TIP))
            .setControl(option -> new CyclingControl<>(option, PageChoices.Style.class, names(PageChoices.Style.values())))
            .setBinding((c, v) -> c.setRealStyle(v.isReal()), c -> PageChoices.Style.of(c.realStyle()))
            .build();

        OptionImpl<FlareConfig, PageChoices.Leaves> leaves = OptionImpl.createBuilder(PageChoices.Leaves.class, storage)
            .setName(Component.literal(PageChoices.LEAVES_NAME))
            .setTooltip(Component.literal(PageChoices.LEAVES_TIP))
            .setControl(option -> new CyclingControl<>(option, PageChoices.Leaves.class, names(PageChoices.Leaves.values())))
            .setBinding((c, v) -> {
                c.setLeaves(v.ordinal());
                LeafCull.setDepth(c.leaves());
            }, c -> PageChoices.Leaves.of(c.leaves()))
            .setImpact(OptionImpact.LOW)
            // the chunks are built with the old level until Sodium reloads them
            .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
            .build();

        OptionImpl<FlareConfig, PageChoices.Guard> guard = OptionImpl.createBuilder(PageChoices.Guard.class, storage)
            .setName(Component.literal(PageChoices.GUARD_NAME))
            .setTooltip(Component.literal(PageChoices.GUARD_TIP))
            .setControl(option -> new CyclingControl<>(option, PageChoices.Guard.class, names(PageChoices.Guard.values())))
            .setBinding((c, v) -> c.setTargetFps(v.fps()), c -> PageChoices.Guard.nearest(c.targetFps()))
            .build();

        OptionImpl<FlareConfig, Boolean> log = OptionImpl.createBuilder(boolean.class, storage)
            .setName(Component.literal(PageChoices.LOG_NAME))
            .setTooltip(Component.literal(PageChoices.LOG_TIP))
            .setControl(TickBoxControl::new)
            .setBinding((c, v) -> c.setPerfLog(v), FlareConfig::perfLog)
            .build();

        List<OptionGroup> groups = List.of(
            OptionGroup.createBuilder().add(flare).add(beams).add(style).build(),
            OptionGroup.createBuilder().add(leaves).build(),
            OptionGroup.createBuilder().add(guard).add(log).build());
        return new OptionPage(Component.literal(PageChoices.PAGE_NAME), ImmutableList.copyOf(groups));
    }

    private static Component[] names(PageChoices.Labeled[] choices) {
        Component[] names = new Component[choices.length];
        for (int i = 0; i < choices.length; i++) names[i] = Component.literal(choices[i].label());
        return names;
    }
}
