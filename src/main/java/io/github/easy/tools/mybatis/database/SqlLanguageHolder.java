package io.github.easy.tools.mybatis.database;

import com.intellij.lang.Language;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * SQL 语言反射持有者
 * <p>
 * SqlLanguage 类的包路径在平台版本间发生过迁移
 * (2024.x-2025.x: com.intellij.sql.SqlLanguage，
 * 2026.x 起: com.intellij.sql.psi.SqlLanguage)，
 * 通过反射按序查找，保证 2024.1+ 全版本兼容。
 * 本类位于可选依赖 Database 插件的模块中，仅 IU 环境加载。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
final class SqlLanguageHolder {

    /**
     * 候选类名(按版本从新到旧)
     */
    private static final String[] SQL_LANGUAGE_CLASSES = {
            "com.intellij.sql.psi.SqlLanguage",
            "com.intellij.sql.SqlLanguage"
    };

    /**
     * 缓存解析结果(解析失败也缓存，避免重复反射)
     */
    private static volatile Language cached;

    private SqlLanguageHolder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析通用 SQL 语言实例
     *
     * @return SQL语言实例，不可用时返回null
     * @since 1.1.0
     */
    static @Nullable Language getSqlLanguage() {
        if (cached != null) {
            return cached;
        }
        synchronized (SqlLanguageHolder.class) {
            if (cached != null) {
                return cached;
            }
            for (String className : SQL_LANGUAGE_CLASSES) {
                try {
                    Class<?> clazz = Class.forName(className);
                    Field instance = clazz.getField("INSTANCE");
                    Object value = instance.get(null);
                    if (value instanceof Language language) {
                        cached = language;
                        return cached;
                    }
                } catch (Throwable ignored) {
                    // 当前版本无此类，尝试下一个候选
                }
            }
            return null;
        }
    }
}
