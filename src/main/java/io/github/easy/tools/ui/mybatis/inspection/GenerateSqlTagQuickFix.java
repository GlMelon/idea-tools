package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import io.github.easy.tools.intention.MyBatisDeleteIntention;
import io.github.easy.tools.intention.MyBatisInsertIntention;
import io.github.easy.tools.intention.MyBatisIntentionAction;
import io.github.easy.tools.intention.MyBatisSelectIntention;
import io.github.easy.tools.intention.MyBatisUpdateIntention;
import org.jetbrains.annotations.NotNull;

/**
 * 生成 MyBatis SQL 标签的快速修复
 * <p>
 * 根据方法名前缀推断语句类型(select/find/get -> select, insert/add/save -> insert,
 * update -> update, delete/remove -> delete, 默认select)，
 * 复用已有的意图操作生成对应XML标签。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class GenerateSqlTagQuickFix implements LocalQuickFix {

    /**
     * 方法指针(防止PSI失效)
     *
     * @since 1.1.0
     */
    private final SmartPsiElementPointer<PsiMethod> methodPointer;

    /**
     * 推断的标签类型
     *
     * @since 1.1.0
     */
    private final String tagType;

    /**
     * 构造函数
     *
     * @param method Mapper方法
     * @since 1.1.0
     */
    public GenerateSqlTagQuickFix(@NotNull PsiMethod method) {
        this.methodPointer = SmartPointerManager.getInstance(method.getProject())
                .createSmartPsiElementPointer(method);
        this.tagType = this.inferTagType(method.getName());
    }

    /**
     * 根据方法名前缀推断语句标签类型
     *
     * @param methodName 方法名
     * @return 标签类型(select/insert/update/delete)
     * @since 1.1.0
     */
    private String inferTagType(@NotNull String methodName) {
        String name = methodName.toLowerCase();
        if (name.startsWith("insert") || name.startsWith("add") || name.startsWith("save")
                || name.startsWith("create") || name.startsWith("batch")) {
            return "insert";
        }
        if (name.startsWith("update") || name.startsWith("modify") || name.startsWith("edit")) {
            return "update";
        }
        if (name.startsWith("delete") || name.startsWith("remove") || name.startsWith("del")) {
            return "delete";
        }
        return "select";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiMethod method = this.methodPointer.getElement();
        if (method == null) {
            return;
        }
        PsiElement identifier = method.getNameIdentifier();
        if (identifier == null) {
            return;
        }

        // 意图操作需要Editor参数
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return;
        }

        this.createIntention().invoke(project, editor, identifier);
    }

    /**
     * 创建对应类型的意图操作
     *
     * @return 意图操作
     * @since 1.1.0
     */
    private MyBatisIntentionAction createIntention() {
        return switch (this.tagType) {
            case "insert" -> new MyBatisInsertIntention();
            case "update" -> new MyBatisUpdateIntention();
            case "delete" -> new MyBatisDeleteIntention();
            default -> new MyBatisSelectIntention();
        };
    }

    @Override
    public @NotNull String getName() {
        return "生成<" + this.tagType + ">SQL标签";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "生成MyBatis SQL标签";
    }
}
