package io.github.easy.tools.lang.ognl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * MyBatis OGNL 语法高亮工厂
 * <p>
 * 关键字(KEYWORD)、字符串(STRING)、数字(NUMBER)、
 * 运算符(OPERATION_SIGN)、括号(PARENTHESES/BRACKETS)着色。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    /**
     * Token类型 -> 颜色映射
     */
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = Map.of(
            MyBatisOgnlTokenTypes.KEYWORD,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD),
            MyBatisOgnlTokenTypes.STRING,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_STRING", DefaultLanguageHighlighterColors.STRING),
            MyBatisOgnlTokenTypes.NUMBER,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_NUMBER", DefaultLanguageHighlighterColors.NUMBER),
            MyBatisOgnlTokenTypes.OPERATOR,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN),
            MyBatisOgnlTokenTypes.LPAREN,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_LPAREN", DefaultLanguageHighlighterColors.PARENTHESES),
            MyBatisOgnlTokenTypes.RPAREN,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_RPAREN", DefaultLanguageHighlighterColors.PARENTHESES),
            MyBatisOgnlTokenTypes.LBRACKET,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_LBRACKET", DefaultLanguageHighlighterColors.BRACKETS),
            MyBatisOgnlTokenTypes.RBRACKET,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_RBRACKET", DefaultLanguageHighlighterColors.BRACKETS),
            MyBatisOgnlTokenTypes.BAD_CHARACTER,
            TextAttributesKey.createTextAttributesKey("MYBATIS_OGNL_BAD_CHARACTER", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
    );

    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project,
                                                           @Nullable com.intellij.openapi.vfs.VirtualFile virtualFile) {
        return new SyntaxHighlighter() {
            private final Lexer lexer = new MyBatisOgnlLexer();

            @Override
            public @NotNull Lexer getHighlightingLexer() {
                return this.lexer;
            }

            @Override
            public TextAttributesKey @NotNull [] getTokenHighlights(@NotNull IElementType tokenType) {
                TextAttributesKey key = ATTRIBUTES.get(tokenType);
                return key == null ? TextAttributesKey.EMPTY_ARRAY : new TextAttributesKey[]{key};
            }
        };
    }
}
