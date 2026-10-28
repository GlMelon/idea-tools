package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.service.mybatis.MyBatisJavaTypeReference;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * resultType 与 Mapper 方法返回值匹配检测
 * <p>
 * 对 select 语句做两类检测:
 * <ul>
 * <li>resultType 不匹配: select 的 resultType 对应类型与方法返回的单行类型
 * (List/IPage 泛型参数或本体)互不兼容(忽略数值类型间转换与 Object 宽容场景)</li>
 * <li>缺少结果类型: select 既无 resultMap 也无 resultType</li>
 * </ul>
 * 方法无法解析时不检测(由语句id一致性检测覆盖)。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class MyBatisResultTypeInspection extends LocalInspectionTool {

    /**
     * 返回值为集合时按单行类型比较的常用集合接口
     */
    private static final Set<String> COLLECTION_TYPES = Set.of(
            "java.util.List", "java.util.Collection", "java.lang.Iterable");

    /**
     * MyBatis-Plus 分页返回接口(IPage 及其实现 Page)
     */
    private static final String PAGE_INTERFACE = "com.baomidou.mybatisplus.core.metadata.IPage";

    /**
     * MyBatis-Plus 分页实现类(旧版本路径，兼容识别)
     */
    private static final Set<String> PAGE_TYPES = Set.of(
            PAGE_INTERFACE,
            "com.baomidou.mybatisplus.extension.plugins.pagination.Page",
            "com.baomidou.mybatisplus.core.metadata.Page");

    /**
     * 数值类型族(resultType 数值间转换不告警)
     */
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Double", "java.lang.Float", "java.lang.Number",
            "java.math.BigInteger", "java.math.BigDecimal",
            "int", "long", "short", "byte", "double", "float");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                MyBatisResultTypeInspection.this.checkResultType(attribute, holder);
            }

            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
                MyBatisResultTypeInspection.this.checkMissingResultType(tag, holder);
            }
        };
    }

    /**
     * 检测 resultType 与方法返回类型是否匹配
     *
     * @param attribute resultType属性
     * @param holder    问题收集器
     * @since 1.2.0
     */
    private void checkResultType(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder) {
        if (!"resultType".equals(attribute.getName())) {
            return;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !"select".equals(tag.getName()) || !MyBatisUtils.isInMapperFile(tag)) {
            return;
        }
        // 已配置 resultMap 时以 resultMap 为准，忽略 resultType
        if (StringUtil.isNotEmpty(tag.getAttributeValue("resultMap"))) {
            return;
        }

        XmlAttributeValue valueElement = attribute.getValueElement();
        String resultTypeName = attribute.getValue();
        if (valueElement == null || StringUtil.isEmpty(resultTypeName) || resultTypeName.contains("${")) {
            return;
        }

        PsiMethod method = MyBatisUtils.findMethod(tag);
        if (method == null) {
            return;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || PsiTypesUtil.getPsiClass(returnType) == null) {
            return;
        }
        PsiClass resultClass = MyBatisJavaTypeReference.resolveTypeText(attribute, resultTypeName);
        // 类型不可解析时不误报(由类型引用侧提示)
        if (resultClass == null) {
            return;
        }

        PsiClass rowClass = this.unwrapRowClass(returnType);
        if (rowClass == null) {
            return;
        }
        if (this.isCompatible(resultClass, rowClass)) {
            return;
        }
        holder.registerProblem(valueElement,
                "resultType '" + resultTypeName + "' 与方法 '" + method.getName()
                        + "' 的返回类型 '" + returnType.getPresentableText() + "' 不匹配",
                ProblemHighlightType.WARNING);
    }

    /**
     * 检测 select 语句是否缺少 resultMap/resultType
     *
     * @param tag    语句标签
     * @param holder 问题收集器
     * @since 1.2.0
     */
    private void checkMissingResultType(@NotNull XmlTag tag, @NotNull ProblemsHolder holder) {
        if (!"select".equals(tag.getName()) || !MyBatisUtils.isInMapperFile(tag)) {
            return;
        }
        XmlTag parentTag = tag.getParentTag();
        if (parentTag == null || !"mapper".equals(parentTag.getName())) {
            return;
        }
        if (StringUtil.isNotEmpty(tag.getAttributeValue("resultMap"))
                || StringUtil.isNotEmpty(tag.getAttributeValue("resultType"))) {
            return;
        }

        PsiMethod method = MyBatisUtils.findMethod(tag);
        if (method == null) {
            return;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || PsiType.VOID.equals(returnType)) {
            return;
        }
        XmlAttributeValue idValue = MyBatisUtils.getIdAttributeValue(tag);
        holder.registerProblem(idValue == null ? tag : idValue,
                "select 语句 '" + tag.getAttributeValue("id") + "' 未配置 resultMap 或 resultType",
                ProblemHighlightType.WARNING);
    }

    /**
     * 解包方法返回类型为"单行类型"
     * <p>
     * List&lt;E&gt;/Collection&lt;E&gt;/E[]/IPage&lt;E&gt;(MyBatis-Plus) 取元素类型，
     * 其余类型取本体；无法静态确定时返回null(跳过检测)。
     * </p>
     *
     * @param returnType 方法返回类型
     * @return 单行类型对应类，无法确定返回null
     * @since 1.2.0
     */
    private @Nullable PsiClass unwrapRowClass(@NotNull PsiType returnType) {
        if (returnType instanceof PsiArrayType arrayType) {
            return PsiTypesUtil.getPsiClass(arrayType.getComponentType());
        }
        if (!(returnType instanceof PsiClassType classType)) {
            return null;
        }
        PsiClass rawClass = classType.resolve();
        if (rawClass == null || rawClass instanceof PsiTypeParameter) {
            return null;
        }
        String fqn = rawClass.getQualifiedName();
        if (fqn == null) {
            return null;
        }

        if (COLLECTION_TYPES.contains(fqn) || this.implementsIPage(rawClass)) {
            PsiType[] parameters = classType.getParameters();
            if (parameters.length != 1) {
                return null;
            }
            return PsiTypesUtil.getPsiClass(parameters[0]);
        }
        // 参数化的自定义泛型返回(如 Result<User>)单行语义未知，跳过检测
        if (classType.getParameters().length > 0) {
            return null;
        }
        return rawClass;
    }

    /**
     * 判断类是否为 MyBatis-Plus 分页类型(IPage 及其实现)
     *
     * @param psiClass 类
     * @return true如果实现了 IPage
     * @since 1.2.0
     */
    private boolean implementsIPage(@NotNull PsiClass psiClass) {
        String fqn = psiClass.getQualifiedName();
        if (PAGE_TYPES.contains(fqn)) {
            return true;
        }
        PsiClass iPageClass = JavaPsiFacade.getInstance(psiClass.getProject())
                .findClass(PAGE_INTERFACE, psiClass.getResolveScope());
        return iPageClass != null && psiClass.isInheritor(iPageClass, true);
    }

    /**
     * 判断 resultType 类型与方法单行类型是否兼容
     * <p>
     * 兼容规则: 双向可赋值、同为数值类型族、任一为 Object。
     * Map 场景由可赋值性覆盖(resultType=map 与 Map 返回兼容，与实体类型互斥告警)。
     * </p>
     *
     * @param resultClass resultType对应的类
     * @param rowClass    方法单行类型
     * @return true如果兼容
     * @since 1.2.0
     */
    private boolean isCompatible(@NotNull PsiClass resultClass, @NotNull PsiClass rowClass) {
        String resultFqn = resultClass.getQualifiedName();
        String rowFqn = rowClass.getQualifiedName();
        if ("java.lang.Object".equals(resultFqn) || "java.lang.Object".equals(rowFqn)) {
            return true;
        }
        if (this.isNumeric(resultFqn) && this.isNumeric(rowFqn)) {
            return true;
        }
        // 同一类型或继承关系(双向可赋值视为兼容)
        if (resultFqn != null && resultFqn.equals(rowFqn)) {
            return true;
        }
        return resultClass.isInheritor(rowClass, true) || rowClass.isInheritor(resultClass, true);
    }

    /**
     * 判断是否为数值类型族
     *
     * @param fqn 完全限定名
     * @return true如果是数值类型
     * @since 1.2.0
     */
    private boolean isNumeric(@Nullable String fqn) {
        return fqn != null && NUMERIC_TYPES.contains(fqn);
    }
}
