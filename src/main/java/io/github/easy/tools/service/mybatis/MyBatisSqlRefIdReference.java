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
 * MyBatis include 标签 refid 属性引用
 * <p>
 * 支持 refid 到 sql 片段定义的引用跳转与补全:
 * <ul>
 * <li>普通refid: 在当前文件中查找 &lt;sql id="..."&gt;</li>
 * <li>带namespace前缀的refid(如 com.xxx.Mapper.sqlId): 跨文件解析</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisSqlRefIdReference extends PsiReferenceBase<XmlAttributeValue> {

    /**
     * 构造函数
     *
     * @param element refid属性值元素
     * @since 1.1.0
     */
    public MyBatisSqlRefIdReference(@NotNull XmlAttributeValue element) {
        super(element, MyBatisNamespaceReference.contentRange(element), true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        String refId = this.getElement().getValue();
        if (StringUtil.isEmpty(refId) || refId.contains("${")) {
            return null;
        }

        XmlFile currentFile = this.getContainingXmlFile();
        if (currentFile == null) {
            return null;
        }

        // 带namespace前缀的refid: namespace.sqlId 跨文件解析
        int lastDot = refId.lastIndexOf('.');
        if (lastDot > 0) {
            String namespace = refId.substring(0, lastDot);
            String sqlId = refId.substring(lastDot + 1);
            XmlFile targetFile = MyBatisUtils.findMapperXmlFile(this.getElement().getProject(), namespace);
            if (targetFile != null) {
                XmlTag sqlTag = MyBatisUtils.findTagByTagNameAndId(targetFile, "sql", sqlId);
                XmlAttributeValue idValue = MyBatisUtils.getIdAttributeValue(sqlTag);
                if (idValue != null) {
                    return idValue;
                }
            }
        }

        // 普通refid: 当前文件内解析
        XmlTag sqlTag = MyBatisUtils.findTagByTagNameAndId(currentFile, "sql", refId);
        return MyBatisUtils.getIdAttributeValue(sqlTag);
    }

    @Override
    public Object @NotNull [] getVariants() {
        XmlFile xmlFile = this.getContainingXmlFile();
        if (xmlFile == null || xmlFile.getRootTag() == null) {
            return new Object[0];
        }

        List<LookupElement> variants = new ArrayList<>();
        for (XmlTag tag : xmlFile.getRootTag().getSubTags()) {
            if (!"sql".equals(tag.getName())) {
                continue;
            }
            String id = tag.getAttributeValue("id");
            if (StringUtil.isEmpty(id)) {
                continue;
            }
            variants.add(LookupElementBuilder
                    .create(tag, id)
                    .withPresentableText(id)
                    .withIcon(AllIcons.Nodes.Include));
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
        // 保持namespace前缀形式(如 com.xxx.Mapper.sqlId)
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
     * 获取包含当前元素的XML文件
     *
     * @return XML文件，非文件场景返回null
     * @since 1.1.0
     */
    private @Nullable XmlFile getContainingXmlFile() {
        PsiFile psiFile = this.getElement().getContainingFile();
        return psiFile instanceof XmlFile xmlFile ? xmlFile : null;
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
