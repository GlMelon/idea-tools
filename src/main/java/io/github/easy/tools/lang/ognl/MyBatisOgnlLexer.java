package io.github.easy.tools.lang.ognl;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis OGNL 手写词法分析器
 * <p>
 * OGNL 表达式足够简单，直接基于字符扫描实现，不依赖 JFlex。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlLexer extends Lexer {

    /**
     * 当前缓冲区
     */
    private CharSequence buffer;

    /**
     * 缓冲区起始偏移
     */
    private int bufferStart;

    /**
     * 缓冲区结束偏移
     */
    private int bufferEnd;

    /**
     * 当前扫描位置
     */
    private int position;

    /**
     * 当前 Token 起始
     */
    private int tokenStart;

    /**
     * 当前 Token 结束
     */
    private int tokenEnd;

    /**
     * 当前 Token 类型
     */
    private IElementType tokenType;

    /**
     * 当前状态(无状态词法器，恒为0)
     */
    private int state;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferStart = startOffset;
        this.bufferEnd = endOffset;
        this.position = startOffset;
        this.state = initialState;
        this.advance();
    }

    @Override
    public int getState() {
        return this.state;
    }

    @Override
    public @org.jetbrains.annotations.Nullable IElementType getTokenType() {
        return this.tokenType;
    }

    @Override
    public int getTokenStart() {
        return this.tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return this.tokenEnd;
    }

    @Override
    public void advance() {
        if (this.position >= this.bufferEnd) {
            this.tokenType = null;
            this.tokenStart = this.tokenEnd = this.position;
            return;
        }
        this.tokenStart = this.position;
        char c = this.buffer.charAt(this.position);

        // 空白
        if (Character.isWhitespace(c)) {
            while (this.position < this.bufferEnd && Character.isWhitespace(this.buffer.charAt(this.position))) {
                this.position++;
            }
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.WHITESPACE;
            return;
        }

        // 标识符/关键字
        if (Character.isJavaIdentifierStart(c)) {
            while (this.position < this.bufferEnd
                    && Character.isJavaIdentifierPart(this.buffer.charAt(this.position))) {
                this.position++;
            }
            this.tokenEnd = this.position;
            String word = this.buffer.subSequence(this.tokenStart, this.position).toString();
            this.tokenType = MyBatisOgnlTokenTypes.KEYWORDS.contains(word)
                    ? MyBatisOgnlTokenTypes.KEYWORD
                    : MyBatisOgnlTokenTypes.IDENTIFIER;
            return;
        }

        // 数字
        if (Character.isDigit(c)) {
            while (this.position < this.bufferEnd) {
                char next = this.buffer.charAt(this.position);
                if (Character.isDigit(next) || next == '.') {
                    this.position++;
                } else {
                    break;
                }
            }
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.NUMBER;
            return;
        }

        // 字符串(单引号/双引号，未闭合则到结尾)
        if (c == '\'' || c == '"') {
            this.position++;
            while (this.position < this.bufferEnd) {
                char next = this.buffer.charAt(this.position);
                this.position++;
                if (next == c) {
                    break;
                }
            }
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.STRING;
            return;
        }

        // 括号
        if (c == '(') {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.LPAREN;
            return;
        }
        if (c == ')') {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.RPAREN;
            return;
        }
        if (c == '[') {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.LBRACKET;
            return;
        }
        if (c == ']') {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.RBRACKET;
            return;
        }
        if (c == ',') {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.COMMA;
            return;
        }
        if (c == '.') {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.DOT;
            return;
        }

        // 运算符(两字符优先)
        if (this.position + 1 < this.bufferEnd) {
            char next = this.buffer.charAt(this.position + 1);
            if ((c == '=' && next == '=') || (c == '!' && next == '=')
                    || (c == '<' && next == '=') || (c == '>' && next == '=')
                    || (c == '&' && next == '&') || (c == '|' && next == '|')) {
                this.position += 2;
                this.tokenEnd = this.position;
                this.tokenType = MyBatisOgnlTokenTypes.OPERATOR;
                return;
            }
        }

        // 运算符(单字符)
        if ("+-*/%<>!=".indexOf(c) >= 0) {
            this.position++;
            this.tokenEnd = this.position;
            this.tokenType = MyBatisOgnlTokenTypes.OPERATOR;
            return;
        }

        // 未知字符
        this.position++;
        this.tokenEnd = this.position;
        this.tokenType = MyBatisOgnlTokenTypes.BAD_CHARACTER;
    }

    @Override
    public @NotNull CharSequence getTokenSequence() {
        return this.buffer.subSequence(this.tokenStart, this.tokenEnd);
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return this.buffer;
    }

    @Override
    public int getBufferEnd() {
        return this.bufferEnd;
    }

    @Override
    public @NotNull com.intellij.lexer.LexerPosition getCurrentPosition() {
        return new MyLexerPosition(this.tokenStart);
    }

    @Override
    public void restore(@NotNull com.intellij.lexer.LexerPosition position) {
        // 无状态词法器: 从位置偏移(即 Token 起点)重新扫描当前 Token
        this.state = position.getState();
        this.position = position.getOffset();
        this.tokenStart = this.tokenEnd = this.position;
        this.tokenType = null;
        this.advance();
    }

    /**
     * 获取缓冲区起始(测试用)
     *
     * @return 起始偏移
     * @since 1.1.0
     */
    public int getBufferStart() {
        return this.bufferStart;
    }

    /**
     * 无状态词法器的位置实现(偏移即 Token 起点)
     *
     * @author haijun
     * @version 1.1.0
     * @date 2026-10-28
     * @since 1.1.0
     */
    private static final class MyLexerPosition implements com.intellij.lexer.LexerPosition {

        /**
         * Token 起始偏移
         */
        private final int offset;

        MyLexerPosition(int offset) {
            this.offset = offset;
        }

        @Override
        public int getOffset() {
            return this.offset;
        }

        @Override
        public int getState() {
            return 0;
        }
    }
}
