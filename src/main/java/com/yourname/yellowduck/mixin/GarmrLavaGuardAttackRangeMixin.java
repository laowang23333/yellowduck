package com.yourname.yellowduck.mixin;

/**
 * 已停用。
 *
 * 熔岩守卫 3 格攻击距离现在直接由 GarmrHelperEntity 的近战 Goal 实现，
 * 不再注入 Minecraft 的 MeleeAttackGoal，避免 Forge 混淆映射编译错误。
 */
public final class GarmrLavaGuardAttackRangeMixin {
    private GarmrLavaGuardAttackRangeMixin() {
    }
}
