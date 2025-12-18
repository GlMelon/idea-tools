package io.github.easy.tools.service.mybatis;

import cn.hutool.core.util.StrUtil;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MyBatis XML代码补全贡献者 <p> 在MyBatis XML文件中提供智能代码补全功能: 1. 输入参数名时提示方法的所有参数 2. 输入参数.属性时提示参数对象的所有字段 3. 支持链式访问(如query.user.name) 4. 自动添加#{}包裹 </p>
 *
 * @author haijun
 * @version 1.0.0
 * @date 2025-12-17 11:00:00
 * @since 1.0.0
 */
public class MyBatisXmlCompletionContributor extends CompletionContributor {

    /**
     * MyBatis SQL标签集合
     */
    private static final Set<String> SQL_TAGS = new HashSet<>(Arrays.asList(
            "select", "insert", "update", "delete"
    ));

    /**
     * 需要排除的字段名称
     */
    private static final Set<String> EXCLUDED_FIELDS = new HashSet<>(Arrays.asList(
            "serialVersionUID", "class"
    ));

    /**
     * 基本数据类型集合
     */
    private static final Set<String> PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
            "int", "Integer",
            "long", "Long",
            "short", "Short",
            "byte", "Byte",
            "float", "Float",
            "double", "Double",
            "boolean", "Boolean",
            "char", "Character",
            "String",
            "BigDecimal",
            "BigInteger",
            "Date",
            "LocalDate",
            "LocalDateTime",
            "LocalTime"
    ));

    /**
     * 构造函数,注册代码补全提供器
     *
     * @since 1.0.0
     */
    public MyBatisXmlCompletionContributor() {
        // 在XML文本内容中提供补全，只匹配select、update、insert、delete标签
        this.extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .inFile(PlatformPatterns.instanceOf(XmlFile.class)),
                new MyBatisParamCompletionProvider());

        // 在XML属性中提供补全，如<if test="..."></if>中的test属性
        this.extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .inFile(PlatformPatterns.instanceOf(XmlFile.class))
                        .withParent(XmlAttributeValue.class),
                new MyBatisParamCompletionProvider());
    }

    /**
     * MyBatis参数补全提供器
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-17 11:00:00
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

            // 检查是否在XML属性中（如test属性）
            boolean isInXmlAttribute = position.getParent() instanceof XmlAttributeValue;

            // 如果不在XML属性中，检查是否在SQL标签内
            XmlTag sqlTag = null;
            if (!isInXmlAttribute) {
                sqlTag = this.findSqlTag(position);
                if (sqlTag == null) {
                    return;
                }
            }

            // 查找对应的Mapper方法
            PsiMethod method = MyBatisUtils.findMethod(position);
            if (method == null) {
                return;
            }

            // 解析当前输入的内容
            String currentText = this.getCurrentText(position);
            if (currentText == null) {
                return;
            }

            // 根据当前输入内容提供补全
            this.provideCompletions(method, currentText, result, isInXmlAttribute);
        }


        /**
         * 查找SQL标签
         *
         * @param element 元素
         * @return SQL标签
         * @since 1.0.0
         */
        @Nullable
        private XmlTag findSqlTag(@NotNull PsiElement element) {
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
         * 获取当前输入的文本
         *
         * @param position 当前位置
         * @return 当前文本
         * @since 1.0.0
         */
        @Nullable
        private String getCurrentText(@NotNull PsiElement position) {
            Project project = position.getProject();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                return "";
            }

            Document document = editor.getDocument();
            int offset = editor.getCaretModel().getOffset();

            // 检查是否在XML属性值中
            if (position.getParent() instanceof XmlAttributeValue) {
                // 在XML属性中，需要提取属性值中光标前的部分
                return this.getCurrentTextInXmlAttribute(document, offset);
            } else {
                // 在XML文本内容中，使用原有的逻辑
                // 从光标位置向前查找，直到遇到空白字符或开始位置
                int startPos = offset;
                while (startPos > 0 && !Character.isWhitespace(document.getCharsSequence().charAt(startPos - 1))) {
                    startPos--;
                }

                // 从光标位置向后查找，直到遇到空白字符或结束位置
                int endPos = offset;
                while (endPos < document.getTextLength() && !Character.isWhitespace(document.getCharsSequence().charAt(endPos))) {
                    endPos++;
                }

                // 这里如果截取的字符串如果是属性值，后面会截取到 >符号，所以这里需要替换掉
                return document.getText().substring(startPos, endPos);
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
            // 查找属性值的开始位置（引号后的位置）
            int attrStart = offset;
            while (attrStart > 0) {
                char c = document.getCharsSequence().charAt(attrStart - 1);
                // 查找引号或属性开始的位置
                if (c == '"' || c == '\'') {
                    attrStart++;
                    break;
                }
                attrStart--;
            }

            // 提取从属性开始到光标位置的文本
            String textBeforeCursor = document.getText().substring(attrStart, offset);

            // 查找最后一个空格，只返回光标前的最后一个单词
            int lastSpace = textBeforeCursor.lastIndexOf(' ');
            if (lastSpace >= 0) {
                return textBeforeCursor.substring(lastSpace + 1);
            }

            return textBeforeCursor;
        }

        /**
         * 提供补全建议
         *
         * @param method           Mapper方法
         * @param currentText      当前输入文本
         * @param result           结果集
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        private void provideCompletions(@NotNull PsiMethod method,
                                        @NotNull String currentText,
                                        @NotNull CompletionResultSet result,
                                        boolean isInXmlAttribute) {
            // Bug修复: 如果当前文本为空,直接提供参数补全
            if (StrUtil.isBlank(currentText)) {
                this.provideParameterCompletions(method, "", result, isInXmlAttribute);
                return;
            }
            String cleanText = currentText.replace("#{", " ")
                    .replace("}", " ")
                    .trim();
            // 检查是否有点分隔符
            if (cleanText.contains(".")) {
                // 处理属性链式访问
                this.handlePropertyChain(method, cleanText, result, isInXmlAttribute);
            } else {
                // 提供参数名补全
                this.provideParameterCompletions(method, cleanText, result, isInXmlAttribute);
            }
        }

        /**
         * 判断是否为基本数据类型
         *
         * @param typeName 类型名称
         * @return true如果是基本类型
         * @since 1.0.0
         */
        private boolean isPrimitiveType(@NotNull String typeName) {
            // 移除泛型部分
            String simpleType = typeName.replaceAll("<.*>", "").trim();
            // 获取简单类名
            int lastDot = simpleType.lastIndexOf('.');
            if (lastDot >= 0) {
                simpleType = simpleType.substring(lastDot + 1);
            }
            return PRIMITIVE_TYPES.contains(simpleType);
        }

        /**
         * 提供参数名补全
         *
         * @param method           方法
         * @param currentText      当前文本
         * @param result           结果集
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        private void provideParameterCompletions(@NotNull PsiMethod method,
                                                 @NotNull String currentText,
                                                 @NotNull CompletionResultSet result,
                                                 boolean isInXmlAttribute) {
            PsiParameter[] parameters = method.getParameterList().getParameters();

            for (PsiParameter parameter : parameters) {
                String paramName = parameter.getName();

                // 获取@Param注解的值
                String annotationValue = MyBatisUtils.getParamAnnotationValue(parameter);
                if (StrUtil.isNotBlank(annotationValue)) {
                    paramName = annotationValue;
                }

                // 如果是空字符串或者参数名匹配当前输入,添加补全项
                if (StrUtil.isBlank(currentText) || paramName.startsWith(currentText)) {
                    String typeName = parameter.getType().getPresentableText();
                    boolean isPrimitive = this.isPrimitiveType(typeName);

                    LookupElementBuilder builder = LookupElementBuilder
                            .create(paramName)
                            .withIcon(AllIcons.Nodes.Parameter)
                            .withTypeText(typeName)
                            .withTailText(" " + typeName, true)
                            .withInsertHandler(new ParameterInsertHandler(isPrimitive, isInXmlAttribute));

                    result.addElement(builder);
                }
            }
        }

        /**
         * 处理属性链式访问
         *
         * @param method           方法
         * @param currentText      当前文本(如query.name)
         * @param result           结果集
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        private void handlePropertyChain(@NotNull PsiMethod method,
                                         @NotNull String currentText,
                                         @NotNull CompletionResultSet result,
                                         boolean isInXmlAttribute) {
            // Bug修复: 使用-1参数确保末尾的空字符串也被包含
            String[] parts = currentText.split("\\.", -1);
            if (parts.length < 1) {
                return;
            }

            // 第一部分是参数名
            String rootParamName = parts[0];

            // 查找对应的参数
            PsiParameter rootParam = null;
            for (PsiParameter param : method.getParameterList().getParameters()) {
                String paramName = param.getName();
                String annotationValue = MyBatisUtils.getParamAnnotationValue(param);
                if (StrUtil.isNotBlank(annotationValue)) {
                    paramName = annotationValue;
                }
                if (rootParamName.equals(paramName)) {
                    rootParam = param;
                    break;
                }
            }

            // Bug修复: 如果没有找到对应的参数,直接返回,不提供任何补全
            if (rootParam == null) {
                return;
            }

            PsiClass currentClass = MyBatisUtils.resolveRootParamClass(rootParam, rootParamName);

            // 兼容泛型类型的获取，当子类指定父类的泛型时，需要解析实际类型
            currentClass = MyBatisUtils.resolveActualClassFromType(currentClass);

            if (currentClass == null) {
                return;
            }

            // Bug修复: 如果只有一个部分且以.结尾(如"query."),直接提供字段补全
            // 当currentText为"query."时,parts为["query", ""]
            if (parts.length == 2 && StrUtil.isBlank(parts[1])) {
                this.provideFieldCompletions(currentClass, "", rootParamName, result, isInXmlAttribute);
                return;
            }

            // 如果只有一个部分(没有点号),不应该进入这个方法
            if (parts.length == 1) {
                return;
            }

            // 构建完整的前缀路径
            StringBuilder prefixBuilder = new StringBuilder(rootParamName);

            // 遍历中间的属性链
            for (int i = 1; i < parts.length - 1; i++) {
                String fieldName = parts[i];
                PsiElement field = MyBatisUtils.findFieldOrPropertyInClass(currentClass, fieldName);

                if (field == null) {
                    return;
                }

                // 添加到前缀路径
                prefixBuilder.append(".").append(fieldName);

                currentClass = MyBatisUtils.getTypeOfResolvedElement(field);
                if (currentClass == null) {
                    return;
                }

                // 解析泛型
                currentClass = MyBatisUtils.resolveActualClassFromType(currentClass);
            }

            // 最后一部分是当前正在输入的字段名
            String currentFieldName = parts[parts.length - 1];

            // 提供当前类的所有字段补全，传入完整的前缀路径
            this.provideFieldCompletions(currentClass, currentFieldName, prefixBuilder.toString(), result, isInXmlAttribute);
        }

        /**
         * 提供字段补全
         *
         * @param psiClass         类
         * @param currentFieldName 当前字段名
         * @param prefixPath       前缀路径
         * @param result           结果集
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        private void provideFieldCompletions(@NotNull PsiClass psiClass,
                                             @NotNull String currentFieldName,
                                             @NotNull String prefixPath,
                                             @NotNull CompletionResultSet result,
                                             boolean isInXmlAttribute) {
            // 获取当前类的字段(不包括父类)
            List<PsiField> ownFields = new ArrayList<>(Arrays.asList(psiClass.getFields()));
            
            // 获取所有字段(包括父类)
            List<PsiField> allFields = new ArrayList<>(Arrays.asList(psiClass.getAllFields()));
            
            // 从所有字段中移除当前类的字段，得到父类字段
            List<PsiField> parentFields = new ArrayList<>(allFields);
            parentFields.removeAll(ownFields);
            
            // 先处理当前类的字段(优先级高)
            this.processFields(ownFields, currentFieldName, prefixPath, result, isInXmlAttribute);
            
            // 再处理父类的字段(优先级低)
            this.processFields(parentFields, currentFieldName, prefixPath, result, isInXmlAttribute);
        }
        
        /**
         * 处理字段列表
         *
         * @param fields           字段列表
         * @param currentFieldName 当前字段名
         * @param prefixPath       前缀路径
         * @param result           结果集
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        private void processFields(@NotNull List<PsiField> fields,
                                   @NotNull String currentFieldName,
                                   @NotNull String prefixPath,
                                   @NotNull CompletionResultSet result,
                                   boolean isInXmlAttribute) {
            for (PsiField field : fields) {
                String fieldName = field.getName();

                // 排除静态字段和特殊字段
                if (field.hasModifierProperty("static") || EXCLUDED_FIELDS.contains(fieldName)) {
                    continue;
                }

                // 模糊匹配
                if (StrUtil.isBlank(currentFieldName) || fieldName.startsWith(currentFieldName)) {
                    // 构建完整的路径
                    String fullPath = StrUtil.isBlank(prefixPath) ? fieldName : prefixPath + "." + fieldName;

                    // 提取字段描述
                    String fieldDescription = this.extractFieldDescription(field);

                    // 构建LookupElement
                    LookupElementBuilder builder = LookupElementBuilder
                            .create(fullPath)
                            .withIcon(AllIcons.Nodes.Field)
                            .withTypeText(field.getType().getPresentableText())
                            .withInsertHandler(new FieldInsertHandler(isInXmlAttribute));

                    // 如果有描述信息,添加到tailText中
                    if (StrUtil.isNotBlank(fieldDescription)) {
                        builder = builder.withTailText(" " + field.getType().getPresentableText() + " - " + fieldDescription, true);
                    } else {
                        builder = builder.withTailText(" " + field.getType().getPresentableText(), true);
                    }

                    result.addElement(builder);
                }
            }
        }

        /**
         * 提取字段描述信息 <p> 优先从Swagger的@Schema注解中读取description字段, 如果没有@Schema注解,则从字段的JavaDoc注释中提取描述 </p>
         *
         * @param field 字段
         * @return 字段描述信息,如果没有则返回空字符串
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

            // 限制描述长度,避免显示过长
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
         * @param attrNames  属性名（可以有多个备选）
         * @return 属性值
         * @since 1.0.0
         */
        @NotNull
        private String getAnnotationAttributeValue(@NotNull PsiAnnotation annotation, String... attrNames) {
            for (String attrName : attrNames) {
                PsiAnnotationMemberValue value = annotation.findAttributeValue(attrName);
                if (value != null) {
                    String text = value.getText();
                    // 去除引号
                    if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
                        text = text.substring(1, text.length() - 1);
                    }
                    return text.trim();
                }
            }
            return "";
        }
    }

    /**
     * 参数插入处理器 <p> 根据参数类型决定是否自动添加 #{} 包裹 只有基本数据类型才自动包裹,对象类型不包裹 </p>
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-17 11:30:00
     * @since 1.0.0
     */
    private static class ParameterInsertHandler implements InsertHandler<LookupElement> {

        /**
         * 是否为基本数据类型
         */
        private final boolean isPrimitiveType;

        /**
         * 是否在XML属性中
         */
        private final boolean isInXmlAttribute;

        /**
         * 构造函数
         *
         * @param isPrimitiveType  是否为基本数据类型
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        public ParameterInsertHandler(boolean isPrimitiveType, boolean isInXmlAttribute) {
            this.isPrimitiveType = isPrimitiveType;
            this.isInXmlAttribute = isInXmlAttribute;
        }

        /**
         * 处理插入
         *
         * @param context       插入上下文
         * @param lookupElement 补全元素
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement lookupElement) {
            // 如果在XML属性中，不添加#{}符号
            if (this.isInXmlAttribute) {
                // 在XML属性中，只需要移动光标到末尾
                Editor editor = context.getEditor();
                editor.getCaretModel().moveToOffset(context.getTailOffset());
                return;
            }

            // Bug修复: 只有基本数据类型才自动添加#{}
            if (!this.isPrimitiveType) {
                return;
            }

            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            int offset = context.getTailOffset();

            // 检查前面是否已经有 #{
            String textBefore = document.getText().substring(Math.max(0, offset - 50), offset);
            boolean hasPrefix = textBefore.contains("#{");

            // 检查后面是否已经有 }
            String textAfter = document.getText().substring(offset, Math.min(document.getTextLength(), offset + 10));
            boolean hasSuffix = textAfter.startsWith("}");

            if (!hasPrefix) {
                // 添加 #{ 前缀
                document.insertString(offset - lookupElement.getLookupString().length(), "#{");
                offset += 2;
            }

            if (!hasSuffix) {
                // 添加 } 后缀
                document.insertString(offset, "}");
            }

            // 移动光标到}后面
            editor.getCaretModel().moveToOffset(offset + 1);
        }
    }

    /**
     * 字段插入处理器 <p> 不自动添加#{},因为可能还要继续输入属性链 </p>
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-17 11:30:00
     * @since 1.0.0
     */
    private static class FieldInsertHandler implements InsertHandler<LookupElement> {

        /**
         * 是否在XML属性中
         */
        private final boolean isInXmlAttribute;

        /**
         * 构造函数
         *
         * @param isInXmlAttribute 是否在XML属性中
         * @since 1.0.0
         */
        public FieldInsertHandler(boolean isInXmlAttribute) {
            this.isInXmlAttribute = isInXmlAttribute;
        }

        /**
         * 处理插入
         *
         * @param context       插入上下文
         * @param lookupElement 补全元素
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement lookupElement) {
            // 检查是否需要添加#{}
            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            int startOffset = context.getStartOffset();
            int tailOffset = context.getTailOffset();

            // 获取插入前光标位置之前的文本，用于检查是否已有#{}包裹
            String textBeforeStart = this.getTextBeforeCaretUntilSpace(document, startOffset);
            // 使用lookupElement的字符串作为要插入的内容
            String insertText = lookupElement.getLookupString();

            StringBuilder textBuilder = new StringBuilder();
            // 检查是否需要添加#{前缀
            if (!StrUtil.startWith(textBeforeStart, "#{") && !this.isInXmlAttribute) {
                textBuilder.append("#{");
            }
            textBuilder.append(insertText);
            // 检查是否需要添加}后缀
            if (!StrUtil.endWith(textBeforeStart, "}") && !this.isInXmlAttribute) {
                textBuilder.append("}");
            }

            // 删除已经插入的内容，重新插入带有#{}的内容
            if (this.isInXmlAttribute) {
                startOffset -= textBeforeStart.length();
                document.deleteString(startOffset, tailOffset);
                startOffset = editor.getCaretModel().getOffset();
            } else {
                document.deleteString(startOffset, tailOffset);
            }
            document.insertString(startOffset, textBuilder.toString());

            // 移动光标到插入内容的末尾
            editor.getCaretModel().moveToOffset(startOffset + textBuilder.length());
        }

        /**
         * Get Text Before Caret Until Space
         *
         * @param document document
         * @param caretOffset caret offset
         * @return string
         * @since 1.0.0
         */
        private String getTextBeforeCaretUntilSpace(Document document, int caretOffset) {
            if (caretOffset <= 0 || document.getTextLength() == 0) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            int currentOffset = caretOffset - 1; // 从光标前一个字符开始
            // 向前遍历直到遇到空格或到达文档开头
            while (currentOffset >= 0) {
                char c = document.getCharsSequence().charAt(currentOffset);
                // 检查是否是空格字符（包括空格、制表符、换行等）
                if (Character.isWhitespace(c)) {
                    break;
                }
                // 在结果前面添加字符（因为是向前遍历）
                result.insert(0, c);
                currentOffset--;
            }

            return result.toString();
        }
    }
}
