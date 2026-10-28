package io.github.easy.tools.ui.mybatis;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Mapper Line Marker Provider
 *
 * @author haijun
 * @date 2025-12-12 14:07:26
 * @version 1.0.0
 * @since 1.0.0
 */
public class MapperLineMarkerProvider extends RelatedItemLineMarkerProvider {

    /**
     * 收集导航标记信息
     *
     * @param element  当前PSI元素
     * @param result   结果收集器
     * @since 1.0.0
     */
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 只处理标识符元素（类名或方法名）
        if (!(element instanceof PsiIdentifier)) {
            return;
        }

        // 获取父元素
        PsiElement parent = element.getParent();

        // 处理类级别的导航（接口名 -> XML文件）
        if (parent instanceof PsiClass psiClass) {
            this.handleClassNavigation(element, psiClass, result);
        }
        // 处理方法级别的导航（方法名 -> XML标签）
        else if (parent instanceof PsiMethod psiMethod) {
            this.handleMethodNavigation(element, psiMethod, result);
        }
    }

    /**
     * 处理类级别的导航（Mapper接口 -> XML文件）
     *
     * @param element 标识符元素
     * @param psiClass 类
     * @param result 结果收集器
     * @since 1.0.0
     */
    private void handleClassNavigation(@NotNull PsiElement element,
                                         @NotNull PsiClass psiClass,
                                         @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 检查是否为接口
        if (!psiClass.isInterface()) {
            return;
        }

        // 检查是否为Mapper接口(@Mapper/@MapperScan/框架BaseMapper)
        if (!MyBatisUtils.isMapperInterface(psiClass)) {
            return;
        }

        // 获取接口的完全限定名
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // 从缓存中查找对应的XML文件
        XmlFile xmlFile = MyBatisUtils.findMapperXmlFile(element.getProject(), qualifiedName);
        if (xmlFile == null) {
            return;
        }

        // 创建导航标记
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.Gutter.RecursiveMethod)
                .setTargets(Collections.singletonList(xmlFile))
                .setTooltipText("导航到MyBatis Mapper XML配置文件")
                .setAlignment(GutterIconRenderer.Alignment.LEFT);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 处理方法级别的导航（Mapper方法 -> XML SQL标签）
     *
     * @param element 标识符元素
     * @param psiMethod 方法
     * @param result 结果收集器
     * @since 1.0.0
     */
    private void handleMethodNavigation(@NotNull PsiElement element,
                                          @NotNull PsiMethod psiMethod,
                                          @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 获取方法所在的类
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null || !containingClass.isInterface()) {
            return;
        }

        // 检查是否为Mapper接口(@Mapper/@MapperScan/框架BaseMapper)
        if (!MyBatisUtils.isMapperInterface(containingClass)) {
            return;
        }

        // 获取接口的完全限定名
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // 从缓存中查找对应的XML文件
        XmlFile xmlFile = MyBatisUtils.findMapperXmlFile(element.getProject(), qualifiedName);
        if (xmlFile == null) {
            // 如果当前接口没有对应的XML文件，尝试在父接口中查找
            XmlTag sqlTag = this.findSqlTagInParentInterfaces(containingClass, psiMethod.getName());
            if (sqlTag != null) {
                // 创建导航标记
                NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                        .create(AllIcons.Gutter.RecursiveMethod)
                        .setTargets(Collections.singletonList(sqlTag))
                        .setTooltipText("导航到MyBatis XML SQL标签（来自父接口）")
                        .setAlignment(GutterIconRenderer.Alignment.LEFT);

                result.add(builder.createLineMarkerInfo(element));
            }
            return;
        }

        // 在XML文件中查找对应的SQL标签
        XmlTag sqlTag = MyBatisUtils.findSqlTagByMethodName(xmlFile, psiMethod.getName());
        if (sqlTag == null) {
            // 如果当前XML文件中没有找到，尝试在父接口的XML文件中查找
            sqlTag = this.findSqlTagInParentInterfaces(containingClass, psiMethod.getName());
            if (sqlTag == null) {
                return;
            }
            // 创建导航标记（来自父接口）
            NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                    .create(AllIcons.Gutter.RecursiveMethod)
                    .setTargets(Collections.singletonList(sqlTag))
                    .setTooltipText("导航到MyBatis XML SQL标签（来自父接口）")
                    .setAlignment(GutterIconRenderer.Alignment.LEFT);

            result.add(builder.createLineMarkerInfo(element));
            return;
        }

        // 创建导航标记
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.Gutter.RecursiveMethod)
                .setTargets(Collections.singletonList(sqlTag))
                .setTooltipText("导航到MyBatis XML SQL标签")
                .setAlignment(GutterIconRenderer.Alignment.LEFT);

        result.add(builder.createLineMarkerInfo(element));
    }

    /**
     * 在父接口中查找SQL标签
     *
     * @param psiClass 当前接口类
     * @param methodName 方法名
     * @return 找到的SQL标签，未找到返回null
     * @since 1.0.0
     */
    private XmlTag findSqlTagInParentInterfaces(@NotNull PsiClass psiClass, @NotNull String methodName) {
        // 获取所有父接口
        PsiClass[] supers = psiClass.getSupers();
        for (PsiClass superClass : supers) {
            if (!superClass.isInterface()) {
                continue;
            }

            String superQualifiedName = superClass.getQualifiedName();
            if (superQualifiedName == null) {
                continue;
            }

            // 查找父接口对应的XML文件
            XmlFile superXmlFile = MyBatisUtils.findMapperXmlFile(superClass.getProject(), superQualifiedName);
            if (superXmlFile != null) {
                // 在父接口的XML文件中查找SQL标签
                XmlTag sqlTag = MyBatisUtils.findSqlTagByMethodName(superXmlFile, methodName);
                if (sqlTag != null) {
                    return sqlTag;
                }
            }

            // 递归查找更上层的父接口
            XmlTag sqlTag = this.findSqlTagInParentInterfaces(superClass, methodName);
            if (sqlTag != null) {
                return sqlTag;
            }
        }

        return null;
    }
}
