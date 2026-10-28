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
 * MyBatis namespace 检测
 * <p>
 * 检测 mapper 标签的 namespace 属性:
 * <ul>
 * <li>namespace 找不到对应的Java类 -> 错误</li>
 * <li>namespace 对应的类不是接口 -> 警告</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisNamespaceInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                MyBatisNamespaceInspection.this.checkNamespace(attribute, holder);
            }
        };
    }

    /**
     * 检测namespace属性
     *
     * @param attribute 属性
     * @param holder    问题收集器
     * @since 1.1.0
     */
    private void checkNamespace(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder) {
        XmlTag tag = attribute.getParent();
        if (tag == null || !"mapper".equals(tag.getName()) || !"namespace".equals(attribute.getName())) {
            return;
        }
        if (!MyBatisUtils.isInMapperFile(tag)) {
            return;
        }

        XmlAttributeValue valueElement = attribute.getValueElement();
        String namespace = attribute.getValue();
        if (valueElement == null || StringUtil.isEmpty(namespace) || namespace.contains("${")) {
            return;
        }

        PsiClass psiClass = MyBatisUtils.findClassByQualifiedName(attribute, namespace);
        if (psiClass == null) {
            holder.registerProblem(valueElement,
                    "未找到namespace对应的Java类: " + namespace,
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            return;
        }
        if (!psiClass.isInterface()) {
            holder.registerProblem(valueElement,
                    "namespace对应的类 '" + psiClass.getName() + "' 不是接口",
                    ProblemHighlightType.WARNING);
        }
    }

}
