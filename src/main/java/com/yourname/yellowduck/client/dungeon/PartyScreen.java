package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.party.PartyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * YellowDuck 组队 / 副本 GUI。
 *
 * v4：真正加载 textures/gui/party 下的 UI PNG。
 * PNG 只提供美术框架；队员、准备状态、副本配置、奖励等动态数据继续由服务端菜单同步，
 * 客户端按钮只发送 menu action，不直接修改队伍数据。
 */
public final class PartyScreen extends AbstractContainerScreen<PartyMenu> {
    private static final ResourceLocation TEX_MAIN = tex("party_main.png");
    private static final ResourceLocation TEX_INFO = tex("party_info.png");
    private static final ResourceLocation TEX_INVITE = tex("party_invite.png");
    private static final ResourceLocation TEX_MANAGE = tex("party_manage.png");
    private static final ResourceLocation TEX_DUNGEON = tex("dungeon_select.png");
    private static final ResourceLocation TEX_CONFIRM = tex("dungeon_confirm.png");

    private final List<HitTarget> targets = new ArrayList<>();
    private Page page = Page.MAIN;
    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;
    private int refreshTicks;
    private int invitePage;
    private int manageCursor;
    private int dungeonFocus = -1; // 0 = 艳后，1 = 小樱

    public PartyScreen(PartyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 384;
        this.imageHeight = 459;
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("yellowduck", "textures/gui/party/" + name);
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
        return switch (page) {
            case MAIN -> new ScreenSize(384, 459);
            case INFO, INVITE -> new ScreenSize(352, 459);
            case MANAGE -> new ScreenSize(388, 459);
            case DUNGEONS -> new ScreenSize(384, 418);
            case CONFIRM -> new ScreenSize(352, 418);
        };
    }

