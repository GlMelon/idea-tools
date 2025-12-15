package io.github.easy.tools.utils;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * My Batis Utils
 *
 * @author haijun
 * @version 1.0.0
 * @date 2025-12-15 09:35:18
 * @since 1.0.0
 */
public final class MyBatisUtils {

    /**
     * 私有构造函数，防止实例化
     *
     * @since 1.0.0
     */
    private MyBatisUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 检查元素是否在MyBatis Mapper XML文件中
     *
     * @param element PSI元素
     * @return true如果在Mapper文件中
     * @since 1.0.0
     */
    public static boolean isInMapperFile(@NotNull PsiElement element) {
        // 向上查找XML文件
        PsiFile psiFile = element.getContainingFile();
        if (!(psiFile instanceof XmlFile xmlFile)) {
            return false;
        }

        // 检查根标签是否为mapper
        XmlTag rootTag = xmlFile.getRootTag();
        return rootTag != null && "mapper".equals(rootTag.getName());
    }

    /**
     * 检查XML标签是否在Mapper XML文件中
     *
     * @param xmlTag XML标签
     * @return true如果在Mapper文件中
     * @since 1.0.0
     */
    public static boolean isInMapperFile(@NotNull XmlTag xmlTag) {
        // 向上查找根标签
        XmlTag currentTag = xmlTag;
        while (currentTag.getParentTag() != null) {
            currentTag = currentTag.getParentTag();
        }

        // 检查根标签是否为mapper
        return "mapper".equals(currentTag.getName());
    }

