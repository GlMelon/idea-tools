package io.github.easy.tools.service.mybatis.sql;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.github.easy.tools.service.mybatis.MapperXmlCacheService;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis SQL 组装器
 * <p>
 * 将语句标签(含动态标签与 include 引用)组装为可直接执行的 SQL 文本:
 * <ul>
 * <li>include 递归内联(支持 namespace 前缀跨文件引用，带环引用保护)</li>
 * <li>where/set/trim 前缀处理(剥离前导 AND/OR)</li>
 * <li>foreach 展开 open/close 包裹</li>
 * <li>if/when/otherwise 分支全量保留(静态无法求值，组装结果为最全形态)</li>
     * <li>bind/selectKey 跳过</li>
 * <li>#{...} 占位符替换为 ?</li>
 * <li>空白归一化为单空格</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public final class MyBatisSqlAssembler {

    /**
     * 递归保护: include 内联最大深度
     */
    private static final int MAX_INCLUDE_DEPTH = 20;

    private MyBatisSqlAssembler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 组装语句标签的完整 SQL
     *
     * @param statementTag 语句标签(select/insert/update/delete)
     * @return 组装后的 SQL，无法组装返回 null
     * @since 1.1.0
     */
    public static @Nullable String assemble(@NotNull XmlTag statementTag) {
        try {
            StringBuilder sql = new StringBuilder();
            appendChildren(statementTag, sql, new HashSet<>(), 0);
            String result = normalize(sql.toString());
            return StringUtil.isEmpty(result) ? null : result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 追加标签的所有子内容(文本 + 子标签)
     *
     * @param tag             标签
     * @param sql             SQL构建器
     * @param visitedIncludes 已内联的 sql 片段(环引用保护)
     * @param depth           当前递归深度
     * @since 1.1.0
     */
    private static void appendChildren(@NotNull XmlTag tag,
                                       @NotNull StringBuilder sql,
                                       @NotNull Set<XmlTag> visitedIncludes,
                                       int depth) {
        PsiElement child = tag.getFirstChild();
        while (child != null) {
            if (child instanceof XmlText xmlText) {
                sql.append(xmlText.getValue()).append(' ');
            } else if (child instanceof XmlTag childTag) {
                appendTag(childTag, sql, visitedIncludes, depth);
            }
            child = child.getNextSibling();
        }
    }

    /**
     * 处理单个子标签
     *
     * @param tag             子标签
     * @param sql             SQL构建器
     * @param visitedIncludes 已内联的 sql 片段
     * @param depth           递归深度
     * @since 1.1.0
     */
    private static void appendTag(@NotNull XmlTag tag,
                                  @NotNull StringBuilder sql,
                                  @NotNull Set<XmlTag> visitedIncludes,
                                  int depth) {
        String name = tag.getName();
        switch (name) {
            case "include" -> appendInclude(tag, sql, visitedIncludes, depth);
            case "where" -> {
                String content = assembleChildren(tag, visitedIncludes, depth);
                content = stripLeadingAndOr(content);
                if (StringUtil.isNotEmpty(content)) {
                    sql.append(" WHERE ").append(content).append(' ');
                }
            }
            case "set" -> {
                String content = assembleChildren(tag, visitedIncludes, depth);
                content = stripTrailingComma(content);
                if (StringUtil.isNotEmpty(content)) {
                    sql.append(" SET ").append(content).append(' ');
                }
            }
            case "trim" -> {
                String content = assembleChildren(tag, visitedIncludes, depth);
                String prefix = tag.getAttributeValue("prefix");
                String prefixOverrides = tag.getAttributeValue("prefixOverrides");
                if (StringUtil.isNotEmpty(prefixOverrides)) {
                    content = stripLeadingTokens(content, prefixOverrides);
                }
                if (StringUtil.isNotEmpty(content)) {
                    if (StringUtil.isNotEmpty(prefix)) {
                        sql.append(' ').append(prefix).append(' ');
                    }
                    sql.append(content).append(' ');
                }
            }
            case "foreach" -> {
                String open = tag.getAttributeValue("open");
                String close = tag.getAttributeValue("close");
                if (StringUtil.isNotEmpty(open)) {
                    sql.append(' ').append(open).append(' ');
                }
                appendChildren(tag, sql, visitedIncludes, depth);
                if (StringUtil.isNotEmpty(close)) {
                    sql.append(' ').append(close).append(' ');
                }
            }
            case "bind", "selectKey" -> {
                // 变量绑定/主键回填与 SQL 主体无关
            }
            default -> {
                // if/when/otherwise/choose 及未知动态标签: 分支全量保留
                appendChildren(tag, sql, visitedIncludes, depth);
            }
        }
    }

    /**
     * 内联 include 引用的 sql 片段
     *
     * @param includeTag     include标签
     * @param sql            SQL构建器
     * @param visitedIncludes 已内联片段
     * @param depth          递归深度
     * @since 1.1.0
     */
    private static void appendInclude(@NotNull XmlTag includeTag,
                                      @NotNull StringBuilder sql,
                                      @NotNull Set<XmlTag> visitedIncludes,
                                      int depth) {
        if (depth >= MAX_INCLUDE_DEPTH) {
            return;
        }
        String refid = includeTag.getAttributeValue("refid");
        if (StringUtil.isEmpty(refid)) {
            return;
        }
        XmlTag sqlFragment = resolveSqlFragment(refid, includeTag);
        if (sqlFragment == null || !visitedIncludes.add(sqlFragment)) {
            return;
        }
        appendChildren(sqlFragment, sql, visitedIncludes, depth + 1);
    }

    /**
     * 解析 refid 对应的 sql 片段(支持 namespace 前缀跨文件)
     *
     * @param refid    refid 值
     * @param context  上下文元素
     * @return sql 片段标签，未找到返回null
     * @since 1.1.0
     */
    private static @Nullable XmlTag resolveSqlFragment(@NotNull String refid, @NotNull XmlTag context) {
        PsiElement file = context.getContainingFile();
        if (file instanceof XmlFile xmlFile) {
            int dot = refid.indexOf('.');
            if (dot > 0) {
                String namespace = refid.substring(0, dot);
                String realId = refid.substring(dot + 1);
                Project project = context.getProject();
                MapperXmlCacheService cacheService = MapperXmlCacheService.getInstance(project);
                if (!cacheService.isInitialized()) {
                    cacheService.scanAndCacheMapperXmlFiles();
                }
                XmlFile targetFile = cacheService.getXmlFileByNamespace(namespace);
                if (targetFile != null) {
                    return MyBatisUtils.findTagByTagNameAndId(targetFile, "sql", realId);
                }
                return null;
            }
            return MyBatisUtils.findTagByTagNameAndId(xmlFile, "sql", refid);
        }
        return null;
    }

    /**
     * 组装标签子内容为字符串
     *
     * @param tag             标签
     * @param visitedIncludes 已内联片段
     * @param depth           递归深度
     * @return 子内容SQL
     * @since 1.1.0
     */
    private static String assembleChildren(@NotNull XmlTag tag,
                                           @NotNull Set<XmlTag> visitedIncludes,
                                           int depth) {
        StringBuilder sb = new StringBuilder();
        appendChildren(tag, sb, visitedIncludes, depth);
        return normalize(sb.toString());
    }

    /**
     * 剥离前导 AND/OR
     *
     * @param content 内容
     * @return 剥离后的内容
     * @since 1.1.0
     */
    private static String stripLeadingAndOr(String content) {
        return stripLeadingTokens(content, "and |or ");
    }

    /**
     * 剥离前缀覆盖 token(trim/where 的 prefixOverrides)
     *
     * @param content  内容
     * @param overrides 覆盖 token，"|"分隔
     * @return 剥离后的内容
     * @since 1.1.0
     */
    private static String stripLeadingTokens(String content, @NotNull String overrides) {
        String result = content == null ? "" : content.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String token : overrides.split("\\|")) {
                String trimmedToken = token.trim();
                if (StringUtil.isNotEmpty(trimmedToken)
                        && result.length() >= trimmedToken.length()
                        && result.regionMatches(true, 0, trimmedToken, 0, trimmedToken.length())) {
                    result = result.substring(trimmedToken.length()).trim();
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 剥离结尾逗号
     *
     * @param content 内容
     * @return 剥离后的内容
     * @since 1.1.0
     */
    private static String stripTrailingComma(String content) {
        String result = content == null ? "" : content.trim();
        while (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    /**
     * 空白归一化 + 占位符替换
     *
     * @param sql 原始SQL
     * @return 归一化SQL
     * @since 1.1.0
     */
    private static String normalize(@NotNull String sql) {
        return sql.replaceAll("\\s+", " ")
                .replaceAll("#\\{[^}]*}", "?")
                .trim();
    }
}
