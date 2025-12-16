package io.github.easy.tools.ui.mybatis;

import cn.hutool.core.util.StrUtil;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Xml Value Completion Contributor
 *
 * @author haijun
 * @version 1.1.0
 * @date 2025-12-17 10:30:00
 * @since 1.0.0
 */
public class XmlValueCompletionContributor extends CompletionContributor {
    /**
     * MyBatis SQL标签集合
     */
    private static final Set<String> SQL_TAGS = Set.of("select", "insert", "update", "delete");

    /**
     * Xml Value Completion Contributor
     *
     * @since 1.0.0
     */
    public XmlValueCompletionContributor() {
        // 匹配XML文本内容中的SQL语句（在SQL标签内部）
        this.extend(CompletionType.BASIC,
                this.inSqlTagText(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        // 总是提供补全建议，让IDE决定何时显示
                        XmlText xmlText = PsiTreeUtil.getParentOfType(parameters.getPosition(), XmlText.class);
                        if (xmlText == null) {
                            return;
                        }

                        // 修复：正确查找SQL标签，支持在where/if等嵌套标签中查找
                        XmlTag sqlTag = XmlValueCompletionContributor.this.findSqlTag(xmlText);
                        if (sqlTag == null || !SQL_TAGS.contains(sqlTag.getName())) {
                            return;
                        }

                        // 获取关联的Mapper方法
                        PsiMethod method = MyBatisUtils.findMethod(sqlTag);
                        if (method == null) {
                            return;
                        }

                        // 获取光标前的文本，用于判断是否是级联补全
                        String prefix = XmlValueCompletionContributor.this.getPrefix(parameters);

                        // 检查是否在 #{} 或 ${} 占位符内
                        if (XmlValueCompletionContributor.this.isInPlaceholder(prefix)) {
                            // 提取占位符内的内容作为前缀
                            prefix = XmlValueCompletionContributor.this.extractPlaceholderContent(prefix);
                        }

                        // 如果没有输入任何内容，显示所有参数
                        if (StrUtil.isBlank(prefix)) {
                            prefix = ""; // 显示所有参数
                        }

                        // 添加参数补全
                        XmlValueCompletionContributor.this.addParameterCompletions(result, method, prefix);
                    }
                });
    }

    /**
     * Before Completion
     *
     * @param context context
     * @since 1.0.0
     */
    @Override
    public void beforeCompletion(@NotNull CompletionInitializationContext context) {
        // 不需要特殊处理，使用默认行为
        super.beforeCompletion(context);
    }

    /**
     * 定义匹配规则：在SQL标签内的XML文本中
     *
     * @return PsiElementPattern
     * @since 1.0.0
     */
    private PsiElementPattern.Capture<PsiElement> inSqlTagText() {
        return PlatformPatterns.psiElement()
                .inside(XmlText.class)
                .inFile(PlatformPatterns.psiFile()
                        .withName(PlatformPatterns.string().endsWith(".xml")))
                .withParent(XmlText.class);
    }

    /**
     * 查找SQL标签（select/insert/update/delete），支持在嵌套标签中查找
     *
     * @param element XML元素
     * @return SQL标签
     * @since 1.0.0
     */
    private XmlTag findSqlTag(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlTag xmlTag) {
                if (SQL_TAGS.contains(xmlTag.getName())) {
                    return xmlTag;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 获取光标前的文本前缀
     *
     * @param parameters CompletionParameters
     * @return 前缀字符串
     * @since 1.0.0
     */
    private String getPrefix(CompletionParameters parameters) {
        Editor editor = parameters.getEditor();
        Document document = editor.getDocument();
        int offset = parameters.getOffset();

        // 获取当前行的起始偏移量
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);

        // 获取光标前的文本
        String lineText = document.getText().substring(lineStartOffset, offset);

        // 从光标位置向前查找，找到最近的空格或行首
        int lastSpaceIndex = lineText.lastIndexOf(' ');

        if (lastSpaceIndex >= 0) {
            // 返回空格后的所有内容
            String prefix = lineText.substring(lastSpaceIndex + 1);

            // 特殊处理：如果前缀包含未闭合的占位符，则提取占位符内的内容
            if (prefix.contains("#{") || prefix.contains("${")) {
                int hashIndex = prefix.lastIndexOf("#{");
                int dollarIndex = prefix.lastIndexOf("${");
                int startIndex = Math.max(hashIndex, dollarIndex);
                if (startIndex >= 0) {
                    return prefix.substring(startIndex + 2); // 跳过 "#{ 或 "${
                }
            }

            return prefix;
        }

        // 如果没有找到空格，返回整行文本
        // 特殊处理：如果文本包含未闭合的占位符，则提取占位符内的内容
        if (lineText.contains("#{") || lineText.contains("${")) {
            int hashIndex = lineText.lastIndexOf("#{");
            int dollarIndex = lineText.lastIndexOf("${");
            int startIndex = Math.max(hashIndex, dollarIndex);
            if (startIndex >= 0) {
                return lineText.substring(startIndex + 2); // 跳过 "#{ 或 "${
            }
        }

        return lineText;
    }

    /**
     * 添加参数补全项
     *
     * @param result   CompletionResultSet
     * @param method   PsiMethod
     * @param prefix   前缀
     * @since 1.0.0
     */
    private void addParameterCompletions(@NotNull CompletionResultSet result,
                                         @NotNull PsiMethod method,
                                         String prefix) {
        PsiParameter[] parameters = method.getParameterList().getParameters();

        // 判断是否是级联补全（如 query. 的情况）
        if (prefix.contains(".")) {
            String[] parts = prefix.split("\\.", 2);
            String paramName = parts[0];

            // 查找匹配的参数
            for (PsiParameter parameter : parameters) {
                String paramAnnotationValue = MyBatisUtils.getParamAnnotationValue(parameter);
                String parameterName = StrUtil.isNotBlank(paramAnnotationValue) ? paramAnnotationValue : parameter.getName();

                if (paramName.equals(parameterName)) {
                    // 获取参数类型
                    PsiClass paramClass = MyBatisUtils.resolveRootParamClass(parameter, paramName);
                    if (paramClass != null) {
                        // 添加该类的所有字段作为补全项
                        this.addFieldCompletions(result, paramClass, prefix);
                    }
                    return; // 找到匹配参数后直接返回，不再显示其他参数
                }
            }
        }

        // 添加所有参数作为补全项（只有在不是级联补全时才执行）
        for (PsiParameter parameter : parameters) {
            String paramAnnotationValue = MyBatisUtils.getParamAnnotationValue(parameter);
            String parameterName = StrUtil.isNotBlank(paramAnnotationValue) ? paramAnnotationValue : parameter.getName();

            // 如果有前缀过滤，则只显示匹配的参数
            if (StrUtil.isNotBlank(prefix) && !parameterName.startsWith(prefix)) {
                continue;
            }

            LookupElementBuilder builder = LookupElementBuilder.create(parameterName)
                    .withTypeText(parameter.getType().getPresentableText())
                    .withIcon(parameter.getIcon(0))
                    .withInsertHandler((context, item) -> {
                        // 参数插入后根据类型决定是否自动添加 #{} 包装
                        int startOffset = context.getStartOffset();
                        int tailOffset = context.getTailOffset();

                        // 检查是否已经在 #{} 中
                        CharSequence documentText = context.getDocument().getImmutableCharSequence();
                        boolean inPlaceholder = XmlValueCompletionContributor.this.isWithinPlaceholder(documentText, startOffset);

                        // 判断参数类型是否为基本数据类型
                        boolean isPrimitiveType = XmlValueCompletionContributor.this.isPrimitiveType(parameter.getType());

                        if (!inPlaceholder && isPrimitiveType) {
                            // 只有基本数据类型才自动添加 #{} 包装
                            context.getDocument().insertString(startOffset, "#{");
                            context.getDocument().insertString(tailOffset + 2, "}");
                            context.getEditor().getCaretModel().moveToOffset(tailOffset + 3);
                        } else if (!inPlaceholder) {
                            // 引用类型不自动添加 #{}，等待用户输入 . 后再处理
                            context.getEditor().getCaretModel().moveToOffset(tailOffset);
                        } else {
                            // 如果在占位符中，检查是否需要补全右括号
                            if (tailOffset < documentText.length() && documentText.charAt(tailOffset) != '}') {
                                context.getDocument().insertString(tailOffset, "}");
                                context.getEditor().getCaretModel().moveToOffset(tailOffset + 1);
                            } else {
                                context.getEditor().getCaretModel().moveToOffset(tailOffset);
                            }
                        }
                    });

            result.addElement(builder);
        }
    }

    /**
     * 添加类字段补全项
     *
     * @param result     CompletionResultSet
     * @param paramClass PsiClass
     * @param prefix     前缀
     * @since 1.0.0
     */
    private void addFieldCompletions(@NotNull CompletionResultSet result, @NotNull PsiClass paramClass, String prefix) {
        // 提取字段前缀（点号后面的部分）
        String fieldPrefix = "";
        if (prefix.contains(".")) {
            String[] parts = prefix.split("\\.", 2);
            if (parts.length > 1) {
                fieldPrefix = parts[1];
            }
        }

        // 获取实际的类定义（处理泛型类型参数）
        PsiClass actualClass = MyBatisUtils.resolveActualClassFromType(paramClass);
        if (actualClass == null) {
            actualClass = paramClass;
        }

        PsiField[] fields = actualClass.getAllFields();
        for (PsiField field : fields) {
            // 排除 serialVersionUID 字段
            if ("serialVersionUID".equals(field.getName())) {
                continue;
            }

            // 排除常量字段（public static final）
            if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
                continue;
            }

            // 排除静态字段
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            // 如果有字段前缀过滤，则只显示匹配的字段
            // 但如果字段前缀为空（如 query. 的情况），则显示所有字段
            if (!field.getName().startsWith(fieldPrefix)) {
                continue;
            }

            LookupElementBuilder builder = LookupElementBuilder.create(field.getName())
                    .withTypeText(field.getType().getPresentableText())
                    .withIcon(field.getIcon(0))
                    .withInsertHandler((context, item) -> {
                        // 字段插入后根据类型决定是否自动添加 #{} 包装
                        int startOffset = context.getStartOffset();
                        int tailOffset = context.getTailOffset();

                        // 检查是否已经在 #{} 中
                        CharSequence documentText = context.getDocument().getImmutableCharSequence();
                        boolean inPlaceholder = this.isWithinPlaceholder(documentText, startOffset);

                        // 判断字段类型是否为基本数据类型
                        boolean isPrimitiveType = this.isPrimitiveType(field.getType());

                        if (!inPlaceholder && isPrimitiveType) {
                            // 只有基本数据类型才自动添加 #{} 包装
                            context.getDocument().insertString(startOffset, "#{");
                            context.getDocument().insertString(tailOffset + 2, "}");
                            context.getEditor().getCaretModel().moveToOffset(tailOffset + 3);
                        } else if (!inPlaceholder) {
                            // 引用类型不自动添加 #{}，等待用户输入 . 后再处理
                            context.getEditor().getCaretModel().moveToOffset(tailOffset);
                        } else {
                            // 如果在占位符中，检查是否需要补全右括号
                            if (tailOffset < documentText.length() && documentText.charAt(tailOffset - 1) != '}') {
                                context.getDocument().insertString(tailOffset, "}");
                                context.getEditor().getCaretModel().moveToOffset(tailOffset + 1);
                            } else {
                                context.getEditor().getCaretModel().moveToOffset(tailOffset);
                            }
                        }
                    });

            result.addElement(builder);
        }
    }

    /**
     * 检查是否在MyBatis占位符内 (#{} 或 ${})
     *
     * @param prefix 前缀字符串
     * @return 是否在占位符内
     * @since 1.0.0
     */
    private boolean isInPlaceholder(String prefix) {
        // 检查是否包含 #{ 或 ${
        int hashIndex = prefix.lastIndexOf("#{");
        int dollarIndex = prefix.lastIndexOf("${");

        // 如果找到了开启符号
        if (hashIndex >= 0 || dollarIndex >= 0) {
            // 检查是否有闭合符号 }
            int openIndex = Math.max(hashIndex, dollarIndex);
            return prefix.indexOf('}', openIndex) < 0; // 如果没有找到闭合符号，则认为在占位符内
        }

        return false;
    }

    /**
     * 提取占位符内容
     *
     * @param prefix 前缀字符串
     * @return 占位符内容
     * @since 1.0.0
     */
    private String extractPlaceholderContent(String prefix) {
        int hashIndex = prefix.lastIndexOf("#{");
        int dollarIndex = prefix.lastIndexOf("${");

        // 找到最后一个占位符的开始位置
        int startIndex = Math.max(hashIndex, dollarIndex);
        if (startIndex >= 0) {
            startIndex += 2; // 跳过 "#{ 或 "${
            int endIndex = prefix.indexOf('}', startIndex);
            if (endIndex > startIndex) {
                return prefix.substring(startIndex, endIndex);
            }
            // 如果没有找到结束符号，返回从开始符号之后的所有内容
            return prefix.substring(startIndex);
        }
        return prefix;
    }

    /**
     * 检查光标位置是否已在占位符内
     *
     * @param documentText 文档文本
     * @param offset       光标位置
     * @return 是否在占位符内
     * @since 1.0.0
     */
    private boolean isWithinPlaceholder(CharSequence documentText, int offset) {
        // 向前查找最近的 #{ 或 ${
        int hashBraceIndex = -1;
        int dollarBraceIndex = -1;

        // 确保不会越界
        int searchEnd = Math.min(offset, documentText.length());

        // 从光标位置向前搜索
        for (int i = searchEnd - 1; i >= 0; i--) {
            // 确保不会越界
            if (i < documentText.length() - 1) {
                if (documentText.charAt(i) == '#' && documentText.charAt(i + 1) == '{') {
                    hashBraceIndex = i;
                    break;
                }
                if (documentText.charAt(i) == '$' && documentText.charAt(i + 1) == '{') {
                    dollarBraceIndex = i;
                    break;
                }
            }
        }

        // 如果找到了开启符号，向后查找是否有闭合符号
        int openIndex = Math.max(hashBraceIndex, dollarBraceIndex);
        if (openIndex >= 0) {
            // 从开启符号之后开始搜索闭合符号
            for (int i = openIndex + 2; i < documentText.length(); i++) {
                if (documentText.charAt(i) == '}') {
                    // 检查光标位置是否在开启符号和闭合符号之间
                    return offset >= openIndex && offset <= i + 1;
                }
            }
            // 如果没有找到闭合符号，但光标在开启符号之后，也算在占位符内
            return offset > openIndex;
        }

        return false;
    }

    /**
     * 检查类型是否为基本数据类型
     *
     * @param type 类型
     * @return 是否为基本数据类型
     * @since 1.0.0
     */
    private boolean isPrimitiveType(PsiType type) {
        return type instanceof PsiPrimitiveType;
    }
}
