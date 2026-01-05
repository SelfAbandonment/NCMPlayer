package org.selfabandonment.ncmplayer.client.screen.tab;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tab 页面基类
 *
 * @author SelfAbandonment
 */
public abstract class AbstractTab {

    protected final MusicScreenContext ctx;

    protected AbstractTab(MusicScreenContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 初始化组件
     */
    public abstract void init();

    /**
     * 渲染页面
     */
    public abstract void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /**
     * 获取所有组件
     */
    public abstract List<AbstractWidget> getWidgets();

    /**
     * 设置组件可见性
     */
    public void setVisible(boolean visible) {
        for (AbstractWidget widget : getWidgets()) {
            widget.visible = visible;
        }
    }

    /**
     * 当 Tab 被激活时调用
     */
    public void onActivate() {
    }

    /**
     * 当 Tab 被停用时调用
     */
    public void onDeactivate() {
    }

    /**
     * 处理鼠标点击
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * 处理鼠标释放
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * 处理鼠标拖动
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /**
     * 处理鼠标滚动
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * 清理资源
     */
    public void cleanup() {
    }

    // 工具方法
    protected Font font() {
        return ctx.font();
    }

    protected int width() {
        return ctx.width();
    }

    protected int height() {
        return ctx.height();
    }

    protected ScheduledExecutorService exec() {
        return ctx.exec();
    }

    protected String baseUrl() {
        return ctx.baseUrl();
    }
}

