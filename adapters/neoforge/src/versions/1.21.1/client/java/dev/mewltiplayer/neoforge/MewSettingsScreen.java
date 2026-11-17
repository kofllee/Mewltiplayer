package dev.mewltiplayer.neoforge;

import dev.mewltiplayer.core.DerpRegions;
import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.MewSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.components.tabs.TabManager;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public final class MewSettingsScreen extends Screen {
    private static final ResourceLocation CAT_FONT = ResourceLocation.fromNamespaceAndPath("mewltiplayer", "settings_cat");
    private final Screen parent;
    private final MewSettings initial;
    private final MewSettings draft;
    private boolean shareCodeTab = true;
    private boolean tabsLive;
    private TabManager tabManager;
    private boolean regionExpanded;
    private GridLayoutTab shareCodesTab;
    private List<DerpRegions.Region> regions = new ArrayList<>();
    private String loadedSource = "";
    private String error = "";
    private String notice = "";
    private String confirm = "";
    private EditBox url;
    private MultiLineEditBox json;
    private boolean pending;
    private boolean confirmDerpChange;

    public MewSettingsScreen(Screen parent) {
        super(Component.translatable("mewltiplayer.settings.title"));
        this.parent = parent;
        initial = MewRuntime.INSTANCE.settings();
        draft = initial.copy();
    }

    private Component label(String key, Object... args) {
        return Component.translatable("mewltiplayer.settings." + key, args);
    }

    @Override
    protected void init() {
        tabsLive = false;
        int x = width / 2 - 150;
        tabManager = new TabManager(w -> {}, w -> {});
        shareCodesTab = new GridLayoutTab(label("tab.codes"));
        GridLayoutTab other = new GridLayoutTab(label("tab.derp"));
        TabNavigationBar tabs = TabNavigationBar.builder(tabManager, width).addTabs(shareCodesTab, other).build();
        tabs.arrangeElements();
        addRenderableWidget(tabs);
        tabManager.setCurrentTab(shareCodeTab ? shareCodesTab : other, false);
        tabsLive = true;
        Button docs = addRenderableWidget(Button.builder(Component.empty(), w ->
                net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(
                        this, java.net.URI.create("https://github.com/tailscale/tailcat/blob/main/README.md")))
                .bounds(x - 26, height - 27, 20, 20).build());
        docs.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("docs_tooltip")));
        if (shareCodeTab) renderShareCodes(x);
        else renderDerp(x);
        int footerY = height - 27;
        Button save = addRenderableWidget(Button.builder(label("save"), w -> save()).bounds(x, footerY, 146, 20).build());
        Button back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), w -> onClose())
                .bounds(x + 154, footerY, 146, 20).build());
        if (regionExpanded && !shareCodeTab) addRegionList(x);
    }

    private void switchPage(boolean shareCode) {
        captureInput();
        shareCodeTab = shareCode;
        regionExpanded = false;
        error = "";
        notice = "";
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (tabsLive && tabManager != null) {
            boolean selected = tabManager.getCurrentTab() == shareCodesTab;
            if (selected != shareCodeTab) switchPage(selected);
        }
    }

    private void renderShareCodes(int x) {
        Button shareCodeModeButton = addRenderableWidget(Button.builder(label("share_code_mode." + draft.shareCodeMode), w -> {
            draft.shareCodeMode = MewSettings.NEW.equals(draft.shareCodeMode) ? MewSettings.WORLD
                    : MewSettings.WORLD.equals(draft.shareCodeMode) ? MewSettings.GLOBAL : MewSettings.NEW;
            confirm = "";
            notice = "";
            rebuildWidgets();
        }).bounds(x, 78, 300, 20).build());
        shareCodeModeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("share_code_help." + draft.shareCodeMode)));

        String code = shareCodePreview();
        EditBox preview = new EditBox(font, x, 127, 222, 20, label("code"));
        preview.setMaxLength(8192);
        preview.setValue(code == null ? (MewSettings.WORLD.equals(draft.shareCodeMode) && world() == null
                ? label("code_world_required").getString() : label("code_none").getString()) : code);
        preview.setEditable(false);
        addRenderableWidget(preview);
        Button copy = addRenderableWidget(Button.builder(label("copy"), w ->
                Minecraft.getInstance().keyboardHandler.setClipboard(code))
                .bounds(x + 228, 127, 72, 20).build());
        copy.active = code != null;

        Button reset = addRenderableWidget(Button.builder(label("reset"), w -> resetSelected())
                .bounds(x, 159, 146, 20).build());
        reset.active = !MewSettings.NEW.equals(draft.shareCodeMode)
                && MewShareCodes.identity(draft.shareCodeMode, world(), MewRuntime.INSTANCE.configDirectory()) != null;
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.reset")));
        Button resetAll = addRenderableWidget(Button.builder(label("reset_all"), w -> resetAll())
                .bounds(x + 154, 159, 146, 20).build());
        resetAll.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.reset_all")));
    }

    private void renderDerp(int x) {
        Button mode = addRenderableWidget(Button.builder(label("mode." + draft.derpMode), w -> {
            captureInput();
            draft.derpMode = MewSettings.AUTO.equals(draft.derpMode) ? MewSettings.MAP
                    : MewSettings.MAP.equals(draft.derpMode) ? MewSettings.URL : MewSettings.AUTO;
            draft.regionId = 0;
            loadedSource = "";
            regions.clear();
            regionExpanded = false;
            error = "";
            notice = "";
            rebuildWidgets();
        }).bounds(x, 78, 146, 20).build());
        mode.active = !regionExpanded;
        mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.derp_mode")));
        if (regionExpanded) {
            // Region list occupies the content area; no fields sit behind it.
        } else if (MewSettings.URL.equals(draft.derpMode)) {
            url = new EditBox(font, x, 127, 300, 20, label("url"));
            url.setMaxLength(1024);
            url.setValue(draft.derpMapUrl);
            url.setResponder(value -> {
                draft.derpMapUrl = value;
                loadedSource = "";
                regions.clear();
                draft.regionId = 0;
                regionExpanded = false;
            });
            url.active = !regionExpanded;
            addRenderableWidget(url);

        } else if (MewSettings.MAP.equals(draft.derpMode)) {
            int editorHeight = Math.max(48, height - 176);
            json = new MultiLineEditBox(font, x, 127, 300, editorHeight, label("json"), label("json_placeholder"));
            json.setCharacterLimit(262144);
            json.setValue(draft.derpMapJson);
            json.setValueListener(value -> {
                draft.derpMapJson = value;
                loadedSource = "";
                regions.clear();
                draft.regionId = 0;
                regionExpanded = false;
            });
            json.active = !regionExpanded;
            addRenderableWidget(json);

        }
        Button choose = addRenderableWidget(Button.builder(label("region_value", currentRegion()), w -> openRegions())
                .bounds(x + 154, 78, 146, 20).build());
        choose.active = !pending && (regionExpanded || !MewSettings.MAP.equals(draft.derpMode)
                || !draft.derpMapJson.isEmpty());
        choose.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.region")));
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
        addRenderableWidget(list);
    }

    private final class RegionList extends ObjectSelectionList<RegionRow> {
        RegionList(int x, int y, int width, int height) {
            super(Minecraft.getInstance(), width, height, y, 22);
            setX(x);
            centerListVertically = false;
        }
        @Override public int getRowWidth() { return 284; }
        void addOption(int id, String name) { addEntry(new RegionRow(id, name)); }
    }

    private final class RegionRow extends ObjectSelectionList.Entry<RegionRow> {
        private final int id;
        private final String name;
        RegionRow(int id, String name) { this.id = id; this.name = name; }
        @Override public Component getNarration() { return Component.literal(name); }
        @Override public void render(GuiGraphics g, int index, int top, int left, int rowWidth,
                                     int rowHeight, int mx, int my, boolean hover, float dt) {
            g.drawString(font, (draft.regionId == id ? "* " : "  ") + name,
                    left + 4, top + 3, 0xffffffff);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            selectRegion(id);
            return true;
        }
    }

    private void selectRegion(int id) {
        draft.regionId = id;
        regionExpanded = false;
        error = "";
        notice = "";
        rebuildWidgets();
    }

    private void openRegions() {
        captureInput();
        if (regionExpanded) {
            regionExpanded = false;
            rebuildWidgets();
            return;
        }
        String source = MewSettings.MAP.equals(draft.derpMode) ? draft.derpMapJson
                : MewSettings.URL.equals(draft.derpMode) ? draft.derpMapUrl : MewSettings.DEFAULT_MAP;
        if (source.isEmpty()) { error = label("url_required").getString(); return; }
        if (source.equals(loadedSource) && !regions.isEmpty()) {
            regionExpanded = true;
            rebuildWidgets();
            return;
        }
        if (MewSettings.MAP.equals(draft.derpMode)) {
            try {
                regions = DerpRegions.parse(source);
                loadedSource = source;
                regionExpanded = true;
                error = "";
                rebuildWidgets();
            } catch (RuntimeException ex) {
                error = label("invalid_map").getString();
            }
            return;
        }
        if (pending) return;
        pending = true;
        notice = label("loading").getString();
        error = "";
        rebuildWidgets();
        final String requestedSource = source;
        new Thread(() -> {
            List<DerpRegions.Region> result = null;
            String failure = null;
            try { result = DerpRegions.load(requestedSource); }
            catch (Exception ex) { failure = ex.getMessage(); }
            final List<DerpRegions.Region> loaded = result;
            final String problem = failure;
            Minecraft.getInstance().execute(() -> {
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
                rebuildWidgets();
            });
        }, "mewltiplayer-derp-regions").start();
    }

    private void captureInput() {
        if (url != null) draft.derpMapUrl = url.getValue().trim();
        if (json != null) draft.derpMapJson = json.getValue();
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
                .whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
                    if (failure == null) {
                        notice = label("reset_done").getString();
                        error = "";
                    } else {
                        notice = "";
                        error = label("invalid").getString();
                    }
                    rebuildWidgets();
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
                Minecraft.getInstance().gameDirectory.toPath().resolve("saves"), true)
                .whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
                    if (failure == null) {
                        notice = label("reset_done").getString();
                        error = "";
                    } else {
                        notice = "";
                        error = label("invalid").getString();
                    }
                    rebuildWidgets();
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
            onClose();
        } catch (Exception exception) {
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (regionExpanded && key == 256) {
            regionExpanded = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.renderItem(Items.KNOWLEDGE_BOOK.getDefaultInstance(), width / 2 - 174, height - 25);
        int x = width / 2 - 150;
        graphics.drawCenteredString(font, Component.empty()
                .append(Component.literal("\uE000").withStyle(style -> style.withFont(CAT_FONT)))
                .append(Component.literal("  Mewltiplayer")), width / 2, 36, 0xffffffff);
        if (regionExpanded && !shareCodeTab) {
            graphics.drawString(font, label("region_picker_title"), x + 2, 111, 0xffaaaaaa);
        } else {
            if (shareCodeTab) {
                graphics.drawString(font, label("share_code_label"), x + 2, 64, 0xffaaaaaa);
                graphics.drawString(font, label("code"), x + 2, 112, 0xffaaaaaa);
            } else {
                graphics.drawString(font, label("derp_label"), x + 2, 64, 0xffaaaaaa);
                graphics.drawString(font, label("region_label"), x + 156, 64, 0xffaaaaaa);
                if (MewSettings.AUTO.equals(draft.derpMode)) {
                    graphics.drawString(font, label("derp_help.auto"), x + 2, 111, 0xff999999);
                }
                if (MewSettings.URL.equals(draft.derpMode)) {
                    graphics.drawString(font, label("url"), x + 2, 111, 0xffaaaaaa);
                } else if (MewSettings.MAP.equals(draft.derpMode)) {
                    graphics.drawString(font, label("json"), x + 2, 111, 0xffaaaaaa);
                }
            }
        }
        if (!error.isEmpty()) graphics.drawCenteredString(font, Component.literal(error), width / 2, height - 39, 0xffff6666);
        else if (!notice.isEmpty()) graphics.drawCenteredString(font, Component.literal(notice), width / 2, height - 39, 0xffaaaaaa);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
