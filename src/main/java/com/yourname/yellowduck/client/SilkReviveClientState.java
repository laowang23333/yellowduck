package com.yourname.yellowduck.client;
/** 客户端死亡界面上的斯尔克20秒复活锁倒计时。 */
public final class SilkReviveClientState {
 private static int ticks;
 private static long lastNanos;
 private SilkReviveClientState() {}
 public static void set(int value) { ticks = Math.max(0, value); lastNanos = System.nanoTime(); }
 public static boolean locked() {
  long now = System.nanoTime();
  if (ticks > 0 && lastNanos > 0) { int elapsed = (int)((now-lastNanos)/50_000_000L); if (elapsed > 0) { ticks = Math.max(0, ticks-elapsed); lastNanos += elapsed*50_000_000L; } }
  return ticks > 0;
 }
}
