package io.github.easy.tools.service.mybatis;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis Mapper XML 结构引用贡献者
 * <p>
 * 参考 MyBatisCodeHelper-Pro，为 Mapper XML 的结构属性注册引用，实现跳转/补全/重命名同步:
 * <ul>
 * <li>mapper 的 namespace 属性 -> Java 接口(跳转+接口全限定名补全)</li>
 * <li>select/insert/update/delete 的 id 属性 -> Mapper 接口方法(跳转+方法名补全)</li>
 * <li>resultMap / resultMap extends 属性 -> resultMap 定义(跳转+id补全)</li>
 * <li>include 的 refid 属性 -> sql 片段定义(跳转+id补全，支持namespace前缀跨文件)</li>
 * <li>parameterType / resultType / resultMap type 属性 -> Java 类(跳转+类名补全，支持内置别名)</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisMapperReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        return MyBatisMapperReferenceContributor.this.createReferences((XmlAttributeValue) element);
                    }
                }
        );
    }

    /**
     * 根据属性上下文创建对应的引用
     *
     * @param attributeValue 属性值元素
     * @return 引用数组，不匹配任何结构属性时为空数组
     * @since 1.1.0
     */
    private PsiReference @NotNull [] createReferences(@NotNull XmlAttributeValue attributeValue) {
        // 必须在Mapper XML文件中
        if (!MyBatisUtils.isInMapperFile(attributeValue)) {
            return PsiReference.EMPTY_ARRAY;
        }

        PsiElement parent = attributeValue.getParent();
        if (!(parent instanceof XmlAttribute attribute)) {
            return PsiReference.EMPTY_ARRAY;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        String attrName = attribute.getName();
        String tagName = tag.getName();

        PsiReference reference = null;
        switch (attrName) {
            case "namespace":
                if ("mapper".equals(tagName)) {
                    reference = new MyBatisNamespaceReference(attributeValue);
                }
                break;
            case "id":
                if (MyBatisUtils.isStatementTag(tagName)) {
                    reference = new MyBatisStatementIdReference(attributeValue);
                }
                break;
            case "resultMap":
                reference = new MyBatisResultMapReference(attributeValue);
                break;
            case "extends":
                if ("resultMap".equals(tagName)) {
                    reference = new MyBatisResultMapReference(attributeValue);
                }
                break;
            case "refid":
                if ("include".equals(tagName)) {
                    reference = new MyBatisSqlRefIdReference(attributeValue);
                }
                break;
            case "parameterType":
            case "resultType":
                if (MyBatisUtils.isStatementTag(tagName) || "selectKey".equals(tagName)) {
                    reference = new MyBatisJavaTypeReference(attributeValue);
                }
                break;
            case "property":
                // resultMap 映射子标签的 property -> Java 类字段/属性
                if ("result".equals(tagName) || "id".equals(tagName)
                        || "collection".equals(tagName) || "association".equals(tagName)) {
                    reference = new MyBatisResultPropertyReference(attributeValue);
                }
                break;
            case "type":
                if ("resultMap".equals(tagName)) {
                    reference = new MyBatisJavaTypeReference(attributeValue);
                }
                break;
            default:
                break;
        }

        if (reference == null) {
            return PsiReference.EMPTY_ARRAY;
        }
        return new PsiReference[]{reference};
    }
}
