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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Parameter Completion Strategy
 *
 * @author haijun
 * @date 2025-12-18 14:29:21
 * @version 1.0.0
 * @since 1.0.0
 */
public class ParameterCompletionStrategy implements CompletionStrategy {

    /**
     * 基本数据类型集合
     *
     */
    private static final Set<String> PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
            "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte",
            "float", "Float", "double", "Double", "boolean", "Boolean",
            "char", "Character", "String", "BigDecimal", "BigInteger",
            "Date", "LocalDate", "LocalDateTime", "LocalTime"
    ));

    /**
     * 提供参数补全
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

        String currentInput = parseResult.getCurrentInput();
        PsiParameter[] parameters = method.getParameterList().getParameters();

        for (PsiParameter parameter : parameters) {
            String paramName = parameter.getName();

            // 获取@Param注解的值
            String annotationValue = MyBatisUtils.getParamAnnotationValue(parameter);
            if (StrUtil.isNotBlank(annotationValue)) {
                paramName = annotationValue;
            }

            // 如果参数名匹配当前输入
            if (StrUtil.isBlank(currentInput) || paramName.startsWith(currentInput)) {
                String typeName = parameter.getType().getPresentableText();
                boolean isPrimitive = this.isPrimitiveType(typeName);

                LookupElementBuilder builder = LookupElementBuilder
                        .create(paramName)
                        .withIcon(AllIcons.Nodes.Parameter)
                        .withTypeText(typeName)
                        .withTailText(" " + typeName, true)
                        .withInsertHandler(new ParameterInsertHandler(isPrimitive, context.isInXmlAttribute()));

                // 为补全项添加高优先级,确保在其他插件之前显示
                LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, 100.0);
                result.addElement(prioritized);
            }
        }
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
        return context.getCompletionType() == CompletionContext.CompletionType.PARAMETER;
    }

    /**
     * 判断是否为基本数据类型
     *
     * @param typeName 类型名称
     * @return true如果是基本类型
     * @since 1.0.0
     */
    private boolean isPrimitiveType(@NotNull String typeName) {
        String simpleType = typeName.replaceAll("<.*>", "").trim();
        int lastDot = simpleType.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleType = simpleType.substring(lastDot + 1);
        }
        return PRIMITIVE_TYPES.contains(simpleType);
    }

    /**
     * 参数插入处理器
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    private static class ParameterInsertHandler implements InsertHandler<LookupElement> {

        /**
         * is primitive type
         *
         */
        private final boolean isPrimitiveType;
        /**
         * is in xml attribute
         *
         */
        private final boolean isInXmlAttribute;

        /**
         * Parameter Insert Handler
         *
         * @param isPrimitiveType is primitive type
         * @param isInXmlAttribute is in xml attribute
         * @since 1.0.0
         */
        public ParameterInsertHandler(boolean isPrimitiveType, boolean isInXmlAttribute) {
            this.isPrimitiveType = isPrimitiveType;
            this.isInXmlAttribute = isInXmlAttribute;
        }

        /**
         * Handle Insert
         *
         * @param context context
         * @param lookupElement lookup element
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement lookupElement) {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            int startOffset = context.getStartOffset();
            int tailOffset = context.getTailOffset();

            // 如果在XML属性中或不是基本类型，使用IntelliJ的默认行为
            // IntelliJ会自动替换startOffset到tailOffset之间的内容
            if (this.isInXmlAttribute || !this.isPrimitiveType) {
                // 不做任何处理，让IntelliJ的默认替换逻辑生效
                return;
            }

            // 只有在XML内容中且是基本类型时，才需要添加 #{ }
            String insertText = lookupElement.getLookupString();
            StringBuilder textBuilder = new StringBuilder();

            // 检查前面是否已经有#{
            String textBefore = "";
            if (startOffset > 2) {
                textBefore = document.getText().substring(Math.max(0, startOffset - 2), startOffset);
            }

            if (!"#{".equals(textBefore)) {
                textBuilder.append("#{");
            }
            textBuilder.append(insertText);

            // 检查后面是否已经有}
            String textAfter = "";
            if (tailOffset < document.getTextLength()) {
                textAfter = document.getText().substring(tailOffset, Math.min(tailOffset + 1, document.getTextLength()));
            }

            if (!"}".equals(textAfter)) {
                textBuilder.append("}");
            }

            // 删除已经插入的内容,重新插入
            document.deleteString(startOffset, tailOffset);
            document.insertString(startOffset, textBuilder.toString());

            // 移动光标到}后面
            editor.getCaretModel().moveToOffset(startOffset + textBuilder.length());
        }
    }
}
