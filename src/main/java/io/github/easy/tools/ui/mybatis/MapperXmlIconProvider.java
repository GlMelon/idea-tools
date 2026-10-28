package io.github.easy.tools.ui.mybatis;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Mapper XML 文件专属图标提供者
 * <p>
 * 根标签为 mapper 的 XML 文件在项目视图中显示 MyBatis 专属图标，
 * 与普通 XML 文件区分。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public class MapperXmlIconProvider extends IconProvider {

    /**
     * Mapper XML 图标
     */
    private static final Icon MAPPER_XML_ICON =
            com.intellij.openapi.util.IconLoader.getIcon("/icons/mybatisMapperXml.svg", MapperXmlIconProvider.class);

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (element instanceof XmlFile xmlFile) {
            return MyBatisUtils.isInMapperFile(xmlFile) ? MAPPER_XML_ICON : null;
        }
        return null;
    }
}
