package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.github.easy.tools.service.mybatis.MyBatisParamSpec;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis #{} 占位符参数检测
 * <p>
 * 对语句中的 #{param, jdbcType=...} 占位符做两类检测:
 * <ul>
 * <li>参数未定义: 占位符首段参数名在 Mapper 方法参数/@Param/foreach/bind 变量中不存在，
 * 或链式属性在参数类型中无法解析</li>
 * <li>属性配置错误: jdbcType/mode 取值非法、出现未知属性 key</li>
 * </ul>
 * 无法解析 Mapper 方法或参数类型时静默跳过，避免误报。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisParamInspection extends LocalInspectionTool {

    /**
     * #{} 占位符正则
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{([^}]+)}");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
                // 只在语句顶层标签处理一次(select/insert/update/delete)
                if (!MyBatisUtils.isStatementTag(tag.getName())
                        || tag.getParentTag() == null
                        || !"mapper".equals(tag.getParentTag().getName())) {
                    return;
                }
                MyBatisParamInspection.this.checkStatement(tag, holder);
            }
        };
    }

    /**
     * 检测整条语句中的占位符
     *
     * @param statementTag 语句标签
     * @param holder       问题收集器
     * @since 1.1.0
     */
    private void checkStatement(@NotNull XmlTag statementTag, @NotNull ProblemsHolder holder) {
        if (!MyBatisUtils.isInMapperFile(statementTag)) {
            return;
        }
        PsiMethod method = MyBatisUtils.findMethod(statementTag);
        Set<String> extraVars = this.collectExtraVariables(statementTag);

        // 文本内容中的占位符(XmlText 原始文本，保证偏移与文档一致)
        for (XmlText text : PsiTreeUtil.findChildrenOfType(statementTag, XmlText.class)) {
            this.checkText(text.getText(), text,
                    text.getTextRange().getStartOffset(), method, extraVars, holder);
        }
        // 属性值中的占位符(如 <if test="#{...}"> 等少见场景)
        for (XmlAttribute attribute : PsiTreeUtil.findChildrenOfType(statementTag, XmlAttribute.class)) {
            XmlAttributeValue valueElement = attribute.getValueElement();
            String raw = valueElement == null ? null : valueElement.getText();
            if (raw != null && raw.length() >= 2 && PLACEHOLDER_PATTERN.matcher(raw).find()) {
                String inner = raw.substring(1, raw.length() - 1);
                this.checkText(inner, valueElement,
                        valueElement.getTextRange().getStartOffset() + 1, method, extraVars, holder);
            }
        }
    }

    /**
     * 检测一段文本中的所有占位符
     *
     * @param text          文本值
     * @param reportElement 问题注册目标元素
     * @param textStart     文本在文档中的起始偏移
     * @param method        Mapper方法(可能为null)
     * @param extraVars     foreach/bind 引入的变量名
     * @param holder        问题收集器
     * @since 1.1.0
     */
    private void checkText(@NotNull String text,
                           @NotNull PsiElement reportElement,
                           int textStart,
                           @Nullable PsiMethod method,
                           @NotNull Set<String> extraVars,
                           @NotNull ProblemsHolder holder) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String content = matcher.group(1);
            if (StringUtil.isEmpty(content)) {
                continue;
            }
            TextRange placeholderRange = new TextRange(
                    textStart + matcher.start(), textStart + matcher.end())
                    .shiftRight(-reportElement.getTextRange().getStartOffset());

            String[] sections = content.split(",", 2);
            String paramPath = sections[0].trim();

            // 属性配置检测
            if (sections.length > 1) {
                this.checkProperties(sections[1], reportElement, placeholderRange, holder);
            }
            if (StringUtil.isEmpty(paramPath) || paramPath.contains("$")) {
                continue;
            }

            // 参数存在性检测
            this.checkParamPath(paramPath, method, extraVars, reportElement, placeholderRange, holder);
        }
    }

    /**
     * 检测占位符属性配置
     *
     * @param propsText      属性部分文本(逗号后)
     * @param reportElement  问题注册目标元素
     * @param placeholderRange 占位符相对元素的范围
     * @param holder         问题收集器
     * @since 1.1.0
     */
    private void checkProperties(@NotNull String propsText,
                                 @NotNull PsiElement reportElement,
                                 @NotNull TextRange placeholderRange,
                                 @NotNull ProblemsHolder holder) {
        for (String prop : propsText.split(",")) {
            String trimmed = prop.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            String key = (eq >= 0 ? trimmed.substring(0, eq) : trimmed).trim();
            String value = eq >= 0 ? trimmed.substring(eq + 1).trim() : null;

            if (!MyBatisParamSpec.PROPERTY_KEYS.contains(key)) {
                holder.registerProblem(reportElement,
                        "未知的 #{} 属性 '" + key + "'",
                        ProblemHighlightType.WARNING, placeholderRange);
                continue;
            }
            if (value == null || value.isEmpty()) {
                continue;
            }
            switch (key) {
                case "jdbcType" -> {
                    if (!MyBatisParamSpec.JDBC_TYPES.contains(value)) {
                        holder.registerProblem(reportElement,
                                "非法的 jdbcType '" + value + "'",
                                ProblemHighlightType.WARNING, placeholderRange);
                    }
                }
                case "mode" -> {
                    if (!MyBatisParamSpec.MODES.contains(value)) {
                        holder.registerProblem(reportElement,
                                "非法的 mode '" + value + "'，可选: IN/OUT/INOUT",
                                ProblemHighlightType.WARNING, placeholderRange);
                    }
                }
                default -> {
                    // javaType/resultMap/typeHandler 的取值不在本地强校验
                }
            }
        }
    }

    /**
     * 检测参数路径可解析性
     *
     * @param paramPath      参数路径(如 user.name)
     * @param method         Mapper方法
     * @param extraVars      foreach/bind 变量
     * @param reportElement  问题注册目标元素
     * @param placeholderRange 占位符相对元素的范围
     * @param holder         问题收集器
     * @since 1.1.0
     */
    private void checkParamPath(@NotNull String paramPath,
                                @Nullable PsiMethod method,
                                @NotNull Set<String> extraVars,
                                @NotNull PsiElement reportElement,
                                @NotNull TextRange placeholderRange,
                                @NotNull ProblemsHolder holder) {
        // 方法无法解析时不做参数检测(避免误报)
        if (method == null) {
            return;
        }
        String[] segments = paramPath.split("\\.");
        String root = segments[0];

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 0) {
            return;
        }

        // 1. 根参数命中 @Param 或参数名
        PsiParameter rootParam = null;
        for (PsiParameter parameter : parameters) {
            String paramName = MyBatisUtils.getParamAnnotationValue(parameter);
            if (paramName == null) {
                paramName = parameter.getName();
            }
            if (root.equals(paramName)) {
                rootParam = parameter;
                break;
            }
        }
        if (rootParam != null) {
            this.checkChain(rootParam, segments, 1, paramPath, reportElement, placeholderRange, holder);
            return;
        }

        // 2. 特殊变量: foreach item/index、bind name、内置 _parameter
        if (extraVars.contains(root) || "_parameter".equals(root)) {
            return;
        }

        // 3. 单参数方法的属性直访 #{name} (MyBatis 单参数可省略参数名)
        if (parameters.length == 1) {
            PsiParameter single = parameters[0];
            PsiClass paramClass = MyBatisUtils.resolveRootParamClass(single, root);
            if (paramClass == null || MyBatisUtils.findFieldOrPropertyInClass(paramClass, root) != null) {
                return;
            }
        }

        holder.registerProblem(reportElement,
                "未定义的参数 '" + paramPath + "'",
                ProblemHighlightType.WARNING, placeholderRange);
    }

    /**
     * 检测链式属性解析
     *
     * @param rootParam       根参数
     * @param segments        路径分段
     * @param fromIndex       从第几段开始检查属性链
     * @param paramPath       完整路径(用于消息)
     * @param reportElement   问题注册目标元素
     * @param placeholderRange 占位符相对元素的范围
     * @param holder          问题收集器
     * @since 1.1.0
     */
    private void checkChain(@NotNull PsiParameter rootParam,
                            @NotNull String[] segments,
                            int fromIndex,
                            @NotNull String paramPath,
                            @NotNull PsiElement reportElement,
                            @NotNull TextRange placeholderRange,
                            @NotNull ProblemsHolder holder) {
        if (fromIndex >= segments.length) {
            return;
        }
        PsiClass currentClass = MyBatisUtils.resolveRootParamClass(rootParam, segments[0]);
        if (currentClass == null) {
            // Map/无法推断类型时跳过
            return;
        }
        for (int i = fromIndex; i < segments.length && currentClass != null; i++) {
            PsiElement resolved = MyBatisUtils.findFieldOrPropertyInClass(currentClass, segments[i]);
            if (resolved == null) {
                holder.registerProblem(reportElement,
                        "属性 '" + segments[i] + "' 在参数 '" + paramPath + "' 的类型链中不存在",
                        ProblemHighlightType.WARNING, placeholderRange);
                return;
            }
            currentClass = MyBatisUtils.getTypeOfResolvedElement(resolved);
        }
    }

    /**
     * 收集语句子树中 foreach/bind 引入的变量名
     *
     * @param statementTag 语句标签
     * @return 变量名集合
     * @since 1.1.0
     */
    private Set<String> collectExtraVariables(@NotNull XmlTag statementTag) {
        Set<String> vars = new HashSet<>();
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(statementTag, XmlTag.class)) {
            if ("foreach".equals(tag.getName())) {
                this.addIfPresent(vars, tag.getAttributeValue("item"));
                this.addIfPresent(vars, tag.getAttributeValue("index"));
            } else if ("bind".equals(tag.getName())) {
                this.addIfPresent(vars, tag.getAttributeValue("name"));
            }
        }
        return vars;
    }

    /**
     * 非空则加入集合
     *
     * @param vars  变量集合
     * @param value 值
     * @since 1.1.0
     */
    private void addIfPresent(@NotNull Set<String> vars, @Nullable String value) {
        if (StringUtil.isNotEmpty(value)) {
            vars.add(value);
        }
    }
}
