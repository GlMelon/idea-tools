package io.github.easy.tools.ui.mybatis.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis OGNL 表达式检测
 * <p>
 * 检测 if/when test 属性中常见的 OGNL 字符串比较误用:
 * <ul>
 * <li>多字符单引号字面量: {@code status == 'ACTIVE'} ——
 * OGNL 将单引号字面量按字符处理，多字符内容会得到错误结果，应使用双引号</li>
 * <li>字符串参数与单引号单字符比较: {@code name == 'Y'} 且 name 为 String 类型 ——
 * String 与 Character 比较恒为 false，应使用双引号</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisOgnlInspection extends LocalInspectionTool {

    /**
     * 多字符单引号字面量
     */
    private static final Pattern MULTI_CHAR_SINGLE_QUOTE = Pattern.compile("'([^'\\n]{2,})'");

    /**
     * 标识符与单引号字面量的比较(两个方向)
     */
    private static final Pattern IDENT_COMPARE_SINGLE_QUOTE = Pattern.compile(
            "([a-zA-Z_$][\\w.$]*)\\s*(==|!=)\\s*'([^']*)'|'([^']*)'\\s*(==|!=)\\s*([a-zA-Z_$][\\w.$]*)");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                MyBatisOgnlInspection.this.checkTestAttribute(attribute, holder);
            }
        };
    }

    /**
     * 检测 test 属性中的字符串比较误用
     *
     * @param attribute 属性
     * @param holder    问题收集器
     * @since 1.1.0
     */
    private void checkTestAttribute(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder) {
        if (!"test".equals(attribute.getName())) {
            return;
        }
        XmlTag tag = attribute.getParent();
        if (tag == null || !("if".equals(tag.getName()) || "when".equals(tag.getName()))) {
            return;
        }
        if (!MyBatisUtils.isInMapperFile(tag)) {
            return;
        }
        XmlAttributeValue valueElement = attribute.getValueElement();
        String value = attribute.getValue();
        if (valueElement == null || StringUtil.isEmpty(value)) {
            return;
        }

        // 1. 多字符单引号字面量
        Matcher matcher = MULTI_CHAR_SINGLE_QUOTE.matcher(value);
        while (matcher.find()) {
            holder.registerProblem(valueElement,
                    "OGNL 中 '" + matcher.group(1) + "' 会被按字符处理，请使用双引号字符串",
                    ProblemHighlightType.WARNING,
                    this.shiftRange(valueElement, matcher.start(), matcher.end()),
                    new ReplaceWithDoubleQuotesQuickFix());
        }

        // 2. 字符串类型参数与单引号字面量比较
        Matcher compareMatcher = IDENT_COMPARE_SINGLE_QUOTE.matcher(value);
        while (compareMatcher.find()) {
            String identifier = compareMatcher.group(1) != null ? compareMatcher.group(1) : compareMatcher.group(6);
            String literal = compareMatcher.group(3) != null ? compareMatcher.group(3) : compareMatcher.group(4);
            if (identifier == null || literal == null || literal.length() > 1) {
                continue;
            }
            if (this.isStringIdentifier(tag, identifier)) {
                holder.registerProblem(valueElement,
                        "字符串参数 '" + identifier + "' 与字符字面量 '" + literal + "' 比较恒为 false，请使用双引号",
                        ProblemHighlightType.WARNING,
                        this.shiftRange(valueElement, compareMatcher.start(), compareMatcher.end()),
                        new ReplaceWithDoubleQuotesQuickFix());
            }
        }
    }

    /**
     * 判断 test 表达式中的标识符是否为 String 类型
     *
     * @param tag         if/when 标签
     * @param identifier 标识符(可能为链式路径)
     * @return true如果是String类型
     * @since 1.1.0
     */
    private boolean isStringIdentifier(@NotNull XmlTag tag, @NotNull String identifier) {
        PsiMethod method = MyBatisUtils.findMethod(tag);
        if (method == null) {
            return false;
        }
        String[] segments = identifier.split("\\.");
        String root = segments[0];

        PsiClass currentClass = null;
        int chainStart;
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            String paramName = MyBatisUtils.getParamAnnotationValue(parameter);
            if (paramName == null) {
                paramName = parameter.getName();
            }
            if (root.equals(paramName)) {
                currentClass = MyBatisUtils.resolveRootParamClass(parameter, root);
                chainStart = 1;
                return this.chainEndsWithString(currentClass, segments, chainStart);
            }
        }
        // 单参数方法的属性直访
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
            currentClass = MyBatisUtils.resolveRootParamClass(parameters[0], root);
            if (currentClass != null) {
                PsiElement resolved = MyBatisUtils.findFieldOrPropertyInClass(currentClass, root);
                if (resolved != null) {
                    return this.isStringElement(resolved)
                            || this.chainEndsWithString(MyBatisUtils.getTypeOfResolvedElement(resolved), segments, 1);
                }
            }
        }
        return false;
    }

    /**
     * 判断链式路径终点是否为 String
     *
     * @param startClass 起始类型
     * @param segments   路径分段
     * @param fromIndex  检查起点
     * @return true如果终点为String
     * @since 1.1.0
     */
    private boolean chainEndsWithString(@Nullable PsiClass startClass, @NotNull String[] segments, int fromIndex) {
        PsiClass currentClass = startClass;
        PsiElement resolved = null;
        for (int i = fromIndex; i < segments.length && currentClass != null; i++) {
            resolved = MyBatisUtils.findFieldOrPropertyInClass(currentClass, segments[i]);
            if (resolved == null) {
                return false;
            }
            currentClass = MyBatisUtils.getTypeOfResolvedElement(resolved);
        }
        return resolved != null && this.isStringElement(resolved);
    }

    /**
     * 判断解析元素是否为 String 类型
     *
     * @param element 字段或getter
     * @return true如果String
     * @since 1.1.0
     */
    private boolean isStringElement(@NotNull PsiElement element) {
        PsiType type = null;
        if (element instanceof com.intellij.psi.PsiField field) {
            type = field.getType();
        } else if (element instanceof PsiMethod method) {
            type = method.getReturnType();
        }
        return type != null && "java.lang.String".equals(type.getCanonicalText());
    }

    /**
     * 将文本内偏移转为元素内范围
     *
     * @param valueElement 属性值元素
     * @param start        文本内起始
     * @param end          文本内结束
     * @return 元素内范围
     * @since 1.1.0
     */
    private TextRange shiftRange(@NotNull XmlAttributeValue valueElement, int start, int end) {
        // 属性值元素文本首字符为引号，值内容偏移+1
        return new TextRange(start + 1, end + 1);
    }

    /**
     * 将 test 表达式中的单引号字面量替换为双引号
     *
     * @author haijun
     * @version 1.1.0
     * @date 2026-10-28
     * @since 1.1.0
     */
    private static class ReplaceWithDoubleQuotesQuickFix implements LocalQuickFix {

        @Override
        public @NotNull String getFamilyName() {
            return "替换为双引号字符串";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof XmlAttributeValue valueElement)) {
                return;
            }
            String raw = valueElement.getText();
            if (raw.length() < 2) {
                return;
            }
            String inner = raw.substring(1, raw.length() - 1);
            String replaced = inner.replaceAll("'([^']*)'", "\"$1\"");
            XmlAttribute attribute = valueElement.getParent() instanceof XmlAttribute attr ? attr : null;
            if (attribute != null) {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    attribute.setValue(replaced);
                });
            }
        }
    }
}
