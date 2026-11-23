package dev.mewltiplayer.neoforge;

import dev.mewltiplayer.core.DerpRegions;
import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.MewSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class MewSettingsScreen extends Screen {
    private static final FontDescription.Resource CAT_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mewltiplayer", "settings_cat"));
    private final Screen parent;
    private final MewSettings initial;
    private final MewSettings draft;
    private boolean shareCodeTab = true;
    private boolean tabsLive;
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
    private boolean confirmLiveSettings;
    private boolean confirmNetworkChange;
    private Button regionButton;
    private boolean regionMetadataRequested;
    private Button saveButton;
    private EditBox shareCodeField;
    private Button copyCodeButton;
    private Button resetCodeButton;
    private String shownShareCode;
    private long regionTextStart = System.nanoTime();

    public MewSettingsScreen(Screen parent) {
        super(Component.translatable("mewltiplayer.settings.title"));
        this.parent = parent;
        initial = MewRuntime.INSTANCE.settings();
        draft = initial.copy();
    }

    private Component label(String key, Object... args) {
        return Component.translatable("mewltiplayer.settings." + key, args);
    }

    private boolean hasChanges() {
        return !Objects.equals(initial.shareCodeMode, draft.shareCodeMode)
                || initial.showShareCodeInChat != draft.showShareCodeInChat
                || !Objects.equals(initial.derpMode, draft.derpMode)
                || !Objects.equals(initial.derpMapUrl, draft.derpMapUrl.trim())
                || !Objects.equals(initial.derpMapJson, draft.derpMapJson)
                || initial.regionId != draft.regionId;
    }

    private void updateSaveButton() {
        if (saveButton != null) saveButton.active = !pending && hasChanges();
    }

    private void drawCaption(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(font, text, x, y, 0xffaaaaaa);
    }

    private void drawMessage(GuiGraphicsExtractor graphics, String message, int x, int y, int color) {
        if (message.isEmpty()) return;
        String visible = message;
        if (font.width(visible) > 296) {
            visible = font.plainSubstrByWidth(visible, 286) + "…";
        }
        graphics.text(font, visible, x, y, color);
    }

    @Override
    protected void init() {
        tabsLive = false;
        saveButton = null;
        shareCodeField = null;
        copyCodeButton = null;
        resetCodeButton = null;
        int x = width / 2 - 150;
        regionButton = null;
        if (confirmLiveSettings) {
            addRenderableWidget(Button.builder(label("apply_restart"), w -> applyAndRestart())
                    .bounds(x, height / 2 + 22, 146, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), w -> cancelApply())
                    .bounds(x + 154, height / 2 + 22, 146, 20).build());
            return;
        }
        if (confirmDerpChange) {
            addRenderableWidget(Button.builder(label("confirm_save"), w -> confirmDerpSave())
                    .bounds(x, height / 2 + 22, 146, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), w -> cancelDerpSave())
                    .bounds(x + 154, height / 2 + 22, 146, 20).build());
            return;
        }
        if (!confirm.isEmpty()) {
            addRenderableWidget(Button.builder(label("reset"), w -> confirmReset())
                    .bounds(x, height / 2 + 22, 146, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), w -> cancelReset())
                    .bounds(x + 154, height / 2 + 22, 146, 20).build());
            return;
        }
        TabManager manager = new TabManager(w -> {}, w -> {},
                tab -> { if (tabsLive) switchPage(tab == shareCodesTab); }, tab -> {});
        shareCodesTab = new GridLayoutTab(label("tab.codes"));
        GridLayoutTab other = new GridLayoutTab(label("tab.derp"));
        MenuTabBar tabs = MenuTabBar.builder(manager, width).addTabs(shareCodesTab, other).build();
        tabs.arrangeElements(width);
        addRenderableWidget(tabs);
        manager.setCurrentTab(shareCodeTab ? shareCodesTab : other, false);
        tabsLive = true;
        Button docs = addRenderableWidget(Button.builder(Component.empty(), w ->
                net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(
                        this, java.net.URI.create("https://github.com/tailscale/tailcat/blob/main/README.md")))
                .bounds(x - 26, height - 27, 20, 20).build());
        docs.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("docs_tooltip")));
        if (shareCodeTab) renderShareCodes(x);
        else renderDerp(x);
        int footerY = height - 27;
        saveButton = addRenderableWidget(Button.builder(label("save"), w -> save()).bounds(x, footerY, 146, 20).build());
        updateSaveButton();
        Button back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), w -> onClose())
                .bounds(x + 154, footerY, 146, 20).build());
        if (regionExpanded && !shareCodeTab) addRegionList(x);
        if (!shareCodeTab) restoreRegionName();
    }

    private void switchPage(boolean shareCode) {
        captureInput();
        shareCodeTab = shareCode;
        regionExpanded = false;
        error = "";
        notice = "";
        rebuildWidgets();
    }

    private void renderShareCodes(int x) {
        Button shareCodeModeButton = addRenderableWidget(Button.builder(label("share_code_mode." + draft.shareCodeMode), w -> {
            draft.shareCodeMode = MewSettings.NEW.equals(draft.shareCodeMode) ? MewSettings.WORLD
                    : MewSettings.WORLD.equals(draft.shareCodeMode) ? MewSettings.GLOBAL : MewSettings.NEW;
            confirm = "";
            confirmLiveSettings = false;
            notice = "";
            rebuildWidgets();
        }).bounds(x, 78, 200, 20).build());
        shareCodeModeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("share_code_help." + draft.shareCodeMode)));
        Button codeChat = addRenderableWidget(Button.builder(label("show_code_chat." + (draft.showShareCodeInChat ? "on" : "off")), w -> {
            draft.showShareCodeInChat = !draft.showShareCodeInChat;
            notice = "";
            rebuildWidgets();
        }).bounds(x + 208, 78, 92, 20).build());
        codeChat.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("show_code_chat_tooltip")));

        String code = shareCodePreview();
        shownShareCode = code;
        shareCodeField = new EditBox(font, x, 127, 222, 20, label("code"));
        shareCodeField.setMaxLength(8192);
        shareCodeField.setValue(code == null ? (MewSettings.WORLD.equals(draft.shareCodeMode) && world() == null
                ? label("code_world_required").getString() : label("code_none").getString()) : code);
        shareCodeField.setEditable(false);
        addRenderableWidget(shareCodeField);
        copyCodeButton = addRenderableWidget(Button.builder(label("copy"), w -> {
            if (shownShareCode != null) Minecraft.getInstance().keyboardHandler.setClipboard(shownShareCode);
        })
                .bounds(x + 228, 127, 72, 20).build());
        copyCodeButton.active = code != null;

        resetCodeButton = addRenderableWidget(Button.builder(label("reset"), w -> resetSelected())
                .bounds(x, 159, 146, 20).build());
        resetCodeButton.active = canResetCode();
        resetCodeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.reset")));
        Button resetAll = addRenderableWidget(Button.builder(label("reset_all"), w -> resetAll())
                .bounds(x + 154, 159, 146, 20).build());
        resetAll.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.reset_all")));
    }

    public void refreshShareCode() {
        if (!shareCodeTab || shareCodeField == null) return;
        String code = shareCodePreview();
        if (!Objects.equals(code, shownShareCode)) {
            shownShareCode = code;
            shareCodeField.setValue(code == null ? (MewSettings.WORLD.equals(draft.shareCodeMode) && world() == null
                    ? label("code_world_required").getString() : label("code_none").getString()) : code);
            copyCodeButton.active = code != null;
        }
        resetCodeButton.active = canResetCode();
    }

    @Override
    public void tick() {
        super.tick();
        refreshShareCode();
    }

    private void renderDerp(int x) {
        Button mode = addRenderableWidget(Button.builder(label("mode." + draft.derpMode), w -> {
            if (pending) return;
            captureInput();
            draft.derpMode = MewSettings.AUTO.equals(draft.derpMode) ? MewSettings.MAP
                    : MewSettings.MAP.equals(draft.derpMode) ? MewSettings.URL : MewSettings.AUTO;
            draft.regionId = 0;
            confirmLiveSettings = false;
            loadedSource = "";
            regions.clear();
            regionExpanded = false;
            error = "";
            notice = "";
            rebuildWidgets();
        }).bounds(x, 78, 146, 20).build());
        mode.active = !regionExpanded && !pending;
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
                confirmLiveSettings = false;
                regionExpanded = false;
                updateSaveButton();
            });
            url.active = !regionExpanded;
            addRenderableWidget(url);

        } else if (MewSettings.MAP.equals(draft.derpMode)) {
            int editorHeight = Math.max(48, height - 176);
            json = MultiLineEditBox.builder().setX(x).setY(127)
                    .setPlaceholder(label("json_placeholder")).build(font, 300, editorHeight, label("json"));
            json.setCharacterLimit(262144);
            json.setValue(draft.derpMapJson);
            json.setValueListener(value -> {
                draft.derpMapJson = value;
                loadedSource = "";
                regions.clear();
                draft.regionId = 0;
                confirmLiveSettings = false;
                regionExpanded = false;
                updateSaveButton();
            });
            json.active = !regionExpanded;
            addRenderableWidget(json);

        }
        regionButton = addRenderableWidget(Button.builder(Component.empty(), w -> openRegions())
                .bounds(x + 154, 78, 146, 20).build());
        regionButton.active = !pending;
        regionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("tip.region")));
        regionTextStart = System.nanoTime();
    }

    private String currentRegion() {
        if (draft.regionId == 0) return label("region_auto").getString();
        for (DerpRegions.Region r : regions) if (r.id == draft.regionId) return r.label();
        return "Region " + draft.regionId;
    }

    private void restoreRegionName() {
        if (regionMetadataRequested || draft.regionId == 0 || !regions.isEmpty()) return;
        regionMetadataRequested = true;
        String source = MewSettings.MAP.equals(draft.derpMode) ? draft.derpMapJson
                : MewSettings.URL.equals(draft.derpMode) ? draft.derpMapUrl : MewSettings.DEFAULT_MAP;
        if (source.isBlank()) return;
        if (MewSettings.MAP.equals(draft.derpMode)) {
            try {
                regions = DerpRegions.parse(source);
                loadedSource = source;
            } catch (RuntimeException ignored) {
                // Keep the saved region ID if its map cannot be read.
            }
            return;
        }
        new Thread(() -> {
            List<DerpRegions.Region> loaded;
            try { loaded = DerpRegions.load(source); }
            catch (Exception ignored) { return; }
            Minecraft.getInstance().execute(() -> {
                String current = MewSettings.MAP.equals(draft.derpMode) ? draft.derpMapJson
                        : MewSettings.URL.equals(draft.derpMode) ? draft.derpMapUrl : MewSettings.DEFAULT_MAP;
                if (source.equals(current) && regions.isEmpty()) {
                    regions = loaded;
                    loadedSource = source;
                    regionTextStart = System.nanoTime();
                }
            });
        }, "mewltiplayer-region-name").start();
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
        @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hover, float dt) {
            g.text(font, (draft.regionId == id ? "* " : "  ") + name,
                    getContentX() + 4, getContentY() + 3, 0xffffffff);
        }
        @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean twice) {
            selectRegion(id);
            return true;
        }
    }

    private void selectRegion(int id) {
        draft.regionId = id;
        confirmLiveSettings = false;
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
        if (source.isBlank()) {
            error = label(MewSettings.MAP.equals(draft.derpMode) ? "json_required" : "url_required").getString();
            return;
        }
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
        final String requestedMode = draft.derpMode;
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
                if (requestedMode.equals(draft.derpMode) && requestedSource.equals(current)) {
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

    private boolean hasSavedIdentity() {
        Path identity = MewShareCodes.identity(draft.shareCodeMode, world(), MewRuntime.INSTANCE.configDirectory());
        return identity != null && java.nio.file.Files.isRegularFile(identity);
    }

    private boolean canResetCode() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        return !runtime.resetInProgress() && ((runtime.sharing() && runtime.onlineSharingEnabled())
                || (!MewSettings.NEW.equals(draft.shareCodeMode) && hasSavedIdentity()));
    }

    private String shareCodePreview() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (runtime.resetInProgress()) return null;
        // While sharing, show the actual active code, not the unsaved draft mode's cached code.
        if (runtime.onlineSharingEnabled() && MewClientCompat.published())
            return runtime.shareCode();
        return MewShareCodes.preview(draft.shareCodeMode, world(), runtime.configDirectory());
    }

    private void resetSelected() {
        confirm = "selected";
        rebuildWidgets();
    }

    private void resetAll() {
        confirm = "all";
        rebuildWidgets();
    }

    private void cancelReset() {
        confirm = "";
        rebuildWidgets();
    }

    private void confirmReset() {
        boolean all = "all".equals(confirm);
        if (!all && !"selected".equals(confirm)) return;
        MewRuntime runtime = MewRuntime.INSTANCE;
        boolean activeTailcat = runtime.onlineSharingEnabled() && runtime.sharing();
        // Reset uses the selected mode. Persist this choice before restarting the active share,
        // without implicitly applying other unsaved settings (such as DERP changes).
        String mode = draft.shareCodeMode;
        if (activeTailcat && !runtime.settings().shareCodeMode.equals(mode)) {
            MewSettings updated = runtime.settings();
            updated.shareCodeMode = mode;
            try {
                runtime.saveSettings(updated);
                initial.shareCodeMode = mode;
            } catch (Exception exception) {
                confirm = "";
                error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
                notice = "";
                rebuildWidgets();
                return;
            }
        }
        confirm = "";
        notice = label("loading").getString();
        Path saves = all ? Minecraft.getInstance().gameDirectory.toPath().resolve("saves") : null;
        runtime.resetShareCode(mode, world(), saves, all)
                .whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
                    if (failure == null) {
                        if (activeTailcat) MewClientCompat.message("mewltiplayer.chat.disconnected", false);
                        notice = activeTailcat ? "" : label("reset_done_hint").getString();
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
        if (pending || !hasChanges()) return;
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
            boolean shareModeChanged = !initial.shareCodeMode.equals(draft.shareCodeMode);
            if ((runtime.sharing() || runtime.joining()) && (networkChanged || shareModeChanged)) {
                confirmLiveSettings = true;
                confirmNetworkChange = networkChanged;
                error = "";
                notice = "";
                rebuildWidgets();
                return;
            }
            if (networkChanged) {
                confirmDerpChange = true;
                error = "";
                notice = "";
                rebuildWidgets();
                return;
            }
            MewRuntime.INSTANCE.saveSettings(draft);
            onClose();
        } catch (Exception exception) {
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
        }
    }

    private void cancelApply() {
        confirmLiveSettings = false;
        confirmNetworkChange = false;
        rebuildWidgets();
    }

    private void cancelDerpSave() {
        confirmDerpChange = false;
        rebuildWidgets();
    }

    private void confirmDerpSave() {
        try {
            MewRuntime.INSTANCE.saveSettings(draft);
            onClose();
        } catch (Exception exception) {
            confirmDerpChange = false;
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
            rebuildWidgets();
        }
    }

    private void applyAndRestart() {
        try {
            draft.validate();
            MewRuntime runtime = MewRuntime.INSTANCE;
            boolean sharing = runtime.sharing() && runtime.onlineSharingEnabled();
            boolean joining = runtime.joining();
            runtime.saveSettings(draft);
            if (sharing) {
                runtime.restartShare();
            }
            if (joining) {
                MewClientActions.disconnect();
                if (Minecraft.getInstance().getConnection() != null)
                    Minecraft.getInstance().getConnection().getConnection().disconnect(label("restart_disconnect"));
            }
            onClose();
        } catch (Exception exception) {
            confirmLiveSettings = false;
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
            rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!confirm.isEmpty() && event.key() == 256) {
            cancelReset();
            return true;
        }
        if (confirmLiveSettings && event.key() == 256) {
            cancelApply();
            return true;
        }
        if (confirmDerpChange && event.key() == 256) {
            cancelDerpSave();
            return true;
        }
        if (regionExpanded && event.key() == 256) {
            regionExpanded = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (confirmLiveSettings) {
            graphics.centeredText(font, label("restart_title"), width / 2, height / 2 - 30, 0xffffffff);
            if (confirmNetworkChange) {
                graphics.centeredText(font, label("derp_code_warning"), width / 2, height / 2 - 12, 0xffffaa55);
                graphics.centeredText(font, label("restart_warning"), width / 2, height / 2 + 2, 0xffaaaaaa);
            } else {
                graphics.centeredText(font, label("restart_warning"), width / 2, height / 2 - 10, 0xffaaaaaa);
            }
            return;
        }
        if (confirmDerpChange) {
            graphics.centeredText(font, label("derp_change_title"), width / 2, height / 2 - 30, 0xffffffff);
            graphics.centeredText(font, label("derp_code_warning"), width / 2, height / 2 - 10, 0xffffaa55);
            return;
        }
        if (!confirm.isEmpty()) {
            boolean all = "all".equals(confirm);
            graphics.centeredText(font, label(all ? "reset_all_title" : "reset_title"),
                    width / 2, height / 2 - 38, 0xffffffff);
            graphics.centeredText(font, label(all ? "reset_all_warning" : "reset_warning"),
                    width / 2, height / 2 - 18, 0xffaaaaaa);
            if (MewRuntime.INSTANCE.sharing() && MewRuntime.INSTANCE.onlineSharingEnabled()) {
                graphics.centeredText(font, label("reset_active_warning"),
                        width / 2, height / 2 - 5, 0xffaaaaaa);
            }
            return;
        }
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                Identifier.withDefaultNamespace("textures/item/knowledge_book.png"),
                width / 2 - 174, height - 25, 0.0f, 0.0f, 16, 16, 16, 16);
        int x = width / 2 - 150;
        if (!shareCodeTab && regionButton != null) {
            String regionName = currentRegion();
            int textWidth = font.width(regionName);
            int availableWidth = regionButton.getWidth() - 12;
            int overflow = Math.max(0, textWidth - availableWidth);
            int offset = 0;
            if (overflow > 0) {
                double seconds = (System.nanoTime() - regionTextStart) / 1_000_000_000.0;
                double travel = overflow / 22.0;
                double cycle = 2.0 * (travel + 1.2);
                double phase = seconds % cycle;
                if (phase < 1.2) offset = 0;
                else if (phase < 1.2 + travel) offset = (int) Math.round((phase - 1.2) * 22.0);
                else if (phase < 2.4 + travel) offset = overflow;
                else offset = (int) Math.round(overflow - (phase - 2.4 - travel) * 22.0);
            }
            graphics.enableScissor(regionButton.getX() + 6, regionButton.getY() + 2,
                    regionButton.getX() + regionButton.getWidth() - 6,
                    regionButton.getY() + regionButton.getHeight() - 2);
            graphics.text(font, regionName,
                    regionButton.getX() + 6 + (overflow == 0 ? (availableWidth - textWidth) / 2 : 0) - offset,
                    regionButton.getY() + (regionButton.getHeight() - 8) / 2, 0xffffffff);
            graphics.disableScissor();
        }
        graphics.centeredText(font,Component.empty()
                .append(Component.literal("\uE000").withStyle(style -> style.withFont(CAT_FONT)))
                .append(Component.literal("  Mewltiplayer")), width / 2, 36, 0xffffffff);
        if (regionExpanded && !shareCodeTab) {
            drawCaption(graphics, label("region_picker_title"), x + 2, 111);
        } else {
            if (shareCodeTab) {
                drawCaption(graphics, label("share_code_label"), x + 2, 64);
            } else {
                drawCaption(graphics, label("derp_label"), x + 2, 64);
                drawCaption(graphics, label("region_label"), x + 156, 64);
                if (MewSettings.AUTO.equals(draft.derpMode)) {
                    drawCaption(graphics, label("derp_help.auto"), x + 2, 111);
                }
                if (MewSettings.URL.equals(draft.derpMode)) {
                    drawCaption(graphics, label("url"), x + 2, 111);
                } else if (MewSettings.MAP.equals(draft.derpMode)) {
                    drawCaption(graphics, label("json"), x + 2, 111);
                }
            }
        }
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (!error.isEmpty()) drawMessage(graphics, error, x + 2, height - 39, 0xffff6666);
        else if (!notice.isEmpty()) drawMessage(graphics, notice, x + 2, height - 39, 0xffaaaaaa);
        else if (shareCodeTab && (runtime.sharing() || runtime.joining()
                || (MewClientCompat.published() && runtime.onlineSharingEnabled()))) {
            String status = runtime.resetInProgress() ? "session_restarting"
                    : runtime.sharing() ? "session_sharing" : runtime.joining() ? "session_joining"
                    : "session_starting";
            drawMessage(graphics, label(status).getString(), x + 2, height - 39,
                    "session_sharing".equals(status) ? 0xff55ff55 : 0xffaaaaaa);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
