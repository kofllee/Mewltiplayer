package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.DerpRegions;
import dev.mewltiplayer.core.MewShareCodes;
import dev.mewltiplayer.core.MewSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private boolean confirmLiveSettings;
    private boolean confirmNetworkChange;
    private boolean regionMetadataRequested;
    private long regionTextStart = System.nanoTime();
    private ButtonWidget saveButton;
    private ButtonWidget regionButton;
    private ButtonWidget copyCodeButton;
    private ButtonWidget resetCodeButton;
    private TextFieldWidget shareCodeField;
    private String shownShareCode;

    public MewSettingsScreen(Screen parent) {
        super(Text.translatable("mewltiplayer.settings.title"));
        this.parent = parent;
        initial = MewRuntime.INSTANCE.settings();
        draft = initial.copy();
    }

    private Text label(String key, Object... args) {
        return Text.translatable("mewltiplayer.settings." + key, args);
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

    private String codeText(String code) {
        return code == null ? (MewSettings.WORLD.equals(draft.shareCodeMode) && world() == null
                ? label("code_world_required").getString() : label("code_none").getString()) : code;
    }

    private boolean hasSavedIdentity() {
        Path file = MewShareCodes.identity(draft.shareCodeMode, world(), MewRuntime.INSTANCE.configDirectory());
        return file != null && java.nio.file.Files.isRegularFile(file);
    }

    private boolean canResetCode() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        return !runtime.resetInProgress() && ((runtime.onlineSharingEnabled() && runtime.sharing())
                || (!MewSettings.NEW.equals(draft.shareCodeMode) && hasSavedIdentity()));
    }

    public void refreshShareCode() {
        if (!shareCodeTab || shareCodeField == null || confirmLiveSettings || confirmDerpChange || !confirm.isEmpty()) return;
        String code = shareCodePreview();
        if (!Objects.equals(code, shownShareCode)) {
            shownShareCode = code;
            shareCodeField.setText(codeText(code));
            copyCodeButton.active = code != null;
        }
        resetCodeButton.active = canResetCode();
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
                regionTextStart = System.nanoTime();
            } catch (RuntimeException ignored) { }
            return;
        }
        new Thread(() -> {
            List<DerpRegions.Region> loaded;
            try { loaded = DerpRegions.load(source); }
            catch (Exception ignored) { return; }
            MinecraftClient.getInstance().execute(() -> {
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

    @Override
    protected void init() {
        tabsLive = false;
        int x = width / 2 - 150;

        saveButton = null;
        shareCodeField = null;
        copyCodeButton = null;
        resetCodeButton = null;
        regionButton = null;
        if (confirmLiveSettings) {
            addDrawableChild(ButtonWidget.builder(label("apply_restart"), w -> applyAndRestart())
                    .dimensions(x, height / 2 + 22, 146, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), w -> cancelApply())
                    .dimensions(x + 154, height / 2 + 22, 146, 20).build());
            return;
        }
        if (confirmDerpChange) {
            addDrawableChild(ButtonWidget.builder(label("confirm_save"), w -> confirmDerpSave())
                    .dimensions(x, height / 2 + 22, 146, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), w -> cancelDerpSave())
                    .dimensions(x + 154, height / 2 + 22, 146, 20).build());
            return;
        }
        if (!confirm.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(label("reset"), w -> confirmReset())
                    .dimensions(x, height / 2 + 22, 146, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), w -> cancelReset())
                    .dimensions(x + 154, height / 2 + 22, 146, 20).build());
            return;
        }

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
        saveButton = addDrawableChild(ButtonWidget.builder(label("save"), w -> save()).dimensions(x, footerY, 146, 20).build());
        updateSaveButton();
        ButtonWidget back = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), w -> close())
                .dimensions(x + 154, footerY, 146, 20).build());
        if (regionExpanded && !shareCodeTab) addRegionList(x);
        if (!shareCodeTab) restoreRegionName();

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
        ButtonWidget mode = addDrawableChild(ButtonWidget.builder(label("share_code_mode." + draft.shareCodeMode), w -> {
            draft.shareCodeMode = MewSettings.NEW.equals(draft.shareCodeMode) ? MewSettings.WORLD
                    : MewSettings.WORLD.equals(draft.shareCodeMode) ? MewSettings.GLOBAL : MewSettings.NEW;
            confirm = "";
            confirmLiveSettings = false;
            notice = "";
            clearAndInit();
        }).dimensions(x, 78, 200, 20).build());
        mode.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("share_code_help." + draft.shareCodeMode)));
        ButtonWidget codeChat = addDrawableChild(ButtonWidget.builder(label("show_code_chat." + (draft.showShareCodeInChat ? "on" : "off")), w -> {
            draft.showShareCodeInChat = !draft.showShareCodeInChat;
            notice = "";
            clearAndInit();
        }).dimensions(x + 208, 78, 92, 20).build());
        codeChat.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("show_code_chat_tooltip")));

        String code = shareCodePreview();
        shownShareCode = code;
        shareCodeField = new TextFieldWidget(textRenderer, x, 127, 222, 20, label("code"));
        shareCodeField.setMaxLength(8192);
        shareCodeField.setText(codeText(code));
        shareCodeField.setEditable(false);
        addDrawableChild(shareCodeField);
        copyCodeButton = addDrawableChild(ButtonWidget.builder(label("copy"), w -> {
            if (shownShareCode != null) MinecraftClient.getInstance().keyboard.setClipboard(shownShareCode);
        }).dimensions(x + 228, 127, 72, 20).build());
        copyCodeButton.active = code != null;

        resetCodeButton = addDrawableChild(ButtonWidget.builder(label("reset"), w -> resetSelected())
                .dimensions(x, 159, 146, 20).build());
        resetCodeButton.active = canResetCode();
        resetCodeButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.reset")));
        ButtonWidget all = addDrawableChild(ButtonWidget.builder(label("reset_all"), w -> resetAll())
                .dimensions(x + 154, 159, 146, 20).build());
        all.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.reset_all")));
    }


    private void renderDerp(int x) {
        ButtonWidget mode = addDrawableChild(ButtonWidget.builder(label("mode." + draft.derpMode), w -> {
            if (pending) return;
            captureInput();
            draft.derpMode = MewSettings.AUTO.equals(draft.derpMode) ? MewSettings.MAP
                    : MewSettings.MAP.equals(draft.derpMode) ? MewSettings.URL : MewSettings.AUTO;
            draft.regionId = 0;
            confirmLiveSettings = false;
            regionMetadataRequested = false;
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
                regionMetadataRequested = false;
                updateSaveButton();
                regionMetadataRequested = false;
                updateSaveButton();
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
        ButtonWidget choose = addDrawableChild(ButtonWidget.builder(Text.empty(), w -> openRegions())
                .dimensions(x + 154, 78, 146, 20).build());
        regionButton = choose;
        choose.active = !pending;
        choose.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("tip.region")));
    }

    private String currentRegion() {
        if (draft.regionId == 0) return label("region_auto").getString();
        for (DerpRegions.Region r : regions) if (r.id == draft.regionId) return r.label();
        return "Region " + draft.regionId;
    }

    // The field label already says "Preferred region". Keep the button itself legible.
    private static String shortRegionName(String fullName) {
        int suffix = fullName.lastIndexOf(" (");
        String name = suffix > 0 ? fullName.substring(0, suffix) : fullName;
        return name.length() > 21 ? name.substring(0, 18) + "..." : name;
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
        if (runtime.resetInProgress()) return null;
        if (runtime.onlineSharingEnabled() && MewClientCompat.published()) return runtime.shareCode();
        return MewShareCodes.preview(draft.shareCodeMode, world(), runtime.configDirectory());
    }


    private void resetSelected() {
        confirm = "selected";
        clearAndInit();
    }

    private void resetAll() {
        confirm = "all";
        clearAndInit();
    }

    private void cancelReset() {
        confirm = "";
        clearAndInit();
    }

    private void confirmReset() {
        boolean all = "all".equals(confirm);
        if (!all && !"selected".equals(confirm)) return;
        MewRuntime runtime = MewRuntime.INSTANCE;
        boolean activeTailcat = runtime.onlineSharingEnabled() && runtime.sharing();
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
                clearAndInit();
                return;
            }
        }
        confirm = "";
        notice = label("loading").getString();
        Path saves = all ? MinecraftClient.getInstance().runDirectory.toPath().resolve("saves") : null;
        runtime.resetShareCode(mode, world(), saves, all)
                .whenComplete((ignored, failure) -> MinecraftClient.getInstance().execute(() -> {
                    if (failure == null) {
                        if (activeTailcat) MewClientCompat.message("mewltiplayer.chat.disconnected", false);
                        notice = activeTailcat ? "" : label("reset_done_hint").getString();
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
                clearAndInit();
                return;
            }
            if (networkChanged) {
                confirmDerpChange = true;
                error = "";
                notice = "";
                clearAndInit();
                return;
            }
            runtime.saveSettings(draft);
            close();
        } catch (Exception exception) {
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
        }
    }

    private void cancelApply() {
        confirmLiveSettings = false;
        confirmNetworkChange = false;
        clearAndInit();
    }

    private void cancelDerpSave() {
        confirmDerpChange = false;
        clearAndInit();
    }

    private void confirmDerpSave() {
        try {
            MewRuntime.INSTANCE.saveSettings(draft);
            close();
        } catch (Exception exception) {
            confirmDerpChange = false;
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
            clearAndInit();
        }
    }

    private void applyAndRestart() {
        try {
            draft.validate();
            MewRuntime runtime = MewRuntime.INSTANCE;
            boolean sharing = runtime.sharing() && runtime.onlineSharingEnabled();
            boolean joining = runtime.joining();
            runtime.saveSettings(draft);
            if (sharing) runtime.restartShare();
            if (joining) {
                MewClientActions.disconnect();
                if (MinecraftClient.getInstance().getNetworkHandler() != null) MinecraftClient.getInstance().getNetworkHandler().getConnection().disconnect(label("restart_disconnect"));
            }
            close();
        } catch (Exception exception) {
            confirmLiveSettings = false;
            error = exception.getMessage() == null ? label("invalid").getString() : exception.getMessage();
            clearAndInit();
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshShareCode();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!confirm.isEmpty() && keyCode == 256) { cancelReset(); return true; }
        if (confirmLiveSettings && keyCode == 256) { cancelApply(); return true; }
        if (confirmDerpChange && keyCode == 256) { cancelDerpSave(); return true; }
        if (regionExpanded && keyCode == 256) {
            regionExpanded = false;
            clearAndInit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        renderBackgroundTexture(graphics, MENU_BACKGROUND_TEXTURE, 0, 0, 0f, 0f, width, height);
        super.render(graphics, mouseX, mouseY, delta);
        if (confirmLiveSettings) {
            graphics.drawCenteredTextWithShadow(textRenderer, label("restart_title"), width / 2, height / 2 - 30, 0xffffffff);
            if (confirmNetworkChange) {
                graphics.drawCenteredTextWithShadow(textRenderer, label("derp_code_warning"), width / 2, height / 2 - 12, 0xffffaa55);
                graphics.drawCenteredTextWithShadow(textRenderer, label("restart_warning"), width / 2, height / 2 + 2, 0xffaaaaaa);
            } else {
                graphics.drawCenteredTextWithShadow(textRenderer, label("restart_warning"), width / 2, height / 2 - 10, 0xffaaaaaa);
            }
            return;
        }
        if (confirmDerpChange) {
            graphics.drawCenteredTextWithShadow(textRenderer, label("derp_change_title"), width / 2, height / 2 - 30, 0xffffffff);
            graphics.drawCenteredTextWithShadow(textRenderer, label("derp_code_warning"), width / 2, height / 2 - 10, 0xffffaa55);
            return;
        }
        if (!confirm.isEmpty()) {
            boolean all = "all".equals(confirm);
            graphics.drawCenteredTextWithShadow(textRenderer, label(all ? "reset_all_title" : "reset_title"), width / 2, height / 2 - 38, 0xffffffff);
            graphics.drawCenteredTextWithShadow(textRenderer, label(all ? "reset_all_warning" : "reset_warning"), width / 2, height / 2 - 18, 0xffaaaaaa);
            if (MewRuntime.INSTANCE.sharing() && MewRuntime.INSTANCE.onlineSharingEnabled()) {
                graphics.drawCenteredTextWithShadow(textRenderer, label("reset_active_warning"), width / 2, height / 2 - 5, 0xffaaaaaa);
            }
            return;
        }
        graphics.drawItem(net.minecraft.item.Items.KNOWLEDGE_BOOK.getDefaultStack(), width / 2 - 174, height - 25);
        int x = width / 2 - 150;
        if (!shareCodeTab && regionButton != null) {
            String name = currentRegion();
            int totalWidth = textRenderer.getWidth(name);
            int room = regionButton.getWidth() - 12;
            int overflow = Math.max(0, totalWidth - room);
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
            graphics.drawTextWithShadow(textRenderer, name,
                    regionButton.getX() + 6 + (overflow == 0 ? (room - totalWidth) / 2 : 0) - offset,
                    regionButton.getY() + (regionButton.getHeight() - 8) / 2, 0xffffffff);
            graphics.disableScissor();
        }
        if (regionExpanded && !shareCodeTab) addRegionList(x);
        graphics.drawCenteredTextWithShadow(textRenderer,Text.empty()
                .append(Text.literal("\uE000").styled(style -> style.withFont(CAT_FONT)))
                .append(Text.literal("  Mewltiplayer")), width / 2, 36, 0xffffffff);
        if (regionExpanded && !shareCodeTab) {
            graphics.drawTextWithShadow(textRenderer, label("region_picker_title"), x + 2, 111, 0xffaaaaaa);
        } else {
            if (shareCodeTab) {
                graphics.drawTextWithShadow(textRenderer, label("share_code_label"), x + 2, 64, 0xffaaaaaa);
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

        MewRuntime runtime = MewRuntime.INSTANCE;
        String message = !error.isEmpty() ? error : !notice.isEmpty() ? notice : "";
        int color = !error.isEmpty() ? 0xffff6666 : 0xffaaaaaa;
        if (message.isEmpty() && shareCodeTab && (runtime.sharing() || runtime.joining()
                || (MewClientCompat.published() && runtime.onlineSharingEnabled()))) {
            String key = runtime.resetInProgress() ? "session_restarting"
                    : runtime.sharing() ? "session_sharing" : runtime.joining() ? "session_joining"
                    : "session_starting";
            message = label(key).getString();
            color = "session_sharing".equals(key) ? 0xff55ff55 : 0xffaaaaaa;
        }
        if (!message.isEmpty()) {
            String visible = textRenderer.getWidth(message) > 296
                    ? textRenderer.trimToWidth(message, 286) + "…" : message;
            graphics.drawTextWithShadow(textRenderer, visible, x + 2, height - 39, color);
        }

    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