    private ResourceLocation screenTexture() {
        return switch (page) {
            case MAIN -> TEX_MAIN;
            case INFO -> TEX_INFO;
            case INVITE -> TEX_INVITE;
            case MANAGE -> TEX_MANAGE;
            case DUNGEONS -> TEX_DUNGEON;
            case CONFIRM -> TEX_CONFIRM;
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
                && (!meta.getBoolean("ViewerLeader") || meta.getBoolean("Locked"))) page = Page.MAIN;

        updateTransform();
        ScreenSize s = screenSize();
        float mx = (mouseX - uiX) / uiScale;
        float my = (mouseY - uiY) / uiScale;

        g.pose().pushPose();
        g.pose().translate(uiX, uiY, 0);
        g.pose().scale(uiScale, uiScale, 1.0F);
        DungeonGuiStyle.tiledWindow(g, 0, 0, s.w, s.h, true);
        g.drawCenteredString(font, pageTitle(), s.w / 2, 6, DungeonGuiStyle.TEXT);

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

    private String pageTitle() { return switch (page) { case MAIN -> "组队系统"; case INFO -> "队伍信息"; case INVITE -> "邀请玩家"; case MANAGE -> "队伍管理"; case DUNGEONS -> "选择副本"; case CONFIRM -> "开始挑战确认"; }; }

    private void renderMain(GuiGraphics g, CompoundTag meta, float mx, float my) {
        boolean hasParty = meta.getBoolean("HasParty");
        boolean leader = meta.getBoolean("ViewerLeader");
        boolean locked = meta.getBoolean("Locked");
        boolean hasInvite = meta.getBoolean("HasInvite");

        // 7 张功能卡的点击区域直接对应 party_main.png。
        target(20, 68, 82, 154, !hasParty ? PartyMenu.ACTION_CREATE : Integer.MIN_VALUE, null,
                hasParty ? "你已经在队伍中" : "创建一个新的冒险队伍");

        if (!hasParty && hasInvite) {
            // 没有队伍但存在邀请时，这张卡切换成“接受邀请”的入口。
            g.fill(109, 185, 188, 219, 0xE81AA640);
            g.drawCenteredString(font, "接受邀请", 148, 194, 0xFFFFFFFF);
            g.drawCenteredString(font, "已有队伍邀请", 148, 207, 0xFFE8FFE8);
            target(108, 68, 82, 154, PartyMenu.ACTION_ACCEPT, null, "接受当前收到的队伍邀请");
        } else {
            target(108, 68, 82, 154,
                    hasParty && leader && !locked ? -1 : Integer.MIN_VALUE,
                    hasParty && leader && !locked ? Page.INVITE : null,
                    !hasParty ? "请先创建或加入队伍" : (!leader ? "只有队长可以邀请玩家" : (locked ? "副本进行中队伍已锁定" : "邀请副本柱子20格内的玩家")));
        }

        target(196, 68, 82, 154, hasParty ? -1 : Integer.MIN_VALUE, hasParty ? Page.INFO : null,
                hasParty ? "查看队伍成员与准备状态" : "请先创建或加入队伍");
        target(284, 68, 82, 154,
                hasParty && leader && !locked ? -1 : Integer.MIN_VALUE,
                hasParty && leader && !locked ? Page.MANAGE : null,
                !hasParty ? "请先创建或加入队伍" : (!leader ? "只有队长可以管理队伍" : (locked ? "副本进行中队伍已锁定" : "管理队伍成员")));
        target(20, 228, 98, 156, hasParty ? -1 : Integer.MIN_VALUE, hasParty ? Page.DUNGEONS : null,
                hasParty ? "查看当前副本柱子固定绑定的副本" : "请先创建或加入队伍");
        target(125, 228, 123, 156, hasParty ? -1 : Integer.MIN_VALUE, hasParty ? Page.CONFIRM : null,
                hasParty ? "检查准备状态并开始挑战" : "请先创建或加入队伍");
        target(254, 228, 112, 156, hasParty && !locked ? PartyMenu.ACTION_LEAVE : Integer.MIN_VALUE, null,
                locked ? "副本进行中无法直接离开队伍，请先离开副本" : "离开当前队伍");

        // 对不可操作的卡片盖一层半透明灰，视觉上避免“看起来可点但实际不能点”。
        if (hasParty) disabledShade(g, 20, 68, 82, 154);
        if (!hasParty || !leader || locked) {
            if (!(hasInvite && !hasParty)) disabledShade(g, 108, 68, 82, 154);
            disabledShade(g, 284, 68, 82, 154);
        }
        if (!hasParty) {
            disabledShade(g, 196, 68, 82, 154);
            disabledShade(g, 20, 228, 98, 156);
            disabledShade(g, 125, 228, 123, 156);
            disabledShade(g, 254, 228, 112, 156);
        } else if (locked) {
            disabledShade(g, 254, 228, 112, 156);
        }

        if (hasParty) {
            // 主页面右下角只做一条小状态，不改 UI 包原本布局。
            String status = meta.getString("PartyName") + "  ·  " + meta.getInt("MemberTotal") + "/" + selectedMaxPlayers()
                    + "  ·  " + selectedDungeonName();
            g.fill(22, 399, 362, 421, 0xCDE9EAEB);
            g.drawCenteredString(font, status, 192, 406, 0xFF343434);
        }
    }

    private void renderInfo(GuiGraphics g, CompoundTag meta, float mx, float my) {
        // 清除设计稿里的示例 YellowDuck / Player001 等内容，重新绘制服务端真实状态。
        g.fill(82, 67, 326, 126, 0xFFE6E8E9);
        g.drawString(font, "队长： " + meta.getString("LeaderName"), 92, 79, 0xFF202020, false);
        g.drawString(font, "队员： " + meta.getInt("MemberTotal") + " / " + selectedMaxPlayers(), 92, 107, 0xFF202020, false);

        List<MemberData> list = members();
        for (int i = 0; i < 5; i++) {
            int y = 133 + i * 48;
            // 保留原框线，仅覆盖行内示例头像/文字。
            g.fill(32, y + 3, 321, y + 44, 0xFFE0E3E5);
            if (i < list.size()) {
                MemberData m = list.get(i);
                DungeonGuiStyle.playerFace(g, minecraft, m.uuid, 36, y + 6, 30);
                g.drawString(font, m.name + (m.leader ? "  [队长]" : ""), 84, y + 15, 0xFF202020, false);
                String state = !m.online ? "离线" : (m.ready ? "✓ 已准备" : "✕ 未准备");
                int color = !m.online ? 0xFF777777 : (m.ready ? 0xFF188F38 : 0xFFC92F2F);
                g.drawString(font, state, 235, y + 15, color, false);
            } else {
                g.drawString(font, "（空位）", 84, y + 15, 0xFF777777, false);
                g.drawString(font, "—", 274, y + 15, 0xFF777777, false);
            }
        }

        boolean locked = meta.getBoolean("Locked");
        boolean ready = meta.getBoolean("ViewerReady");
        g.fill(214, 409, 315, 438, locked ? 0xFF777777 : 0xFF12A63A);
        g.drawCenteredString(font, locked ? "副本进行中" : (ready ? "取消准备" : "准备"), 265, 419,
                locked ? 0xFFBEBEBE : 0xFFFFFFFF);

        target(23, 392, 141, 56, -1, Page.MAIN, "返回组队系统");
        target(184, 392, 145, 56, locked ? Integer.MIN_VALUE : PartyMenu.ACTION_READY, null,
                locked ? "副本进行中无法修改准备状态" : (ready ? "取消准备" : "标记为已准备"));
    }

    private void renderInvite(GuiGraphics g, CompoundTag meta, float mx, float my) {
        List<InviteData> all = invites();
        int maxPage = Math.max(0, (all.size() - 1) / 3);
        invitePage = Math.max(0, Math.min(invitePage, maxPage));
        int start = invitePage * 3;

        g.fill(55, 101, 328, 123, 0xFFE3E5E6);
        g.drawString(font, "柱子20格内玩家（" + all.size() + "）", 63, 108, 0xFF202020, false);

        for (int row = 0; row < 3; row++) {
            int y = 128 + row * 73;
            g.fill(59, y + 3, 324, y + 58, 0xFFE0E3E5);
            int idx = start + row;
            if (idx < all.size()) {
                InviteData p = all.get(idx);
                DungeonGuiStyle.playerFace(g, minecraft, p.uuid, 67, y + 12, 30);
                g.drawString(font, p.name, 112, y + 13, 0xFF202020, false);
                g.drawString(font, "● 在线", 112, y + 34, 0xFF15923A, false);
                g.fill(258, y + 12, 320, y + 49, 0xFF14A53A);
                g.drawCenteredString(font, "邀请", 289, y + 25, 0xFFFFFFFF);
                target(254, y + 8, 72, 48, PartyMenu.ACTION_INVITE_BASE + p.index, null,
                        "邀请 " + p.name + " 加入队伍");
            }
        }

        // UI 稿一次展示 3 人；在线玩家更多时提供轻量翻页，不破坏原布局。
        if (maxPage > 0) {
            g.fill(117, 354, 235, 378, 0xD9E4E5E7);
            g.drawCenteredString(font, "‹  " + (invitePage + 1) + "/" + (maxPage + 1) + "  ›", 176, 362, 0xFF333333);
            target(117, 354, 45, 24, -2, null, "上一页");
            target(190, 354, 45, 24, -3, null, "下一页");
        }
        if (all.isEmpty()) {
            g.drawCenteredString(font, "副本柱子20格内没有可邀请玩家", 176, 243, 0xFF686868);
        }

        target(128, 398, 168, 53, -1, Page.MAIN, "返回组队系统");
    }

    private void renderManage(GuiGraphics g, CompoundTag meta, float mx, float my) {
        UUID viewer = parseUuid(meta.getString("ViewerUuid"));
        List<MemberData> other = new ArrayList<>();
        for (MemberData m : members()) if (viewer == null || !m.uuid.equals(viewer)) other.add(m);

        if (other.isEmpty()) {
            g.fill(53, 77, 336, 212, 0xFFE2E4E5);
            g.drawCenteredString(font, "目前没有可管理的其他队员", 194, 139, 0xFF666666);
            disabledShade(g, 70, 244, 248, 64);
            disabledShade(g, 70, 315, 248, 65);
        } else {
            manageCursor = Math.max(0, Math.min(manageCursor, other.size() - 1));
            MemberData m = other.get(manageCursor);

            g.fill(57, 83, 331, 213, 0xFFE2E4E5);
            DungeonGuiStyle.playerFace(g, minecraft, m.uuid, 73, 108, 82);
            g.drawString(font, m.name, 178, 107, 0xFF202020, false);
            g.drawString(font, "队伍成员", 178, 137, 0xFF333333, false);
            g.drawString(font, "当前状态： " + (m.ready ? "已准备" : "未准备"), 178, 164,
                    m.ready ? 0xFF178C36 : 0xFFC63434, false);
            g.drawString(font, m.online ? "当前在线" : "当前离线", 178, 190,
                    m.online ? 0xFF178C36 : 0xFF777777, false);

            if (other.size() > 1) {
                g.fill(140, 216, 248, 238, 0xDCE4E5E6);
                g.drawCenteredString(font, "‹  " + (manageCursor + 1) + "/" + other.size() + "  ›", 194, 223, 0xFF333333);
                target(140, 216, 38, 22, -4, null, "上一个队员");
                target(210, 216, 38, 22, -5, null, "下一个队员");
            }

            target(70, 244, 248, 64, m.online ? PartyMenu.ACTION_LEADER_BASE + m.index : Integer.MIN_VALUE, null,
                    m.online ? "把队长转让给 " + m.name : "离线队员不能接任队长");
            if (!m.online) disabledShade(g, 70, 244, 248, 64);
            target(70, 315, 248, 65, PartyMenu.ACTION_KICK_BASE + m.index, null,
                    "将 " + m.name + " 移出队伍");
        }

        target(112, 401, 164, 51, -1, Page.MAIN, "返回组队系统");
    }

    private void renderDungeons(GuiGraphics g, CompoundTag meta, float mx, float my) {
        CompoundTag bound = tag(menu.stateStack(PartyMenu.BOUND_DUNGEON_SLOT));
        boolean enabled = bound.getBoolean("Enabled");
        String id = bound.getString("Id");

        // v6 起一根柱子只绑定一个副本。覆盖原“双副本选择”示例区域，只展示当前柱子的固定副本。
        g.fill(24, 62, 365, 336, 0xE7D9DCDD);
        g.drawCenteredString(font, "当前副本柱子", 194, 78, 0xFF2A2A2A);
        DungeonGuiStyle.IconRegion icon = iconFor(id);
        redrawDungeonCardData(g, bound, 107, 92, true, icon);

        g.fill(28, 344, 329, 58, enabled ? 0xFF13A83A : 0xFF777777);
        g.drawCenteredString(font, enabled ? "此柱子固定进入该副本" : "该副本当前不可用", 192, 364,
                enabled ? 0xFFFFFFFF : 0xFFD0D0D0);
        target(28, 344, 329, 58, enabled ? -1 : Integer.MIN_VALUE,
                enabled ? Page.CONFIRM : null,
                enabled ? "查看挑战条件；副本不能在GUI内切换" : "管理员需要检查柱子绑定或副本配置");
    }

    private void redrawDungeonCardData(GuiGraphics g, CompoundTag t, int x, int y, boolean focused,
                                       DungeonGuiStyle.IconRegion icon) {
        // 卡片大图由 PNG 提供，下面只覆盖文字数据和选中边框。
        g.fill(x + 8, y + 154, x + 164, y + 255, 0xEDE8E9EA);
        String name = t.getString("DisplayName");
        if (name.isBlank()) name = "未配置";
        g.drawCenteredString(font, name, x + 86, y + 166, 0xFF202020);
        g.drawCenteredString(font, "推荐人数：" + t.getInt("MinPlayers") + "-" + t.getInt("MaxPlayers") + "人",
                x + 86, y + 198, 0xFF202020);
        g.drawCenteredString(font, "副本时限：" + minutes(t.getInt("TimeLimit")) + " 分钟",
                x + 86, y + 224, 0xFF202020);

        int border = focused ? 0xFFFFC52A : 0xFF777A7D;
        frame(g, x, y, 174, 270, border, focused ? 3 : 1);
        if (!t.getBoolean("Enabled")) {
            g.fill(x + 3, y + 3, x + 171, y + 267, 0x88000000);
            g.drawCenteredString(font, "未启用", x + 87, y + 132, 0xFFFF7777);
        }
        // 使用现有 Boss 血条头像再叠一次，确保和实际 HUD 头像完全一致。
        DungeonGuiStyle.bossIcon(g, icon, x + 60, y + 58, 54, 48);
    }

    private void renderConfirm(GuiGraphics g, CompoundTag meta, float mx, float my) {
        DungeonData selected = selectedDungeon();
        if (selected == null) {
            g.fill(45, 86, 307, 326, 0xE8E9D5AE);
            g.drawCenteredString(font, "当前没有可用副本", 176, 195, 0xFFC92F2F);
            target(183, 348, 150, 60, -1, Page.DUNGEONS, "返回副本选择");
            disabledShade(g, 18, 348, 151, 60);
            return;
        }

        DungeonGuiStyle.IconRegion icon = iconFor(selected.id);
        g.fill(44, 89, 306, 279, 0xEDE8D4AA);
        DungeonGuiStyle.bossIcon(g, icon, 48, 102, 72, 68);

        int ready = 0;
        for (MemberData m : members()) if (m.ready) ready++;
        int total = Math.max(0, meta.getInt("MemberTotal"));

        g.drawString(font, "副本： " + selected.displayName, 134, 107, 0xFF242424, false);
        g.drawString(font, "队伍人数： " + total, 134, 143, 0xFF242424, false);
        g.drawString(font, "副本时限： " + minutes(selected.timeLimit) + " 分钟", 134, 179, 0xFF242424, false);
        g.drawString(font, "团队复活： " + reviveText(selected, total), 134, 215, 0xFF242424, false);
        g.drawString(font, "准备状态： " + ready + " / " + total, 134, 251,
                meta.getBoolean("AllReady") ? 0xFF168D36 : 0xFFC72E2E, false);

        // 进度条：按真实准备人数覆盖设计稿里的 3/3 示例。
        g.fill(57, 283, 296, 308, 0xFF78725D);
        int segments = Math.max(1, total);
        int usable = 232;
        for (int i = 0; i < segments; i++) {
            int sx = 60 + i * usable / segments;
            int ex = 60 + (i + 1) * usable / segments - 2;
            g.fill(sx, 286, ex, 305, i < ready ? 0xFF18B63F : 0xFF5A5A54);
        }

        boolean canStart = meta.getBoolean("ViewerLeader") && meta.getBoolean("AllReady")
                && selected.enabled && !meta.getBoolean("Locked");
        if (!canStart) disabledShade(g, 18, 348, 151, 60);
        target(18, 348, 151, 60, canStart ? PartyMenu.ACTION_START : Integer.MIN_VALUE, null,
                canStart ? "服务端将再次检查人数、准备状态和副本配置"
                        : (!meta.getBoolean("ViewerLeader") ? "只有队长可以开始挑战" : "所有队员必须准备完成"));
        target(183, 348, 150, 60, -1, Page.DUNGEONS, "取消并返回副本选择");
    }

    private void target(int x, int y, int w, int h, int action, Page page, String tooltip) {
        targets.add(new HitTarget(x, y, w, h, action, page, tooltip));
    }

    private void disabledShade(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x72000000);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int color, int thickness) {
        for (int i = 0; i < thickness; i++) {
            g.fill(x + i, y + i, x + w - i, y + i + 1, color);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, color);
            g.fill(x + i, y + i, x + i + 1, y + h - i, color);
            g.fill(x + w - i - 1, y + i, x + w - i, y + h - i, color);
        }
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
        return "sakura".equalsIgnoreCase(id) ? DungeonGuiStyle.SAKURA_ICON : DungeonGuiStyle.CLEOPATRA_ICON;
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
        return new DungeonData(t.getString("Id"), t.getString("DisplayName"), t.getBoolean("Enabled"),
                t.getInt("MinPlayers"), t.getInt("MaxPlayers"), t.getInt("TimeLimit"),
                t.getString("ReviveMode"), t.getInt("FixedRevives"));
    }

    private static String reviveText(DungeonData data, int members) {
        return "fixed".equalsIgnoreCase(data.reviveMode) ? data.fixedRevives + " 次" : Math.max(0, members) + " 次（按人数）";
    }

    private static int minutes(int seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.hasTag() ? stack.getTag() : new CompoundTag();
    }

    private static UUID parseUuid(String text) {
        try { return UUID.fromString(text); }
        catch (Exception ignored) { return null; }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float lx = (float) ((mouseX - uiX) / uiScale);
        float ly = (float) ((mouseY - uiY) / uiScale);
        for (int i = targets.size() - 1; i >= 0; i--) {
            HitTarget target = targets.get(i);
            if (!target.contains(lx, ly) || target.action == Integer.MIN_VALUE) continue;
            playClick();

            // 纯客户端翻页/选择，不涉及队伍权威数据。
            if (target.action == -2) { invitePage = Math.max(0, invitePage - 1); return true; }
            if (target.action == -3) { invitePage++; return true; }
            if (target.action == -4) { manageCursor = Math.max(0, manageCursor - 1); return true; }
            if (target.action == -5) { manageCursor++; return true; }
            if (target.action == -6) { dungeonFocus = 0; return true; }
            if (target.action == -7) { dungeonFocus = 1; return true; }

            if (target.page != null) page = target.page;
            if (target.action >= 0) sendAction(target.action);
            return true;
        }
        // 隐藏状态槽不接收鼠标操作，避免误操作背包。
        return true;
    }

    private void sendAction(int action) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private enum Page { MAIN, INFO, INVITE, MANAGE, DUNGEONS, CONFIRM }
    private record ScreenSize(int w, int h) {}
    private record HitTarget(int x, int y, int w, int h, int action, Page page, String tooltip) {
        boolean contains(float mx, float my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record MemberData(UUID uuid, String name, boolean leader, boolean ready, boolean online, int index) {}
    private record InviteData(UUID uuid, String name, int index) {}
    private record DungeonData(String id, String displayName, boolean enabled, int minPlayers, int maxPlayers,
                               int timeLimit, String reviveMode, int fixedRevives) {}
}
