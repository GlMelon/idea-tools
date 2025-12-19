package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategyManager;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis XML代码补全贡献者(重构版)
 * <p>
 * 使用策略模式重构,提供以下功能:
 * <ul>
 * <li>参数补全: 提示方法的所有参数</li>
 * <li>字段补全: 支持链式访问(如query.user.name)</li>
 * <li>标签模板: 支持快捷生成标签(如query.name.if)</li>
 * <li>表达式解析: 使用专业的表达式解析器</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 2.0.0
 * @date 2025-12-18
 * @since 1.0.0
 */
public class MyBatisXmlCompletionContributor extends CompletionContributor {

    /**
     * LOG
     *
     */
    private static final Logger LOG = Logger.getInstance(MyBatisXmlCompletionContributor.class);

    /**
     * MyBatis SQL标签集合
     *
     */
    private static final Set<String> SQL_TAGS = new HashSet<>(Arrays.asList(
            "select", "insert", "update", "delete", "sql"
    ));

    /**
     * 构造函数,注册代码补全提供器
     * 统一注册一个补全提供器,在内部区分是属性还是文本内容
     *
     * @since 1.0.0
     */
    public MyBatisXmlCompletionContributor() {
        // 创建统一的补全提供器实例
        MyBatisParamCompletionProvider provider = new MyBatisParamCompletionProvider();

        // 注册统一的 BASIC 类型补全,匹配所有 XML 文件中的 PsiElement
        // 在 MyBatisParamCompletionProvider 内部再判断是属性还是文本内容
        this.extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .inFile(PlatformPatterns.instanceOf(XmlFile.class)),
                provider);

