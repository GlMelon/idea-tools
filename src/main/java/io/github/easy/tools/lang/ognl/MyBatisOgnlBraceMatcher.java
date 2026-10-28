package io.github.easy.tools.lang.ognl;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MyBatis OGNL 括号匹配器
 * <p>
 * 支持 () 与 [] 的匹配高亮与 Ctrl+Shift+M 跳转。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlBraceMatcher implements PairedBraceMatcher {

    /**
     * 括号对定义
     */
    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(MyBatisOgnlTokenTypes.LPAREN, MyBatisOgnlTokenTypes.RPAREN, false),
            new BracePair(MyBatisOgnlTokenTypes.LBRACKET, MyBatisOgnlTokenTypes.RBRACKET, false)
    };

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType,
                                                   @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(@NotNull PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
