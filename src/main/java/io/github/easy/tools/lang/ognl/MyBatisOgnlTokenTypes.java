package io.github.easy.tools.lang.ognl;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.util.Set;

/**
 * MyBatis OGNL Token 类型定义
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public final class MyBatisOgnlTokenTypes {

    /**
     * 标识符(变量/属性名/方法名)
     */
    public static final IElementType IDENTIFIER = new IElementType("MYBATIS_OGNL_IDENTIFIER", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 关键字(and/or/not/eq/ne/lt/gt/le/ge/null/true/false/in/instanceof)
     */
    public static final IElementType KEYWORD = new IElementType("MYBATIS_OGNL_KEYWORD", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 数字字面量
     */
    public static final IElementType NUMBER = new IElementType("MYBATIS_OGNL_NUMBER", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 字符串字面量('...' 或 "...")
     */
    public static final IElementType STRING = new IElementType("MYBATIS_OGNL_STRING", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 运算符
     */
    public static final IElementType OPERATOR = new IElementType("MYBATIS_OGNL_OPERATOR", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 左圆括号
     */
    public static final IElementType LPAREN = new IElementType("MYBATIS_OGNL_LPAREN", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 右圆括号
     */
    public static final IElementType RPAREN = new IElementType("MYBATIS_OGNL_RPAREN", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 左方括号
     */
    public static final IElementType LBRACKET = new IElementType("MYBATIS_OGNL_LBRACKET", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 右方括号
     */
    public static final IElementType RBRACKET = new IElementType("MYBATIS_OGNL_RBRACKET", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 逗号
     */
    public static final IElementType COMMA = new IElementType("MYBATIS_OGNL_COMMA", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 点号
     */
    public static final IElementType DOT = new IElementType("MYBATIS_OGNL_DOT", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 空白
     */
    public static final IElementType WHITESPACE = new IElementType("MYBATIS_OGNL_WHITESPACE", MyBatisOgnlLanguage.INSTANCE);

    /**
     * 非法字符
     */
    public static final IElementType BAD_CHARACTER = new IElementType("MYBATIS_OGNL_BAD_CHARACTER", MyBatisOgnlLanguage.INSTANCE);

    /**
     * OGNL/MyBatis 表达式关键字集合
     */
    public static final Set<String> KEYWORDS = Set.of(
            "and", "or", "not", "xor",
            "eq", "ne", "neq", "lt", "gt", "le", "ge",
            "null", "true", "false", "in", "not in", "instanceof", "new", "size", "empty"
    );

    /**
     * 空白 Token 集合
     */
    public static final TokenSet WHITESPACE_SET = TokenSet.create(WHITESPACE);

    /**
     * 字符串字面量集合
     */
    public static final TokenSet STRING_SET = TokenSet.create(STRING);

    private MyBatisOgnlTokenTypes() {
        throw new UnsupportedOperationException("Utility class");
    }
}
