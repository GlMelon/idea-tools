package io.github.easy.tools.service.mybatis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.PsiModificationTracker;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis @MapperScan 扫描信息服务
 * <p>
 * 解析项目中 @MapperScan / @MapperScans (org.mybatis.spring.annotation) 的使用，
 * 识别其扫描包覆盖的 Mapper 接口，使无 @Mapper 注解的接口也能被插件识别，
 * 并为 Spring 注入抑制器提供依据。结果按 PSI 修改计数缓存。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class MyBatisMapperScanService {

    /**
     * mybatis-spring 的 @MapperScan 注解完全限定名
     */
    private static final String MAPPER_SCAN_ANNOTATION = "org.mybatis.spring.annotation.MapperScan";

    /**
     * mybatis-spring 的 @MapperScans 容器注解完全限定名
     */
    private static final String MAPPER_SCANS_ANNOTATION = "org.mybatis.spring.annotation.MapperScans";

    /**
     * 包名分隔符
     */
    private static final String DOT = ".";

    /**
     * AnnotatedElementsSearch#searchPsiClasses(2024.3+)，反射兼容
     */
    private static final @Nullable Method SEARCH_PSI_CLASSES_METHOD;

    /**
     * AnnotatedElementsSearch#search(Class, PsiClass, SearchScope)(2024.1-2024.2)，反射兼容
     */
    private static final @Nullable Method SEARCH_GENERIC_METHOD;

    static {
        SEARCH_PSI_CLASSES_METHOD = findMethod("searchPsiClasses", PsiClass.class, SearchScope.class);
        SEARCH_GENERIC_METHOD = findMethod("search", Class.class, PsiClass.class, SearchScope.class);
    }

    /**
     * 查找 AnnotatedElementsSearch 的指定方法(跨版本签名迁移兼容)
     *
     * @param name          方法名
     * @param parameterTypes 参数类型
     * @return 方法，不存在返回null
     * @since 1.2.0
     */
    private static @Nullable Method findMethod(@NotNull String name, @NotNull Class<?>... parameterTypes) {
        try {
            return AnnotatedElementsSearch.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * 关联的项目
     */
    private final Project project;

    /**
     * 缓存的扫描信息(可能为空列表)
     */
    private volatile List<MapperScanInfo> cachedScans;

    /**
     * 缓存对应的PSI修改计数
     */
    private volatile long cachedStamp = -1;

    /**
     * 构造函数
     *
     * @param project 关联的项目
     * @since 1.2.0
     */
    public MyBatisMapperScanService(Project project) {
        this.project = project;
    }

    /**
     * 获取服务实例
     *
     * @param project 项目
     * @return 服务实例
     * @since 1.2.0
     */
    public static MyBatisMapperScanService getInstance(@NotNull Project project) {
        return project.getService(MyBatisMapperScanService.class);
    }

    /**
     * 判断接口是否被项目中的 @MapperScan 扫描覆盖
     * <p>
     * 接口完全限定名落在任一扫描包下即视为覆盖；
     * 若 @MapperScan 指定了 annotationClass/markerInterface 过滤条件，
     * 还需接口实际携带对应注解/父接口才视为 Mapper。
     * </p>
     *
     * @param psiClass 接口
     * @return true如果被扫描覆盖
     * @since 1.2.0
     */
    public boolean isScannedMapper(@Nullable PsiClass psiClass) {
        if (psiClass == null || !psiClass.isInterface()) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return false;
        }
        for (MapperScanInfo info : this.getScans()) {
            if (info.covers(qualifiedName) && info.matchesFilters(psiClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取扫描信息(按PSI修改计数缓存)
     *
     * @return 扫描信息列表，无 @MapperScan 时为空列表
     * @since 1.2.0
     */
    private @NotNull List<MapperScanInfo> getScans() {
        long stamp = PsiModificationTracker.getInstance(this.project).getModificationCount();
        List<MapperScanInfo> scans = this.cachedScans;
        if (scans != null && stamp == this.cachedStamp) {
            return scans;
        }
        scans = this.computeScans();
        this.cachedScans = scans;
        this.cachedStamp = stamp;
        return scans;
    }

    /**
     * 扫描项目中的 @MapperScan / @MapperScans 使用并解析
     *
     * @return 扫描信息列表
     * @since 1.2.0
     */
    private @NotNull List<MapperScanInfo> computeScans() {
        try {
            return ReadAction.compute(() -> {
                PsiClass scanAnnotationClass = JavaPsiFacade.getInstance(this.project)
                        .findClass(MAPPER_SCAN_ANNOTATION, GlobalSearchScope.allScope(this.project));
                // mybatis-spring 不在类路径上时项目中不可能有 @MapperScan
                if (scanAnnotationClass == null) {
                    return Collections.<MapperScanInfo>emptyList();
                }

                List<MapperScanInfo> result = new ArrayList<>();
                for (PsiClass configClass : this.findAnnotatedClasses(scanAnnotationClass,
                        GlobalSearchScope.projectScope(this.project))) {
                    for (PsiAnnotation annotation : configClass.getAnnotations()) {
                        String annotationFqn = annotation.getQualifiedName();
                        if (MAPPER_SCAN_ANNOTATION.equals(annotationFqn)) {
                            MapperScanInfo info = this.parseMapperScan(annotation);
                            if (!info.basePackages().isEmpty()) {
                                result.add(info);
                            }
                        } else if (MAPPER_SCANS_ANNOTATION.equals(annotationFqn)) {
                            this.parseMapperScansContainer(annotation, result);
                        }
                    }
                }
                return result;
            });
        } catch (Exception ex) {
            log.debug("解析 @MapperScan 使用失败", ex);
            return Collections.emptyList();
        }
    }

    /**
     * 查找标注了指定注解的类
     * <p>跨版本兼容: 2024.3+ 使用 searchPsiClasses，早期版本使用泛型 search。</p>
     *
     * @param annotationClass 注解类
     * @param scope           搜索范围
     * @return 标注该注解的类
     * @since 1.2.0
     */
    private @NotNull List<PsiClass> findAnnotatedClasses(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
        try {
            Iterable<?> query;
            if (SEARCH_PSI_CLASSES_METHOD != null) {
                query = (Iterable<?>) SEARCH_PSI_CLASSES_METHOD.invoke(null, annotationClass, scope);
            } else if (SEARCH_GENERIC_METHOD != null) {
                query = (Iterable<?>) SEARCH_GENERIC_METHOD.invoke(null, PsiClass.class, annotationClass, scope);
            } else {
                return Collections.emptyList();
            }
            List<PsiClass> result = new ArrayList<>();
            for (Object element : query) {
                if (element instanceof PsiClass psiClass) {
                    result.add(psiClass);
                }
            }
            return result;
        } catch (Exception ex) {
            log.debug("注解搜索失败", ex);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 @MapperScans 容器注解中嵌套的 @MapperScan
     *
     * @param container 容器注解
     * @param result    收集目标
     * @since 1.2.0
     */
    private void parseMapperScansContainer(@NotNull PsiAnnotation container, @NotNull List<MapperScanInfo> result) {
        PsiAnnotationMemberValue value = container.findAttributeValue("value");
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                if (initializer instanceof PsiAnnotation annotation) {
                    MapperScanInfo info = this.parseMapperScan(annotation);
                    if (!info.basePackages().isEmpty()) {
                        result.add(info);
                    }
                }
            }
        }
    }

    /**
     * 解析单个 @MapperScan 注解的扫描配置
     *
     * @param annotation @MapperScan 注解
     * @return 扫描信息
     * @since 1.2.0
     */
    private @NotNull MapperScanInfo parseMapperScan(@NotNull PsiAnnotation annotation) {
        List<String> basePackages = new ArrayList<>();
        this.collectPackageNames(annotation.findAttributeValue("value"), basePackages);
        this.collectPackageNames(annotation.findAttributeValue("basePackages"), basePackages);
        this.collectBasePackageClasses(annotation.findAttributeValue("basePackageClasses"), basePackages);

        return new MapperScanInfo(List.copyOf(basePackages),
                this.resolveClassFqn(annotation.findAttributeValue("annotationClass")),
                this.resolveClassFqn(annotation.findAttributeValue("markerInterface")));
    }

    /**
     * 从字符串属性值中收集包名(支持单值/数组/逗号分隔)
     *
     * @param value        注解属性值
     * @param basePackages 收集目标
     * @since 1.2.0
     */
    private void collectPackageNames(@Nullable PsiAnnotationMemberValue value, @NotNull List<String> basePackages) {
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                this.collectPackageNames(initializer, basePackages);
            }
            return;
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
            for (String part : text.split("[,;\\s]+")) {
                if (StringUtil.isNotEmpty(part)) {
                    basePackages.add(part);
                }
            }
        }
    }

    /**
     * 从 basePackageClasses 的类字面量中收集包名(取类所在包)
     *
     * @param value        注解属性值
     * @param basePackages 收集目标
     * @since 1.2.0
     */
    private void collectBasePackageClasses(@Nullable PsiAnnotationMemberValue value, @NotNull List<String> basePackages) {
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                this.collectBasePackageClasses(initializer, basePackages);
            }
            return;
        }
        String fqn = this.resolveClassFqn(value);
        if (fqn != null) {
            int lastDot = fqn.lastIndexOf(DOT);
            if (lastDot > 0) {
                basePackages.add(fqn.substring(0, lastDot));
            }
        }
    }

    /**
     * 解析类字面量属性的完全限定名
     *
     * @param value 注解属性值(如 SomeClass.class)
     * @return 类完全限定名，无法解析返回null
     * @since 1.2.0
     */
    private @Nullable String resolveClassFqn(@Nullable PsiAnnotationMemberValue value) {
        if (!(value instanceof PsiClassObjectAccessExpression classObject)) {
            return null;
        }
        if (classObject.getOperand().getType() instanceof PsiClassType classType
                && classType.resolve() instanceof PsiClass psiClass) {
            return psiClass.getQualifiedName();
        }
        return null;
    }

    /**
     * 单个 @MapperScan 的扫描配置
     *
     * @param basePackages        扫描的基础包
     * @param annotationClassFqn  注解过滤类(annotationClass)，null表示未配置
     * @param markerInterfaceFqn  标记接口过滤(markerInterface)，null表示未配置
     * @since 1.2.0
     */
    private record MapperScanInfo(@NotNull List<String> basePackages,
                                  @Nullable String annotationClassFqn,
                                  @Nullable String markerInterfaceFqn) {

        /**
         * 判断接口完全限定名是否落在扫描包内
         *
         * @param interfaceFqn 接口完全限定名
         * @return true如果被扫描包覆盖
         * @since 1.2.0
         */
        boolean covers(@NotNull String interfaceFqn) {
            for (String basePackage : this.basePackages) {
                if (interfaceFqn.equals(basePackage) || interfaceFqn.startsWith(basePackage + DOT)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 校验接口是否满足 annotationClass / markerInterface 过滤条件
         *
         * @param psiClass 接口
         * @return true如果满足(未配置过滤条件时恒为true)
         * @since 1.2.0
         */
        boolean matchesFilters(@NotNull PsiClass psiClass) {
            if (this.annotationClassFqn != null && !psiClass.hasAnnotation(this.annotationClassFqn)) {
                return false;
            }
            if (this.markerInterfaceFqn != null) {
                PsiClass markerClass = JavaPsiFacade.getInstance(psiClass.getProject())
                        .findClass(this.markerInterfaceFqn, psiClass.getResolveScope());
                if (markerClass == null || !psiClass.isInheritor(markerClass, true)) {
                    return false;
                }
            }
            return true;
        }
    }
}
