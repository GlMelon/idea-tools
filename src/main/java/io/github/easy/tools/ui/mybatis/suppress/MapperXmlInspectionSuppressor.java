package io.github.easy.tools.ui.mybatis.suppress;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Mapper XML inspection 抑制器
 * <p>
 * 抑制 Mapper XML 上无意义的检查噪音:
 * <ul>
 * <li>SQL 类: "SQL dialect is not configured"(SqlDialectInspection)、
 * "No data sources"(SqlNoDataSourceInspection)、当前 Schema 提示 ——
 * Mapper SQL 通过占位符组装、无法静态绑定单一数据源，只会产生噪音</li>
 * <li>拼写检查(SpellCheckingInspection): OGNL test 属性(变量/关键字)
 * 与 SQL 语句文本(表名/列名/关键字)不是自然语言，不参与拼写检查</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MapperXmlInspectionSuppressor implements InspectionSuppressor {

    /**
     * 抑制的 SQL inspection shortName 集合
     */
    private static final Set<String> SUPPRESSED_SQL_INSPECTIONS = Set.of(
            "SqlDialectInspection",
            "SqlNoDataSourceInspection",
            "SqlCurrentSchemaInspection"
    );

    /**
     * 拼写检查 inspection shortName
     */
    private static final String SPELL_CHECKING_INSPECTION = "SpellCheckingInspection";

    /**
     * OGNL 表达式属性: 标签名 -> 属性名集合
     */
    private static final Map<String, Set<String>> OGNL_ATTRIBUTES = Map.of(
            "if", Set.of("test"),
            "when", Set.of("test"),
            "foreach", Set.of("collection", "item", "index", "open", "separator", "close"),
            "bind", Set.of("name", "value")
    );

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (SUPPRESSED_SQL_INSPECTIONS.contains(toolId)) {
            return this.isInStatementText(element);
        }
        if (SPELL_CHECKING_INSPECTION.equals(toolId)) {
            // SQL 语句文本与 OGNL 属性不做拼写检查
            return this.isInStatementText(element) || this.isInOgnlAttribute(element);
        }
        return false;
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }

    /**
     * 判断元素是否位于 Mapper XML 语句标签内的 SQL 文本中
     *
     * @param element 元素
     * @return true如果在语句文本中
     * @since 1.1.0
     */
    private boolean isInStatementText(@NotNull PsiElement element) {
        if (element instanceof XmlText xmlText) {
            XmlTag parentTag = xmlText.getParentTag();
            return parentTag != null
                    && MyBatisUtils.isInMapperFile(parentTag)
                    && MyBatisUtils.findSqlTag(parentTag) != null;
        }
        PsiElement parent = element.getParent();
        return parent instanceof XmlText xmlText
                && xmlText.getParentTag() != null
                && MyBatisUtils.isInMapperFile(xmlText.getParentTag())
                && MyBatisUtils.findSqlTag(xmlText.getParentTag()) != null;
    }

    /**
     * 判断元素是否位于 OGNL 表达式属性中
     *
     * @param element 元素
     * @return true如果在 OGNL 属性中
     * @since 1.1.0
     */
    private boolean isInOgnlAttribute(@NotNull PsiElement element) {
        XmlAttributeValue attributeValue = null;
        if (element instanceof XmlAttributeValue value) {
            attributeValue = value;
        } else {
            PsiElement parent = element.getParent();
            if (parent instanceof XmlAttributeValue value) {
                attributeValue = value;
            }
        }
        if (attributeValue == null
                || !(attributeValue.getParent() instanceof XmlAttribute attribute)) {
            return false;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !MyBatisUtils.isInMapperFile(tag)) {
            return false;
        }
        Set<String> attrs = OGNL_ATTRIBUTES.get(tag.getName());
        return attrs != null && attrs.contains(attribute.getName());
    }
}
