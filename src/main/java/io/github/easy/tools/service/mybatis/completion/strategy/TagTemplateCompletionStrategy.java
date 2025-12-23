package io.github.easy.tools.service.mybatis.completion.strategy;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.github.easy.tools.action.conversion.PropertyNameConverter;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.utils.MyBatisUtils;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag Template Completion Strategy
 *
 * @author haijun
 * @date 2025-12-18 14:30:59
 * @version 1.0.0
 * @since 1.0.0
 */
public class TagTemplateCompletionStrategy implements CompletionStrategy {

    /**
     * 支持的标签模板列表
     *
     */
    private final List<TagTemplate> templates;

    /**
     * 构造函数
     *
     * @since 1.0.0
     */
    public TagTemplateCompletionStrategy() {
        this.templates = new ArrayList<>();
        this.initializeTemplates();
    }

    /**
     * 初始化标签模板
     *
     * @since 1.0.0
     */
    private void initializeTemplates() {
        // if标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("if")
                .description("生成if标签(判断非空)")
                .template("<if test=\"{expression} != null and {expression} != ''\">\n    {cursor}\n</if>")
                .build());

        this.templates.add(TagTemplate.builder()
                .keyword("ifnull")
                .description("生成if标签(判断为空)")
                .template("<if test=\"{expression} == null or {expression} == ''\">\n    {cursor}\n</if>")
                .build());

