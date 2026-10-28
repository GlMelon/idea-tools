package io.github.easy.tools.service.mybatis.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import io.github.easy.tools.action.conversion.PropertyNameConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * MyBatis-Plus QueryWrapper 列名补全贡献者
 * <p>
 * 在 QueryWrapper/UpdateWrapper/LambdaQueryWrapper 及 ChainWrapper 链式调用的
 * 条件方法(eq/like/in/orderByAsc...)首个 String 参数处，按包装器泛型实体
 * 补全数据库列名(@TableField/@TableId 值优先，默认驼峰转下划线)。
 * MyBatis-Plus 不在类路径时不产生任何候选。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class MyBatisPlusWrapperCompletionContributor extends CompletionContributor {

    /**
     * MyBatis-Plus 条件列方法(首参数为列名)
     */
    private static final Set<String> COLUMN_CONDITION_METHODS = Set.of(
            "eq", "ne", "gt", "ge", "lt", "le",
            "like", "notLike", "likeLeft", "likeRight",
            "in", "notIn", "between", "notBetween",
            "isNull", "isNotNull",
            "groupBy", "orderByAsc", "orderByDesc");

    /**
     * MyBatis-Plus AbstractWrapper 基类(QueryWrapper/UpdateWrapper/LambdaQueryWrapper 的父类)
     */
    private static final String ABSTRACT_WRAPPER = "com.baomidou.mybatisplus.core.conditions.AbstractWrapper";

    /**
     * MyBatis-Plus ChainWrapper 基类(QueryChainWrapper/UpdateChainWrapper 的父类)
     */
    private static final String ABSTRACT_CHAIN_WRAPPER = "com.baomidou.mybatisplus.core.conditions.AbstractChainWrapper";

    /**
     * MyBatis-Plus Lambda 包装器基类(条件参数为 SFunction 方法引用，非字符串列名)
     */
    private static final String ABSTRACT_LAMBDA_WRAPPER = "com.baomidou.mybatisplus.core.conditions.AbstractLambdaWrapper";

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        PsiExpressionList argumentList = PsiTreeUtil.getParentOfType(position, PsiExpressionList.class);
        if (argumentList == null || !(argumentList.getParent() instanceof PsiMethodCallExpression call)) {
            return;
        }
        // 只补全首个参数(列名参数)
        PsiExpression[] arguments = argumentList.getExpressions();
        int argumentIndex = -1;
        for (int i = 0; i < arguments.length; i++) {
            if (PsiTreeUtil.isAncestor(arguments[i], position, false)) {
                argumentIndex = i;
                break;
            }
        }
        if (argumentIndex != 0) {
            return;
        }
        String methodName = call.getMethodExpression().getReferenceName();
        if (methodName == null || !COLUMN_CONDITION_METHODS.contains(methodName)) {
            return;
        }

        PsiClass entityClass = this.resolveWrapperEntity(call, position);
        if (entityClass == null) {
            return;
        }
        this.addColumnVariants(entityClass, result);
        result.stopHere();
    }

    /**
     * 解析包装器调用接收者上的实体类型(泛型首参)
     *
     * @param call     条件方法调用
     * @param position 补全位置
     * @return 实体类，无法解析返回null
     * @since 1.2.0
     */
    private @Nullable PsiClass resolveWrapperEntity(@NotNull PsiMethodCallExpression call, @NotNull PsiElement position) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) {
            return null;
        }
        PsiType qualifierType = qualifier.getType();
        if (!(qualifierType instanceof PsiClassType classType)) {
            return null;
        }
        PsiClass wrapperClass = classType.resolve();
        if (wrapperClass == null || !this.isWrapperClass(wrapperClass, position)) {
            return null;
        }
        PsiType[] parameters = classType.getParameters();
        if (parameters.length < 1) {
            return null;
        }
        PsiClass entityClass = PsiTypesUtil.getPsiClass(parameters[0]);
        // 泛型未绑定(如原生类型变量)时不提供候选
        return entityClass instanceof PsiTypeParameter ? null : entityClass;
    }

    /**
     * 判断类是否为 MyBatis-Plus 包装器类型
     *
     * @param wrapperClass 接收者类型对应的类
     * @param position     补全位置(提供解析范围)
     * @return true如果是Wrapper类型
     * @since 1.2.0
     */
    private boolean isWrapperClass(@NotNull PsiClass wrapperClass, @NotNull PsiElement position) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(position.getProject());
        // Lambda 包装器(LambdaQueryWrapper等)条件参数为 SFunction 方法引用，不做列名补全
        PsiClass lambdaBase = facade.findClass(ABSTRACT_LAMBDA_WRAPPER, position.getResolveScope());
        if (lambdaBase != null && wrapperClass.isInheritor(lambdaBase, true)) {
            return false;
        }
        for (String baseFqn : new String[]{ABSTRACT_WRAPPER, ABSTRACT_CHAIN_WRAPPER}) {
            PsiClass baseClass = facade.findClass(baseFqn, position.getResolveScope());
            if (baseClass != null && wrapperClass.isInheritor(baseClass, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加实体列名补全候选
     *
     * @param entityClass 实体类
     * @param result      补全结果
     * @since 1.2.0
     */
    private void addColumnVariants(@NotNull PsiClass entityClass, @NotNull CompletionResultSet result) {
        for (PsiField field : entityClass.getAllFields()) {
            String fieldName = field.getName();
            if (fieldName == null || fieldName.startsWith("serialVersionUID")
                    || field.hasModifierProperty(PsiModifier.STATIC)
                    || field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                continue;
            }
            String explicitColumn = this.getExplicitColumnName(field);
            String columnName = explicitColumn != null ? explicitColumn
                    : PropertyNameConverter.toLowerUnderline(fieldName);
            if (columnName == null || columnName.isEmpty()) {
                continue;
            }
            LookupElementBuilder builder = LookupElementBuilder.create(columnName)
                    .withIcon(AllIcons.Nodes.Field)
                    .withTypeText(field.getType().getPresentableText(), true);
            if (!columnName.equals(fieldName)) {
                builder = builder.withTailText(" (" + fieldName + ")", true);
            }
            result.addElement(builder);
        }
    }

    /**
     * 读取字段 @TableField/@TableId 显式指定的列名
     *
     * @param field 实体字段
     * @return 显式列名，未指定返回null
     * @since 1.2.0
     */
    private @Nullable String getExplicitColumnName(@NotNull PsiField field) {
        String columnValue = this.annotationStringValue(field, "com.baomidou.mybatisplus.annotation.TableField");
        if (columnValue == null) {
            columnValue = this.annotationStringValue(field, "com.baomidou.mybatisplus.annotation.TableId");
        }
        return columnValue != null && !columnValue.isEmpty() ? columnValue : null;
    }

    /**
     * 读取注解 value 属性的常量字符串值
     *
     * @param field         字段
     * @param annotationFqn 注解完全限定名
     * @return 字符串值，未找到返回null
     * @since 1.2.0
     */
    private @Nullable String annotationStringValue(@NotNull PsiField field, @NotNull String annotationFqn) {
        var annotation = field.getAnnotation(annotationFqn);
        if (annotation == null) {
            return null;
        }
        var value = annotation.findAttributeValue("value");
        if (value instanceof com.intellij.psi.PsiLiteralExpression literal
                && literal.getValue() instanceof String text) {
            return text;
        }
        return null;
    }
}
