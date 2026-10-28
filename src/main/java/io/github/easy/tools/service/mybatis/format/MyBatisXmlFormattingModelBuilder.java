package io.github.easy.tools.service.mybatis.format;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper XML 格式化模型构建器
 * <p>
 * 针对 Mapper XML 的格式化痛点: 平台默认 XML 格式化器会重排 SQL 文本、
 * 破坏 where/if/foreach 内部的 SQL 排版。本构建器对 Mapper 文件使用
 * 保守的格式化模型:
 * <ul>
 * <li>标签嵌套结构正常缩进(where/if/foreach 逐层缩进，不破坏层级)</li>
 * <li>SQL 文本块保持原有换行与空行(不合并、不折行、不改列对齐)</li>
 * <li>标签内联部分保持原有行结构(不强制断行/合并)</li>
 * </ul>
 * 非 Mapper 的普通 XML 文件委托给平台默认 XML 格式化器，行为不变。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisXmlFormattingModelBuilder implements FormattingModelBuilder {

    /**
     * 平台默认 XML 格式化器(非 Mapper 文件委托)
     */
    private static final FormattingModelBuilder PLATFORM_XML_FORMATTER =
            new com.intellij.lang.xml.XmlFormattingModelBuilder();

    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext context) {
        PsiFile file = context.getContainingFile();
        if (file instanceof XmlFile xmlFile && MyBatisUtils.isInMapperFile(xmlFile)) {
            return FormattingModelProvider.createFormattingModelForPsiFile(file,
                    new MapperFileBlock(file.getNode(), null),
                    context.getCodeStyleSettings());
        }
        return PLATFORM_XML_FORMATTER.createModel(context);
    }

    /**
     * 文件级根块
     *
     * @author haijun
     * @version 1.1.0
     * @date 2026-10-28
     * @since 1.1.0
     */
    private static class MapperFileBlock extends AbstractBlock {

        MapperFileBlock(ASTNode node, com.intellij.formatting.Wrap wrap) {
            super(node, wrap, null);
        }

        @Override
        protected List<com.intellij.formatting.Block> buildChildren() {
            List<com.intellij.formatting.Block> blocks = new ArrayList<>();
            for (ASTNode child = this.myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() == XmlElementType.XML_TAG) {
                    blocks.add(new MapperTagBlock(child, true));
                }
            }
            return blocks;
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.intellij.formatting.Indent getIndent() {
            return null;
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.intellij.formatting.Spacing getSpacing(
                com.intellij.formatting.Block child1, com.intellij.formatting.Block child2) {
            return null;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }
    }

    /**
     * 标签块
     * <p>
     * 子块只包含子标签与文本块，标签头(属性列表)不进入子块，
     * 格式化时不会重排属性。文本块保持原样，仅调整缩进。
     * </p>
     *
     * @author haijun
     * @version 1.1.0
     * @date 2026-10-28
     * @since 1.1.0
     */
    private static class MapperTagBlock extends AbstractBlock {

        /**
         * 保持原有行结构: 不强制换行/合并，保留既有换行与空行，
         * 行内空白折叠为1..N个空格
         */
        private static final com.intellij.formatting.Spacing KEEP_LAYOUT_SPACING =
                com.intellij.formatting.Spacing.createSpacing(1, Integer.MAX_VALUE, 0, true, Integer.MAX_VALUE);

        /**
         * 是否为 mapper 根标签
         */
        private final boolean topLevel;

        MapperTagBlock(ASTNode node, boolean topLevel) {
            super(node, null, null);
            this.topLevel = topLevel;
        }

        @Override
        protected List<com.intellij.formatting.Block> buildChildren() {
            List<com.intellij.formatting.Block> blocks = new ArrayList<>();
            for (ASTNode child = this.myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() == XmlElementType.XML_TAG) {
                    blocks.add(new MapperTagBlock(child, false));
                } else if (child.getElementType() == XmlElementType.XML_TEXT) {
                    blocks.add(new MapperTextBlock(child));
                }
            }
            return blocks;
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.intellij.formatting.Indent getIndent() {
            // 根标签顶格，内层标签相对父标签缩进一级
            return this.topLevel ? null
                    : com.intellij.formatting.Indent.getNormalIndent();
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.intellij.formatting.Spacing getSpacing(
                com.intellij.formatting.Block child1, com.intellij.formatting.Block child2) {
            return KEEP_LAYOUT_SPACING;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }
    }

    /**
     * SQL 文本块(叶子，仅参与缩进，不重排内容)
     *
     * @author haijun
     * @version 1.1.0
     * @date 2026-10-28
     * @since 1.1.0
     */
    private static class MapperTextBlock extends AbstractBlock {

        MapperTextBlock(ASTNode node) {
            super(node, null, null);
        }

        @Override
        protected List<com.intellij.formatting.Block> buildChildren() {
            return new ArrayList<>();
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.intellij.formatting.Indent getIndent() {
            return com.intellij.formatting.Indent.getNormalIndent();
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.intellij.formatting.Spacing getSpacing(
                com.intellij.formatting.Block child1, com.intellij.formatting.Block child2) {
            return null;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }
    }
}
