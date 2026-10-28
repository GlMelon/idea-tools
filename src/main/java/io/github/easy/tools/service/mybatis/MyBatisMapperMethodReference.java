package io.github.easy.tools.service.mybatis;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mapper 接口方法名 -> XML 语句 id 的引用
 * <p>
 * 在 Mapper 接口方法名上注册指向 XML 语句 id 的软引用，
 * 实现 Ctrl+B 从方法跳转到 XML、Find Usages 包含 XML 引用，
 * 并配合 XML 侧引用完成方法重命名时 id 的双向同步。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-27
 * @since 1.1.0
 */
public class MyBatisMapperMethodReference extends PsiReferenceBase<PsiElement> {

    /**
     * 对应的Mapper方法
     *
     * @since 1.1.0
     */
    private final PsiMethod mapperMethod;

    /**
     * 构造函数
     *
     * @param element     方法名标识符元素
     * @param mapperMethod 对应的Mapper方法
     * @since 1.1.0
     */
    public MyBatisMapperMethodReference(@NotNull PsiElement element, @NotNull PsiMethod mapperMethod) {
        super(element, true);
        this.mapperMethod = mapperMethod;
    }

    @Override
    public @Nullable PsiElement resolve() {
        PsiClass containingClass = this.mapperMethod.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        String qualifiedName = containingClass.getQualifiedName();
        XmlFile xmlFile = MyBatisUtils.findMapperXmlFile(this.mapperMethod.getProject(), qualifiedName);
        if (xmlFile == null) {
            return null;
        }
        XmlTag sqlTag = MyBatisUtils.findSqlTagByMethodName(xmlFile, this.mapperMethod.getName());
        return MyBatisUtils.getIdAttributeValue(sqlTag);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // 方法名声明处无需补全
        return new Object[0];
    }
}
