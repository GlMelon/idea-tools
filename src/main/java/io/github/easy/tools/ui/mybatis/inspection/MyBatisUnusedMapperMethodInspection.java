package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlFile;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mapper 未使用方法检测
 * <p>
 * 检测 Mapper 接口中从未被 Java 代码引用的抽象方法。
 * XML 语句 id 与方法的关联不算作使用(它是方法与 SQL 的绑定，不是方法的调用方)，
 * 因此"有 XML 但无调用方"的方法也会被报告——这类方法通常是可以清理的死代码。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class MyBatisUnusedMapperMethodInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                MyBatisUnusedMapperMethodInspection.this.checkMethod(method, holder);
            }
        };
    }

    /**
     * 检测Mapper方法是否从未被使用
     *
     * @param method 方法
     * @param holder 问题收集器
     * @since 1.2.0
     */
    private void checkMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        Project project = method.getProject();
        if (DumbService.isDumb(project)) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (!MyBatisUtils.isMapperInterface(containingClass)) {
            return;
        }
        // 只检测接口中声明的抽象方法
        if (method.isConstructor()
                || method.getBody() != null
                || method.hasModifierProperty(PsiModifier.STATIC)
                || method.getNameIdentifier() == null) {
            return;
        }
        // 方法必须实际声明在当前接口中(而非父接口)
        if (!method.equals(containingClass.findMethodBySignature(method, false))) {
            return;
        }

        if (this.hasJavaUsage(method)) {
            return;
        }

        holder.registerProblem(method.getNameIdentifier(),
                "Mapper方法 '" + method.getName() + "' 未被任何Java代码使用",
                ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }

    /**
     * 查找方法的Java侧使用(XML中的id引用不算使用)
     *
     * @param method 方法
     * @return true如果存在Java使用
     * @since 1.2.0
     */
    private boolean hasJavaUsage(@NotNull PsiMethod method) {
        for (PsiReference reference : ReferencesSearch.search(method, method.getUseScope())) {
            PsiElement element = reference.getElement();
            // 跳过 XML 中的语句 id 关联(方法与SQL的绑定，不是调用)
            if (element.getContainingFile() instanceof XmlFile) {
                continue;
            }
            return true;
        }
        return false;
    }
}
