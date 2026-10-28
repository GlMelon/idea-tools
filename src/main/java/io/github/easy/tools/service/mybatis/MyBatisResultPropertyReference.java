package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MyBatis resultMap 映射属性引用
 * <p>
 * result/id/collection/association 标签的 property 属性
 * 到 resultMap type 对应 Java 类字段/属性的引用跳转、补全与重命名同步。
 * 支持 "user.name" 形式的嵌套属性。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisResultPropertyReference extends PsiReferenceBase<XmlAttributeValue> {

    /**
     * 需要引用的映射标签集合
     */
    private static final Set<String> MAPPING_TAGS = Set.of("result", "id", "collection", "association");

    public MyBatisResultPropertyReference(@NotNull XmlAttributeValue element) {
        super(element, MyBatisNamespaceReference.contentRange(element), true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        String property = this.getElement().getValue();
        if (property == null || property.isEmpty()) {
            return null;
        }
        PsiClass typeClass = this.resolveResultMapTypeClass();
        if (typeClass == null) {
            return null;
        }
        // 支持嵌套属性路径: user.name
        PsiClass currentClass = typeClass;
        String[] segments = property.split("\\.");
        PsiElement resolved = null;
        for (int i = 0; i < segments.length && currentClass != null; i++) {
            resolved = MyBatisUtils.findFieldOrPropertyInClass(currentClass, segments[i]);
            if (i < segments.length - 1 && resolved != null) {
                currentClass = MyBatisUtils.getTypeOfResolvedElement(resolved);
            }
        }
        return resolved;
    }

    @Override
    public Object @NotNull [] getVariants() {
        PsiClass typeClass = this.resolveResultMapTypeClass();
        if (typeClass == null) {
            return new Object[0];
        }

        List<LookupElement> variants = new ArrayList<>();
        for (PsiField field : typeClass.getAllFields()) {
            if (field.hasModifierProperty("static") || field.getName() == null) {
                continue;
            }
            String declaringClass = field.getContainingClass() == null ? null
                    : field.getContainingClass().getQualifiedName();
            if (declaringClass != null && declaringClass.startsWith("java.")) {
                continue;
            }
            variants.add(LookupElementBuilder.create(field, field.getName())
                    .withPresentableText(field.getName())
                    .withIcon(AllIcons.Nodes.Field)
                    .withTypeText(field.getType().getPresentableText(), true));
        }
        // 仅 getter 的属性(无字段)也提供补全
        for (PsiMethod method : typeClass.getAllMethods()) {
            String propertyName = this.propertyNameOfGetter(method);
            if (propertyName != null) {
                variants.add(LookupElementBuilder.create(method, propertyName)
                        .withPresentableText(propertyName)
                        .withIcon(AllIcons.Nodes.Property)
                        .withTypeText(method.getReturnType() == null ? "" : method.getReturnType().getPresentableText(), true));
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
        attribute.setValue(newElementName);
        return this.getElement();
    }

    /**
     * 获取 getter 方法对应的属性名
     *
     * @param method 方法
     * @return 属性名，非getter返回null
     * @since 1.1.0
     */
    private @Nullable String propertyNameOfGetter(@NotNull PsiMethod method) {
        String name = method.getName();
        if (method.getParameterList().getParametersCount() != 0 || name.length() <= 3) {
            return null;
        }
        String propertyName = null;
        if (name.startsWith("get") && !name.equals("getClass")) {
            propertyName = name.substring(3);
        } else if (name.startsWith("is") && (name.length() > 3)) {
            propertyName = name.substring(2);
        }
        if (propertyName == null) {
            return null;
        }
        return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    /**
     * 解析属性所属 resultMap 的类型
     * <p>
     * collection/association 的 property 指向的是外层 resultMap 类型的属性，
     * 因此统一解析到最近的 resultMap 标签的 type。
     * </p>
     *
     * @return resultMap类型类
     * @since 1.1.0
     */
    private @Nullable PsiClass resolveResultMapTypeClass() {
        PsiElement parent = this.getElement().getParent();
        if (!(parent instanceof XmlAttribute attribute)) {
            return null;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !MAPPING_TAGS.contains(tag.getName())) {
            return null;
        }
        XmlTag resultMapTag = MyBatisUtils.findEnclosingResultMap(tag);
        if (resultMapTag == null) {
            return null;
        }
        return MyBatisUtils.resolveResultMapType(resultMapTag, this.getElement());
    }

    /**
     * 获取当前引用所在的XML属性
     *
     * @return XML属性，异常情况返回null
     * @since 1.1.0
     */
    private @Nullable XmlAttribute getAttribute() {
        PsiElement parent = this.getElement().getParent();
        return parent instanceof XmlAttribute attribute ? attribute : null;
    }
}
