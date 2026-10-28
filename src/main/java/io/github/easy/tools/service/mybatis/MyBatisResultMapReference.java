package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis resultMap 属性引用
 * <p>
 * 支持 select 等标签的 resultMap 属性、resultMap 标签的 extends 属性
 * 到本文件 resultMap 定义的引用跳转与 id 补全。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisResultMapReference extends PsiReferenceBase<XmlAttributeValue> {

    /**
     * 构造函数
     *
     * @param element resultMap属性值元素
     * @since 1.1.0
     */
    public MyBatisResultMapReference(@NotNull XmlAttributeValue element) {
        super(element, MyBatisNamespaceReference.contentRange(element), true);
    }

    /**
     * 获取包含当前元素的XML文件
     *
     * @return XML文件，非文件场景返回null
     * @since 1.1.0
     */
    private @Nullable XmlFile getContainingXmlFile() {
        PsiFile psiFile = this.getElement().getContainingFile();
        return psiFile instanceof XmlFile xmlFile ? xmlFile : null;
    }

    @Override
    public @Nullable PsiElement resolve() {
        String resultMapId = this.getElement().getValue();
        if (StringUtil.isEmpty(resultMapId)) {
            return null;
        }
        XmlFile xmlFile = this.getContainingXmlFile();
        if (xmlFile == null) {
            return null;
        }
        XmlTag resultMapTag = MyBatisUtils.findTagByTagNameAndId(xmlFile, "resultMap", resultMapId);
        return MyBatisUtils.getIdAttributeValue(resultMapTag);
    }

    @Override
    public Object @NotNull [] getVariants() {
        XmlFile xmlFile = this.getContainingXmlFile();
        if (xmlFile == null || xmlFile.getRootTag() == null) {
            return new Object[0];
        }

        List<LookupElement> variants = new ArrayList<>();
        for (XmlTag tag : xmlFile.getRootTag().getSubTags()) {
            if (!"resultMap".equals(tag.getName())) {
                continue;
            }
            String id = tag.getAttributeValue("id");
            if (StringUtil.isEmpty(id)) {
                continue;
            }
            String type = tag.getAttributeValue("type");
            LookupElementBuilder builder = LookupElementBuilder
                    .create(tag, id)
                    .withPresentableText(id)
                    .withIcon(AllIcons.Nodes.Class);
            if (StringUtil.isNotEmpty(type)) {
                builder = builder.withTypeText(type, true);
            }
            variants.add(builder);
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
