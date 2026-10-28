package io.github.easy.tools.service.mybatis;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper 接口方法名引用贡献者
 * <p>
 * 为 @Mapper 接口的方法名标识符注册指向 XML 语句 id 的引用，
 * 使 Ctrl+B / Find Usages 能够直接定位到 XML 中对应的 SQL 语句。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisMapperMethodReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiIdentifier.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        if (!(element.getParent() instanceof PsiMethod method)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        PsiClass containingClass = method.getContainingClass();
                        if (!MyBatisUtils.isMapperInterface(containingClass)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        return new PsiReference[]{new MyBatisMapperMethodReference(element, method)};
                    }
                }
        );
    }
}
