package io.github.easy.tools.mybatis.database;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Mapper XML SQL 语言注入器(可选数据源模块)
 * <p>
 * 将 SQL 语句标签(select/insert/update/delete/sql 及其子标签)中的文本
 * 注入为 SQL 语言，从而获得 IDE 原生的 SQL 能力:
 * <ul>
 * <li>SQL 关键字/表/列补全(表列补全依赖项目配置的数据源与 SQL Resolution Scope)</li>
 * <li>SQL 语法高亮与语法检查(表/列是否存在等 inspection)</li>
 * </ul>
 * 未配置数据源时的 "SQL dialect is not configured" 等噪音告警
 * 由核心插件的 MapperXmlInspectionSuppressor 抑制。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisSqlInjectionContributor implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlText xmlText) || !(context instanceof PsiLanguageInjectionHost host)) {
            return;
        }
        if (!this.isStatementText(xmlText)) {
            return;
        }
        Language sqlLanguage = SqlLanguageHolder.getSqlLanguage();
        if (sqlLanguage == null) {
            return;
        }
        registrar.startInjecting(sqlLanguage)
                .addPlace(null, null, host, ElementManipulators.getValueTextRange(host));
    }

    @Override
    public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlText.class);
    }

    /**
     * 判断文本是否位于 Mapper 文件的 SQL 语句标签内
     *
     * @param xmlText 文本元素
     * @return true如果在语句标签文本内
     * @since 1.1.0
     */
    private boolean isStatementText(@NotNull XmlText xmlText) {
        XmlTag parentTag = xmlText.getParentTag();
        if (parentTag == null) {
            return false;
        }
        // select/insert/update/delete/sql 及其动态子标签内的文本
        if (MyBatisUtils.findSqlTag(parentTag, true) == null) {
            return false;
        }
        return MyBatisUtils.isInMapperFile(parentTag);
    }
}
