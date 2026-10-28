package io.github.easy.tools.mybatis.database;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import io.github.easy.tools.service.mybatis.sql.MyBatisSqlDialectResolver;
import org.jetbrains.annotations.Nullable;

/**
 * 数据源模块的 SQL 方言解析器
 * <p>
 * Database 插件可用时返回通用 SQL 语言，
 * 使"组装并运行 SQL"生成的临时文件成为可执行的 SQL 控制台文件。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class DatabaseSqlDialectResolver implements MyBatisSqlDialectResolver {

    @Override
    public @Nullable Language resolveSqlLanguage(@Nullable PsiFile file) {
        return SqlLanguageHolder.getSqlLanguage();
    }
}
