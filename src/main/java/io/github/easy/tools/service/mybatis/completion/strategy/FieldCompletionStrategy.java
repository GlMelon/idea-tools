package io.github.easy.tools.service.mybatis.completion.strategy;

import cn.hutool.core.util.StrUtil;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Field Completion Strategy
 *
 * @author haijun
 * @date 2025-12-18 14:30:15
 * @version 1.0.0
 * @since 1.0.0
 */
public class FieldCompletionStrategy implements CompletionStrategy {

    /**
     * 需要排除的字段名称
     */
    private static final Set<String> EXCLUDED_FIELDS = new HashSet<>(Arrays.asList(
            "serialVersionUID", "class"
    ));

    /**
     * 提供字段补全
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    @Override
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        PsiMethod method = context.getMapperMethod();
        if (method == null) {
            return;
        }

        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(context.getCurrentText());

        String rootParam = parseResult.getRootParam();
        if (StrUtil.isBlank(rootParam)) {
            return;
        }

        // 查找根参数对应的PsiParameter
        PsiParameter rootParameter = this.findRootParameter(method, rootParam);
        if (rootParameter == null) {
            return;
        }

        // 解析到目标类
        PsiClass targetClass = this.resolveTargetClass(rootParameter, parseResult);
        if (targetClass == null) {
            return;
        }

        // 计算用于匹配字段名的前缀
        // 例如：currentText = "query.ty"，则fieldNamePrefix = "ty"
        // 例如：currentText = "query."，则fieldNamePrefix = ""
        String currentInput = parseResult.getCurrentInput();
        String fieldNamePrefix = this.extractFieldNamePrefix(currentInput);

        // 使用自定义前缀匹配器，只匹配字段名部分
        CompletionResultSet fieldResult = result.withPrefixMatcher(fieldNamePrefix);

        // 提供字段补全
        this.provideFieldCompletions(targetClass, parseResult, context, fieldResult);
    }

    /**
     * 提取用于匹配字段名的前缀
     * 从currentInput中提取最后一个点号之后的内容
     *
     * @param currentInput 当前输入
     * @return 字段名前缀
     * @since 1.0.0
     */
    @NotNull
    private String extractFieldNamePrefix(@NotNull String currentInput) {
        int lastDotIndex = currentInput.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < currentInput.length() - 1) {
            // 有点号，返回点号后面的内容
            return currentInput.substring(lastDotIndex + 1);
        } else if (lastDotIndex == currentInput.length() - 1) {
            // 点号在最后，返回空字符串
            return "";
        }
        // 没有点号，返回整个输入
        return currentInput;
    }

    /**
     * 判断是否支持该上下文
     *
     * @param context 补全上下文
     * @return true如果支持
     * @since 1.0.0
     */
    @Override
    public boolean supports(@NotNull CompletionContext context) {
        return context.getCompletionType() == CompletionContext.CompletionType.FIELD;
    }

    /**
     * 查找根参数
     *
     * @param method    方法
     * @param paramName 参数名
     * @return 参数对象
     * @since 1.0.0
     */
    private PsiParameter findRootParameter(@NotNull PsiMethod method, @NotNull String paramName) {
        for (PsiParameter param : method.getParameterList().getParameters()) {
            String name = param.getName();
            String annotationValue = MyBatisUtils.getParamAnnotationValue(param);
            if (StrUtil.isNotBlank(annotationValue)) {
                name = annotationValue;
            }
            if (paramName.equals(name)) {
                return param;
            }
        }
        return null;
    }

    /**
     * 解析目标类
     *
     * @param rootParameter 根参数
     * @param parseResult   解析结果
     * @return 目标类
     * @since 1.0.0
     */
    private PsiClass resolveTargetClass(@NotNull PsiParameter rootParameter,
                                        @NotNull MyBatisExpressionParser.ExpressionParseResult parseResult) {
        PsiClass currentClass = MyBatisUtils.resolveRootParamClass(rootParameter, parseResult.getRootParam());
        if (currentClass == null) {
            return null;
        }

        currentClass = MyBatisUtils.resolveActualClassFromType(currentClass);
        if (currentClass == null) {
            return null;
        }

        // 如果有中间路径,逐级解析
        String[] parts = parseResult.getParts();
        for (int i = 1; i < parts.length - 1; i++) {
            String fieldName = parts[i];
            PsiElement field = MyBatisUtils.findFieldOrPropertyInClass(currentClass, fieldName);
            if (field == null) {
                return null;
            }

            currentClass = MyBatisUtils.getTypeOfResolvedElement(field);
            if (currentClass == null) {
                return null;
            }

            currentClass = MyBatisUtils.resolveActualClassFromType(currentClass);
        }

        return currentClass;
    }

    /**
     * 提供字段补全
     *
     * @param psiClass    类
     * @param parseResult 解析结果
     * @param context     上下文
     * @param result      结果集
     * @since 1.0.0
     */
    private void provideFieldCompletions(@NotNull PsiClass psiClass,
                                         @NotNull MyBatisExpressionParser.ExpressionParseResult parseResult,
                                         @NotNull CompletionContext context,
                                         @NotNull CompletionResultSet result) {
        String currentInput = parseResult.getCurrentInput();
        String prefixPath = parseResult.getPrefixPath();
        boolean isInXmlAttribute = context.isInXmlAttribute();

        // 获取当前类的字段(不包括父类)
        List<PsiField> ownFields = new ArrayList<>(Arrays.asList(psiClass.getFields()));

        // 获取所有字段(包括父类)
        List<PsiField> allFields = new ArrayList<>(Arrays.asList(psiClass.getAllFields()));

        // 从所有字段中移除当前类的字段,得到父类字段
        List<PsiField> parentFields = new ArrayList<>(allFields);
        parentFields.removeAll(ownFields);

        // 先处理当前类的字段(优先级高)
        this.processFields(ownFields, prefixPath, isInXmlAttribute, context, result);

        // 再处理父类的字段(优先级低)
        this.processFields(parentFields, prefixPath, isInXmlAttribute, context, result);
    }

    /**
     * 处理字段列表
     *
     * @param fields           字段列表
     * @param prefixPath       前缀路径
     * @param isInXmlAttribute 是否在XML属性中
     * @param context          上下文
     * @param result           结果集
     * @since 1.0.0
     */
    private void processFields(@NotNull List<PsiField> fields,
                               @NotNull String prefixPath,
                               boolean isInXmlAttribute,
                               @NotNull CompletionContext context,
                               @NotNull CompletionResultSet result) {
        for (PsiField field : fields) {
            String fieldName = field.getName();

            // 排除静态字段和特殊字段
            if (field.hasModifierProperty("static") || EXCLUDED_FIELDS.contains(fieldName)) {
                continue;
            }

            // 不需要手动模糊匹配，PrefixMatcher会自动过滤
            // 构建完整的路径（用于展示）
            String fullPath = StrUtil.isBlank(prefixPath) ? fieldName : prefixPath + "." + fieldName;

            // 提取字段描述
            String fieldDescription = this.extractFieldDescription(field);

            // 构建LookupElement，lookupString设置为字段名
            LookupElementBuilder builder = LookupElementBuilder
                    .create(fieldName)
                    .withPresentableText(fullPath)  // 显示完整路径
                    .withIcon(AllIcons.Nodes.Field)
                    .withTypeText(field.getType().getPresentableText())
                    .withInsertHandler(new FieldInsertHandler(isInXmlAttribute, prefixPath, fullPath, context));

            // 如果有描述信息,添加到tailText中
            if (StrUtil.isNotBlank(fieldDescription)) {
                builder = builder.withTailText(" " + field.getType().getPresentableText() + " - " + fieldDescription, true);
            } else {
                builder = builder.withTailText(" " + field.getType().getPresentableText(), true);
            }

            // 为补全项添加高优先级,确保在其他插件之前显示
            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, 100.0);
            result.addElement(prioritized);
        }
    }

    /**
     * 提取字段描述信息
     *
     * @param field 字段
     * @return 字段描述信息
     * @since 1.0.0
     */
    @NotNull
    private String extractFieldDescription(@NotNull PsiField field) {
        // 1. 尝试从 Swagger 3.x @Schema 注解获取描述
        PsiAnnotation schemaAnnotation = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
        if (schemaAnnotation != null) {
            String description = this.getAnnotationAttributeValue(schemaAnnotation, "description");
            if (StrUtil.isNotBlank(description)) {
                return description;
            }
        }

        // 2. 尝试从 Swagger 2.x @ApiModelProperty 注解获取描述
        PsiAnnotation apiModelPropertyAnnotation = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
        if (apiModelPropertyAnnotation != null) {
            String value = this.getAnnotationAttributeValue(apiModelPropertyAnnotation, "value");
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }

        // 3. 尝试从JavaDoc注释中提取描述
        PsiDocComment docComment = field.getDocComment();
        if (docComment != null) {
            String javadocDescription = this.extractJavadocDescription(docComment);
            if (StrUtil.isNotBlank(javadocDescription)) {
                return javadocDescription;
            }
        }

        return "";
    }

    /**
     * 从JavaDoc注释中提取描述文本
     *
     * @param docComment JavaDoc注释
     * @return 描述文本
     * @since 1.0.0
     */
    @NotNull
    private String extractJavadocDescription(@NotNull PsiDocComment docComment) {
        PsiElement[] descriptionElements = docComment.getDescriptionElements();
        if (descriptionElements.length == 0) {
            return "";
        }

        StringBuilder description = new StringBuilder();
        for (PsiElement element : descriptionElements) {
            String text = element.getText().trim();
            if (StrUtil.isNotBlank(text)) {
                description.append(text).append(" ");
            }
        }

        String result = description.toString().trim();
        if (StrUtil.isBlank(result)) {
            return "";
        }

        // 清理HTML标签和多余空格
        result = result.replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();

        // 限制描述长度
        int maxLength = 100;
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength) + "...";
        }

        return result;
    }

    /**
     * 获取注解属性值
     *
     * @param annotation 注解
     * @param attrNames  属性名
     * @return 属性值
     * @since 1.0.0
     */
    @NotNull
    private String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, String... attrNames) {
        for (String attrName : attrNames) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue(attrName);
            if (value != null) {
                String text = value.getText();
                if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
                    text = text.substring(1, text.length() - 1);
                }
                return text.trim();
            }
        }
        return "";
    }

    /**
     * 字段插入处理器
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    private static class FieldInsertHandler implements InsertHandler<LookupElement> {

        /**
         * is in xml attribute
         */
        private final boolean isInXmlAttribute;

        /**
         * prefix path (如: query 或 query.user)
         */
        private final String prefixPath;

        /**
         * full path (如: query.type)
         */
        private final String fullPath;

        /**
         * 补全上下文
         */
        private final CompletionContext context;

        /**
         * Field Insert Handler
         *
         * @param isInXmlAttribute is in xml attribute
         * @param prefixPath       prefix path
         * @param fullPath         full path
         * @param context          补全上下文
         * @since 1.0.0
         */
        public FieldInsertHandler(boolean isInXmlAttribute, String prefixPath, String fullPath, CompletionContext context) {
            this.isInXmlAttribute = isInXmlAttribute;
            this.prefixPath = prefixPath;
            this.fullPath = fullPath;
            this.context = context;
        }

        /**
         * Handle Insert
         *
         * @param insertionContext insertion context
         * @param lookupElement lookup element
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext insertionContext, @NotNull LookupElement lookupElement) {
            Document document = insertionContext.getDocument();
            Editor editor = insertionContext.getEditor();
            int startOffset = insertionContext.getStartOffset();
            int tailOffset = insertionContext.getTailOffset();

            // 先删除IntelliJ刚插入的内容
            document.deleteString(startOffset, tailOffset);

            // 如果在XML属性中
            if (this.isInXmlAttribute) {
                // 在XML属性中，只需要插入完整路径，不需要#{}
                this.handleInsertInAttribute(document, editor, startOffset);
                return;
            }

            // 在XML内容中，需要特殊处理
            // 此时IntelliJ已经插入了字段名，我们需要：
            // 1. 删除IntelliJ插入的字段名（已完成）
            // 2. 删除前面的前缀
            // 3. 插入完整的 #{完整路径}

            // 检查是否在#{}表达式内
            boolean isInExpression = this.context.getExpressionStartOffset() >= 0;

            if (isInExpression) {
                // 在#{}表达式内
                this.handleInsertInExpression(document, editor, startOffset);
            } else {
                // 不在#{}表达式内
                this.handleInsertOutsideExpression(document, editor, startOffset);
            }
        }

        /**
         * 处理在XML属性中的插入
         *
         * @param document document
         * @param editor editor
         * @param currentOffset 当前偏移量（IntelliJ插入后又删除的位置）
         * @since 1.0.0
         */
        private void handleInsertInAttribute(@NotNull Document document,
                                            @NotNull Editor editor,
                                            int currentOffset) {
            // 在XML属性中，需要删除前面的前缀，然后插入完整路径
            String currentInput = this.context.getCurrentText();
            int documentLength = document.getTextLength();

            // 计算前缀的起始位置
            int prefixStart = currentOffset - currentInput.length();
            if (prefixStart < 0) {
                prefixStart = 0;
            }

            // 边界检查
            if (prefixStart > documentLength || currentOffset > documentLength) {
                // 如果offset无效，直接插入完整路径
                document.insertString(currentOffset, this.fullPath);
                editor.getCaretModel().moveToOffset(currentOffset + this.fullPath.length());
                return;
            }

            // 删除前缀
            if (prefixStart < currentOffset) {
                document.deleteString(prefixStart, currentOffset);
            }

            // 插入完整路径
            document.insertString(prefixStart, this.fullPath);

            // 移动光标到插入内容的末尾
            editor.getCaretModel().moveToOffset(prefixStart + this.fullPath.length());
        }

        /**
         * 处理在#{}表达式内的插入
         *
         * @param document document
         * @param editor editor
         * @param currentOffset 当前偏移量（IntelliJ插入后又删除的位置）
         * @since 1.0.0
         */
        private void handleInsertInExpression(@NotNull Document document,
                                             @NotNull Editor editor,
                                             int currentOffset) {
            int exprStart = this.context.getExpressionStartOffset();
            int exprEnd = this.context.getExpressionEndOffset();
            int documentLength = document.getTextLength();

            // 边界检查
            if (exprStart < 0 || exprStart > documentLength) {
                this.insertSimpleExpression(document, editor, currentOffset);
                return;
            }

            // 计算需要删除的范围
            // 从表达式开始位置(#{)到当前位置
            int deleteStart = exprStart;
            int deleteEnd = currentOffset;

            // 如果找到了表达式结束位置(}),也删除它
            if (exprEnd >= 0 && exprEnd < documentLength && exprEnd >= currentOffset) {
                deleteEnd = exprEnd + 1;
            }

            // 删除整个表达式内容（包括#{}）
            if (deleteStart < deleteEnd && deleteEnd <= documentLength) {
                document.deleteString(deleteStart, deleteEnd);
            }

            // 插入新的完整表达式
            String newExpression = "#{" + this.fullPath + "}";
            document.insertString(deleteStart, newExpression);

            // 移动光标到表达式结束位置
            editor.getCaretModel().moveToOffset(deleteStart + newExpression.length());
        }

        /**
         * 处理不在#{}表达式内的插入
         *
         * @param document document
         * @param editor editor
         * @param currentOffset 当前偏移量（IntelliJ插入后又删除的位置）
         * @since 1.0.0
         */
        private void handleInsertOutsideExpression(@NotNull Document document,
                                                  @NotNull Editor editor,
                                                  int currentOffset) {
            // 需要删除前面的前缀（如query.）
            String currentInput = this.context.getCurrentText();
            int documentLength = document.getTextLength();

            // 计算前缀的起始位置
            int prefixStart = currentOffset - currentInput.length();
            if (prefixStart < 0) {
                prefixStart = 0;
            }

            // 边界检查
            if (prefixStart > documentLength || currentOffset > documentLength) {
                this.insertSimpleExpression(document, editor, currentOffset);
                return;
            }

            // 删除前缀
            if (prefixStart < currentOffset) {
                document.deleteString(prefixStart, currentOffset);
            }

            // 插入新的完整表达式
            String newExpression = "#{" + this.fullPath + "}";
            document.insertString(prefixStart, newExpression);

            // 移动光标到表达式结束位置
            editor.getCaretModel().moveToOffset(prefixStart + newExpression.length());
        }

        /**
         * 简单模式插入表达式(当offset计算出错时的回退方案)
         *
         * @param document document
         * @param editor editor
         * @param offset offset
         * @since 1.0.0
         */
        private void insertSimpleExpression(@NotNull Document document,
                                           @NotNull Editor editor,
                                           int offset) {
            String newExpression = "#{" + this.fullPath + "}";
            document.insertString(offset, newExpression);
            editor.getCaretModel().moveToOffset(offset + newExpression.length());
        }
    }
}
