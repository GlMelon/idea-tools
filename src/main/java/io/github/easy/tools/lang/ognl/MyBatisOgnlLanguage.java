package io.github.easy.tools.lang.ognl;

import com.intellij.lang.Language;

/**
 * MyBatis OGNL 表达式语言
 * <p>
 * 注入到 Mapper XML 的 if/when test 等属性中，
 * 提供语法高亮、括号匹配、数字/字符串着色等编辑体验。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public final class MyBatisOgnlLanguage extends Language {

    /**
     * 语言实例
     */
    public static final MyBatisOgnlLanguage INSTANCE = new MyBatisOgnlLanguage();

    private MyBatisOgnlLanguage() {
        super("MyBatisOgnl");
    }

    @Override
    public @org.jetbrains.annotations.NotNull String getDisplayName() {
        return "MyBatis OGNL";
    }
}
