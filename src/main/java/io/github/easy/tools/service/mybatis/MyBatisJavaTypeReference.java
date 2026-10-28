package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
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
import java.util.Map;

/**
 * MyBatis Java类型属性引用(parameterType/resultType/type)
 * <p>
 * 支持类型属性到Java类的引用跳转，支持MyBatis内置别名解析(如 map、string、long)，
 * 补全候选为MyBatis内置别名 + 项目内的类。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisJavaTypeReference extends PsiReferenceBase<XmlAttributeValue> {

    /**
     * MyBatis TypeAliasRegistry 内置别名表(别名 -> 完全限定名)
     *
     * @since 1.1.0
     */
    private static final Map<String, String> BUILT_IN_ALIASES = Map.ofEntries(
            Map.entry("string", "java.lang.String"),
            Map.entry("byte", "java.lang.Byte"),
            Map.entry("_byte", "byte"),
            Map.entry("long", "java.lang.Long"),
            Map.entry("_long", "long"),
            Map.entry("short", "java.lang.Short"),
            Map.entry("_short", "short"),
            Map.entry("int", "java.lang.Integer"),
            Map.entry("_int", "int"),
            Map.entry("integer", "java.lang.Integer"),
            Map.entry("double", "java.lang.Double"),
            Map.entry("_double", "double"),
            Map.entry("float", "java.lang.Float"),
            Map.entry("_float", "float"),
            Map.entry("boolean", "java.lang.Boolean"),
            Map.entry("_boolean", "boolean"),
            Map.entry("date", "java.util.Date"),
            Map.entry("decimal", "java.math.BigDecimal"),
            Map.entry("bigdecimal", "java.math.BigDecimal"),
            Map.entry("biginteger", "java.math.BigInteger"),
            Map.entry("object", "java.lang.Object"),
            Map.entry("map", "java.util.Map"),
            Map.entry("hashmap", "java.util.HashMap"),
            Map.entry("list", "java.util.List"),
            Map.entry("arraylist", "java.util.ArrayList"),
            Map.entry("collection", "java.util.Collection"),
            Map.entry("iterator", "java.util.Iterator"),
            Map.entry("char", "java.lang.Character"));

    /**
     * 项目类补全候选数量上限
     *
     * @since 1.1.0
     */
    private static final int MAX_CLASS_VARIANTS = 2000;

    /**
     * 构造函数
     *
     * @param element 类型属性值元素
     * @since 1.1.0
     */
    public MyBatisJavaTypeReference(@NotNull XmlAttributeValue element) {
        super(element, MyBatisNamespaceReference.contentRange(element), true);
    }

    /**
     * 解析类型属性文本对应的 Java 类(含内置别名)
     * <p>供 resultType/resultMap type 等类型一致性检测复用。</p>
     *
     * @param context  上下文元素(提供解析范围)
     * @param typeName 类型属性文本(完全限定名或内置别名)
     * @return 对应的PsiClass，未找到返回null
     * @since 1.2.0
     */
    public static @Nullable PsiClass resolveTypeText(@NotNull PsiElement context, @Nullable String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        String trimmed = typeName.trim();
        String aliasFqn = BUILT_IN_ALIASES.get(trimmed.toLowerCase());
        if (aliasFqn != null && !aliasFqn.startsWith("java.")) {
            PsiClass aliasClass = MyBatisUtils.findClassByQualifiedName(context, aliasFqn);
            if (aliasClass != null) {
                return aliasClass;
            }
        }
        return MyBatisUtils.findClassByQualifiedName(context, trimmed);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return resolveTypeText(this.getElement(), this.getElement().getValue());
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<LookupElement> variants = new ArrayList<>();

        // 内置别名
        for (Map.Entry<String, String> entry : BUILT_IN_ALIASES.entrySet()) {
            variants.add(LookupElementBuilder
                    .create(entry.getKey())
                    .withIcon(AllIcons.Nodes.Class)
                    .withTypeText(entry.getValue(), true));
        }

        // 项目内的类
        Project project = this.getElement().getProject();
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (String className : cache.getAllClassNames()) {
            for (PsiClass psiClass : cache.getClassesByName(className, scope)) {
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName == null) {
                    continue;
                }
                variants.add(LookupElementBuilder
                        .create(psiClass, qualifiedName)
                        .withPresentableText(psiClass.getName() == null ? qualifiedName : psiClass.getName())
                        .withIcon(psiClass.isInterface() ? AllIcons.Nodes.Interface : AllIcons.Nodes.Class));
                if (variants.size() >= MAX_CLASS_VARIANTS + BUILT_IN_ALIASES.size()) {
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
