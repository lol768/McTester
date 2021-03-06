package org.spongepowered.mctester.internal.mixin;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldSettings;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.mctester.api.RunnerEvents;
import org.spongepowered.mctester.internal.McTester;
import org.spongepowered.mctester.internal.interfaces.IMixinMinecraft;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.annotation.Nullable;

// McTester always runs in a deobfuscated environment - it depends on GradleStart, after all!
// Therefore, we disable all remapping, since we'll never need it.
@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraft implements IMixinMinecraft {

    @Shadow public abstract void displayGuiScreen(@Nullable GuiScreen guiScreenIn);

    @Shadow public abstract void launchIntegratedServer(String folderName, String worldName, @Nullable WorldSettings worldSettingsIn);

    @Shadow private volatile boolean running;

    @Shadow protected abstract void clickMouse();

    @Shadow protected abstract void rightClickMouse();

    @Shadow @Final private Queue<FutureTask<?>> scheduledTasks;

    @Shadow public GameSettings gameSettings;
    @Shadow private int leftClickCounter;
    @Shadow public RayTraceResult objectMouseOver;
    @Shadow public WorldClient world;
    private boolean leftClicking;
    private boolean rightClicking;

    private boolean allowPause = false;

    @Inject(method = "init", at = @At(value = "RETURN"))
    public void onInitDone(CallbackInfo ci) {
        RunnerEvents.setClientInit();
    }

    @Inject(method = "shutdownMinecraftApplet", cancellable = true, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;destroy()V", remap = false))
    public void onSystemExitCalled(CallbackInfo ci) {
        // Notify any listenres that the game has closed, but don't actually
        // call System.exit here. We want to let JUnit exit cleanly.
        RunnerEvents.setGameClosed();
        ci.cancel();
    }

    @Inject(method = "stopIntegratedServer", at = @At("HEAD"), cancellable = true)
    private static void onStopIntegratedServer(CallbackInfo ci) {
        // If we're already shutting down, don't try to shutdown again
        if (Minecraft.getMinecraft() != null && !((IMixinMinecraft) Minecraft.getMinecraft()).isRunning()) {
            ci.cancel();
        }
    }

    @Override
    public <T> ListenableFuture<T> addScheduledTaskAlwaysDelay(Callable<T> callable) {
        Validate.notNull(callable);
        ListenableFutureTask<T> listenablefuturetask = ListenableFutureTask.<T>create(callable);

        synchronized (this.scheduledTasks)
        {
            this.scheduledTasks.add(listenablefuturetask);
            return listenablefuturetask;
        }
    }


    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public void leftClick() {
        this.leftClickCounter = 0;
        this.clickMouse();
    }

    @Inject(method = "clickMouse", at = @At(value = "HEAD"))
    public void onClickMouse(CallbackInfo ci) {
        McTester.INSTANCE.logger.debug(String.format("Click mouse hit: %s %s", this.leftClickCounter, this.objectMouseOver));
        if (this.objectMouseOver != null && this.objectMouseOver.getBlockPos() != null) {
            McTester.INSTANCE.logger.debug("Hit block type: " + this.world.getBlockState(this.objectMouseOver.getBlockPos()));
        }
    }

    @Redirect(method = "setIngameFocus", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;isActive()Z"))
    public boolean onDisplayIsActive() {
        // Force Display.isActive() to always return 'true'.
        // This allows McTester to work properly in headless environemnts (e.g. with XVFB)
        return true;
    }

    @Override
    public void holdLeftClick(boolean clicking) {
        this.leftClicking = clicking;

        KeyBinding attack = this.gameSettings.keyBindAttack;

        KeyBinding.setKeyBindState(attack.getKeyCode(), clicking);
        if (clicking) {
            KeyBinding.onTick(attack.getKeyCode());
        }
    }

    @Override
    public void holdRightClick(boolean clicking) {
        this.rightClicking = clicking;

        KeyBinding useItem = this.gameSettings.keyBindUseItem;

        KeyBinding.setKeyBindState(useItem.getKeyCode(), clicking);
        if (clicking) {
            KeyBinding.onTick(useItem.getKeyCode());
        }
    }

    @Override
    public void rightClick() {
        this.rightClickMouse();
    }

    @Redirect(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;doesGuiPauseGame()Z"))
    public boolean onDoesGuiPauseGame(GuiScreen screen) {
        return this.allowPause && screen.doesGuiPauseGame();
    }

    @Override
    public void setAllowPause(boolean allowPause) {
        this.allowPause = allowPause;
    }
}
