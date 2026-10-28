package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis 语句标签 id 属性引用
 * <p>
 * 支持 select/insert/update/delete 标签的 id 属性到 Mapper 接口方法的
 * 引用跳转与方法名补全；配合引用机制可在重命名方法时同步更新XML中的id。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisStatementIdReference extends PsiReferenceBase<XmlAttributeValue> {

    /**
     * 构造函数
     *
     * @param element id属性值元素
     * @since 1.1.0
     */
    public MyBatisStatementIdReference(@NotNull XmlAttributeValue element) {
        super(element, MyBatisNamespaceReference.contentRange(element), true);
    }

    /**
     * 解析namespace对应的Mapper接口类
     *
     * @return Mapper接口类，未找到返回null
     * @since 1.1.0
     */
    private @Nullable PsiClass resolveMapperClass() {
        XmlTag parentTag = MyBatisUtils.findParentTag(this.getElement());
        String namespace = parentTag == null ? null : MyBatisUtils.getMapperNamespace(parentTag);
        if (StringUtil.isEmpty(namespace)) {
            return null;
        }
        return MyBatisUtils.findClassByQualifiedName(this.getElement(), namespace);
    }

    @Override
    public @Nullable PsiElement resolve() {
        String id = this.getElement().getValue();
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        PsiClass mapperClass = this.resolveMapperClass();
        if (mapperClass == null) {
            return null;
        }
        return MyBatisUtils.findMethodInHierarchy(mapperClass, id);
    }

    @Override
    public Object @NotNull [] getVariants() {
        PsiClass mapperClass = this.resolveMapperClass();
        if (mapperClass == null) {
            return new Object[0];
        }

        List<LookupElement> variants = new ArrayList<>();
        for (PsiMethod method : mapperClass.getAllMethods()) {
            PsiClass declaringClass = method.getContainingClass();
            String declaringFqn = declaringClass == null ? null : declaringClass.getQualifiedName();
            // 跳过JDK中的方法、static/default方法(它们不对应XML语句)
            if (declaringFqn == null || declaringFqn.startsWith("java.")) {
                continue;
            }
            // 跳过 MyBatis-Plus/Flex/tk-mapper 框架 BaseMapper 方法(SQL由框架提供)
            if (MyBatisUtils.isFrameworkMapperMethod(method)) {
                continue;
            }
            if (method.isConstructor() || method.getBody() != null
                    || method.hasModifierProperty("static")) {
                continue;
            }

            LookupElementBuilder builder = LookupElementBuilder
                    .create(method, method.getName())
                    .withIcon(AllIcons.Nodes.Method)
                    .withBoldness(!MyBatisUtils.hasSqlAnnotation(method));
            if (method.getReturnType() != null) {
                builder = builder.withTypeText(method.getReturnType().getPresentableText());
            }
            String parameters = this.buildParameterText(method);
            if (parameters != null) {
                builder = builder.withTailText("(" + parameters + ")", true);
            }
            variants.add(builder);
        }
        return variants.toArray();
    }

    /**
     * 构建方法参数展示文本
     *
     * @param method 方法
     * @return 参数文本，无参数返回null
     * @since 1.1.0
     */
    private @Nullable String buildParameterText(@NotNull PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (PsiParameter parameter : parameters) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(parameter.getType().getPresentableText());
        }
        return builder.toString();
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
