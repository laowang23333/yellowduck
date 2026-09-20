package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.party.PartyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * YellowDuck 组队 / 副本 GUI。
 *
 * 当前版本不再依赖整张“画死”的界面 PNG。
 * 外框、功能卡、按钮、成员行、状态条全部由代码实时绘制；
 * 玩家、队伍、准备、副本、邀请等数据仍由 PartyMenu 服务端状态槽实时同步。
 */
public final class PartyScreen extends AbstractContainerScreen<PartyMenu> {
    private final List<HitTarget> targets = new ArrayList<>();
    private Page page = Page.MAIN;
    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;
    private int refreshTicks;
    private int invitePage;
    private int manageCursor;

    public PartyScreen(PartyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 384;
        this.imageHeight = 459;
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
        sendAction(PartyMenu.ACTION_REFRESH);
    }

    @Override
    protected void containerTick() {
        // 每秒向服务端刷新一次状态，保证队员、准备、邀请等内容实时变化。
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            sendAction(PartyMenu.ACTION_REFRESH);
        }
    }

    private ScreenSize screenSize() {
        return switch (page) {
            case MAIN -> new ScreenSize(384, 459);
            case INFO, INVITE -> new ScreenSize(352, 459);
            case MANAGE -> new ScreenSize(388, 459);
            case DUNGEONS -> new ScreenSize(384, 418);
            case CONFIRM -> new ScreenSize(352, 418);
        };
    }

    private void updateTransform() {
        ScreenSize s = screenSize();
        uiScale = Math.min(1.0F, Math.min((width - 16.0F) / s.w, (height - 16.0F) / s.h));
        uiScale = Math.max(0.35F, uiScale);
        uiX = (width - s.w * uiScale) / 2.0F;
        uiY = (height - s.h * uiScale) / 2.0F;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        updateTransform();
        targets.clear();

        CompoundTag meta = tag(menu.stateStack(PartyMenu.META_SLOT));
        boolean hasParty = meta.getBoolean("HasParty");
        if (!hasParty && page != Page.MAIN) page = Page.MAIN;
        if ((page == Page.INVITE || page == Page.MANAGE)
                && (!meta.getBoolean("ViewerLeader") || meta.getBoolean("Locked"))) {
            page = Page.MAIN;
        }

        updateTransform();
        ScreenSize s = screenSize();
        float mx = (mouseX - uiX) / uiScale;
        float my = (mouseY - uiY) / uiScale;

        g.pose().pushPose();
        g.pose().translate(uiX, uiY, 0);
        g.pose().scale(uiScale, uiScale, 1.0F);

        DungeonGuiStyle.tiledWindow(g, 0, 0, s.w, s.h, true);
        renderHeader(g, meta, s.w);

        switch (page) {
            case MAIN -> renderMain(g, meta, mx, my);
            case INFO -> renderInfo(g, meta, mx, my);
            case INVITE -> renderInvite(g, meta, mx, my);
            case MANAGE -> renderManage(g, meta, mx, my);
            case DUNGEONS -> renderDungeons(g, meta, mx, my);
            case CONFIRM -> renderConfirm(g, meta, mx, my);
        }
        g.pose().popPose();

        for (HitTarget target : targets) {
            if (target.tooltip != null && !target.tooltip.isBlank() && target.contains(mx, my)) {
                g.renderTooltip(font, Component.literal(target.tooltip), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderHeader(GuiGraphics g, CompoundTag meta, int width) {
        if (page == Page.MAIN) {
            drawItemIcon(g, new ItemStack(Items.PLAYER_HEAD), 15, 5, 1.05F);
            int count = meta.getBoolean("HasParty") ? Math.max(1, meta.getInt("MemberTotal")) : 0;
            g.drawString(font, count + "  组队系统", 38, 10, DungeonGuiStyle.TEXT, false);
        } else {
            g.drawCenteredString(font, pageTitle(), width / 2, 10, DungeonGuiStyle.TEXT);
        }
    }

    private String pageTitle() {
        return switch (page) {
            case MAIN -> "组队系统";
            case INFO -> "队伍信息";
            case INVITE -> "邀请玩家";
            case MANAGE -> "队伍管理";
            case DUNGEONS -> "选择副本";
            case CONFIRM -> "开始挑战确认";
        };
    }

    private void renderMain(GuiGraphics g, CompoundTag meta, float mx, float my) {
        boolean hasParty = meta.getBoolean("HasParty");
        boolean leader = meta.getBoolean("ViewerLeader");
        boolean locked = meta.getBoolean("Locked");
        boolean hasInvite = meta.getBoolean("HasInvite");

        // 第一排：四张卡
        drawFunctionCard(g, 20, 64, 82, 150,
                new ItemStack(Items.PAPER),
                "创建队伍", "创建一个新的", "冒险队伍",
                !hasParty, inside(mx, my, 20, 64, 82, 150), DungeonGuiStyle.ButtonTone.GRAY);
        target(20, 64, 82, 150,
                !hasParty ? PartyMenu.ACTION_CREATE : Integer.MIN_VALUE, null,
                hasParty ? "你已经在队伍中" : "创建一个新的冒险队伍");

        if (!hasParty && hasInvite) {
            drawFunctionCard(g, 108, 64, 82, 150,
                    new ItemStack(Items.EMERALD),
                    "接受邀请", "已有队伍邀请", "点击加入队伍",
                    true, inside(mx, my, 108, 64, 82, 150), DungeonGuiStyle.ButtonTone.GREEN);
            target(108, 64, 82, 150, PartyMenu.ACTION_ACCEPT, null, "接受当前收到的队伍邀请");
        } else {
            boolean canInvite = hasParty && leader && !locked;
            drawFunctionCard(g, 108, 64, 82, 150,
                    new ItemStack(Items.ENDER_EYE),
                    "邀请玩家", "邀请其他玩家", "加入队伍",
                    canInvite, inside(mx, my, 108, 64, 82, 150), DungeonGuiStyle.ButtonTone.GRAY);
            target(108, 64, 82, 150,
                    canInvite ? -1 : Integer.MIN_VALUE,
                    canInvite ? Page.INVITE : null,
                    !hasParty ? "请先创建或加入队伍"
                            : (!leader ? "只有队长可以邀请玩家"
                            : (locked ? "副本进行中队伍已锁定" : "邀请副本柱子20格内的玩家")));
        }

        drawFunctionCard(g, 196, 64, 82, 150,
                new ItemStack(Items.WRITABLE_BOOK),
                "队伍信息", "查看当前", "队伍信息",
                hasParty, inside(mx, my, 196, 64, 82, 150), DungeonGuiStyle.ButtonTone.GRAY);
        target(196, 64, 82, 150,
                hasParty ? -1 : Integer.MIN_VALUE,
                hasParty ? Page.INFO : null,
                hasParty ? "查看队伍成员与准备状态" : "请先创建或加入队伍");

        boolean canManage = hasParty && leader && !locked;
        drawFunctionCard(g, 284, 64, 82, 150,
                new ItemStack(Items.PLAYER_HEAD),
                "队伍管理", "踢出 / 转让", "队长等",
                canManage, inside(mx, my, 284, 64, 82, 150), DungeonGuiStyle.ButtonTone.GRAY);
        target(284, 64, 82, 150,
                canManage ? -1 : Integer.MIN_VALUE,
                canManage ? Page.MANAGE : null,
                !hasParty ? "请先创建或加入队伍"
                        : (!leader ? "只有队长可以管理队伍"
                        : (locked ? "副本进行中队伍已锁定" : "管理队伍成员")));

        // 第二排：三张卡
        drawFunctionCard(g, 20, 224, 98, 156,
                new ItemStack(Items.COMPASS),
                "选择副本", "查看当前柱子", "绑定的副本",
                hasParty, inside(mx, my, 20, 224, 98, 156), DungeonGuiStyle.ButtonTone.GRAY);
        target(20, 224, 98, 156,
                hasParty ? -1 : Integer.MIN_VALUE,
                hasParty ? Page.DUNGEONS : null,
                hasParty ? "查看当前副本柱子固定绑定的副本" : "请先创建或加入队伍");

        drawFunctionCard(g, 125, 224, 123, 156,
                new ItemStack(Items.DIAMOND_SWORD),
                "开始挑战", "当所有队员", "准备后开始",
                hasParty, inside(mx, my, 125, 224, 123, 156), DungeonGuiStyle.ButtonTone.GREEN);
        target(125, 224, 123, 156,
                hasParty ? -1 : Integer.MIN_VALUE,
                hasParty ? Page.CONFIRM : null,
                hasParty ? "检查准备状态并开始挑战" : "请先创建或加入队伍");

        boolean canLeave = hasParty && !locked;
        drawFunctionCard(g, 254, 224, 112, 156,
                new ItemStack(Items.BARRIER),
                "离开队伍", "离开当前", "冒险队伍",
                canLeave, inside(mx, my, 254, 224, 112, 156), DungeonGuiStyle.ButtonTone.RED);
        target(254, 224, 112, 156,
                canLeave ? PartyMenu.ACTION_LEAVE : Integer.MIN_VALUE, null,
                !hasParty ? "请先创建或加入队伍"
                        : (locked ? "副本进行中无法直接离开队伍，请先离开副本" : "离开当前队伍"));

        // 底部实时状态条。
        DungeonGuiStyle.innerPanel(g, 20, 394, 346, 34);
        if (hasParty) {
            String status = meta.getString("PartyName") + "  ·  " + meta.getInt("MemberTotal") + "/"
                    + selectedMaxPlayers() + "  ·  " + selectedDungeonName();
            g.drawCenteredString(font, status, 193, 407, 0xFF343434);
        } else if (hasInvite) {
            g.drawCenteredString(font, "你有一个待处理的队伍邀请", 193, 407, 0xFF188F38);
        } else {
            g.drawCenteredString(font, "创建队伍或等待其他玩家邀请", 193, 407, DungeonGuiStyle.MUTED);
        }
    }

    private void renderInfo(GuiGraphics g, CompoundTag meta, float mx, float my) {
        DungeonGuiStyle.innerPanel(g, 22, 52, 308, 66);
        drawItemIcon(g, new ItemStack(Items.PLAYER_HEAD), 34, 68, 1.65F);
        g.drawString(font, "队长：" + meta.getString("LeaderName"), 82, 68, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "队员：" + meta.getInt("MemberTotal") + " / " + selectedMaxPlayers(),
                82, 94, DungeonGuiStyle.TEXT, false);

        List<MemberData> list = members();
        for (int i = 0; i < 5; i++) {
            int y = 127 + i * 48;
            boolean hovered = inside(mx, my, 22, y, 308, 42);
            DungeonGuiStyle.row(g, 22, y, 308, 42, hovered);
            if (i < list.size()) {
                MemberData m = list.get(i);
                DungeonGuiStyle.playerFace(g, minecraft, m.uuid, 30, y + 6, 30);
                g.drawString(font, m.name + (m.leader ? "  [队长]" : ""), 72, y + 9, DungeonGuiStyle.TEXT, false);
                String state = !m.online ? "离线" : (m.ready ? "✓ 已准备" : "✕ 未准备");
                int color = !m.online ? 0xFF777777 : (m.ready ? 0xFF188F38 : 0xFFC92F2F);
                g.drawString(font, state, 228, y + 23, color, false);
            } else {
                g.drawString(font, "（空位）", 72, y + 15, 0xFF777777, false);
            }
        }

        boolean locked = meta.getBoolean("Locked");
        boolean ready = meta.getBoolean("ViewerReady");

        boolean backHover = inside(mx, my, 22, 392, 142, 48);
        DungeonGuiStyle.button(g, minecraft, 22, 392, 142, 48,
                "返回", backHover, DungeonGuiStyle.ButtonTone.GRAY, true);
        target(22, 392, 142, 48, -1, Page.MAIN, "返回组队系统");

        boolean readyHover = inside(mx, my, 184, 392, 146, 48);
        DungeonGuiStyle.button(g, minecraft, 184, 392, 146, 48,
                locked ? "副本进行中" : (ready ? "取消准备" : "准备"),
                readyHover, ready ? DungeonGuiStyle.ButtonTone.GOLD : DungeonGuiStyle.ButtonTone.GREEN, !locked);
        target(184, 392, 146, 48,
                locked ? Integer.MIN_VALUE : PartyMenu.ACTION_READY, null,
                locked ? "副本进行中无法修改准备状态" : (ready ? "取消准备" : "标记为已准备"));
    }

    private void renderInvite(GuiGraphics g, CompoundTag meta, float mx, float my) {
        List<InviteData> all = invites();
        int maxPage = Math.max(0, (all.size() - 1) / 3);
        invitePage = Math.max(0, Math.min(invitePage, maxPage));
        int start = invitePage * 3;

        DungeonGuiStyle.innerPanel(g, 22, 54, 308, 38);
        g.drawString(font, "副本柱子 20 格内玩家", 32, 66, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "共 " + all.size() + " 人", 268, 66, DungeonGuiStyle.MUTED, false);

        for (int row = 0; row < 3; row++) {
            int y = 103 + row * 78;
            DungeonGuiStyle.row(g, 22, y, 308, 64, false);
            int idx = start + row;
            if (idx < all.size()) {
                InviteData p = all.get(idx);
                DungeonGuiStyle.playerFace(g, minecraft, p.uuid, 31, y + 16, 32);
                g.drawString(font, p.name, 78, y + 13, DungeonGuiStyle.TEXT, false);
                g.drawString(font, "● 在线", 78, y + 36, 0xFF15923A, false);

                boolean hover = inside(mx, my, 241, y + 12, 78, 40);
                DungeonGuiStyle.button(g, minecraft, 241, y + 12, 78, 40,
                        "邀请", hover, DungeonGuiStyle.ButtonTone.GREEN, true);
                target(241, y + 12, 78, 40,
                        PartyMenu.ACTION_INVITE_BASE + p.index, null,
                        "邀请 " + p.name + " 加入队伍");
            } else {
                g.drawString(font, "（空位）", 78, y + 27, DungeonGuiStyle.MUTED, false);
            }
        }

        if (all.isEmpty()) {
            g.drawCenteredString(font, "附近暂无可邀请玩家", 176, 212, DungeonGuiStyle.MUTED);
        }

        if (maxPage > 0) {
            boolean prevHover = inside(mx, my, 92, 348, 64, 28);
            boolean nextHover = inside(mx, my, 196, 348, 64, 28);
            DungeonGuiStyle.button(g, minecraft, 92, 348, 64, 28,
                    "上一页", prevHover, DungeonGuiStyle.ButtonTone.GRAY, invitePage > 0);
            DungeonGuiStyle.button(g, minecraft, 196, 348, 64, 28,
                    "下一页", nextHover, DungeonGuiStyle.ButtonTone.GRAY, invitePage < maxPage);
            target(92, 348, 64, 28, invitePage > 0 ? -2 : Integer.MIN_VALUE, null, "上一页");
            target(196, 348, 64, 28, invitePage < maxPage ? -3 : Integer.MIN_VALUE, null, "下一页");
            g.drawCenteredString(font, (invitePage + 1) + " / " + (maxPage + 1), 176, 356, DungeonGuiStyle.TEXT);
        }

        boolean backHover = inside(mx, my, 92, 399, 168, 42);
        DungeonGuiStyle.button(g, minecraft, 92, 399, 168, 42,
                "返回组队系统", backHover, DungeonGuiStyle.ButtonTone.GRAY, true);
        target(92, 399, 168, 42, -1, Page.MAIN, "返回组队系统");
    }

    private void renderManage(GuiGraphics g, CompoundTag meta, float mx, float my) {
        UUID viewer = parseUuid(meta.getString("ViewerUuid"));
        List<MemberData> other = new ArrayList<>();
        for (MemberData m : members()) {
            if (viewer == null || !m.uuid.equals(viewer)) other.add(m);
        }

        if (other.isEmpty()) {
            DungeonGuiStyle.innerPanel(g, 40, 72, 308, 154);
            g.drawCenteredString(font, "目前没有可管理的其他队员", 194, 140, DungeonGuiStyle.MUTED);
        } else {
            manageCursor = Math.max(0, Math.min(manageCursor, other.size() - 1));
            MemberData m = other.get(manageCursor);

            DungeonGuiStyle.innerPanel(g, 40, 72, 308, 154);
            DungeonGuiStyle.playerFace(g, minecraft, m.uuid, 61, 103, 72);
            g.drawString(font, m.name, 160, 101, DungeonGuiStyle.TEXT, false);
            g.drawString(font, "队伍成员", 160, 127, DungeonGuiStyle.MUTED, false);
            g.drawString(font, "状态：" + (m.ready ? "已准备" : "未准备"), 160, 154,
                    m.ready ? 0xFF178C36 : 0xFFC63434, false);
            g.drawString(font, m.online ? "● 当前在线" : "● 当前离线", 160, 181,
                    m.online ? 0xFF178C36 : 0xFF777777, false);

            if (other.size() > 1) {
                boolean prevHover = inside(mx, my, 117, 235, 64, 28);
                boolean nextHover = inside(mx, my, 207, 235, 64, 28);
                DungeonGuiStyle.button(g, minecraft, 117, 235, 64, 28,
                        "上一个", prevHover, DungeonGuiStyle.ButtonTone.GRAY, manageCursor > 0);
                DungeonGuiStyle.button(g, minecraft, 207, 235, 64, 28,
                        "下一个", nextHover, DungeonGuiStyle.ButtonTone.GRAY, manageCursor < other.size() - 1);
                target(117, 235, 64, 28, manageCursor > 0 ? -4 : Integer.MIN_VALUE, null, "上一个队员");
                target(207, 235, 64, 28, manageCursor < other.size() - 1 ? -5 : Integer.MIN_VALUE, null, "下一个队员");
            }

            boolean leaderHover = inside(mx, my, 70, 277, 248, 56);
            DungeonGuiStyle.button(g, minecraft, 70, 277, 248, 56,
                    "转让队长给 " + m.name, leaderHover, DungeonGuiStyle.ButtonTone.GOLD, m.online);
            target(70, 277, 248, 56,
                    m.online ? PartyMenu.ACTION_LEADER_BASE + m.index : Integer.MIN_VALUE, null,
                    m.online ? "把队长转让给 " + m.name : "离线队员不能接任队长");

            boolean kickHover = inside(mx, my, 70, 340, 248, 56);
            DungeonGuiStyle.button(g, minecraft, 70, 340, 248, 56,
                    "移出队伍", kickHover, DungeonGuiStyle.ButtonTone.RED, true);
            target(70, 340, 248, 56,
                    PartyMenu.ACTION_KICK_BASE + m.index, null,
                    "将 " + m.name + " 移出队伍");
        }

        boolean backHover = inside(mx, my, 112, 404, 164, 40);
        DungeonGuiStyle.button(g, minecraft, 112, 404, 164, 40,
                "返回组队系统", backHover, DungeonGuiStyle.ButtonTone.GRAY, true);
        target(112, 404, 164, 40, -1, Page.MAIN, "返回组队系统");
    }

    private void renderDungeons(GuiGraphics g, CompoundTag meta, float mx, float my) {
        CompoundTag bound = tag(menu.stateStack(PartyMenu.BOUND_DUNGEON_SLOT));
        boolean enabled = bound.getBoolean("Enabled");
        String id = bound.getString("Id");

        DungeonGuiStyle.innerPanel(g, 28, 56, 328, 278);
        g.drawCenteredString(font, "当前副本柱子", 192, 68, DungeonGuiStyle.MUTED);

        DungeonGuiStyle.IconRegion icon = iconFor(id);
        drawDungeonCard(g, bound, 104, 88, icon);

        boolean hover = inside(mx, my, 42, 348, 300, 50);
        DungeonGuiStyle.button(g, minecraft, 42, 348, 300, 50,
                enabled ? "查看挑战条件" : "该副本当前不可用",
                hover, enabled ? DungeonGuiStyle.ButtonTone.GREEN : DungeonGuiStyle.ButtonTone.GRAY, enabled);
        target(42, 348, 300, 50,
                enabled ? -1 : Integer.MIN_VALUE,
                enabled ? Page.CONFIRM : null,
                enabled ? "查看挑战条件；副本不能在GUI内切换" : "管理员需要检查柱子绑定或副本配置");
    }

    private void drawDungeonCard(GuiGraphics g, CompoundTag t, int x, int y,
                                 DungeonGuiStyle.IconRegion icon) {
        DungeonGuiStyle.card(g, x, y, 176, 226, false, t.getBoolean("Enabled"), DungeonGuiStyle.ButtonTone.GOLD);
        DungeonGuiStyle.bossIcon(g, icon, x + 50, y + 25, 76, 66);

        String name = t.getString("DisplayName");
        if (name.isBlank()) name = "未配置";
        g.drawCenteredString(font, name, x + 88, y + 105, DungeonGuiStyle.TEXT);
        g.drawCenteredString(font,
                "推荐人数：" + t.getInt("MinPlayers") + "-" + t.getInt("MaxPlayers") + "人",
                x + 88, y + 141, DungeonGuiStyle.TEXT);
        g.drawCenteredString(font,
                "副本时限：" + minutes(t.getInt("TimeLimit")) + " 分钟",
                x + 88, y + 167, DungeonGuiStyle.TEXT);
        g.drawCenteredString(font,
                t.getBoolean("Enabled") ? "已启用" : "未启用",
                x + 88, y + 194,
                t.getBoolean("Enabled") ? 0xFF188F38 : 0xFFC92F2F);
    }

    private void renderConfirm(GuiGraphics g, CompoundTag meta, float mx, float my) {
        DungeonData selected = selectedDungeon();
        if (selected == null) {
            DungeonGuiStyle.innerPanel(g, 30, 74, 292, 226);
            g.drawCenteredString(font, "当前没有可用副本", 176, 180, 0xFFC92F2F);

            boolean backHover = inside(mx, my, 100, 344, 152, 50);
            DungeonGuiStyle.button(g, minecraft, 100, 344, 152, 50,
                    "返回", backHover, DungeonGuiStyle.ButtonTone.GRAY, true);
            target(100, 344, 152, 50, -1, Page.DUNGEONS, "返回副本选择");
            return;
        }

        DungeonGuiStyle.innerPanel(g, 30, 64, 292, 250);
        DungeonGuiStyle.IconRegion icon = iconFor(selected.id);
        DungeonGuiStyle.bossIcon(g, icon, 48, 86, 74, 68);

        int ready = 0;
        for (MemberData m : members()) if (m.ready) ready++;
        int total = Math.max(0, meta.getInt("MemberTotal"));

        g.drawString(font, "副本：" + selected.displayName, 136, 84, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "队伍人数：" + total, 136, 118, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "副本时限：" + minutes(selected.timeLimit) + " 分钟",
                136, 152, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "团队复活：" + reviveText(selected, total),
                136, 186, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "准备状态：" + ready + " / " + total,
                136, 220,
                meta.getBoolean("AllReady") ? 0xFF168D36 : 0xFFC72E2E, false);

        // 实时准备进度条。
        g.fill(48, 264, 304, 292, 0xFF6E7073);
        int segments = Math.max(1, total);
        int usable = 248;
        for (int i = 0; i < segments; i++) {
            int sx = 52 + i * usable / segments;
            int ex = 52 + (i + 1) * usable / segments - 3;
            g.fill(sx, 268, ex, 288, i < ready ? 0xFF1DB943 : 0xFF4F5154);
        }

        boolean canStart = meta.getBoolean("ViewerLeader")
                && meta.getBoolean("AllReady")
                && selected.enabled
                && !meta.getBoolean("Locked");

        boolean startHover = inside(mx, my, 18, 344, 151, 56);
        DungeonGuiStyle.button(g, minecraft, 18, 344, 151, 56,
                "开始挑战", startHover, DungeonGuiStyle.ButtonTone.GREEN, canStart);
        target(18, 344, 151, 56,
                canStart ? PartyMenu.ACTION_START : Integer.MIN_VALUE, null,
                canStart ? "服务端将再次检查人数、准备状态和副本配置"
                        : (!meta.getBoolean("ViewerLeader") ? "只有队长可以开始挑战" : "所有队员必须准备完成"));

        boolean cancelHover = inside(mx, my, 183, 344, 151, 56);
        DungeonGuiStyle.button(g, minecraft, 183, 344, 151, 56,
                "取消", cancelHover, DungeonGuiStyle.ButtonTone.GRAY, true);
        target(183, 344, 151, 56, -1, Page.DUNGEONS, "取消并返回副本选择");
    }

    private void drawFunctionCard(GuiGraphics g, int x, int y, int w, int h,
                                  ItemStack icon, String title, String line1, String line2,
                                  boolean enabled, boolean hovered, DungeonGuiStyle.ButtonTone tone) {
        DungeonGuiStyle.card(g, x, y, w, h, hovered, enabled, tone);

        float scale = w >= 110 ? 2.55F : 2.25F;
        int iconSize = Math.round(16 * scale);
        drawItemIcon(g, icon, x + (w - iconSize) / 2, y + 18, scale);

        int titleColor = enabled ? DungeonGuiStyle.TEXT : 0xFF8B8B8B;
        int descColor = enabled ? 0xFF444444 : 0xFF8F8F8F;
        g.drawCenteredString(font, title, x + w / 2, y + 78, titleColor);
        g.drawCenteredString(font, line1, x + w / 2, y + 103, descColor);
        g.drawCenteredString(font, line2, x + w / 2, y + 118, descColor);
    }

    private void drawItemIcon(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 40.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void target(int x, int y, int w, int h, int action, Page page, String tooltip) {
        targets.add(new HitTarget(x, y, w, h, action, page, tooltip));
    }

    private List<MemberData> members() {
        List<MemberData> out = new ArrayList<>();
        for (int i = 0; i < PartyMenu.MEMBER_COUNT; i++) {
            CompoundTag t = tag(menu.stateStack(PartyMenu.MEMBER_START + i));
            if (!"member".equals(t.getString("YDType"))) continue;
            UUID uuid = parseUuid(t.getString("Uuid"));
            if (uuid == null) continue;
            out.add(new MemberData(uuid, t.getString("Name"), t.getBoolean("Leader"),
                    t.getBoolean("Ready"), t.getBoolean("Online"), t.getInt("Index")));
        }
        return out;
    }

    private List<InviteData> invites() {
        List<InviteData> out = new ArrayList<>();
        for (int i = 0; i < PartyMenu.INVITE_COUNT; i++) {
            CompoundTag t = tag(menu.stateStack(PartyMenu.INVITE_START + i));
            if (!"invite".equals(t.getString("YDType"))) continue;
            UUID uuid = parseUuid(t.getString("Uuid"));
            if (uuid != null) out.add(new InviteData(uuid, t.getString("Name"), t.getInt("Index")));
        }
        return out;
    }

    private DungeonData selectedDungeon() {
        CompoundTag bound = tag(menu.stateStack(PartyMenu.BOUND_DUNGEON_SLOT));
        if (!"dungeon".equals(bound.getString("YDType"))) return null;
        return dungeonData(bound);
    }

    private DungeonGuiStyle.IconRegion iconFor(String id) {
        return "sakura".equalsIgnoreCase(id)
                ? DungeonGuiStyle.SAKURA_ICON
                : DungeonGuiStyle.CLEOPATRA_ICON;
    }

    private String selectedDungeonName() {
        DungeonData data = selectedDungeon();
        return data == null ? "未选择" : data.displayName;
    }

    private int selectedMaxPlayers() {
        DungeonData data = selectedDungeon();
        return data == null ? 5 : data.maxPlayers;
    }

    private DungeonData dungeonData(CompoundTag t) {
        return new DungeonData(
                t.getString("Id"),
                t.getString("DisplayName"),
                t.getBoolean("Enabled"),
                t.getInt("MinPlayers"),
                t.getInt("MaxPlayers"),
                t.getInt("TimeLimit"),
                t.getString("ReviveMode"),
                t.getInt("FixedRevives")
        );
    }

    private static String reviveText(DungeonData data, int members) {
        return "fixed".equalsIgnoreCase(data.reviveMode)
                ? data.fixedRevives + " 次"
                : Math.max(0, members) + " 次（按人数）";
    }

    private static int minutes(int seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.hasTag() ? stack.getTag() : new CompoundTag();
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float lx = (float) ((mouseX - uiX) / uiScale);
        float ly = (float) ((mouseY - uiY) / uiScale);

        for (int i = targets.size() - 1; i >= 0; i--) {
            HitTarget target = targets.get(i);
            if (!target.contains(lx, ly) || target.action == Integer.MIN_VALUE) continue;

            playClick();

            // 纯客户端翻页，不修改服务端权威状态。
            if (target.action == -2) {
                invitePage = Math.max(0, invitePage - 1);
                return true;
            }
            if (target.action == -3) {
                invitePage++;
                return true;
            }
            if (target.action == -4) {
                manageCursor = Math.max(0, manageCursor - 1);
                return true;
            }
            if (target.action == -5) {
                manageCursor++;
                return true;
            }

            if (target.page != null) page = target.page;
            if (target.action >= 0) sendAction(target.action);
            return true;
        }

        // 隐藏状态槽不接收鼠标操作。
        return true;
    }

    private void sendAction(int action) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private enum Page { MAIN, INFO, INVITE, MANAGE, DUNGEONS, CONFIRM }

    private record ScreenSize(int w, int h) {}

    private record HitTarget(int x, int y, int w, int h, int action, Page page, String tooltip) {
        boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record MemberData(UUID uuid, String name, boolean leader,
                              boolean ready, boolean online, int index) {}

    private record InviteData(UUID uuid, String name, int index) {}

    private record DungeonData(String id, String displayName, boolean enabled,
                               int minPlayers, int maxPlayers, int timeLimit,
                               String reviveMode, int fixedRevives) {}
}
