package io.github.easy.tools.service.mybatis.sql;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * MyBatis SQL 方言解析器扩展点
 * <p>
 * 核心插件通过本扩展点获取 Mapper XML 对应的 SQL 语言/方言，
 * Database 插件可用时(Ultimate)由可选模块注册实现返回 SQL 语言，
 * Community 环境下无实现则降级为纯文本。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public interface MyBatisSqlDialectResolver {

    /**
     * 扩展点名
     */
    ExtensionPointName<MyBatisSqlDialectResolver> EP_NAME =
            ExtensionPointName.create("io.github.easy.tools.mybatis.sqlDialectResolver");

    /**
     * 解析 Mapper XML 文件对应的 SQL 语言
     *
     * @param file Mapper XML文件
     * @return SQL语言，无法解析返回null(降级为纯文本)
     * @since 1.1.0
     */
    @Nullable
    Language resolveSqlLanguage(@Nullable PsiFile file);
}