        // 注册SMART类型补全,提高优先级
        this.extend(CompletionType.SMART,
                PlatformPatterns.psiElement()
                        .inFile(PlatformPatterns.instanceOf(XmlFile.class)),
                provider);
    }

    /**
     * 重写fillCompletionVariants,提供更强的控制权
     * 在Mapper XML文件中,当我们提供了补全项时,阻止其他插件的补全
     *
     * 修复: 确保在有DOCTYPE声明的XML文件中也能正常工作
     *
     * @param parameters 补全参数
     * @param result     补全结果集
     * @since 1.0.0
     */
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        // 只在Mapper XML文件中处理
        boolean isInMapperFile = MyBatisUtils.isInMapperFile(position);

        if (!isInMapperFile) {
            super.fillCompletionVariants(parameters, result);
            return;
        }

        // 查找对应的Mapper方法
        PsiMethod method = MyBatisUtils.findMethod(position);

        // 检查是否在SQL相关的位置(文本内容或属性值)
        boolean isInRelevantContext = this.isInRelevantContext(position);

        boolean shouldProvideCompletion = method != null && isInRelevantContext;

        if (shouldProvideCompletion) {
            // 调用我们自己的补全逻辑
            super.fillCompletionVariants(parameters, result);

            result.stopHere();
        } else {
            // 不在相关上下文中,使用默认行为
            super.fillCompletionVariants(parameters, result);
        }
    }

    /**
     * 检查是否在相关的上下文中(需要提供补全的位置)
     *
     * @param position 当前位置
     * @return true如果在相关上下文中
     * @since 1.0.0
     */
    private boolean isInRelevantContext(@NotNull PsiElement position) {
        PsiElement parent = position.getParent();

        // 检查是否在 XML 属性值中
        if (this.isInXmlAttributeValue(parent)) {
            return this.isInSqlRelatedAttribute(parent);
        }

        // 检查是否在 XML 文本内容中
        if (this.isInXmlTextContent(parent)) {
            return this.isInSqlRelatedTag(parent);
        }

        return false;
    }

    /**
     * 检查是否在 XML 属性值中
     *
     * @param element PSI元素
     * @return true如果在属性值中
     * @since 1.0.0
     */
    private boolean isInXmlAttributeValue(@Nullable PsiElement element) {
        while (element != null) {
            if (element instanceof XmlAttributeValue) {
                return true;
            }
            if (element instanceof XmlTag) {
                return false;
            }
            element = element.getParent();
        }
        return false;
    }

    /**
     * 检查是否在 XML 文本内容中
     *
     * @param element PSI元素
     * @return true如果在文本内容中
     * @since 1.0.0
     */
    private boolean isInXmlTextContent(@Nullable PsiElement element) {
        while (element != null) {
            if (element instanceof XmlText) {
                return true;
            }
            if (element instanceof XmlAttributeValue) {
                return false;
            }
            element = element.getParent();
        }
        return false;
    }

    /**
     * 检查属性是否与SQL相关
     * 只要属性在SQL相关标签内(select/insert/update/delete等),就返回true
     *
     * @param element PSI元素
     * @return true如果是SQL相关属性
     * @since 1.0.0
     */
    private boolean isInSqlRelatedAttribute(@NotNull PsiElement element) {
        PsiElement current = element;
        XmlAttribute attribute = null;

        // 先向上查找到属性节点
        while (current != null) {
            if (current instanceof XmlAttribute) {
                attribute = (XmlAttribute) current;
                break;
            }
            if (current instanceof XmlTag) {
                return false;
            }
            current = current.getParent();
        }

        if (attribute == null) {
            return false;
        }

        // 从属性的父标签开始向上查找SQL相关标签
        XmlTag tag = attribute.getParent();
        while (tag != null) {
            String tagName = tag.getName();

            // 检查是否是SQL相关标签
            if (SQL_TAGS.contains(tagName)) {
                return true;
            }

            // 检查是否是其他MyBatis动态SQL标签
            if ("where".equals(tagName) || "set".equals(tagName) ||
                "foreach".equals(tagName) || "trim".equals(tagName) ||
                "if".equals(tagName) || "when".equals(tagName) ||
                "otherwise".equals(tagName) || "choose".equals(tagName) ||
                "bind".equals(tagName)) {
                return true;
            }

            // 继续向上查找
            PsiElement parent = tag.getParent();
            if (parent instanceof XmlTag) {
                tag = (XmlTag) parent;
            } else {
                break;
            }
        }

        return false;
    }

    /**
     * 检查是否在SQL相关的标签中
     *
     * @param element PSI元素
     * @return true如果在SQL标签中
     * @since 1.0.0
     */
    private boolean isInSqlRelatedTag(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlTag tag) {
                // 检查是否是SQL相关标签
                if (SQL_TAGS.contains(tag.getName())) {
                    return true;
                }
                // 检查是否是带有test属性的标签(如if, when等)
                if (tag.getAttribute("test") != null) {
                    return true;
                }
                // 检查是否是其他SQL标签(where, set, foreach等)
                String tagName = tag.getName();
                if ("where".equals(tagName) || "set".equals(tagName) ||
                    "foreach".equals(tagName) || "trim".equals(tagName) ||
                    "if".equals(tagName) || "when".equals(tagName) ||
                    "otherwise".equals(tagName) || "choose".equals(tagName)) {
                    return true;
                }
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * MyBatis参数补全提供器(重构版)
     * 统一处理属性值和文本内容的补全,在内部区分类型
     *
     * @author haijun
     * @version 2.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    private static class MyBatisParamCompletionProvider extends CompletionProvider<CompletionParameters> {

        /**
         * 添加补全项
         *
         * @param parameters 补全参数
         * @param context    处理上下文
         * @param result     补全结果集
         * @since 1.0.0
         */
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();

            // 检查是否在Mapper XML文件中
            if (!MyBatisUtils.isInMapperFile(position)) {
                return;
            }

            // 判断是否在相关上下文中
            PsiElement parent = position.getParent();
            boolean isInAttribute = this.isInXmlAttributeValue(parent);
            boolean isInTextContent = this.isInXmlTextContent(parent);

            // 必须在属性值或文本内容中
            if (!isInAttribute && !isInTextContent) {
                return;
            }

            // 如果在属性中,检查是否在SQL相关标签内的属性
            if (isInAttribute && !this.isInSqlRelatedAttribute(parent)) {
                return;
            }

            // 如果在文本内容中,检查是否在SQL相关标签中
            if (isInTextContent && !this.isInSqlRelatedTag(parent)) {
                return;
            }

            // 查找对应的Mapper方法
            PsiMethod method = MyBatisUtils.findMethod(position);
            if (method == null) {
                return;
            }

            // 解析当前输入的内容
            String currentText = this.getCurrentText(position);

            // 构建补全上下文
            CompletionContext completionContext = this.buildCompletionContext(
                    position, method, currentText, isInAttribute);

            // 使用策略管理器提供补全
            CompletionStrategyManager.getInstance().provideCompletions(completionContext, result);
        }

        /**
         * 检查是否在 XML 属性值中
         *
         * @param element PSI元素
         * @return true如果在属性值中
         * @since 1.0.0
         */
        private boolean isInXmlAttributeValue(@Nullable PsiElement element) {
            while (element != null) {
                if (element instanceof XmlAttributeValue) {
                    return true;
                }
                if (element instanceof XmlTag) {
                    return false;
                }
                element = element.getParent();
            }
            return false;
        }

        /**
         * 检查是否在 XML 文本内容中
         *
         * @param element PSI元素
         * @return true如果在文本内容中
         * @since 1.0.0
         */
        private boolean isInXmlTextContent(@Nullable PsiElement element) {
            while (element != null) {
                if (element instanceof XmlText) {
                    return true;
                }
                if (element instanceof XmlAttributeValue) {
                    return false;
                }
                element = element.getParent();
            }
            return false;
        }

        /**
         * 检查属性是否与SQL相关
         * 只要属性在SQL相关标签内(select/insert/update/delete等),就返回true
         *
         * @param element PSI元素
         * @return true如果是SQL相关属性
         * @since 1.0.0
         */
        private boolean isInSqlRelatedAttribute(@NotNull PsiElement element) {
            PsiElement current = element;
            XmlAttribute attribute = null;

            // 先向上查找到属性节点
            while (current != null) {
                if (current instanceof XmlAttribute) {
                    attribute = (XmlAttribute) current;
                    break;
                }
                if (current instanceof XmlTag) {
                    return false;
                }
                current = current.getParent();
            }

            if (attribute == null) {
                return false;
            }

            // 从属性的父标签开始向上查找SQL相关标签
            XmlTag tag = attribute.getParent();
            while (tag != null) {
                String tagName = tag.getName();

                // 检查是否是SQL相关标签
                if (SQL_TAGS.contains(tagName)) {
                    return true;
                }

                // 检查是否是其他MyBatis动态SQL标签
                if ("where".equals(tagName) || "set".equals(tagName) ||
                    "foreach".equals(tagName) || "trim".equals(tagName) ||
                    "if".equals(tagName) || "when".equals(tagName) ||
                    "otherwise".equals(tagName) || "choose".equals(tagName) ||
                    "bind".equals(tagName)) {
                    return true;
                }

                // 继续向上查找
                PsiElement parent = tag.getParent();
                if (parent instanceof XmlTag) {
                    tag = (XmlTag) parent;
                } else {
                    break;
                }
            }

            return false;
        }

        /**
         * 检查是否在SQL相关的标签中
         *
         * @param element PSI元素
         * @return true如果在SQL标签中
         * @since 1.0.0
         */
        private boolean isInSqlRelatedTag(@NotNull PsiElement element) {
            PsiElement current = element;
            while (current != null) {
                if (current instanceof XmlTag tag) {
                    String tagName = tag.getName();
                    // 检查是否是SQL相关标签
                    if (SQL_TAGS.contains(tagName)) {
                        return true;
                    }
                    // 检查是否是其他SQL标签(where, set, foreach, if等)
                    if ("where".equals(tagName) || "set".equals(tagName) ||
                        "foreach".equals(tagName) || "trim".equals(tagName) ||
                        "if".equals(tagName) || "when".equals(tagName) ||
                        "otherwise".equals(tagName) || "choose".equals(tagName)) {
                        return true;
                    }
                }
                current = current.getParent();
            }
            return false;
        }

        /**
         * 获取当前输入的文本
         *
         * @param position 当前位置
         * @return 当前文本
         * @since 1.0.0
         */
        @NotNull
        private String getCurrentText(@NotNull PsiElement position) {
            Project project = position.getProject();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                return "";
            }

            Document document = editor.getDocument();
            int offset = editor.getCaretModel().getOffset();

            // 检查是否在XML属性值中
            PsiElement parent = position.getParent();
            if (this.isInXmlAttributeValue(parent)) {
                return this.getCurrentTextInXmlAttribute(document, offset);
            } else {
                // 在XML文本内容中
                return this.getCurrentTextInXmlContent(document, offset);
            }
        }

        /**
         * 获取XML属性中的当前文本
         *
         * @param document 文档
         * @param offset   光标位置
         * @return 当前文本
         * @since 1.0.0
         */
        @NotNull
        private String getCurrentTextInXmlAttribute(@NotNull Document document, int offset) {
            // 查找属性值的开始位置(引号后的位置)
            int attrStart = offset;
            while (attrStart > 0) {
                char c = document.getCharsSequence().charAt(attrStart - 1);
                if (c == '"' || c == '\'') {
                    break;
                }
                attrStart--;
            }

            // 提取从属性开始到光标位置的文本
            String textBeforeCursor = document.getText().substring(attrStart, offset);

            // 查找最后一个空格,只返回光标前的最后一个单词
            int lastSpace = textBeforeCursor.lastIndexOf(' ');
            if (lastSpace >= 0) {
                return textBeforeCursor.substring(lastSpace + 1);
            }

            return textBeforeCursor;
        }

        /**
         * 获取XML文本内容中的当前文本
         *
         * @param document 文档
         * @param offset   光标位置
         * @return 当前文本
         * @since 1.0.0
         */
        @NotNull
        private String getCurrentTextInXmlContent(@NotNull Document document, int offset) {
            // 从光标位置向前查找,直到遇到空白字符或开始位置
            int startPos = offset;
            while (startPos > 0) {
                char c = document.getCharsSequence().charAt(startPos - 1);
                // 遇到空白字符、<、>、{、}等分隔符就停止
                if (Character.isWhitespace(c) || c == '<' || c == '>' || c == '{' || c == '}') {
                    break;
                }
                startPos--;
            }

            // 只提取从 startPos 到 offset 之间的文本(不包括光标后的内容)
            return document.getText().substring(startPos, offset);
        }

        /**
         * 构建补全上下文
         *
         * @param position         当前位置
         * @param method           Mapper方法
         * @param currentText      当前文本
         * @param isInXmlAttribute 是否在XML属性中
         * @return 补全上下文
         * @since 1.0.0
         */
        @NotNull
        private CompletionContext buildCompletionContext(@NotNull PsiElement position,
                                                         @NotNull PsiMethod method,
                                                         @NotNull String currentText,
                                                         boolean isInXmlAttribute) {
            Project project = position.getProject();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

            // 解析表达式
            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(currentText);

            // 决定补全类型
            CompletionContext.CompletionType completionType = this.determineCompletionType(
                    currentText, parseResult, isInXmlAttribute);

            // 提取标签信息
            String tagName = null;
            String attributeName = null;
            if (isInXmlAttribute) {
                PsiElement parent = position.getParent();
                while (parent != null) {
                    if (parent instanceof XmlAttribute attr) {
                        attributeName = attr.getName();
                        XmlTag tag = attr.getParent();
                        if (tag != null) {
                            tagName = tag.getName();
                        }
                        break;
                    }
                    parent = parent.getParent();
                }
            }

            return CompletionContext.builder()
                    .project(project)
                    .editor(editor)
                    .position(position)
                    .mapperMethod(method)
                    .currentText(currentText)
                    .inXmlAttribute(isInXmlAttribute)
                    .tagName(tagName)
                    .attributeName(attributeName)
                    .completionType(completionType)
                    .build();
        }

        /**
         * 决定补全类型
         *
         * @param currentText      当前文本
         * @param parseResult      解析结果
         * @param isInXmlAttribute 是否在XML属性中
         * @return 补全类型
         * @since 1.0.0
         */
        @NotNull
        private CompletionContext.CompletionType determineCompletionType(
                @NotNull String currentText,
                @NotNull MyBatisExpressionParser.ExpressionParseResult parseResult,
                boolean isInXmlAttribute) {

            // 优先判断标签模板(最高优先级)
            // 如果在XML属性中,不支持标签模板
            if (!isInXmlAttribute && parseResult.getParts().length >= 2) {
                // 检查最后一部分是否是标签关键字
                String lastPart = parseResult.getParts()[parseResult.getParts().length - 1].toLowerCase();
                if (this.isTagKeyword(lastPart)) {
                    return CompletionContext.CompletionType.TAG_TEMPLATE;
                }
            }

            // 如果包含点号且不是标签关键字,是字段补全
            if (currentText.contains(".")) {
                return CompletionContext.CompletionType.FIELD;
            }

            // 默认是参数补全
            return CompletionContext.CompletionType.PARAMETER;
        }

        /**
         * 判断是否为标签关键字
         *
         * @param keyword 关键字
         * @return true如果是标签关键字
         * @since 1.0.0
         */
        private boolean isTagKeyword(@NotNull String keyword) {
            return keyword.equals("if") || keyword.equals("ifnull") ||
                   keyword.equals("for") || keyword.equals("foreach") ||
                   keyword.equals("where") || keyword.equals("set") ||
                   keyword.equals("choose");
        }
    }
}
