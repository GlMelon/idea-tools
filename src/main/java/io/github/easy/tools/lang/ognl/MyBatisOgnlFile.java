package io.github.easy.tools.lang.ognl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis OGNL PSI 文件(注入片段)
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlFile extends PsiFileBase {

    public MyBatisOgnlFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, MyBatisOgnlLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        // 注入语言无独立文件类型
        return com.intellij.openapi.fileTypes.UnknownFileType.INSTANCE;
    }
}
