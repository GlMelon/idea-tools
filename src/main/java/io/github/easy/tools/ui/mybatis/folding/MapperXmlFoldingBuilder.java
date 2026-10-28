package io.github.easy.tools.ui.mybatis.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mapper XML 代码折叠构建器
 * <p>
 * 在平台默认 XML 折叠之外，为 Mapper XML 提供额外折叠区域:
 * <ul>
 * <li>select/insert/update/delete/sql/resultMap 等长标签整体折叠，
 * 占位符显示为 "标签名 id" 形式，快速浏览语句清单</li>
 * <li>超过一行的长 SQL 文本折叠，占位符显示首行内容</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MapperXmlFoldingBuilder extends FoldingBuilderEx implements DumbAware {

    /**
     * 可整体折叠的 Mapper 结构标签
     */
    private static final Set<String> FOLDABLE_TAGS = Set.of(
            "select", "insert", "update", "delete", "sql", "resultMap"
    );

    /**
     * 标签整体折叠的最小行数(低于该行数的标签折叠意义不大)
     */
    private static final int MIN_TAG_LINES = 5;

    /**
     * SQL 文本折叠的最小行数
     */
    private static final int MIN_TEXT_LINES = 3;

    /**
     * 占位符最大长度
     */
    private static final int PLACEHOLDER_MAX = 40;

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root,
                                                          @NotNull Document document,
                                                          boolean quick) {
        if (!(root instanceof XmlFile xmlFile) || !MyBatisUtils.isInMapperFile(xmlFile)) {
            return FoldingDescriptor.EMPTY;
        }
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        XmlTag mapperTag = xmlFile.getRootTag();
        if (mapperTag == null) {
            return FoldingDescriptor.EMPTY;
        }
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(mapperTag, XmlTag.class)) {
            this.collectTagFold(tag, document, descriptors);
            this.collectTextFolds(tag, document, descriptors);
        }
        return descriptors.toArray(FoldingDescriptor.EMPTY);
    }

    /**
     * 收集结构标签整体折叠区域
     *
     * @param tag         标签
     * @param document    文档
     * @param descriptors 折叠描述集合
     * @since 1.1.0
     */
    private void collectTagFold(@NotNull XmlTag tag, @NotNull Document document,
                                @NotNull List<FoldingDescriptor> descriptors) {
        if (!FOLDABLE_TAGS.contains(tag.getName())) {
            return;
        }
        int startLine = document.getLineNumber(tag.getTextRange().getStartOffset());
        int endLine = document.getLineNumber(tag.getTextRange().getEndOffset());
        if (endLine - startLine + 1 < MIN_TAG_LINES) {
            return;
        }
        // 折叠区域从标签头结束(第一个 > 之后)到闭合标签开始
        TextRange headerRange = tag.getFirstChild() == null ? null : tag.getFirstChild().getTextRange();
        if (headerRange == null) {
            return;
        }
        int foldStart = headerRange.getEndOffset();
        int foldEnd = tag.getTextRange().getEndOffset() - ("</" + tag.getName() + ">").length();
        if (foldEnd <= foldStart) {
            return;
        }
        String id = tag.getAttributeValue("id");
        String placeholder = tag.getName() + (id == null ? "" : " " + id) + " ...";
        descriptors.add(new FoldingDescriptor(tag, foldStart, foldEnd, null, placeholder));
    }

    /**
     * 收集长 SQL 文本折叠区域
     *
     * @param tag         标签
     * @param document    文档
     * @param descriptors 折叠描述集合
     * @since 1.1.0
     */
    private void collectTextFolds(@NotNull XmlTag tag, @NotNull Document document,
                                  @NotNull List<FoldingDescriptor> descriptors) {
        for (XmlText xmlText : tag.getValue().getTextElements()) {
            TextRange range = xmlText.getTextRange();
            if (range.getLength() <= 0) {
                continue;
            }
            int startLine = document.getLineNumber(range.getStartOffset());
            int endLine = document.getLineNumber(range.getEndOffset());
            if (endLine - startLine + 1 < MIN_TEXT_LINES) {
                continue;
            }
            String placeholder = this.firstLinePlaceholder(xmlText.getText());
            if (placeholder == null) {
                continue;
            }
            descriptors.add(new FoldingDescriptor(xmlText, range.getStartOffset(), range.getEndOffset(),
                    null, placeholder));
        }
    }

    /**
     * 生成 SQL 文本首行占位符
     *
     * @param text SQL文本
     * @return 占位符，无有效内容返回null
     * @since 1.1.0
     */
    private @Nullable String firstLinePlaceholder(@NotNull String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > PLACEHOLDER_MAX) {
                    trimmed = trimmed.substring(0, PLACEHOLDER_MAX) + "...";
                }
                return trimmed;
            }
        }
        return null;
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        // 占位符在创建 FoldingDescriptor 时已指定
        return "...";
    }

    /**
     * 旧版本平台的单参占位符接口实现
     *
     * @param node 节点
     * @return 占位符
     * @since 1.1.0
     */
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
