package io.github.easy.tools.action.mybatis;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.github.easy.tools.utils.MyBatisUtils;
import io.github.easy.tools.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提取 SQL 片段动作
 * <p>
 * 在 Mapper XML 的 SQL 语句中选中一段 SQL 文本后，按 Ctrl+Alt+C 将其提取为
 * {@code <sql>} 片段，原选中位置替换为 {@code <include refid="..."/>} 引用。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class ExtractSqlFragmentAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || !(psiFile instanceof XmlFile xmlFile)) {
            return;
        }

        Document document = editor.getDocument();
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (StringUtil.isEmpty(selectedText)) {
            return;
        }

        // 输入片段 id
        String fragmentId = this.promptFragmentId(project, xmlFile);
        if (StringUtil.isEmpty(fragmentId)) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, "Extract SQL Fragment", null, () -> {
            PsiDocumentManager.getInstance(project).commitDocument(document);

            // 提取选中文本(去除首尾空白)
            int start = editor.getSelectionModel().getSelectionStart();
            int end = editor.getSelectionModel().getSelectionEnd();
            String fragment = document.getText().substring(start, end).trim();

            // 1. 选中位置替换为 include 引用
            document.replaceString(start, end, "<include refid=\"" + fragmentId + "\"/>");

            // 2. 在 mapper 根标签末尾追加 sql 片段
            PsiDocumentManager.getInstance(project).commitDocument(document);
            XmlFile refreshed = (XmlFile) PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (refreshed == null || refreshed.getRootTag() == null) {
                return;
            }
            XmlTag rootTag = refreshed.getRootTag();
            com.intellij.psi.XmlElementFactory factory = com.intellij.psi.XmlElementFactory.getInstance(project);
            // 选区是原始 XML 源文本(可能含动态标签)，直接作为片段内容，不做转义
            XmlTag sqlTag = factory.createTagFromText(
                    "<sql id=\"" + fragmentId + "\">" + fragment + "</sql>");

            XmlTag[] subTags = rootTag.getSubTags();
            if (subTags.length > 0) {
                rootTag.addAfter(sqlTag, subTags[subTags.length - 1]);
            } else {
                rootTag.add(sqlTag);
            }
            // 片段标签后补一个换行，保持结构清晰
            document.insertString(sqlTag.getTextRange().getEndOffset(), "\n");
        }, psiFile);

        NotificationUtil.showInfo(project, "已提取 SQL 片段: " + fragmentId);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || !(psiFile instanceof XmlFile xmlFile)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        boolean hasSelection = editor.getSelectionModel().hasSelection();
        boolean inStatement = hasSelection && this.isSelectionInStatementText(xmlFile, editor);
        e.getPresentation().setEnabledAndVisible(inStatement);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // update 中需要提交文档，必须在 EDT 执行
        return ActionUpdateThread.EDT;
    }

    /**
     * 检查选区是否位于 Mapper XML 的 SQL 语句内
     *
     * @param xmlFile XML文件
     * @param editor  编辑器
     * @return true如果选区在语句标签内
     * @since 1.1.0
     */
    private boolean isSelectionInStatementText(@NotNull XmlFile xmlFile, @NotNull Editor editor) {
        if (!MyBatisUtils.isInMapperFile(xmlFile)) {
            return false;
        }
        Document document = editor.getDocument();
        PsiDocumentManager manager = PsiDocumentManager.getInstance(xmlFile.getProject());
        manager.commitDocument(document);
        int start = editor.getSelectionModel().getSelectionStart();
        int end = editor.getSelectionModel().getSelectionEnd();
        PsiElement startElement = xmlFile.findElementAt(start);
        PsiElement endElement = xmlFile.findElementAt(Math.max(start, end - 1));
        if (startElement == null || endElement == null) {
            return false;
        }
        // 起止都在同一个语句标签(select/insert/update/delete/sql)内
        XmlTag startTag = MyBatisUtils.findSqlTag(MyBatisUtils.findParentTag(startElement));
        XmlTag endTag = MyBatisUtils.findSqlTag(MyBatisUtils.findParentTag(endElement));
        return startTag != null && startTag == endTag;
    }

    /**
     * 弹窗输入片段 id(带唯一性校验)
     *
     * @param project 项目
     * @param xmlFile XML文件
     * @return 片段 id，取消返回null
     * @since 1.1.0
     */
    private @Nullable String promptFragmentId(@NotNull Project project, @NotNull XmlFile xmlFile) {
        String suggestion = this.suggestFragmentId(xmlFile);
        Set<String> existingIds = this.collectSqlFragmentIds(xmlFile);
        return Messages.showInputDialog(project,
                "SQL 片段 id:",
                "提取 SQL 片段",
                null,
                suggestion,
                new InputValidator() {
                    @Override
                    public boolean checkInput(String inputString) {
                        return StringUtil.isNotEmpty(inputString)
                                && inputString.matches("[a-zA-Z][a-zA-Z0-9_]*")
                                && !existingIds.contains(inputString);
                    }

                    @Override
                    public boolean canClose(String inputString) {
                        return this.checkInput(inputString);
                    }
                });
    }

    /**
     * 生成片段 id 建议(sql1/sql2...)
     *
     * @param xmlFile XML文件
     * @return 建议的 id
     * @since 1.1.0
     */
    private @NotNull String suggestFragmentId(@NotNull XmlFile xmlFile) {
        Set<String> existingIds = this.collectSqlFragmentIds(xmlFile);
        int index = existingIds.size() + 1;
        while (existingIds.contains("sql" + index)) {
            index++;
        }
        return "sql" + index;
    }

    /**
     * 收集文件中已存在的 sql 片段 id
     *
     * @param xmlFile XML文件
     * @return id集合
     * @since 1.1.0
     */
    private Set<String> collectSqlFragmentIds(@NotNull XmlFile xmlFile) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return Set.of();
        }
        return java.util.Arrays.stream(rootTag.getSubTags())
                .filter(tag -> "sql".equals(tag.getName()))
                .map(tag -> tag.getAttributeValue("id"))
                .filter(StringUtil::isNotEmpty)
                .collect(Collectors.toSet());
    }
}
