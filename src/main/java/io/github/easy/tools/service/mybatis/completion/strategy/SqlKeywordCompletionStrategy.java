package io.github.easy.tools.service.mybatis.completion.strategy;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * SQL 关键字补全策略
 * <p>
 * 在 Mapper XML 的 SQL 语句文本中提供 SQL 关键字补全(无需数据源)，
 * 与参数补全并存，参数项优先展示。当 SQL 语言已被注入(Ultimate 数据源场景)
 * 时由 IDE 原生 SQL 补全接管，本策略自动让位。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class SqlKeywordCompletionStrategy implements CompletionStrategy {

    /**
     * 常用 SQL 关键字(大写展示，输入不区分大小写)
     */
    private static final List<String> SQL_KEYWORDS = List.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE",
            "BETWEEN", "EXISTS", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "ON", "AS",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "ASC", "DESC", "COUNT", "SUM", "AVG",
            "MIN", "MAX", "CREATE", "TABLE", "INDEX", "VIEW", "ALTER", "DROP", "TRUNCATE",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "DEFAULT", "CHECK",
            "CONSTRAINT", "AUTO_INCREMENT", "IF", "EXISTS", "WITH", "RECURSIVE",
            "INTERVAL", "DAY", "MONTH", "YEAR", "NOW", "CURDATE", "CONCAT", "LENGTH",
            "SUBSTRING", "TRIM", "UPPER", "LOWER", "COALESCE", "NULLIF", "CAST", "LEFT",
            "RIGHT", "MOD", "ROUND", "FLOOR", "CEIL", "ABORT", "BEGIN", "COMMIT",
            "ROLLBACK", "TRANSACTION", "GRANT", "REVOKE", "USING", "OVER", "PARTITION"
    );

    @Override
    public boolean supports(@NotNull CompletionContext context) {
        // 在XML属性(如 test)中不提供 SQL 关键字
        if (context.isInXmlAttribute()) {
            return false;
        }
        if (context.getCompletionType() == CompletionContext.CompletionType.SQL_KEYWORD) {
            return true;
        }
        // 与参数补全并存: SQL 语句文本中的普通单词(不在#{}表达式内、非字段链式访问)
        return context.getCompletionType() == CompletionContext.CompletionType.PARAMETER
                && context.getExpressionStartOffset() < 0
                && !context.getCurrentText().contains(".")
                && !context.getCurrentText().contains("#{")
                && !context.getCurrentText().contains("${");
    }

    @Override
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        // SQL 语言已被注入时(Ultimate 数据源场景)由 IDE 原生 SQL 补全接管
        if (com.intellij.lang.injection.InjectedLanguageManager
                .getInstance(context.getProject())
                .isInjectedFragment(context.getPosition().getContainingFile())) {
            return;
        }
        for (String keyword : SQL_KEYWORDS) {
            result.addElement(LookupElementBuilder.create(keyword)
                    .withIcon(AllIcons.Nodes.Static)
                    .bold());
        }
    }
}
