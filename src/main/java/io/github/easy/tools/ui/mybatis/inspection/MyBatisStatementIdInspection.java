package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis 语句 id 检测
 * <p>
 * 检测 select/insert/update/delete 标签的 id 属性在 namespace 对应的
 * Mapper 接口(含父接口)中是否存在同名方法，不存在则报告警告，
 * 用于发现接口方法重命名后 XML 未同步、或XML中多余的语句。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisStatementIdInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                MyBatisStatementIdInspection.this.checkStatementId(attribute, holder);
            }
        };
    }

    /**
     * 检测语句id属性
     *
     * @param attribute 属性
     * @param holder    问题收集器
     * @since 1.1.0
     */
    private void checkStatementId(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder) {
        if (!"id".equals(attribute.getName())) {
            return;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !MyBatisUtils.isStatementTag(tag.getName())) {
            return;
        }
        if (!MyBatisUtils.isInMapperFile(tag)) {
            return;
        }

        XmlAttributeValue valueElement = attribute.getValueElement();
        String id = attribute.getValue();
        if (valueElement == null || StringUtil.isEmpty(id) || id.contains("${")) {
            return;
        }

        // namespace对应的类不存在时由namespace检测报告，这里跳过
        String namespace = MyBatisUtils.getMapperNamespace(tag);
        if (StringUtil.isEmpty(namespace)) {
            return;
        }
        PsiClass mapperClass = MyBatisUtils.findClassByQualifiedName(attribute, namespace);
        if (mapperClass == null) {
            return;
        }

        if (MyBatisUtils.findMethodInHierarchy(mapperClass, id) == null) {
            holder.registerProblem(valueElement,
                    "Mapper接口 '" + mapperClass.getName() + "' 中不存在方法 '" + id + "'",
                    ProblemHighlightType.WARNING);
        }
    }

}
