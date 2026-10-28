package io.github.easy.tools.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import io.github.easy.tools.utils.MyBatisUtils;
import io.github.easy.tools.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 一键添加 @Param 注解意图操作
 * <p>
 * 在 Mapper 接口方法上调用时，为所有尚未标注 @Param 的参数
 * 批量添加 {@code @Param("参数名")} 注解，避免 XML 中多参数引用失败。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class AddParamAnnotationIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    /**
     * MyBatis @Param 注解完全限定名
     */
    private static final String PARAM_ANNOTATION = "org.apache.ibatis.annotations.Param";

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiMethod method = this.findMethod(element);
        if (method == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
            int added = 0;
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
                if (parameter.getName() == null || MyBatisUtils.getParamAnnotationValue(parameter) != null) {
                    continue;
                }
                PsiModifierList modifierList = parameter.getModifierList();
                if (modifierList == null) {
                    continue;
                }
                PsiElement annotation = factory.createAnnotationFromText(
                        "@" + PARAM_ANNOTATION + "(\"" + parameter.getName() + "\")", parameter);
                modifierList.add(annotation);
                styleManager.shortenClassReferences(annotation);
                added++;
            }
            if (added > 0) {
                NotificationUtil.showInfo(project, "已为 " + added + " 个参数添加 @Param 注解");
            } else {
                NotificationUtil.showWarning(project, "所有参数均已标注 @Param");
            }
        });
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiMethod method = this.findMethod(element);
        if (method == null) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (!MyBatisUtils.isMapperInterface(containingClass)) {
            return false;
        }
        // 至少存在一个未标注 @Param 的命名参数才可用
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (parameter.getName() != null && MyBatisUtils.getParamAnnotationValue(parameter) == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查找光标所在的方法
     *
     * @param element PSI元素
     * @return 方法，未找到返回null
     * @since 1.2.0
     */
    private PsiMethod findMethod(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiMethod method) {
                return method;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "MyBatis参数注解";
    }

    @Override
    public @NotNull String getText() {
        return "为所有参数添加@Param注解";
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
