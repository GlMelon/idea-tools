package io.github.easy.tools.service.mybatis.template;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis Live Templates 提供者
 * <p>
 * 注册 5 组 MyBatis 相关的 Live Template:
 * <ul>
 * <li>MyBatis-SQL: SQL 语句(select/insert/update/delete/sql片段/include/resultMap)</li>
 * <li>MyBatis-Comment: SQL 注释</li>
 * <li>MyBatis-SqlNav: 动态 SQL 标签(where/if/foreach/choose/set/trim/bind)</li>
 * <li>MyBatis-OGNL: OGNL test 表达式快捷输入</li>
 * <li>MyBatis-Param: #{}/${} 占位符快捷输入</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MyBatisLiveTemplatesProvider implements DefaultLiveTemplatesProvider {

    /**
     * Live Template 资源文件(classpath 路径, 不含扩展名)
     */
    private static final String[] TEMPLATE_FILES = {
            "liveTemplates/MyBatisSql",
            "liveTemplates/MyBatisComment",
            "liveTemplates/MyBatisSqlNav",
            "liveTemplates/MyBatisOgnl",
            "liveTemplates/MyBatisParam"
    };

    @Override
    public String @NotNull [] getDefaultLiveTemplateFiles() {
        return TEMPLATE_FILES;
    }

    @Override
    public String @NotNull [] getHiddenLiveTemplateFiles() {
        return new String[0];
    }
}
