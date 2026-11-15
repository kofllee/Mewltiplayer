package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.DerpRegions;
import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.MewSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.TabNavigationWidget;
import net.minecraft.client.gui.tab.TabManager;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import net.minecraft.util.Identifier;

public final class MewSettingsScreen extends Screen {
    private static final Identifier CAT_FONT = Identifier.of("mewltiplayer", "settings_cat");
    private final Screen parent;
    private final MewSettings initial;
    private final MewSettings draft;
    private boolean shareCodeTab = true;
    private boolean tabsLive;
    private boolean regionExpanded;
    private GridScreenTab shareCodesTab;
    private List<DerpRegions.Region> regions = new ArrayList<>();
    private String loadedSource = "";
    private String error = "";
    private String notice = "";
    private String confirm = "";
    private TextFieldWidget url;
    private EditBoxWidget json;
    private boolean pending;
    private boolean confirmDerpChange;

    public MewSettingsScreen(Screen parent) {
        super(Text.translatable("mewltiplayer.settings.title"));
        this.parent = parent;
        initial = MewRuntime.INSTANCE.settings();
        draft = initial.copy();
    }

    private Text label(String key, Object... args) {
        return Text.translatable("mewltiplayer.settings." + key, args);
    }

    @Override
    protected void init() {
        tabsLive = false;
        int x = width / 2 - 150;
        TabManager manager = new TabManager(w -> {}, w -> {});
        shareCodesTab = new GridScreenTab(label("tab.codes")) {
            @Override
            public void refreshGrid(ScreenRect area) {
                super.refreshGrid(area);
                if (tabsLive) switchPage(true);
            }
        };
        GridScreenTab other = new GridScreenTab(label("tab.derp")) {
            @Override
            public void refreshGrid(ScreenRect area) {
                super.refreshGrid(area);
                if (tabsLive) switchPage(false);
            }
        };
        TabNavigationWidget tabs = TabNavigationWidget.builder(manager, width)
                .tabs(shareCodesTab, other).build();
        tabs.init();
        addDrawableChild(tabs);
        manager.setTabArea(new ScreenRect(0, 24, width, Math.max(20, height - 56)));
        manager.setCurrentTab(shareCodeTab ? shareCodesTab : other, false);
        tabsLive = true;
        ButtonWidget docs = addDrawableChild(ButtonWidget.builder(Text.empty(), w ->
                net.minecraft.client.gui.screen.ConfirmLinkScreen.open(
                        this, "https://github.com/tailscale/tailcat/blob/main/README.md"))
                .dimensions(x - 26, height - 27, 20, 20).build());
        docs.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("docs_tooltip")));
        if (shareCodeTab) renderShareCodes(x);
        else renderDerp(x);
        int footerY = height - 27;
        ButtonWidget save = addDrawableChild(ButtonWidget.builder(label("save"), w -> save()).dimensions(x, footerY, 146, 20).build());
        ButtonWidget back = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), w -> close())
                .dimensions(x + 154, footerY, 146, 20).build());
        if (regionExpanded && !shareCodeTab) addRegionList(x);
    }

    private void switchPage(boolean shareCode) {
        captureInput();
        shareCodeTab = shareCode;
        regionExpanded = false;
        error = "";
        notice = "";
        clearAndInit();
    }

    private void renderShareCodes(int x) {
        ButtonWidget shareCodeModeButton = addDrawableChild(ButtonWidget.builder(label("share_code_mode." + draft.shareCodeMode), w -> {
            draft.shareCodeMode = MewSettings.NEW.equals(draft.shareCodeMode) ? MewSettings.WORLD
                    : MewSettings.WORLD.equals(draft.shareCodeMode) ? MewSettings.GLOBAL : MewSettings.NEW;
            confirm = "";
            notice = "";
            clearAndInit();
        }).dimensions(x, 78, 300, 20).build());
        shareCodeModeButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("share_code_help." + draft.shareCodeMode)));

        String code = shareCodePreview();
        TextFieldWidget preview = new TextFieldWidget(textRenderer, x, 127, 222, 20, label("code"));
        preview.setMaxLength(8192);
        preview.setText(code == null ? (MewSettings.WORLD.equals(draft.shareCodeMode) && world() == null
                ? label("code_world_required").getString() : label("code_none").getString()) : code);
        preview.setEditable(false);
        addDrawableChild(preview);
        ButtonWidget copy = addDrawableChild(ButtonWidget.builder(label("copy"), w ->
                MinecraftClient.getInstance().keyboard.setClipboard(code))
                .dimensions(x + 228, 127, 72, 20).build());
        copy.active = code != null;

        ButtonWidget reset = addDrawableChild(ButtonWidget.builder(label("reset"), w -> resetSelected())
                .dimensions(x, 159, 146, 20).build());
        reset.active = !MewSettings.NEW.equals(draft.shareCodeMode)
                && MewShareCodes.identity(draft.shareCodeMode, world(), MewRuntime.INSTANCE.configDirectory()) != null;
        reset.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.reset")));
        ButtonWidget resetAll = addDrawableChild(ButtonWidget.builder(label("reset_all"), w -> resetAll())
                .dimensions(x + 154, 159, 146, 20).build());
        resetAll.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.reset_all")));
    }

    private void renderDerp(int x) {
        ButtonWidget mode = addDrawableChild(ButtonWidget.builder(label("mode." + draft.derpMode), w -> {
            captureInput();
            draft.derpMode = MewSettings.AUTO.equals(draft.derpMode) ? MewSettings.MAP
                    : MewSettings.MAP.equals(draft.derpMode) ? MewSettings.URL : MewSettings.AUTO;
            draft.regionId = 0;
            loadedSource = "";
            regions.clear();
            regionExpanded = false;
            error = "";
            notice = "";
            clearAndInit();
        }).dimensions(x, 78, 146, 20).build());
        mode.active = !regionExpanded;
        mode.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.derp_mode")));
        if (regionExpanded) {
            // Region list occupies the content area; no fields sit behind it.
        } else if (MewSettings.URL.equals(draft.derpMode)) {
            url = new TextFieldWidget(textRenderer, x, 127, 300, 20, label("url"));
            url.setMaxLength(1024);
            url.setText(draft.derpMapUrl);
            url.setChangedListener(value -> {
                draft.derpMapUrl = value;
                loadedSource = "";
                regions.clear();
                draft.regionId = 0;
                regionExpanded = false;
            });
            url.active = !regionExpanded;
            addDrawableChild(url);

        } else if (MewSettings.MAP.equals(draft.derpMode)) {
            int editorHeight = Math.max(48, height - 176);
            json = new EditBoxWidget(textRenderer, x, 127, 300, editorHeight,
                    label("json"), label("json_placeholder"));
            json.setMaxLength(262144);
            json.setText(draft.derpMapJson);
            json.setChangeListener(value -> {
                draft.derpMapJson = value;
                loadedSource = "";
                regions.clear();
                draft.regionId = 0;
                regionExpanded = false;
            });
            json.active = !regionExpanded;
            addDrawableChild(json);

        }
        ButtonWidget choose = addDrawableChild(ButtonWidget.builder(label("region_value", currentRegion()), w -> openRegions())
                .dimensions(x + 154, 78, 146, 20).build());
        choose.active = !pending && (regionExpanded || !MewSettings.MAP.equals(draft.derpMode)
                || !draft.derpMapJson.isEmpty());
        choose.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.region")));
    }

    private String currentRegion() {
        if (draft.regionId == 0) return label("region_auto").getString();
        for (DerpRegions.Region r : regions) if (r.id == draft.regionId) return r.label();
        return Integer.toString(draft.regionId);
    }

    private void addRegionList(int x) {
        RegionList list = new RegionList(x, 128, 300, Math.max(44, height - 174));
        list.addOption(0, label("region_auto").getString());
        for (DerpRegions.Region r : regions) list.addOption(r.id, r.label());
        addDrawableChild(list);
    }

    private final class RegionList extends AlwaysSelectedEntryListWidget<RegionRow> {
        RegionList(int x, int y, int width, int height) {
            super(MinecraftClient.getInstance(), width, height, y, 22);
            setX(x);
            centerListVertically = false;
        }
        @Override public int getRowWidth() { return 284; }
        void addOption(int id, String name) { addEntry(new RegionRow(id, name)); }
    }

    private final class RegionRow extends AlwaysSelectedEntryListWidget.Entry<RegionRow> {
        private final int id;
        private final String name;
        RegionRow(int id, String name) { this.id = id; this.name = name; }
        @Override public Text getNarration() { return Text.literal(name); }
        @Override public void render(DrawContext g, int index, int y, int x, int width, int entryHeight,
                                     int mx, int my, boolean hover, float delta) {
            g.drawTextWithShadow(textRenderer, (draft.regionId == id ? "* " : "  ") + name,
                    x + 4, y + 5, 0xffffffff);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            selectRegion(id);
            return true;
        }
    }

    private void selectRegion(int id) {
        draft.regionId = id;
        regionExpanded = false;
        error = "";
        notice = "";
        clearAndInit();
    }

    private void openRegions() {
        captureInput();
        if (regionExpanded) {
            regionExpanded = false;
            clearAndInit();
            return;
        }
        String source = MewSettings.MAP.equals(draft.derpMode) ? draft.derpMapJson
                : MewSettings.URL.equals(draft.derpMode) ? draft.derpMapUrl : MewSettings.DEFAULT_MAP;
        if (source.isEmpty()) { error = label("url_required").getString(); return; }
        if (source.equals(loadedSource) && !regions.isEmpty()) {
            regionExpanded = true;
            clearAndInit();
            return;
        }
        if (MewSettings.MAP.equals(draft.derpMode)) {
            try {
                regions = DerpRegions.parse(source);
                loadedSource = source;
                regionExpanded = true;
                error = "";
                clearAndInit();
            } catch (RuntimeException ex) {
                error = label("invalid_map").getString();
            }
            return;
        }
        if (pending) return;
        pending = true;
        notice = label("loading").getString();
        error = "";
        clearAndInit();
        final String requestedSource = source;
        new Thread(() -> {
            List<DerpRegions.Region> result = null;
            String failure = null;
            try { result = DerpRegions.load(requestedSource); }
            catch (Exception ex) { failure = ex.getMessage(); }
            final List<DerpRegions.Region> loaded = result;
            final String problem = failure;
            MinecraftClient.getInstance().execute(() -> {
                pending = false;
                String current = MewSettings.URL.equals(draft.derpMode) ? draft.derpMapUrl : MewSettings.DEFAULT_MAP;
                if (requestedSource.equals(current)) {
                    if (problem == null) {
                        regions = loaded;
                        loadedSource = requestedSource;
                        regionExpanded = true;
                        notice = "";
                    } else {
                        notice = "";
                        error = label("load_failed").getString() + ": " + problem;
                    }
                }
                clearAndInit();
            });
        }, "mewltiplayer-derp-regions").start();
    }

    private void captureInput() {
        if (url != null) draft.derpMapUrl = url.getText().trim();
        if (json != null) draft.derpMapJson = json.getText();
        url = null;
        json = null;
    }

    private Path world() {
        return MewClientCompat.worldDirectory();
    }

    private String shareCodePreview() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (runtime.isSharingMode(draft.shareCodeMode, world()) && runtime.shareCode() != null)
            return runtime.shareCode();
        return MewShareCodes.preview(draft.shareCodeMode, world(), runtime.configDirectory());
    }

    private void resetSelected() {
        if (!"selected".equals(confirm)) {
            confirm = "selected";
            notice = label("confirm_reset").getString();
            return;
        }
        confirm = "";
        notice = label("loading").getString();
        MewRuntime.INSTANCE.resetShareCode(draft.shareCodeMode, world(), null, false)
                .whenComplete((ignored, failure) -> MinecraftClient.getInstance().execute(() -> {
                    if (failure == null) {
                        notice = label("reset_done").getString();
                        error = "";
                    } else {
                        notice = "";
                        error = label("invalid").getString();
                    }
                    clearAndInit();
                }));
    }

    private void resetAll() {
        if (!"all".equals(confirm)) {
            confirm = "all";
            notice = label("confirm_all").getString();
            return;
        }
        confirm = "";
        notice = label("loading").getString();
        MewRuntime.INSTANCE.resetShareCode(draft.shareCodeMode, world(),
                MinecraftClient.getInstance().runDirectory.toPath().resolve("saves"), true)
                .whenComplete((ignored, failure) -> MinecraftClient.getInstance().execute(() -> {
                    if (failure == null) {
                        notice = label("reset_done").getString();
                        error = "";
                    } else {
                        notice = "";
                        error = label("invalid").getString();
                    }
                    clearAndInit();
                }));
    }

    private void save() {
        captureInput();
        try {
            draft.validate();
            if (draft.regionId > 0 && (draft.regionId != initial.regionId
                    || !initial.activeMapUrl().equals(draft.activeMapUrl())
                    || !initial.activeMapJson().equals(draft.activeMapJson()))) {
                if (!loadedSource.equals(MewSettings.MAP.equals(draft.derpMode)
                        ? draft.derpMapJson : MewSettings.URL.equals(draft.derpMode)
                        ? draft.derpMapUrl : MewSettings.DEFAULT_MAP)
                        || regions.stream().noneMatch(r -> r.id == draft.regionId)) {
                    throw new IllegalArgumentException(label("region_missing").getString());
                }
            }
            boolean networkChanged = initial.regionId != draft.regionId
                    || !initial.activeMapUrl().equals(draft.activeMapUrl())
                    || !initial.activeMapJson().equals(draft.activeMapJson());
            MewRuntime runtime = MewRuntime.INSTANCE;
            boolean hasSavedCode = MewShareCodes.preview(MewSettings.WORLD, world(), runtime.configDirectory()) != null
                    || MewShareCodes.preview(MewSettings.GLOBAL, world(), runtime.configDirectory()) != null;
            if (networkChanged && hasSavedCode && !confirmDerpChange) {
                confirmDerpChange = true;
                error = "";
                notice = label("derp_warning").getString();
                return;
            }
            MewRuntime.INSTANCE.saveSettings(draft);
            close();
        } catch (Exception exception) {
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (regionExpanded && keyCode == 256) {
            regionExpanded = false;
            clearAndInit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawItem(net.minecraft.item.Items.KNOWLEDGE_BOOK.getDefaultStack(), width / 2 - 174, height - 25);
        int x = width / 2 - 150;
        graphics.drawCenteredTextWithShadow(textRenderer,Text.empty()
                .append(Text.literal("\uE000").styled(style -> style.withFont(CAT_FONT)))
                .append(Text.literal("  Mewltiplayer")), width / 2, 36, 0xffffffff);
        if (regionExpanded && !shareCodeTab) {
            graphics.drawTextWithShadow(textRenderer, label("region_picker_title"), x + 2, 111, 0xffaaaaaa);
        } else {
            if (shareCodeTab) {
                graphics.drawTextWithShadow(textRenderer, label("share_code_label"), x + 2, 64, 0xffaaaaaa);
                graphics.drawTextWithShadow(textRenderer, label("code"), x + 2, 112, 0xffaaaaaa);
            } else {
                graphics.drawTextWithShadow(textRenderer, label("derp_label"), x + 2, 64, 0xffaaaaaa);
                graphics.drawTextWithShadow(textRenderer, label("region_label"), x + 156, 64, 0xffaaaaaa);
                if (MewSettings.AUTO.equals(draft.derpMode)) {
                    graphics.drawTextWithShadow(textRenderer, label("derp_help.auto"), x + 2, 111, 0xff999999);
                }
                if (MewSettings.URL.equals(draft.derpMode)) {
                    graphics.drawTextWithShadow(textRenderer, label("url"), x + 2, 111, 0xffaaaaaa);
                } else if (MewSettings.MAP.equals(draft.derpMode)) {
                    graphics.drawTextWithShadow(textRenderer, label("json"), x + 2, 111, 0xffaaaaaa);
                }
            }
        }
        if (!error.isEmpty()) graphics.drawCenteredTextWithShadow(textRenderer,Text.literal(error), width / 2, height - 39, 0xffff6666);
        else if (!notice.isEmpty()) graphics.drawCenteredTextWithShadow(textRenderer,Text.literal(notice), width / 2, height - 39, 0xffaaaaaa);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
