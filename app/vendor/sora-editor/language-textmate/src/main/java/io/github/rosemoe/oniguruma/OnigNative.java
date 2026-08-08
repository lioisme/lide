/*
 * 本仓库桩类（非上游源码）：sora-editor 0.24.6 的 language-textmate 以
 * compileOnly 依赖 oniguruma-native 模块的 JNI 类 io.github.rosemoe.oniguruma.OnigNative，
 * 而该模块的 C 源码（oniguruma submodule）未随上游 tag 分发，故此处以纯 Java 桩替代。
 *
 * 运行期行为：NativeOnigConfig.isAvailable() 调用 releaseRegex(0L) 捕获本桩抛出的异常
 * 返回 false → 全部 TextMate 正则走 Joni（纯 Java）实现，与官方 Maven 制品
 * language-textmate:0.24.6（同样不携带 oniguruma-native）完全一致。
 *
 * 本桩方法签名与上游 io.github.rosemoe.oniguruma.OnigNative 保持一致，
 * 仅保证编译期引用闭合；正常流程不会被调用（原生路径必须先过 isAvailable() 判定）。
 */
package io.github.rosemoe.oniguruma;

public final class OnigNative {

    private OnigNative() {
    }

    private static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException(
                "native oniguruma is not bundled in this repository build; use the Joni (pure Java) path");
    }

    public static long newRegex(String pattern, boolean ignoreCase) {
        throw unavailable();
    }

    public static int[] regexSearch(long nativePtr, long cacheKey, byte[] utf8Bytes,
                                    int startPosition, int bytesCount) {
        throw unavailable();
    }

    public static int[] regexSearchBatch(long[] pointers, long cacheKey, byte[] utf8Bytes,
                                         int byteOffset, int bytesCount) {
        throw unavailable();
    }

    public static void releaseRegex(long nativePtr) {
        throw unavailable();
    }
}
