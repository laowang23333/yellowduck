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
 * YellowDuck 副本组队主界面。
 *
 * 视觉层参考三栏式副本大厅：左侧副本、中间队员、右侧副本信息，底部为邮箱和操作按钮。
 * 所有玩家、准备、配置奖励、邮箱、开本状态仍由 PartyMenu 服务端实时同步。
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
        this.imageWidth = 640;
        this.imageHeight = 405;
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
        sendAction(PartyMenu.ACTION_REFRESH);
    }

    @Override
    protected void containerTick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            sendAction(PartyMenu.ACTION_REFRESH);
        }
    }

    private ScreenSize screenSize() {
        return page == Page.MAIN ? new ScreenSize(640, 405) : new ScreenSize(470, 405);
    }

    private void updateTransform() {
        ScreenSize s = screenSize();
        uiScale = Math.min(1.0F, Math.min((width - 18.0F) / s.w, (height - 18.0F) / s.h));
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
        if (!meta.getBoolean("HasParty") && page == Page.MANAGE) page = Page.MAIN;
        if (page == Page.MANAGE && (!meta.getBoolean("ViewerLeader") || meta.getBoolean("Locked"))) page = Page.MAIN;
        if (page == Page.INVITE && (!meta.getBoolean("ViewerLeader") || meta.getBoolean("Locked"))) page = Page.MAIN;

        updateTransform();
        ScreenSize s = screenSize();
        float mx = (mouseX - uiX) / uiScale;
        float my = (mouseY - uiY) / uiScale;

        g.pose().pushPose();
        g.pose().translate(uiX, uiY, 0);
        g.pose().scale(uiScale, uiScale, 1.0F);

        DungeonGuiStyle.tiledWindow(g, 0, 0, s.w, s.h, true);
        renderHeader(g, s.w, mx, my);

        if (page == Page.MAIN) renderMain(g, meta, mx, my);
        else if (page == Page.INVITE) renderInvite(g, mx, my);
        else renderManage(g, meta, mx, my);

        g.pose().popPose();

        for (HitTarget target : targets) {
            if (target.tooltip != null && !target.tooltip.isBlank() && target.contains(mx, my)) {
                g.renderTooltip(font, Component.literal(target.tooltip), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderHeader(GuiGraphics g, int width, float mx, float my) {
        // 中间标题牌。
        int titleW = 180;
        int titleX = (width - titleW) / 2;
        g.fill(titleX - 4, 6, titleX + titleW + 4, 39, 0xFF6B4022);
        g.fill(titleX - 2, 8, titleX + titleW + 2, 37, 0xFFD4A357);
        g.fill(titleX + 1, 10, titleX + titleW - 1, 35, 0xFFF9ECCE);
        g.drawCenteredString(font, page == Page.MAIN ? "◆  副本组队  ◆" : pageTitle(), width / 2, 18, DungeonGuiStyle.TEXT);

        // 关闭按钮。
        boolean closeHover = inside(mx, my, width - 41, 10, 27, 27);
        int c = closeHover ? 0xFF9A6231 : 0xFF6E4428;
        g.fill(width - 41, 10, width - 14, 37, 0xFF4B2C1B);
        g.fill(width - 39, 12, width - 16, 35, c);
        g.drawCenteredString(font, "×", width - 27, 19, 0xFFFFF1D7);
        target(width - 41, 10, 27, 27, -9, null, "关闭");
    }

    private String pageTitle() {
        return page == Page.INVITE ? "邀请玩家" : "队伍管理";
    }

    private void renderMain(GuiGraphics g, CompoundTag meta, float mx, float my) {
        boolean hasParty = meta.getBoolean("HasParty");
        boolean leader = meta.getBoolean("ViewerLeader");
        boolean locked = meta.getBoolean("Locked");
        boolean hasInvite = meta.getBoolean("HasInvite");
        boolean hasMailbox = meta.getBoolean("HasMailbox");

        CompoundTag bound = tag(menu.stateStack(PartyMenu.BOUND_DUNGEON_SLOT));
        DungeonData dungeon = "dungeon".equals(bound.getString("YDType")) ? dungeonData(bound) : null;

        renderDungeonSelect(g, dungeon, mx, my);
        renderMemberPanel(g, meta, hasParty, leader, locked, mx, my);
        renderDungeonInfo(g, dungeon, meta);
        renderBottom(g, meta, dungeon, hasParty, leader, locked, hasInvite, hasMailbox, mx, my);
    }

    private void renderDungeonSelect(GuiGraphics g, DungeonData dungeon, float mx, float my) {
        int x = 14, y = 53, w = 151, h = 264;
        DungeonGuiStyle.panel(g, x, y, w, h);
        DungeonGuiStyle.sectionHeader(g, minecraft, x + 7, y + 7, w - 14, "♟  选择副本");

        int cardX = x + 10, cardY = y + 37, cardW = w - 20, cardH = 60;
        boolean enabled = dungeon != null && dungeon.enabled;
        DungeonGuiStyle.card(g, cardX, cardY, cardW, cardH, inside(mx, my, cardX, cardY, cardW, cardH),
                enabled, DungeonGuiStyle.ButtonTone.GOLD);

        if (dungeon != null) {
            DungeonGuiStyle.bossIconForDungeon(g, dungeon.id, cardX + 6, cardY + 7, 48, 44);
            g.drawString(font, dungeon.displayName, cardX + 59, cardY + 12, DungeonGuiStyle.TEXT, false);
            // 按预览图保留副本名下方的等级文字。
            g.drawString(font, "Lv.60", cardX + 59, cardY + 35, DungeonGuiStyle.MUTED, false);
        } else {
            drawItemIcon(g, new ItemStack(Items.BARRIER), cardX + 12, cardY + 13, 1.7F);
            g.drawString(font, "未绑定副本", cardX + 59, cardY + 22, 0xFF9B3D36, false);
        }

        // 左栏底部的难度显示，和当前 YellowDuck 精英 Boss 定位保持一致。
        g.drawString(font, "⚔  难度选择", x + 14, y + h - 35, DungeonGuiStyle.TEXT, false);
        DungeonGuiStyle.button(g, minecraft, x + 91, y + h - 44, 48, 27,
                "精英", false, DungeonGuiStyle.ButtonTone.GOLD, enabled);
    }

    private void renderMemberPanel(GuiGraphics g, CompoundTag meta, boolean hasParty,
                                   boolean leader, boolean locked, float mx, float my) {
        int x = 173, y = 53, w = 251, h = 264;
        DungeonGuiStyle.panel(g, x, y, w, h);
        int total = hasParty ? Math.max(0, meta.getInt("MemberTotal")) : 0;
        int max = selectedMaxPlayers();
        DungeonGuiStyle.sectionHeader(g, minecraft, x + 7, y + 7, w - 14,
                "♟  队伍成员  (" + total + "/" + max + ")");

        if (leader && !locked) {
            boolean hover = inside(mx, my, x + w - 63, y + 7, 52, 18);
            DungeonGuiStyle.button(g, minecraft, x + w - 63, y + 6, 52, 19,
                    "管理", hover, DungeonGuiStyle.ButtonTone.GRAY, true);
            target(x + w - 63, y + 6, 52, 19, -1, Page.MANAGE, "踢出成员或转让队长");
        }

        List<MemberData> list = members();
        for (int i = 0; i < 5; i++) {
            int ry = y + 36 + i * 43;
            boolean hovered = inside(mx, my, x + 8, ry, w - 16, 38);
            DungeonGuiStyle.row(g, x + 8, ry, w - 16, 38, hovered);
            if (i >= list.size()) {
                g.drawString(font, "（空位）", x + 55, ry + 15, 0xFF9B8875, false);
                continue;
            }

            MemberData m = list.get(i);
            DungeonGuiStyle.playerFace(g, minecraft, m.uuid, x + 13, ry + 4, 30);
            g.drawString(font, m.name, x + 52, ry + 7, DungeonGuiStyle.TEXT, false);
            if (m.leader) {
                g.drawString(font, "♛ 队长", x + 52, ry + 22, 0xFF9B6A22, false);
            } else {
                g.drawString(font, m.online ? "在线" : "离线", x + 52, ry + 22,
                        m.online ? 0xFF6F765E : 0xFF9B8875, false);
            }

            String state = !m.online ? "离线" : (m.ready ? "✓ 已准备" : "未准备");
            int color = !m.online ? 0xFF8A837B : (m.ready ? 0xFF277B45 : 0xFF9B493E);
            int sx = x + w - 17 - font.width(state);
            g.drawString(font, state, sx, ry + 14, color, false);
        }
    }

    private void renderDungeonInfo(GuiGraphics g, DungeonData dungeon, CompoundTag meta) {
        int x = 432, y = 53, w = 194, h = 264;
        DungeonGuiStyle.panel(g, x, y, w, h);
        DungeonGuiStyle.sectionHeader(g, minecraft, x + 7, y + 7, w - 14, "▣  副本信息");

        if (dungeon == null) {
            g.drawCenteredString(font, "当前柱子未绑定可用副本", x + w / 2, y + 130, 0xFF9B493E);
            return;
        }

        // Boss 横幅区域：直接使用该 Boss 当前血量 HUD 所使用的头像资源。
        int bx = x + 10, by = y + 36, bw = w - 20, bh = 79;
        g.fill(bx, by, bx + bw, by + bh, 0xFF3A2924);
        g.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, 0xFF5A3B31);
        DungeonGuiStyle.bossIconForDungeon(g, dungeon.id, bx + 6, by + 5, 78, 66);
        g.fill(bx + 2, by + bh - 22, bx + bw - 2, by + bh - 2, 0xAA2B1712);
        g.drawString(font, dungeon.displayName, bx + 8, by + bh - 17, 0xFFFFF1D6, false);

        g.drawString(font, "当前副本柱子绑定的精英挑战。", x + 12, y + 123, DungeonGuiStyle.MUTED, false);
        g.drawString(font, "全员准备后由队长开始挑战。", x + 12, y + 137, DungeonGuiStyle.MUTED, false);

        g.fill(x + 10, y + 157, x + w - 10, y + 158, 0x5576532F);
        g.drawString(font, "♟  队伍人数", x + 13, y + 169, DungeonGuiStyle.TEXT, false);
        g.drawString(font, meta.getInt("MemberTotal") + " 人", x + w - 42, y + 169, DungeonGuiStyle.TEXT, false);
        g.drawString(font, "♥  队伍复活次数", x + 13, y + 190, DungeonGuiStyle.TEXT, false);
        g.drawString(font, reviveText(dungeon, Math.max(0, meta.getInt("MemberTotal"))),
                x + w - 54, y + 190, DungeonGuiStyle.TEXT, false);

        List<ItemStack> preview = configuredDrops();
        if (!preview.isEmpty()) {
            g.fill(x + 10, y + 215, x + w - 10, y + 216, 0x5576532F);
            g.drawCenteredString(font, "◆  奖励预览  ◆", x + w / 2, y + 223, DungeonGuiStyle.TEXT);
            int size = 27;
            int gap = 5;
            int columns = 5;
            int startX = x + 17;
            int startY = y + 239;
            int visible = Math.min(5, preview.size());
            for (int i = 0; i < visible; i++) {
                int col = i % columns;
                int sx = startX + col * (size + gap);
                int sy = startY;
                DungeonGuiStyle.rewardSlot(g, sx, sy, size);
                ItemStack stack = preview.get(i);
                g.renderItem(stack, sx + 5, sy + 5);
            }
        }
    }

    private void renderBottom(GuiGraphics g, CompoundTag meta, DungeonData dungeon,
                              boolean hasParty, boolean leader, boolean locked,
                              boolean hasInvite, boolean hasMailbox, float mx, float my) {
        int y = 324;

        // 左下角待领取邮箱。
        int mailX = 14, mailY = y, mailW = 203, mailH = 67;
        boolean mailHover = inside(mx, my, mailX, mailY, mailW, mailH);
        DungeonGuiStyle.card(g, mailX, mailY, mailW, mailH, mailHover, hasMailbox,
                hasMailbox ? DungeonGuiStyle.ButtonTone.GOLD : DungeonGuiStyle.ButtonTone.GRAY);
        drawItemIcon(g, new ItemStack(Items.CHEST), mailX + 13, mailY + 13, 2.25F);
        g.drawString(font, "待领取邮箱", mailX + 62, mailY + 12, DungeonGuiStyle.TEXT, false);
        if (hasMailbox) {
            int stacks = Math.max(1, meta.getInt("MailboxStacks"));
            g.drawString(font, "有 " + stacks + " 堆副本奖励待领取", mailX + 62, mailY + 31, 0xFF9C4B2D, false);
            g.drawString(font, "未领取时无法开启下一副本", mailX + 62, mailY + 47, 0xFF9C4B2D, false);
            target(mailX, mailY, mailW, mailH, PartyMenu.ACTION_MAILBOX, null,
                    "打开待领取邮箱；背包放不下的奖励会掉在脚下");
        } else {
            g.drawString(font, "当前没有待领取奖励", mailX + 62, mailY + 35, DungeonGuiStyle.MUTED, false);
            target(mailX, mailY, mailW, mailH, Integer.MIN_VALUE, null, "当前没有待领取奖励");
        }

        // 第一排：创建 / 加入 / 邀请。
        int bx = 225;
        int bw = 126;
        int gap = 6;
        boolean create = !hasParty;
        buttonTarget(g, bx, y, bw, 29, "创建队伍", create, mx, my,
                DungeonGuiStyle.ButtonTone.GRAY,
                create ? PartyMenu.ACTION_CREATE : Integer.MIN_VALUE, null,
                create ? "创建一个新的冒险队伍" : "你已经在队伍中");

        boolean join = !hasParty && hasInvite;
        buttonTarget(g, bx + bw + gap, y, bw, 29, "加入队伍", join, mx, my,
                DungeonGuiStyle.ButtonTone.GRAY,
                join ? PartyMenu.ACTION_ACCEPT : Integer.MIN_VALUE, null,
                join ? "接受当前收到的队伍邀请" : (hasParty ? "你已经在队伍中" : "当前没有队伍邀请"));

        boolean invite = hasParty && leader && !locked;
        buttonTarget(g, bx + (bw + gap) * 2, y, 132, 29, "邀请玩家", invite, mx, my,
                DungeonGuiStyle.ButtonTone.GRAY,
                invite ? -1 : Integer.MIN_VALUE, invite ? Page.INVITE : null,
                invite ? "邀请副本柱子20格内的玩家" : "只有未锁定队伍的队长可以邀请玩家");

        // 第二排：准备 / 开始挑战 / 离队。
        boolean ready = hasParty && !locked;
        String readyText = meta.getBoolean("ViewerReady") ? "✓ 取消准备" : "✓ 准备";
        buttonTarget(g, bx, y + 36, bw, 29, readyText, ready, mx, my,
                DungeonGuiStyle.ButtonTone.GREEN,
                ready ? PartyMenu.ACTION_READY : Integer.MIN_VALUE, null,
                locked ? "副本进行中无法修改准备状态" : "切换自己的准备状态");

        boolean canStart = hasParty && leader && meta.getBoolean("AllReady")
                && dungeon != null && dungeon.enabled && !locked && !hasMailbox;
        String startTip;
        if (hasMailbox) startTip = "请先领取待领取邮箱中的副本奖励";
        else if (!leader) startTip = "只有队长可以开始挑战";
        else if (!meta.getBoolean("AllReady")) startTip = "所有队员必须准备完成";
        else startTip = "服务端会再次检查人数、冷却和所有队员的待领取邮箱";
        buttonTarget(g, bx + bw + gap, y + 36, 164, 29, "⚔ 开始挑战", canStart, mx, my,
                DungeonGuiStyle.ButtonTone.GOLD,
                canStart ? PartyMenu.ACTION_START : Integer.MIN_VALUE, null, startTip);

        boolean leave = hasParty && !locked;
        buttonTarget(g, bx + bw + gap + 170, y + 36, 94, 29, "离开队伍", leave, mx, my,
                DungeonGuiStyle.ButtonTone.RED,
                leave ? PartyMenu.ACTION_LEAVE : Integer.MIN_VALUE, null,
                locked ? "副本进行中无法直接离开队伍" : "离开当前冒险队伍");
    }

    private void renderInvite(GuiGraphics g, float mx, float my) {
        List<InviteData> all = invites();
        int perPage = 5;
        int maxPage = Math.max(0, (all.size() - 1) / perPage);
        invitePage = Math.max(0, Math.min(invitePage, maxPage));
        int start = invitePage * perPage;

        DungeonGuiStyle.panel(g, 24, 55, 422, 285);
        DungeonGuiStyle.sectionHeader(g, minecraft, 34, 65, 402,
                "副本柱子 20 格内可邀请玩家   共 " + all.size() + " 人");

        for (int row = 0; row < perPage; row++) {
            int yy = 94 + row * 46;
            DungeonGuiStyle.row(g, 38, yy, 394, 40, false);
            int idx = start + row;
            if (idx >= all.size()) {
                g.drawString(font, "（空位）", 86, yy + 15, DungeonGuiStyle.MUTED, false);
                continue;
            }
            InviteData p = all.get(idx);
            DungeonGuiStyle.playerFace(g, minecraft, p.uuid, 44, yy + 5, 30);
            g.drawString(font, p.name, 85, yy + 8, DungeonGuiStyle.TEXT, false);
            g.drawString(font, "● 在线", 85, yy + 23, 0xFF2D7848, false);

            boolean hover = inside(mx, my, 337, yy + 7, 82, 27);
            DungeonGuiStyle.button(g, minecraft, 337, yy + 7, 82, 27,
                    "邀请", hover, DungeonGuiStyle.ButtonTone.GREEN, true);
            target(337, yy + 7, 82, 27, PartyMenu.ACTION_INVITE_BASE + p.index, null,
                    "邀请 " + p.name + " 加入队伍");
        }

        if (maxPage > 0) {
            buttonTarget(g, 75, 350, 80, 27, "上一页", invitePage > 0, mx, my,
                    DungeonGuiStyle.ButtonTone.GRAY, invitePage > 0 ? -2 : Integer.MIN_VALUE,
                    null, "上一页");
            buttonTarget(g, 315, 350, 80, 27, "下一页", invitePage < maxPage, mx, my,
                    DungeonGuiStyle.ButtonTone.GRAY, invitePage < maxPage ? -3 : Integer.MIN_VALUE,
                    null, "下一页");
            g.drawCenteredString(font, (invitePage + 1) + " / " + (maxPage + 1), 235, 359, DungeonGuiStyle.TEXT);
        }

        buttonTarget(g, 180, 350, 110, 27, "返回", true, mx, my,
                DungeonGuiStyle.ButtonTone.GOLD, -1, Page.MAIN, "返回副本组队");
    }

    private void renderManage(GuiGraphics g, CompoundTag meta, float mx, float my) {
        UUID viewer = parseUuid(meta.getString("ViewerUuid"));
        List<MemberData> other = new ArrayList<>();
        for (MemberData m : members()) {
            if (viewer == null || !m.uuid.equals(viewer)) other.add(m);
        }

        DungeonGuiStyle.panel(g, 55, 65, 360, 255);
        if (other.isEmpty()) {
            g.drawCenteredString(font, "目前没有可管理的其他队员", 235, 180, DungeonGuiStyle.MUTED);
        } else {
            manageCursor = Math.max(0, Math.min(manageCursor, other.size() - 1));
            MemberData m = other.get(manageCursor);
            DungeonGuiStyle.playerFace(g, minecraft, m.uuid, 85, 105, 72);
            g.drawString(font, m.name, 182, 105, DungeonGuiStyle.TEXT, false);
            g.drawString(font, "状态：" + (m.online ? "在线" : "离线"), 182, 132,
                    m.online ? 0xFF2D7848 : DungeonGuiStyle.MUTED, false);
            g.drawString(font, "准备：" + (m.ready ? "已准备" : "未准备"), 182, 155,
                    m.ready ? 0xFF2D7848 : 0xFF9B493E, false);

            buttonTarget(g, 88, 205, 290, 34, "转让队长给 " + m.name, m.online, mx, my,
                    DungeonGuiStyle.ButtonTone.GOLD,
                    m.online ? PartyMenu.ACTION_LEADER_BASE + m.index : Integer.MIN_VALUE,
                    null, m.online ? "把队长转让给 " + m.name : "离线队员不能接任队长");
            buttonTarget(g, 88, 249, 290, 34, "移出队伍", true, mx, my,
                    DungeonGuiStyle.ButtonTone.RED,
                    PartyMenu.ACTION_KICK_BASE + m.index, null, "将 " + m.name + " 移出队伍");

            if (other.size() > 1) {
                buttonTarget(g, 92, 329, 76, 27, "上一个", manageCursor > 0, mx, my,
                        DungeonGuiStyle.ButtonTone.GRAY, manageCursor > 0 ? -4 : Integer.MIN_VALUE,
                        null, "上一个队员");
                buttonTarget(g, 302, 329, 76, 27, "下一个", manageCursor < other.size() - 1, mx, my,
                        DungeonGuiStyle.ButtonTone.GRAY, manageCursor < other.size() - 1 ? -5 : Integer.MIN_VALUE,
                        null, "下一个队员");
            }
        }

        buttonTarget(g, 185, 365, 100, 27, "返回", true, mx, my,
                DungeonGuiStyle.ButtonTone.GOLD, -1, Page.MAIN, "返回副本组队");
    }

    private void buttonTarget(GuiGraphics g, int x, int y, int w, int h, String text,
                              boolean enabled, float mx, float my, DungeonGuiStyle.ButtonTone tone,
                              int action, Page targetPage, String tooltip) {
        boolean hover = inside(mx, my, x, y, w, h);
        DungeonGuiStyle.button(g, minecraft, x, y, w, h, text, hover, tone, enabled);
        target(x, y, w, h, enabled ? action : Integer.MIN_VALUE, enabled ? targetPage : null, tooltip);
    }

    private void drawItemIcon(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 40.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
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

    private List<ItemStack> configuredDrops() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < PartyMenu.DROP_PREVIEW_COUNT; i++) {
            ItemStack stack = menu.stateStack(PartyMenu.DROP_PREVIEW_START + i);
            if (!stack.isEmpty()) out.add(stack);
        }
        return out;
    }

    private int selectedMaxPlayers() {
        CompoundTag t = tag(menu.stateStack(PartyMenu.BOUND_DUNGEON_SLOT));
        int max = t.getInt("MaxPlayers");
        return max > 0 ? max : 5;
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
                : Math.max(0, members) + " 次";
    }

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void target(int x, int y, int w, int h, int action, Page targetPage, String tooltip) {
        targets.add(new HitTarget(x, y, w, h, action, targetPage, tooltip));
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

            if (target.action == -9) {
                onClose();
                return true;
            }
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

    private enum Page { MAIN, INVITE, MANAGE }

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
