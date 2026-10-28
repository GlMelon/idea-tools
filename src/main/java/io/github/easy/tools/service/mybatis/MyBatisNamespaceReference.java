package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis namespace 属性引用
 * <p>
 * 支持 mapper 标签的 namespace 属性到 Java 接口的引用跳转、
 * 接口全限定名补全，以及类重命名时保留包名的同步更新。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisNamespaceReference extends PsiReferenceBase<XmlAttributeValue> {

    /**
     * 补全候选数量上限，防止超大项目补全卡顿
     *
     * @since 1.1.0
     */
    private static final int MAX_VARIANTS = 3000;

    /**
     * 构造函数
     *
     * @param element namespace属性值元素
     * @since 1.1.0
     */
    public MyBatisNamespaceReference(@NotNull XmlAttributeValue element) {
        super(element, contentRange(element), true);
    }

    /**
     * 计算属性值内容(不含引号)在元素中的文本范围
     *
     * @param element 属性值元素
     * @return 文本范围
     * @since 1.1.0
     */
    static @NotNull TextRange contentRange(@NotNull XmlAttributeValue element) {
        int length = element.getTextLength();
        return TextRange.from(1, Math.max(0, length - 2));
    }

    /**
     * 获取当前引用的属性值文本
     *
     * @return 属性值
     * @since 1.1.0
     */
    protected @Nullable String getAttributeValue() {
        return this.getElement().getValue();
    }

    /**
     * 获取当前引用所在的XML属性
     *
     * @return XML属性，异常情况返回null
     * @since 1.1.0
     */
    protected @Nullable XmlAttribute getAttribute() {
        PsiElement parent = this.getElement().getParent();
        return parent instanceof XmlAttribute attribute ? attribute : null;
    }

    @Override
    public @Nullable PsiElement resolve() {
        String fqn = this.getAttributeValue();
        if (StringUtil.isEmpty(fqn) || fqn.contains("${")) {
            return null;
        }
        return MyBatisUtils.findClassByQualifiedName(this.getElement(), fqn);
    }

    @Override
    public Object @NotNull [] getVariants() {
        Project project = this.getElement().getProject();
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        List<LookupElement> variants = new ArrayList<>();
        for (String className : cache.getAllClassNames()) {
            for (PsiClass psiClass : cache.getClassesByName(className, scope)) {
                if (!psiClass.isInterface()) {
                    continue;
                }
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName == null) {
                    continue;
                }
                variants.add(LookupElementBuilder
                        .create(psiClass, qualifiedName)
                        .withPresentableText(psiClass.getName() == null ? qualifiedName : psiClass.getName())
                        .withIcon(AllIcons.Nodes.Interface));
                if (variants.size() >= MAX_VARIANTS) {
                    return variants.toArray();
                }
            }
        }
        return variants.toArray();
    }

    @Override
    public @Nullable PsiElement handleElementRename(@NotNull String newElementName) {
        XmlAttribute attribute = this.getAttribute();
        if (attribute == null) {
            return null;
        }
        String current = attribute.getValue();
        String newName = newElementName;
        // 类重命名时传入的是简名，保留原有包前缀
        if (newElementName.indexOf('.') < 0 && current != null) {
            int lastDot = current.lastIndexOf('.');
            if (lastDot >= 0) {
                newName = current.substring(0, lastDot + 1) + newElementName;
            }
        }
        attribute.setValue(newName);
        return this.getElement();
    }
}
