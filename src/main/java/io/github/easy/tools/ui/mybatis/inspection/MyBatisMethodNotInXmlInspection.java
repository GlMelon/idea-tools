package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper 方法缺少 SQL 语句检测
 * <p>
 * 检测 @Mapper 接口中声明的抽象方法是否具有对应的SQL语句:
 * <ul>
 * <li>跳过使用注解SQL的方法(@Select/@Insert等)</li>
 * <li>跳过static/default方法</li>
 * <li>在自身及父接口对应的XML文件中查找语句</li>
 * </ul>
 * 未找到语句时报告警告，并提供生成SQL标签的快速修复。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisMethodNotInXmlInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                MyBatisMethodNotInXmlInspection.this.checkMethod(method, holder);
            }
        };
    }

    /**
     * 检测Mapper方法是否有对应SQL语句
     *
     * @param method 方法
     * @param holder 问题收集器
     * @since 1.1.0
     */
    private void checkMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        PsiClass containingClass = method.getContainingClass();
        if (!MyBatisUtils.isMapperInterface(containingClass)) {
            return;
        }

        // 只检测接口中声明的方法，跳过static/default方法和构造器
        if (method.isConstructor()
                || method.getBody() != null
                || method.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }
        if (method.getNameIdentifier() == null) {
            return;
        }

        // 注解SQL无需XML语句
        if (MyBatisUtils.hasSqlAnnotation(method)) {
            return;
        }

        // MyBatis-Plus/Flex/tk-mapper 的 BaseMapper 方法由框架提供SQL，豁免检测
        if (MyBatisUtils.isFrameworkMapperMethod(method)) {
            return;
        }

        // Mapper 接口自身 extends 框架 BaseMapper 且未声明额外方法时，
        // 其方法均由框架提供(用户自建通用 BaseDao 中的自定义方法仍需XML)
        XmlTag sqlTag = MyBatisUtils.findSqlTagInHierarchy(containingClass, method.getName());
        if (sqlTag != null) {
            return;
        }

        holder.registerProblem(method.getNameIdentifier(),
                "Mapper方法 '" + method.getName() + "' 没有对应的SQL语句(XML或注解)",
                ProblemHighlightType.WARNING,
                new GenerateSqlTagQuickFix(method));
    }

}
