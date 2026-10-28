package io.github.easy.tools.lang.ognl;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * MyBatis OGNL 语言注入器
 * <p>
 * 将 Mapper XML 中 if/when 标签的 test 属性值注入为 MyBatisOgnl 语言，
 * 使其获得语法高亮、括号匹配等编辑能力。
 * 使用 MultiHostInjector 稳定 API，兼容 2024.1+ 全版本。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlMultiHostInjector implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlAttributeValue attributeValue)
                || !(context instanceof PsiLanguageInjectionHost host)) {
            return;
        }
        if (!this.isTestAttribute(attributeValue)) {
            return;
        }
        registrar.startInjecting(MyBatisOgnlLanguage.INSTANCE)
                .addPlace(null, null, host, ElementManipulators.getValueTextRange(host));
    }

    @Override
    public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlAttributeValue.class);
    }

    /**
     * 判断属性值是否为 Mapper 文件中 if/when 的 test 属性
     *
     * @param attributeValue 属性值
     * @return true如果是待注入的 test 属性
     * @since 1.1.0
     */
    private boolean isTestAttribute(@NotNull XmlAttributeValue attributeValue) {
        if (!(attributeValue.getParent() instanceof XmlAttribute attribute)) {
            return false;
        }
        if (!"test".equals(attribute.getName())) {
            return false;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !("if".equals(tag.getName()) || "when".equals(tag.getName()))) {
            return false;
        }
        return MyBatisUtils.isInMapperFile(tag);
    }
}