    /**
     * 获取Mapper的namespace属性
     *
     * @param xmlTag XML标签
     * @return namespace值，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable String getMapperNamespace(@NotNull XmlTag xmlTag) {
        // 向上查找根标签
        XmlTag rootTag = xmlTag;
        while (rootTag.getParentTag() != null) {
            rootTag = rootTag.getParentTag();
        }

        // 检查根标签是否为mapper
        if (!"mapper".equals(rootTag.getName())) {
            return null;
        }

        // 获取namespace属性
        return rootTag.getAttributeValue("namespace");
    }

    /**
     * 在XML文件中查找对应的SQL标签
     *
     * @param xmlFile    XML文件
     * @param methodName 方法名
     * @return 找到的SQL标签，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable XmlTag findSqlTagByMethodName(@NotNull XmlFile xmlFile, @NotNull String methodName) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return null;
        }

        // 遍历所有子标签
        for (XmlTag tag : rootTag.getSubTags()) {
            String id = tag.getAttributeValue("id");
            if (methodName.equals(id)) {
                return tag;
            }
        }

        return null;
    }

    /**
     * 向上查找XML标签
     *
     * @param element PSI元素
     * @return XML标签，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable XmlTag findParentTag(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlTag) {
                return (XmlTag) current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 向上查找SQL标签(select/insert/update/delete)
     *
     * @param xmlTag 当前XML标签
     * @return SQL标签，未找到返回null
     * @since 1.0.0
     */
    public static @Nullable XmlTag findSqlTag(@NotNull XmlTag xmlTag) {
        XmlTag current = xmlTag;
        while (current != null) {
            String tagName = current.getName();
            if ("select".equals(tagName) || "insert".equals(tagName) ||
                    "update".equals(tagName) || "delete".equals(tagName)) {
                return current;
            }
            current = current.getParentTag();
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】解析根参数对应的 Java 类 (PsiClass)。 核心：优先读取 XML 标签上的 'parameterType' 属性，为链式访问提供准确的类型。
     *
     * @param context   context
     * @param paramName param name
     * @return psi class
     * @since 1.0.0
     */
    public static @Nullable PsiClass resolveRootParamClass(@NotNull PsiElement context, @NotNull String paramName) {
        // 1. 优先读取 XML 标签上的 parameterType 属性，解决泛型绑定失败时类型缺失的问题。
        XmlTag sqlTag = findSqlTag(findParentTag(context));
        if (sqlTag != null) {
            String type = sqlTag.getAttributeValue("parameterType");
            if (StrUtil.isNotBlank(type)) {
                Project project = context.getProject();
                return JavaPsiFacade.getInstance(project)
                        .findClass(type, context.getResolveScope());
            }
        }

        // 2. 如果 parameterType 无法提供类型，回退到从方法参数中查找
        PsiParameter parameter = findPsiParameter(context, paramName);
        if (parameter != null) {
            return PsiTypesUtil.getPsiClass(parameter.getType());
        }

        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】获取当前 XML 对应的 Mapper 接口方法中的参数。
     *
     * @param context   context
     * @param paramName param name
     * @return psi parameter
     * @since 1.0.0
     */
    public static @Nullable PsiParameter findPsiParameter(@NotNull PsiElement context, @NotNull String paramName) {
        PsiMethod method = findMethod(context);
        if (method == null) {
            return null;
        }

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            String paramAnnotationValue = getParamAnnotationValue(parameter);
            if (StrUtil.equals(paramAnnotationValue, paramName)) {
                return parameter;
            }
            if (parameter.getName().equals(paramName)) {
                return parameter;
            }
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】获取当前 XML 对应的 Mapper 接口方法。 支持向上查找父接口/类中定义的方法。
     *
     * @param context context
     * @return psi method
     * @since 1.0.0
     */
    public static @Nullable PsiMethod findMethod(@NotNull PsiElement context) {
        XmlTag elementTag = findParentTag(context);
        if (elementTag == null) {
            return null;
        }

        XmlTag sqlTag = findSqlTag(elementTag);
        if (sqlTag == null) {
            return null;
        }

        String id = sqlTag.getAttributeValue("id");
        String namespace = getMapperNamespace(sqlTag);

        if (StrUtil.hasBlank(id, namespace)) {
            return null;
        }

        PsiClass mapperClass = JavaPsiFacade.getInstance(context.getProject())
                .findClass(namespace, context.getResolveScope());
        if (mapperClass == null) {
            return null;
        }

        // 查找所有方法，包括继承的方法 (实现向上查找)
        for (PsiMethod method : mapperClass.getAllMethods()) {
            if (method.getName().equals(id)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】提取 @Param 注解的值。
     *
     * @param parameter parameter
     * @return string
     * @since 1.0.0
     */
    public static String getParamAnnotationValue(PsiParameter parameter) {
        PsiAnnotation annotation = parameter.getAnnotation("org.apache.ibatis.annotations.Param");
        if (annotation != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (value instanceof PsiLiteralExpression literalExpression) {
                Object val = literalExpression.getValue();
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】根据已解析的元素获取其类型对应的 PsiClass。
     *
     * @param element element
     * @return psi class
     * @since 1.0.0
     */
    public static @Nullable PsiClass getTypeOfResolvedElement(@NotNull PsiElement element) {
        if (element instanceof PsiField field) {
            return PsiTypesUtil.getPsiClass(field.getType());
        } else if (element instanceof PsiMethod method) {
            return PsiTypesUtil.getPsiClass(method.getReturnType());
        }
        return null;
    }

    /**
     * 【复用自 MyBatisParamReference】辅助方法：尝试将泛型类型参数 (PsiTypeParameter) 解析为其具体绑定的 PsiClass。
     *
     * @param psiClass psi class
     * @return psi class
     * @since 1.0.0
     */
    public static @Nullable PsiClass resolveActualClassFromType(@NotNull PsiClass psiClass) {
        if (!(psiClass instanceof PsiTypeParameter typeParameter)) {
            return psiClass;
        }

        PsiReferenceList extendsList = typeParameter.getExtendsList();

        PsiClassType[] boundTypes = extendsList.getReferencedTypes();
        if (boundTypes.length > 0) {
            return boundTypes[0].resolve();
        }

        Project project = typeParameter.getProject();
        return JavaPsiFacade.getInstance(project).findClass("java.lang.Object", typeParameter.getResolveScope());
    }

    /**
     * 【复用自 MyBatisParamReference】在类中查找字段或属性 (支持继承和 Getter/Setter 属性)。
     *
     * @param psiClass  psi class
     * @param fieldName field name
     * @return psi element
     * @since 1.0.0
     */
    public static @Nullable PsiElement findFieldOrPropertyInClass(@NotNull PsiClass psiClass, @NotNull String fieldName) {
        PsiField field = PropertyUtilBase.findPropertyField(psiClass, fieldName, false);
        if (field != null) {
            return field;
        }

        PsiMethod getter = PropertyUtilBase.findPropertyGetter(psiClass, fieldName, true, false);
        if (getter != null) {
            return getter;
        }

        String cleanName = fieldName.replace("_", "");
        for (PsiField f : psiClass.getAllFields()) {
            if (f.getName().replace("_", "").equalsIgnoreCase(cleanName)) {
                return f;
            }
        }
        return null;
    }
}
