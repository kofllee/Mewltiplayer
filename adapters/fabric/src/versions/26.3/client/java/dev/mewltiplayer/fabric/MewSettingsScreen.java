package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.DerpRegions;
import dev.mewltiplayer.core.MewInvitations;
import dev.mewltiplayer.core.MewSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final FontDescription.Resource CAT_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mewltiplayer", "chat_cat"));
    private final Screen parent;
    private final MewSettings initial;
    private final MewSettings draft;
    private boolean invitationTab = true;
    private boolean tabsLive;
    private boolean regionExpanded;
    private GridLayoutTab invitationsTab;
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
        TabManager manager = new TabManager(w -> {}, w -> {},
                tab -> { if (tabsLive) switchPage(tab == invitationsTab); }, tab -> {});
        invitationsTab = new GridLayoutTab(label("tab.invites"));
        GridLayoutTab other = new GridLayoutTab(label("tab.derp"));
        MenuTabBar tabs = MenuTabBar.builder(manager, width).addTabs(invitationsTab, other).build();
        tabs.arrangeElements(width);
        addRenderableWidget(tabs);
        manager.setCurrentTab(invitationTab ? invitationsTab : other, false);
        tabsLive = true;
        Button docs = addRenderableWidget(Button.builder(Component.literal("?"), w ->
                net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(
                        this, java.net.URI.create("https://github.com/tailscale/tailcat/blob/main/README.md")))
                .bounds(width - 26, height - 27, 20, 20).build());
        docs.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("docs_tooltip")));
        if (invitationTab) renderInvites(x);
        else renderDerp(x);
        int footerY = height - 27;
        addRenderableWidget(Button.builder(label("save"), w -> save()).bounds(x, footerY, 146, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), w -> onClose())
                .bounds(x + 154, footerY, 146, 20).build());
        if (regionExpanded && !invitationTab) addRegionList(x);
    }

    private void switchPage(boolean invite) {
        captureInput();
        invitationTab = invite;
        regionExpanded = false;
        error = "";
        notice = "";
        rebuildWidgets();
    }

    private void renderInvites(int x) {
        Button invitationModeButton = addRenderableWidget(Button.builder(label("invite_mode." + draft.invitationMode), w -> {
            draft.invitationMode = MewSettings.NEW.equals(draft.invitationMode) ? MewSettings.WORLD
                    : MewSettings.WORLD.equals(draft.invitationMode) ? MewSettings.GLOBAL : MewSettings.NEW;
            confirm = "";
            notice = "";
            rebuildWidgets();
        }).bounds(x, 78, 300, 20).build());
        invitationModeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("invite_help." + draft.invitationMode)));

        String code = invitationPreview();
        EditBox preview = new EditBox(font, x, 127, 222, 20, label("code"));
        preview.setMaxLength(8192);
        preview.setValue(code == null ? (MewSettings.WORLD.equals(draft.invitationMode) && world() == null
                ? label("code_world_required").getString() : label("code_none").getString()) : code);
        preview.setEditable(false);
        addRenderableWidget(preview);
        Button copy = addRenderableWidget(Button.builder(label("copy"), w ->
                Minecraft.getInstance().keyboardHandler.setClipboard(code))
                .bounds(x + 228, 127, 72, 20).build());
        copy.active = code != null;

        Button reset = addRenderableWidget(Button.builder(label("reset"), w -> resetSelected())
                .bounds(x, 159, 146, 20).build());
        reset.active = !MewSettings.NEW.equals(draft.invitationMode)
                && MewInvitations.identity(draft.invitationMode, world(), MewRuntime.INSTANCE.configDirectory()) != null;
        addRenderableWidget(Button.builder(label("reset_all"), w -> resetAll())
                .bounds(x + 154, 159, 146, 20).build());
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
            json = MultiLineEditBox.builder().setX(x).setY(127)
                    .setPlaceholder(label("json_placeholder")).build(font, 300, editorHeight, label("json"));
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

    private String invitationPreview() {
        MewRuntime runtime = MewRuntime.INSTANCE;
        if (runtime.isSharingMode(draft.invitationMode, world()) && runtime.invite() != null)
            return runtime.invite();
        return MewInvitations.preview(draft.invitationMode, world(), runtime.configDirectory());
    }

    private void resetSelected() {
        if (!"selected".equals(confirm)) {
            confirm = "selected";
            notice = label("confirm_reset").getString();
            return;
        }
        confirm = "";
        notice = label("loading").getString();
        MewRuntime.INSTANCE.resetInvitation(draft.invitationMode, world(), null, false)
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
        MewRuntime.INSTANCE.resetInvitation(draft.invitationMode, world(),
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
            boolean hasSavedCode = MewInvitations.preview(MewSettings.WORLD, world(), runtime.configDirectory()) != null
                    || MewInvitations.preview(MewSettings.GLOBAL, world(), runtime.configDirectory()) != null;
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
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
        int x = width / 2 - 150;
        graphics.centeredText(font,Component.empty()
                .append(Component.literal("\uE000").withStyle(style -> style.withFont(CAT_FONT)))
                .append(Component.literal("  Mewltiplayer")), width / 2, 36, 0xffffffff);
        if (regionExpanded && !invitationTab) {
            graphics.text(font, label("region_picker_title"), x + 2, 111, 0xffaaaaaa);
        } else {
            if (invitationTab) {
                graphics.text(font, label("invite_label"), x + 2, 64, 0xffaaaaaa);
                graphics.text(font, label("code"), x + 2, 112, 0xffaaaaaa);
            } else {
                graphics.text(font, label("derp_label"), x + 2, 64, 0xffaaaaaa);
                graphics.text(font, label("region_label"), x + 156, 64, 0xffaaaaaa);
                if (MewSettings.AUTO.equals(draft.derpMode)) {
                    graphics.text(font, label("derp_help.auto"), x + 2, 111, 0xff999999);
                }
                if (MewSettings.URL.equals(draft.derpMode)) {
                    graphics.text(font, label("url"), x + 2, 111, 0xffaaaaaa);
                } else if (MewSettings.MAP.equals(draft.derpMode)) {
                    graphics.text(font, label("json"), x + 2, 111, 0xffaaaaaa);
                }
            }
        }
        if (!error.isEmpty()) graphics.centeredText(font,Component.literal(error), width / 2, height - 39, 0xffff6666);
        else if (!notice.isEmpty()) graphics.centeredText(font,Component.literal(notice), width / 2, height - 39, 0xffaaaaaa);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
