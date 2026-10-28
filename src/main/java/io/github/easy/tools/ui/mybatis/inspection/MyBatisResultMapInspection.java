package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MyBatis resultMap 映射检测
 * <p>
 * 检测 resultMap 定义中的两类常见问题:
 * <ul>
 * <li>property 未解析: result/id/collection/association 的 property 属性
 * 在 resultMap type 对应的 Java 类型中不存在(字段重命名后未同步)</li>
 * <li>column 重复: 同一 resultMap 中多个映射指向同一个数据库列</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisResultMapInspection extends LocalInspectionTool {

    /**
     * 映射子标签集合
     */
    private static final Set<String> MAPPING_TAGS = Set.of("result", "id", "collection", "association");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                MyBatisResultMapInspection.this.checkProperty(attribute, holder);
            }

            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
                MyBatisResultMapInspection.this.checkDuplicateColumns(tag, holder);
            }
        };
    }

    /**
     * 检测 property 属性是否能在类型中解析
     *
     * @param attribute 属性
     * @param holder    问题收集器
     * @since 1.1.0
     */
    private void checkProperty(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder) {
        if (!"property".equals(attribute.getName())) {
            return;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !MAPPING_TAGS.contains(tag.getName())) {
            return;
        }
        if (!MyBatisUtils.isInMapperFile(tag)) {
            return;
        }
        XmlTag resultMapTag = MyBatisUtils.findEnclosingResultMap(tag);
        if (resultMapTag == null) {
            return;
        }

        XmlAttributeValue valueElement = attribute.getValueElement();
        String property = attribute.getValue();
        if (valueElement == null || StringUtil.isEmpty(property) || property.contains("${")) {
            return;
        }

        // 类型不可解析时不误报(类型引用检测由其他检查负责)
        PsiClass typeClass = MyBatisUtils.resolveResultMapType(resultMapTag, attribute);
        if (typeClass == null) {
            return;
        }

        // 支持嵌套属性路径: user.name 逐段解析
        String[] segments = property.split("\\.");
        PsiClass currentClass = typeClass;
        boolean resolved = true;
        for (int i = 0; i < segments.length && currentClass != null; i++) {
            PsiElement element = MyBatisUtils.findFieldOrPropertyInClass(currentClass, segments[i]);
            if (element == null) {
                resolved = false;
                break;
            }
            if (i < segments.length - 1) {
                currentClass = MyBatisUtils.getTypeOfResolvedElement(element);
            }
        }

        if (!resolved) {
            holder.registerProblem(valueElement,
                    "属性 '" + property + "' 在类型 '" + typeClass.getName() + "' 中不存在",
                    ProblemHighlightType.WARNING);
        }
    }

    /**
     * 检测 resultMap 中重复的 column 映射
     *
     * @param resultMapTag resultMap标签
     * @param holder       问题收集器
     * @since 1.1.0
     */
    private void checkDuplicateColumns(@NotNull XmlTag resultMapTag, @NotNull ProblemsHolder holder) {
        if (!"resultMap".equals(resultMapTag.getName())) {
            return;
        }
        if (!MyBatisUtils.isInMapperFile(resultMapTag)) {
            return;
        }

        // column -> 出现过的 attribute 值元素
        Map<String, XmlAttributeValue> seen = new HashMap<>();
        Set<XmlAttributeValue> reported = new HashSet<>();
        this.collectColumns(resultMapTag, seen, reported, holder);
    }

    /**
     * 递归收集 column 属性并报告重复
     *
     * @param tag      当前标签
     * @param seen     已见列
     * @param reported 已报告元素(防止一次遍历中重复注册)
     * @param holder   问题收集器
     * @since 1.1.0
     */
    private void collectColumns(@NotNull XmlTag tag,
                                @NotNull Map<String, XmlAttributeValue> seen,
                                @NotNull Set<XmlAttributeValue> reported,
                                @NotNull ProblemsHolder holder) {
        if (MAPPING_TAGS.contains(tag.getName()) || "discriminator".equals(tag.getName())) {
            XmlAttribute columnAttr = tag.getAttribute("column");
            XmlAttributeValue valueElement = columnAttr == null ? null : columnAttr.getValueElement();
            String column = columnAttr == null ? null : columnAttr.getValue();
            if (valueElement != null && StringUtil.isNotEmpty(column)) {
                String key = column.toLowerCase();
                XmlAttributeValue first = seen.get(key);
                if (first != null && !reported.contains(valueElement)) {
                    reported.add(valueElement);
                    holder.registerProblem(valueElement,
                            "列 '" + column + "' 在该 resultMap 中被重复映射",
                            ProblemHighlightType.WARNING);
                } else {
                    seen.put(key, valueElement);
                }
            }
        }
        for (XmlTag child : tag.getSubTags()) {
            this.collectColumns(child, seen, reported, holder);
        }
    }
}