        // foreach标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("for")
                .description("生成foreach标签")
                .template("<foreach collection=\"{expression}\" item=\"item\" separator=\",\">\n    {cursor}\n</foreach>")
                .build());

        this.templates.add(TagTemplate.builder()
                .keyword("foreach")
                .description("生成foreach标签(完整)")
                .template("<foreach collection=\"{expression}\" item=\"item\" index=\"index\" separator=\",\" open=\"(\" close=\")\">\n    {cursor}\n</foreach>")
                .build());

        // where标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("where")
                .description("生成where标签")
                .template("<where>\n    {cursor}\n</where>")
                .build());

        // set标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("set")
                .description("生成set标签(用于update)")
                .template("<set>\n    <if test=\"{expression} != null\">\n        {field} = #{{{expression}}},\n    </if>\n    {cursor}\n</set>")
                .build());

        // choose-when-otherwise标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("choose")
                .description("生成choose-when-otherwise标签")
                .template("<choose>\n    <when test=\"{expression} != null\">\n        {cursor}\n    </when>\n    <otherwise>\n        \n    </otherwise>\n</choose>")
                .build());
    }

    /**
     * 提供标签模板补全
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    @Override
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        String currentText = context.getCurrentText();

        // 解析表达式,提取关键字
        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(currentText);

        String[] parts = parseResult.getParts();
        if (parts.length < 2) {
            return;
        }

        // 最后一部分是关键字（可能是空字符串或部分关键字）
        String keyword = parts[parts.length - 1].toLowerCase();

        // 前面部分是表达式
        StringBuilder expressionBuilder = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                expressionBuilder.append(".");
            }
            expressionBuilder.append(parts[i]);
        }
        String expression = expressionBuilder.toString();

        // 验证表达式是否有效
        if (!this.isValidExpression(context, expression)) {
            return;
        }

        // 使用自定义前缀匹配器，只匹配标签关键字部分
        // 例如：输入"query.endTime."，keyword=""，匹配所有标签
        // 例如：输入"query.endTime.i"，keyword="i"，匹配if和ifnull
        CompletionResultSet tagResult = result.withPrefixMatcher(keyword);

        // 提供匹配的模板
        for (TagTemplate template : this.templates) {
            // 由于使用了自定义PrefixMatcher，这里不需要手动检查startsWith
            // PrefixMatcher会自动过滤
            String fieldName = this.extractFieldName(parts);
            LookupElementBuilder builder = LookupElementBuilder
                    .create(template.getKeyword())
                    .withIcon(AllIcons.Nodes.Tag)
                    .withTypeText("MyBatis标签")
                    .withTailText(" " + template.getDescription(), true)
                    .withInsertHandler(new TagTemplateInsertHandler(template, expression, fieldName));

            // 为补全项添加高优先级,确保在其他插件之前显示
            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, 100.0);
            tagResult.addElement(prioritized);
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
        // 只在非XML属性中支持
        if (context.isInXmlAttribute()) {
            return false;
        }

        // 如果已经确定是TAG_TEMPLATE类型，直接返回true
        if (context.getCompletionType() == CompletionContext.CompletionType.TAG_TEMPLATE) {
            return true;
        }

        // 即使是FIELD类型，也检查是否可能是标签模板
        // 这样可以同时提供字段补全和标签模板补全
        String currentText = context.getCurrentText();
        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(currentText);

        // 至少要有两个部分: 参数名 + 关键字或字段
        if (parseResult.getParts().length < 2) {
            return false;
        }

        // 检查最后一部分是否可能是标签关键字
        String lastPart = parseResult.getParts()[parseResult.getParts().length - 1].toLowerCase();
        return this.isTagKeywordPrefix(lastPart);
    }

    /**
     * 判断是否为标签关键字的前缀
     *
     * @param input 输入文本
     * @return true如果是标签关键字的前缀
     * @since 1.0.0
     */
    private boolean isTagKeywordPrefix(@NotNull String input) {
        if (StringUtil.isEmpty(input)) {
            return true; // 空字符串匹配所有标签
        }

        // 检查是否是任何标签关键字的前缀
        for (TagTemplate template : this.templates) {
            if (template.getKeyword().startsWith(input)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 验证表达式是否有效
     *
     * @param context    上下文
     * @param expression 表达式
     * @return true如果有效
     * @since 1.0.0
     */
    private boolean isValidExpression(@NotNull CompletionContext context, @NotNull String expression) {
        PsiMethod method = context.getMapperMethod();
        if (method == null) {
            return false;
        }

        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(expression);

        String rootParam = parseResult.getRootParam();
        if (StringUtil.isEmpty(rootParam)) {
            return false;
        }

        // 验证根参数是否存在
        for (PsiParameter param : method.getParameterList().getParameters()) {
            String paramName = param.getName();
            String annotationValue = MyBatisUtils.getParamAnnotationValue(param);
            if (StringUtil.isNotEmpty(annotationValue)) {
                paramName = annotationValue;
            }
            if (rootParam.equals(paramName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提取字段名(用于set标签)
     *
     * @param parts 表达式部分
     * @return 字段名
     * @since 1.0.0
     */
    private String extractFieldName(@NotNull String[] parts) {
        if (parts.length >= 2) {
            // 倒数第二个部分通常是字段名
            return PropertyNameConverter.toLowerUnderline(parts[parts.length - 2]);
        }
        return "column_name";
    }

    /**
     * 标签模板
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    @Data
    @Builder
    private static class TagTemplate {
        /**
         * 关键字
         *
         */
        private String keyword;

        /**
         * 描述
         *
         */
        private String description;

        /**
         * 模板内容
         *
         */
        private String template;
    }

    /**
     * 标签模板插入处理器
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    private static class TagTemplateInsertHandler implements InsertHandler<LookupElement> {

        /**
         * template
         *
         */
        private final TagTemplate template;
        /**
         * expression
         *
         */
        private final String expression;
        /**
         * field name
         *
         */
        private final String fieldName;

        /**
         * Tag Template Insert Handler
         *
         * @param template template
         * @param expression expression
         * @param fieldName field name
         * @since 1.0.0
         */
        public TagTemplateInsertHandler(@NotNull TagTemplate template,
                                        @NotNull String expression,
                                        @NotNull String fieldName) {
            this.template = template;
            this.expression = expression;
            this.fieldName = fieldName;
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

            // 删除已输入的内容(包括表达式和关键字)
            WriteCommandAction.runWriteCommandAction(context.getProject(), () -> {
                // 向前查找,删除整个表达式
                int deleteStart = this.findExpressionStart(document, startOffset);
                
                // 检查是否被#{}或${}包裹,如果是则一并删除
                int finalDeleteStart = deleteStart;
                int finalDeleteEnd = tailOffset;
                
                // 向前检查是否有#{或${
                if (deleteStart >= 2) {
                    String textBefore = document.getText().substring(deleteStart - 2, deleteStart);
                    if ("#{".equals(textBefore) || "${".equals(textBefore)) {
                        finalDeleteStart = deleteStart - 2;
                    }
                }
                
                // 向后检查是否有}
                if (tailOffset < document.getTextLength()) {
                    char charAfter = document.getCharsSequence().charAt(tailOffset);
                    if (charAfter == '}') {
                        finalDeleteEnd = tailOffset + 1;
                    }
                }
                
                document.deleteString(finalDeleteStart, finalDeleteEnd);

                // 生成模板内容
                String templateContent = this.generateTemplateContent();

                // 插入模板
                document.insertString(finalDeleteStart, templateContent);

                // 移动光标到{cursor}位置
                int cursorPos = templateContent.indexOf("{cursor}");
                if (cursorPos >= 0) {
                    int finalCursorPos = finalDeleteStart + cursorPos;
                    document.deleteString(finalCursorPos, finalCursorPos + "{cursor}".length());
                    editor.getCaretModel().moveToOffset(finalCursorPos);
                }
            });
        }

        /**
         * 查找表达式开始位置
         *
         * @param document    文档
         * @param startOffset 开始偏移量
         * @return 表达式开始位置
         * @since 1.0.0
         */
        private int findExpressionStart(@NotNull Document document, int startOffset) {
            int pos = startOffset - 1;
            while (pos > 0) {
                char c = document.getCharsSequence().charAt(pos);
                if (Character.isWhitespace(c) || c == '{' || c == '>') {
                    return pos + 1;
                }
                pos--;
            }
            return pos;
        }

        /**
         * 生成模板内容
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateTemplateContent() {
            return this.template.getTemplate()
                    .replace("{expression}", this.expression)
                    .replace("{field}", this.fieldName);
        }
    }
}
