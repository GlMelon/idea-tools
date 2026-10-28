package io.github.easy.tools.lang.ognl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis OGNL 解析器定义
 * <p>
 * 采用"扁平解析"策略: 不构建语法树，只保留词法 Token 序列，
 * 保证任何合法/非法表达式都不会产生红色错误标记，
 * 同时高亮/括号匹配等基于 Token 的功能全部可用。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlParserDefinition implements ParserDefinition {

    /**
     * 文件节点类型
     */
    public static final IFileElementType FILE = new IFileElementType(MyBatisOgnlLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(@NotNull Project project) {
        return new MyBatisOgnlLexer();
    }

    @Override
    public @NotNull PsiParser createParser(@NotNull Project project) {
        return (root, builder) -> {
            // 扁平解析: 所有 Token 直接挂在根节点下，永不产生错误节点
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            return builder.getTreeBuilt();
        };
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return MyBatisOgnlTokenTypes.WHITESPACE_SET;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return MyBatisOgnlTokenTypes.STRING_SET;
    }

    @Override
    public @NotNull PsiElement createElement(@NotNull ASTNode node) {
        // 扁平语法树无复合元素，直接复用平台为节点生成的 PSI
        return node.getPsi();
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new MyBatisOgnlFile(viewProvider);
    }
}
