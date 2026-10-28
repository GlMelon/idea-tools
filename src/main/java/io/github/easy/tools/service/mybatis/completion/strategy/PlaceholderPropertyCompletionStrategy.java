package io.github.easy.tools.service.mybatis.completion.strategy;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.service.mybatis.MyBatisParamSpec;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * #{} 占位符属性配置补全策略
 * <p>
 * 在 {@code #{param, jdbcType=...}} 的属性部分提供补全:
 * <ul>
 * <li>属性 key 补全: javaType/jdbcType/mode/numericScale/resultMap/typeHandler</li>
 * <li>jdbcType 取值补全(JdbcType 枚举)</li>
 * <li>mode 取值补全(IN/OUT/INOUT)</li>
 * <li>javaType 常用类型补全</li>
 * <li>resultMap 取值补全(当前文件的 resultMap id)</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class PlaceholderPropertyCompletionStrategy implements CompletionStrategy {

    @Override
    public boolean supports(@NotNull CompletionContext context) {
        return context.getCompletionType() == CompletionContext.CompletionType.PLACEHOLDER_PROPERTY;
    }

    @Override
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        String currentText = context.getCurrentText();
        // 文本内容场景: "param, jdbcType=VAR" -> 属性部分为逗号后内容
        // 属性值场景: "jdbcType=VAR" 或 "jdbcTy"
        String props = currentText.contains(",") ? currentText.substring(currentText.indexOf(',') + 1) : currentText;
        props = props.trim();

        if (props.contains("=")) {
            String key = props.substring(0, props.indexOf('=')).trim();
            String normalizedKey = key.toLowerCase();
            switch (normalizedKey) {
                case "jdbctype" -> this.addValues(result, MyBatisParamSpec.JDBC_TYPES, AllIcons.Nodes.Enum);
                case "mode" -> this.addValues(result, MyBatisParamSpec.MODES, AllIcons.Nodes.Enum);
                case "javatype" -> this.addValues(result, MyBatisParamSpec.JAVA_TYPES, AllIcons.Nodes.Class);
                case "resultmap" -> this.addResultMapIds(context, result);
                default -> {
                    // numericScale(数字)/typeHandler(类名) 不提供取值补全
                }
            }
        } else {
            // 属性 key 补全，补全后自动带上 "="
            for (String key : MyBatisParamSpec.PROPERTY_KEYS) {
                result.addElement(LookupElementBuilder.create(key + "=")
                        .withPresentableText(key)
                        .withIcon(AllIcons.Nodes.Property)
                        .withTailText("=")
                        .bold());
            }
        }
    }

    /**
     * 添加枚举/类型取值补全项
     *
     * @param result 补全结果集
     * @param values 取值列表
     * @param icon   图标
     * @since 1.1.0
     */
    private void addValues(@NotNull CompletionResultSet result, @NotNull List<String> values,
                           @NotNull javax.swing.Icon icon) {
        for (String value : values) {
            result.addElement(LookupElementBuilder.create(value)
                    .withIcon(icon)
                    .bold());
        }
    }

    /**
     * 添加当前文件 resultMap id 补全项
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.1.0
     */
    private void addResultMapIds(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        PsiFile file = InjectedLanguageManager.getInstance(context.getProject())
                .getTopLevelFile(context.getPosition());
        if (!(file instanceof XmlFile xmlFile) || xmlFile.getRootTag() == null) {
            return;
        }
        for (XmlTag tag : xmlFile.getRootTag().getSubTags()) {
            if ("resultMap".equals(tag.getName())) {
                String id = tag.getAttributeValue("id");
                if (StringUtil.isNotEmpty(id)) {
                    result.addElement(LookupElementBuilder.create(id)
                            .withIcon(AllIcons.Nodes.Tag)
                            .bold());
                }
            }
        }
    }
}
