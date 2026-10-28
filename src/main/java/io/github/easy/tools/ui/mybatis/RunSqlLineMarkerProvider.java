package io.github.easy.tools.ui.mybatis;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import io.github.easy.tools.service.mybatis.sql.MyBatisSqlAssembler;
import io.github.easy.tools.service.mybatis.sql.MyBatisSqlDialectResolver;
import io.github.easy.tools.utils.MyBatisUtils;
import io.github.easy.tools.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * Mapper XML 语句运行行标记
 * <p>
 * 在 select/insert/update/delete 语句标签行显示执行图标，点击后:
 * <ul>
 * <li>组装完整 SQL(include 内联、动态标签展开、#{} 替换为 ?)</li>
 * <li>生成 SQL 控制台临时文件(.sql scratch)并打开，
 * Ultimate(数据源插件可用)下为 SQL 文件可直接 Ctrl+Enter 执行；
 * Community 下降级为纯文本文件</li>
 * <li>同时复制到剪贴板，便于粘贴到外部工具</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class RunSqlLineMarkerProvider implements LineMarkerProvider {

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof XmlTag xmlTag)) {
            return null;
        }
        // 只处理 mapper 根下的语句标签(子标签不重复标记)
        if (!MyBatisUtils.isStatementTag(xmlTag.getName())) {
            return null;
        }
        XmlTag parent = xmlTag.getParentTag();
        if (parent == null || !"mapper".equals(parent.getName())) {
            return null;
        }
        if (!MyBatisUtils.isInMapperFile(xmlTag)) {
            return null;
        }

        // 标记范围定位到标签名 token
        ASTNode nameNode = xmlTag.getNode().findChildByType(XmlTokenType.XML_NAME);
        TextRange range = nameNode != null ? nameNode.getTextRange() : xmlTag.getTextRange();

        return new LineMarkerInfo<>(
                xmlTag,
                range,
                AllIcons.Actions.Execute,
                elt -> "组装并运行 SQL",
                (mouseEvent, elt) -> {
                    if (elt instanceof XmlTag tag) {
                        RunSqlLineMarkerProvider.this.runStatement(tag);
                    }
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "Run MyBatis SQL");
    }

    /**
     * 组装并运行语句
     *
     * @param statementTag 语句标签
     * @since 1.1.0
     */
    private void runStatement(@NotNull XmlTag statementTag) {
        Project project = statementTag.getProject();
        String sql = MyBatisSqlAssembler.assemble(statementTag);
        if (StringUtil.isEmpty(sql)) {
            NotificationUtil.showWarning(project, "无法组装该语句的 SQL(内容为空或结构异常)");
            return;
        }

        // 复制到剪贴板
        CopyPasteManager.getInstance().setContents(new StringSelection(sql));

        // 生成 SQL 控制台临时文件
        String id = statementTag.getAttributeValue("id");
        String fileName = "mapper-" + (StringUtil.isEmpty(id) ? "statement" : id) + ".sql";
        Language language = this.resolveSqlLanguage(statementTag);
        if (language == null) {
            // 无数据源模块时降级为纯文本
            language = Language.findLanguageByID("TEXT");
        }
        if (language != null) {
            try {
                var scratch = ScratchRootType.getInstance().createScratchFile(project, fileName, language, sql);
                if (scratch != null) {
                    new OpenFileDescriptor(project, scratch).navigate(true);
                    NotificationUtil.showInfo(project, "SQL 已组装并生成控制台文件(已复制到剪贴板)，按 Ctrl+Enter 执行");
                    return;
                }
            } catch (Exception e) {
                // 创建临时文件失败时降级为仅复制
            }
        }
        NotificationUtil.showInfo(project, "SQL 已组装并复制到剪贴板");
    }

    /**
     * 通过扩展点解析 SQL 语言(可选数据源模块提供)
     *
     * @param context 上下文元素
     * @return SQL语言，无实现返回null
     * @since 1.1.0
     */
    private Language resolveSqlLanguage(@NotNull PsiElement context) {
        for (MyBatisSqlDialectResolver resolver : MyBatisSqlDialectResolver.EP_NAME.getExtensionList()) {
            Language language = resolver.resolveSqlLanguage(context.getContainingFile());
            if (language != null) {
                return language;
            }
        }
        return null;
    }
}
