package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MyBatis *Provider 注解引用贡献者
 * <p>
 * 为 {@code @SelectProvider/@InsertProvider/@UpdateProvider/@DeleteProvider}
 * 的 {@code method = "xxx"} 字符串参数注册指向 Provider 类方法的引用，
 * 支持 Ctrl+B 跳转、方法名补全与重命名同步；Provider 类取 {@code type}/
 * {@code value} 属性的类字面量，未指定时回退为 Mapper 接口自身。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class MyBatisProviderReferenceContributor extends PsiReferenceContributor {

    /**
     * MyBatis *Provider 注解完全限定名集合
     */
    private static final Set<String> PROVIDER_ANNOTATIONS = Set.of(
            "org.apache.ibatis.annotations.SelectProvider",
            "org.apache.ibatis.annotations.InsertProvider",
            "org.apache.ibatis.annotations.UpdateProvider",
            "org.apache.ibatis.annotations.DeleteProvider");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class)
                        .withParent(com.intellij.psi.PsiNameValuePair.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        return MyBatisProviderReferenceContributor.this.createReference((PsiLiteralExpression) element);
                    }
                });
    }

    /**
     * 为 *Provider 注解的 method 属性字面量创建引用
     *
     * @param literal 字符串字面量
     * @return 引用数组，不匹配返回空数组
     * @since 1.2.0
     */
    private PsiReference @NotNull [] createReference(@NotNull PsiLiteralExpression literal) {
        if (!(literal.getParent() instanceof com.intellij.psi.PsiNameValuePair pair)
                || !"method".equals(pair.getName())) {
            return PsiReference.EMPTY_ARRAY;
        }
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation.class);
        if (annotation == null || annotation.getQualifiedName() == null
                || !PROVIDER_ANNOTATIONS.contains(annotation.getQualifiedName())) {
            return PsiReference.EMPTY_ARRAY;
        }
        if (!(literal.getValue() instanceof String methodName) || StringUtil.isEmpty(methodName)) {
            return PsiReference.EMPTY_ARRAY;
        }
        return new PsiReference[]{new ProviderMethodReference(literal, annotation)};
    }

    /**
     * Provider method 属性字符串 -> Provider 类方法的引用
     *
     * @author haijun
     * @since 1.2.0
     */
    private static class ProviderMethodReference extends PsiReferenceBase<PsiLiteralExpression> {

        /**
         * *Provider 注解
         */
        private final PsiAnnotation annotation;

        /**
         * 构造函数
         *
         * @param literal    method属性字符串字面量
         * @param annotation *Provider 注解
         * @since 1.2.0
         */
        ProviderMethodReference(@NotNull PsiLiteralExpression literal, @NotNull PsiAnnotation annotation) {
            super(literal, true);
            this.annotation = annotation;
        }

        /**
         * 解析 Provider 类
         * <p>优先 type/value 属性的类字面量，未指定时回退 Mapper 接口自身。</p>
         *
         * @return Provider 类，未找到返回null
         * @since 1.2.0
         */
        private @Nullable PsiClass resolveProviderClass() {
            PsiAnnotationMemberValue typeValue = this.annotation.findAttributeValue("type");
            if (typeValue == null) {
                typeValue = this.annotation.findAttributeValue("value");
            }
            if (typeValue instanceof PsiClassObjectAccessExpression classObject) {
                if (classObject.getOperand().getType() instanceof com.intellij.psi.PsiClassType providerType
                        && providerType.resolve() instanceof PsiClass psiClass) {
                    return psiClass;
                }
            }
            // 未显式指定 Provider 类时，MyBatis 以 Mapper 接口自身为默认 Provider
            PsiElement parent = this.annotation.getParent();
            PsiElement grand = parent == null ? null : parent.getParent();
            if (grand instanceof PsiMethod method && method.getContainingClass() != null) {
                return method.getContainingClass();
            }
            return null;
        }

        @Override
        public @Nullable PsiElement resolve() {
            String methodName = (String) this.getElement().getValue();
            if (StringUtil.isEmpty(methodName)) {
                return null;
            }
            PsiClass providerClass = this.resolveProviderClass();
            if (providerClass == null) {
                return null;
            }
            for (PsiMethod method : providerClass.getAllMethods()) {
                if (methodName.equals(method.getName())) {
                    return method;
                }
            }
            return null;
        }

        @Override
        public Object @NotNull [] getVariants() {
            PsiClass providerClass = this.resolveProviderClass();
            if (providerClass == null) {
                return new Object[0];
            }
            List<LookupElement> variants = new ArrayList<>();
            for (PsiMethod method : providerClass.getAllMethods()) {
                PsiClass declaringClass = method.getContainingClass();
                String declaringFqn = declaringClass == null ? null : declaringClass.getQualifiedName();
                if (declaringFqn == null || declaringFqn.startsWith("java.")) {
                    continue;
                }
                LookupElementBuilder builder = LookupElementBuilder
                        .create(method, method.getName())
                        .withIcon(AllIcons.Nodes.Method);
                if (method.getReturnType() != null) {
                    builder = builder.withTypeText(method.getReturnType().getPresentableText());
                }
                variants.add(builder);
            }
            return variants.toArray();
        }

        @Override
        public @Nullable PsiElement handleElementRename(@NotNull String newElementName) {
            PsiExpression newLiteral = JavaPsiFacade.getElementFactory(this.getElement().getProject())
                    .createExpressionFromText("\"" + newElementName + "\"", this.getElement());
            return this.getElement().replace(newLiteral);
        }
    }
}
