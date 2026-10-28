package io.github.easy.tools.ui.mybatis.suppress;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTypesUtil;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Mapper Spring 注入报错抑制器
 * <p>
 * Mapper 接口的 Bean 由 MyBatis 运行时代理生成(FactoryBean/@MapperScan)，
 * Spring 静态模型无法解析，注入点(@Autowired/@Resource 字段、构造器/Setter参数)
 * 会报 "Could not autowire. No beans of 'XxxMapper' type found."。
 * 本抑制器在注入点类型为 Mapper 接口(@Mapper / @MapperScan 覆盖 / 继承
 * MyBatis-Plus 等 BaseMapper)时抑制该类检查，与 Ultimate 的 Spring 插件原生
 * MyBatis 支持互补，Community 或未配置 Spring 模型的项目同样生效。
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class MapperSpringInjectionSuppressor implements InspectionSuppressor {

    /**
     * 抑制的 Spring 装配检查 shortName
     * <ul>
     * <li>SpringJavaInjectionPointsAutowiringInspection: "Could not autowire"(2020.3+)</li>
     * <li>SpringJavaAutowiringInspection: 同类检查的旧版本ID，双保险</li>
     * </ul>
     */
    private static final Set<String> SPRING_AUTOWIRING_INSPECTIONS = Set.of(
            "SpringJavaInjectionPointsAutowiringInspection",
            "SpringJavaAutowiringInspection");

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (!SPRING_AUTOWIRING_INSPECTIONS.contains(toolId)) {
            return false;
        }
        // 从问题元素向上定位注入点声明(字段/参数/方法)，判断其类型是否为 Mapper 接口
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            if (current instanceof PsiField field) {
                return this.isMapperType(field.getType());
            }
            if (current instanceof PsiParameter parameter) {
                return this.isMapperType(parameter.getType());
            }
            if (current instanceof PsiMethod method) {
                // Setter 注入: 单参数 setter 的参数类型
                PsiParameter[] parameters = method.getParameterList().getParameters();
                return parameters.length == 1 && this.isMapperType(parameters[0].getType());
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * 判断类型是否为 Mapper 接口
     *
     * @param type 注入点类型
     * @return true如果是Mapper接口
     * @since 1.2.0
     */
    private boolean isMapperType(@Nullable PsiType type) {
        if (type == null) {
            return false;
        }
        PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
        if (psiClass instanceof PsiTypeParameter) {
            return false;
        }
        return MyBatisUtils.isMapperInterface(psiClass);
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }
}
