package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.fabric.MewClientCompat;
import dev.mewltiplayer.fabric.MewRuntime;
import dev.mewltiplayer.fabric.MewSettingsScreen;
import java.io.IOException;
import java.util.IdentityHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenToLanScreen.class)
public abstract class MewLanMixin extends Screen {
    @Unique private boolean mew$enabled;
    @Unique private int mew$scroll;
    @Unique private boolean mew$scrollbarDragging;
    @Unique private double mew$dragOffset;
    @Unique private IdentityHashMap<ClickableWidget, Integer> mew$originalY;

    protected MewLanMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$addOptions(CallbackInfo ci) {
        mew$enabled = MewWorldSettings.shareViaTailcat(MewClientCompat.worldDirectory());
        int buttonY = 220;
        addDrawableChild(ButtonWidget.builder(mew$label(), button -> {
            boolean next = !mew$enabled;
            try {
                MewWorldSettings.setShareViaTailcat(MewClientCompat.worldDirectory(), next);
                mew$enabled = next;
                button.setMessage(mew$label());
                if (MewClientCompat.published()) {
                    boolean wasSharing = MewRuntime.INSTANCE.sharing() || MewRuntime.INSTANCE.onlineSharingEnabled();
                    MewRuntime.INSTANCE.setOnlineSharingEnabled(next);
                    if (!next && wasSharing) MewClientCompat.message("mewltiplayer.chat.stopped", false);
                }
            } catch (IOException error) {
                MewClientCompat.notice("mewltiplayer.settings.saveFailed");
            }
        }).dimensions(width / 2 - 155, buttonY, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mewltiplayer.settings.open"),
                button -> MinecraftClient.getInstance().setScreen(new MewSettingsScreen(this)))
                .dimensions(width / 2 + 5, buttonY, 150, 20).build());
        mew$layoutWidgets();
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 142))
    private int mew$portHeadingY(int y) {
        return y - mew$scroll;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mew$renderHeading(DrawContext graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        graphics.drawCenteredTextWithShadow(textRenderer, Text.literal("Mewltiplayer"),
                width / 2, 202 - mew$scroll, 0xffffffff);
        if (mew$maxScroll() > 0) {
            int top = mew$scrollbarTop();
            int track = mew$scrollbarTrackHeight();
            int thumb = mew$scrollbarThumbHeight();
            int thumbY = top + mew$scroll * (track - thumb) / mew$maxScroll();
            int right = mew$scrollbarX();
            // Vanilla list-style scrollbar: black gutter, grey thumb and light edge.
            graphics.fill(right, top, right + 6, top + track, 0xff000000);
            graphics.fill(right, thumbY, right + 6, thumbY + thumb, 0xff808080);
            graphics.fill(right, thumbY, right + 5, thumbY + thumb - 1, 0xffc0c0c0);
            graphics.fill(right + 1, thumbY + 1, right + 5, thumbY + thumb - 1, 0xff808080);
        }
    }

    @Unique
    private int mew$maxScroll() {
        // Vanilla's vertical spacing is preserved; only the viewport is smaller.
        return Math.max(0, 300 - height);
    }

    @Unique private int mew$scrollbarX() { return width / 2 + 166; }
    @Unique private int mew$scrollbarTop() { return 10; }
    @Unique private int mew$scrollbarTrackHeight() { return Math.max(24, height - 20); }
    @Unique private int mew$scrollbarThumbHeight() {
        int track = mew$scrollbarTrackHeight();
        return Math.min(track, Math.max(22, track * height / 300));
    }

    @Unique
    private void mew$dragScrollbar(double mouseY) {
        int travel = mew$scrollbarTrackHeight() - mew$scrollbarThumbHeight();
        if (travel <= 0) return;
        double fraction = (mouseY - mew$scrollbarTop() - mew$dragOffset) / travel;
        mew$scroll = Math.max(0, Math.min(mew$maxScroll(),
                (int) Math.round(fraction * mew$maxScroll())));
        mew$positionWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mew$maxScroll() > 0
                && mouseX >= mew$scrollbarX() - 2 && mouseX < mew$scrollbarX() + 8
                && mouseY >= mew$scrollbarTop()
                && mouseY < mew$scrollbarTop() + mew$scrollbarTrackHeight()) {
            int thumbTop = mew$scrollbarTop() + mew$scroll
                    * (mew$scrollbarTrackHeight() - mew$scrollbarThumbHeight()) / mew$maxScroll();
            mew$dragOffset = mouseY >= thumbTop && mouseY < thumbTop + mew$scrollbarThumbHeight()
                    ? mouseY - thumbTop : mew$scrollbarThumbHeight() / 2.0;
            mew$scrollbarDragging = true;
            mew$dragScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && mew$scrollbarDragging) {
            mew$dragScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && mew$scrollbarDragging) {
            mew$scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Unique
    private void mew$layoutWidgets() {
        if (mew$originalY == null) mew$originalY = new IdentityHashMap<>();
        else mew$originalY.clear();
        int index = 0;
        for (var child : children()) {
            if (child instanceof ClickableWidget widget) {
                // Vanilla adds Game Mode, Cheats, Port, Start and Cancel in this order.
                // Keep the footer after the mod options rather than on top of them.
                int baseline = (index == 3 || index == 4)
                        ? Math.max(height - 28, 272) : widget.getY();
                mew$originalY.put(widget, baseline);
            }
            index++;
        }
        mew$scroll = Math.min(mew$scroll, mew$maxScroll());
        mew$positionWidgets();
    }

    @Unique
    private void mew$positionWidgets() {
        for (var item : mew$originalY.entrySet()) {
            item.getKey().setY(item.getValue() - mew$scroll);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mew$maxScroll() > 0 && verticalAmount != 0) {
            int step = (int) Math.round(verticalAmount * 25.0);
            if (step == 0) step = verticalAmount > 0 ? 1 : -1;
            int scroll = Math.max(0, Math.min(mew$maxScroll(), mew$scroll - step));
            if (mew$scroll != scroll) {
                mew$scroll = scroll;
                mew$positionWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 50))
    private int mew$lanHeadingY(int original) {
        return original - mew$scroll;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 82))
    private int mew$otherHeadingY(int original) {
        return original - mew$scroll;
    }


    @Unique
    private Text mew$label() {
        return Text.translatable("mewltiplayer.lan.short",
                Text.translatable(mew$enabled ? "options.on" : "options.off")
                        .formatted(mew$enabled ? Formatting.GREEN : Formatting.RED));
    }
}
