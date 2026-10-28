package io.github.easy.tools.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import io.github.easy.tools.service.mybatis.MapperXmlCacheService;
import io.github.easy.tools.utils.MyBatisUtils;
import io.github.easy.tools.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 迁移注解 SQL 到 XML 意图操作
 * <p>
 * 将 Mapper 方法上的 @Select/@Insert/@Update/@Delete 注解 SQL 迁移为
 * 对应 Mapper XML 中的语句标签:
 * <ul>
 * <li>支持 &lt;script&gt; 动态 SQL(标签体原样迁移)与普通文本 SQL(含特殊字符时 CDATA 包裹)</li>
 * <li>select 自动带出 resultType(List/IPage 泛型解包，数值类型装箱)</li>
 * <li>Mapper XML 不存在时在模块资源根目录(回退接口所在目录)创建同名 XML</li>
 * <li>迁移成功后删除原注解</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 1.2.0
 * @date 2026-10-28
 * @since 1.2.0
 */
public class MigrateSqlAnnotationIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    /**
     * 注解SQL: 注解简单名 -> 语句标签名
     */
    private static final Map<String, String> SQL_ANNOTATION_TAGS = Map.of(
            "Select", "select",
            "Insert", "insert",
            "Update", "update",
            "Delete", "delete");

    /**
     * MyBatis SQL 注解包前缀
     */
    private static final String SQL_ANNOTATION_PACKAGE = "org.apache.ibatis.annotations.";

    /**
     * 数值/基础类型到装箱类型的映射(resultType 使用)
     */
    private static final Map<String, String> PRIMITIVE_WRAPPERS = Map.of(
            "int", "java.lang.Integer", "long", "java.lang.Long",
            "short", "java.lang.Short", "byte", "java.lang.Byte",
            "double", "java.lang.Double", "float", "java.lang.Float",
            "boolean", "java.lang.Boolean", "char", "java.lang.Character");

    /**
     * 返回集合解包时按单行类型处理的集合接口
     */
    private static final Set<String> COLLECTION_TYPES = Set.of(
            "java.util.List", "java.util.Collection", "java.lang.Iterable");

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiMethod method = this.findMethod(element);
        if (method == null) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (!MyBatisUtils.isMapperInterface(containingClass)) {
            return false;
        }
        return this.findSqlAnnotation(method) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiMethod method = this.findMethod(element);
        PsiAnnotation annotation = method == null ? null : this.findSqlAnnotation(method);
        if (method == null || annotation == null) {
            return;
        }
        PsiClass mapperClass = method.getContainingClass();
        String qualifiedName = mapperClass == null ? null : mapperClass.getQualifiedName();
        if (qualifiedName == null) {
            NotificationUtil.showWarning(project, "无法确定Mapper接口的完全限定名");
            return;
        }
        String sql = this.extractSqlText(annotation);
        if (StringUtil.isEmpty(sql)) {
            NotificationUtil.showWarning(project, "无法解析注解中的SQL文本(仅支持常量表达式)");
            return;
        }

        String tagName = SQL_ANNOTATION_TAGS.get(this.annotationSimpleName(annotation));
        if (tagName == null) {
            return;
        }
        String annotationName = this.annotationSimpleName(annotation);

        WriteCommandAction.runWriteCommandAction(project, "迁移注解SQL到XML", null, () -> {
            XmlFile xmlFile = this.findOrCreateMapperXml(project, mapperClass, qualifiedName);
            if (xmlFile == null) {
                NotificationUtil.showError(project, "未找到且无法创建Mapper XML文件");
                return;
            }
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                NotificationUtil.showError(project, "Mapper XML 文件结构异常");
                return;
            }
            if (MyBatisUtils.findSqlTagByMethodName(xmlFile, method.getName()) != null) {
                NotificationUtil.showWarning(project, "XML中已存在 id='" + method.getName() + "' 的语句");
                return;
            }

            XmlTag statementTag = XmlElementFactory.getInstance(project)
                    .createTagFromText(this.buildStatementXml(tagName, method, sql));
            rootTag.addSubTag(statementTag, false);
            annotation.delete();
            NotificationUtil.showInfo(project, "已迁移 @" + annotationName
                    + " 到 " + xmlFile.getName() + " 的 <" + tagName + "> 标签");
        });
    }

    /**
     * 查找光标所在的方法
     *
     * @param element PSI元素
     * @return 方法，未找到返回null
     * @since 1.2.0
     */
    private PsiMethod findMethod(@NotNull PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }

    /**
     * 查找方法上的注解SQL注解(@Select/@Insert/@Update/@Delete)
     *
     * @param method 方法
     * @return 注解，未找到返回null
     * @since 1.2.0
     */
    private @Nullable PsiAnnotation findSqlAnnotation(@NotNull PsiMethod method) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String name = this.annotationSimpleName(annotation);
            if (name != null && SQL_ANNOTATION_TAGS.containsKey(name)
                    && annotation.getQualifiedName() != null
                    && annotation.getQualifiedName().startsWith(SQL_ANNOTATION_PACKAGE)) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * 获取注解简单名
     *
     * @param annotation 注解
     * @return 简单名(如 Select)，无法解析返回null
     * @since 1.2.0
     */
    private @Nullable String annotationSimpleName(@NotNull PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    }

    /**
     * 提取注解 SQL 文本
     * <p>支持字符串常量、字符串拼接与字符串数组({@code @Select({"a","b"})})。</p>
     *
     * @param annotation SQL注解
     * @return SQL文本，无法解析返回null
     * @since 1.2.0
     */
    private @Nullable String extractSqlText(@NotNull PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            return null;
        }
        // 常量求值覆盖字符串与拼接表达式
        Object constant = JavaPsiFacade.getInstance(annotation.getProject())
                .getConstantEvaluationHelper().computeConstantExpression(value);
        if (constant instanceof String text) {
            return text;
        }
        // 字符串数组逐项拼接
        if (value instanceof PsiArrayInitializerMemberValue array) {
            List<String> parts = new ArrayList<>();
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                if (initializer instanceof PsiLiteralExpression literal
                        && literal.getValue() instanceof String part) {
                    parts.add(part);
                }
            }
            return parts.isEmpty() ? null : String.join("\n", parts);
        }
        return null;
    }

    /**
     * 构建语句标签XML文本
     *
     * @param tagName 语句标签名(select/insert/update/delete)
     * @param method  Mapper方法
     * @param sql     注解SQL文本
     * @return 标签XML文本
     * @since 1.2.0
     */
    private @NotNull String buildStatementXml(@NotNull String tagName, @NotNull PsiMethod method, @NotNull String sql) {
        StringBuilder attributes = new StringBuilder("id=\"").append(method.getName()).append("\"");
        if ("select".equals(tagName)) {
            String resultType = this.resolveResultType(method);
            if (resultType != null) {
                attributes.append(" resultType=\"").append(resultType).append("\"");
            }
        }
        return "<" + tagName + " " + attributes + ">\n" + this.asXmlBody(sql) + "\n</" + tagName + ">";
    }

    /**
     * 解析 select 语句的 resultType
     * <p>List/IPage 泛型解包为单行类型，装箱基本类型，无法确定返回null。</p>
     *
     * @param method Mapper方法
     * @return resultType全限定名，未确定返回null
     * @since 1.2.0
     */
    private @Nullable String resolveResultType(@NotNull PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null || PsiType.VOID.equals(returnType)) {
            return null;
        }
        PsiType rowType = returnType;
        if (returnType instanceof PsiClassType classType) {
            PsiClass rawClass = classType.resolve();
            String fqn = rawClass == null ? null : rawClass.getQualifiedName();
            if (fqn != null && COLLECTION_TYPES.contains(fqn) && classType.getParameters().length == 1) {
                rowType = classType.getParameters()[0];
            }
        }
        // 泛型类型参数/带泛型的未知类型不生成 resultType
        if (rowType instanceof PsiClassType classType
                && classType.resolve() instanceof PsiTypeParameter) {
            return null;
        }
        String canonical = rowType.getCanonicalText();
        if (canonical.contains("<") || canonical.contains("?")) {
            return null;
        }
        return PRIMITIVE_WRAPPERS.getOrDefault(canonical, canonical);
    }

    /**
     * SQL 文本转 XML 标签体
     * <p>&lt;script&gt; 动态 SQL 保留内部标签；普通 SQL 含特殊字符时 CDATA 包裹。</p>
     *
     * @param sql SQL文本
     * @return XML标签体
     * @since 1.2.0
     */
    private @NotNull String asXmlBody(@NotNull String sql) {
        String trimmed = sql.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("<script")) {
            int contentStart = trimmed.indexOf('>');
            int contentEnd = lower.lastIndexOf("</script>");
            if (contentStart >= 0 && contentEnd > contentStart) {
                return trimmed.substring(contentStart + 1, contentEnd).trim();
            }
        }
        if (trimmed.contains("<") || trimmed.contains("&")) {
            if (!trimmed.contains("]]>")) {
                return "<![CDATA[" + trimmed + "]]>";
            }
            return trimmed.replace("&", "&amp;").replace("<", "&lt;");
        }
        return trimmed;
    }

    /**
     * 查找Mapper XML，不存在时创建
     *
     * @param project      项目
     * @param mapperClass  Mapper接口
     * @param qualifiedName 接口完全限定名
     * @return Mapper XML文件，失败返回null
     * @since 1.2.0
     */
    private @Nullable XmlFile findOrCreateMapperXml(@NotNull Project project,
                                                    @NotNull PsiClass mapperClass,
                                                    @NotNull String qualifiedName) {
        XmlFile existing = MyBatisUtils.findMapperXmlFile(project, qualifiedName);
        if (existing != null) {
            return existing;
        }

        try {
            VirtualFile targetRoot = this.findResourceRoot(project, mapperClass);
            if (targetRoot == null) {
                return null;
            }
            String relativePath = qualifiedName.replace('.', '/') + ".xml";
            int lastSlash = relativePath.lastIndexOf('/');
            VirtualFile directory = lastSlash <= 0
                    ? targetRoot
                    : VfsUtil.createDirectoryIfMissing(targetRoot, relativePath.substring(0, lastSlash));
            if (directory == null) {
                return null;
            }
            String fileName = relativePath.substring(lastSlash + 1);
            VirtualFile xmlVirtualFile = directory.findOrCreateChildData(project, fileName);
            String initialContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
                    + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
                    + "<mapper namespace=\"" + qualifiedName + "\">\n</mapper>\n";
            VfsUtil.saveText(xmlVirtualFile, initialContent);

            XmlFile xmlFile = (XmlFile) PsiManager.getInstance(project).findFile(xmlVirtualFile);
            if (xmlFile != null) {
                MapperXmlCacheService.getInstance(project).addToCache(qualifiedName, xmlFile);
            }
            return xmlFile;
        } catch (Exception ex) {
            NotificationUtil.showError(project, "创建Mapper XML失败: " + ex.getMessage());
            return null;
        }
    }

    /**
     * 查找用于放置 Mapper XML 的资源根目录
     * <p>优先模块下名为 resources 的源根，其次任一源根，回退接口文件所在目录。</p>
     *
     * @param project     项目
     * @param mapperClass Mapper接口
     * @return 目标目录，未找到返回null
     * @since 1.2.0
     */
    private @Nullable VirtualFile findResourceRoot(@NotNull Project project, @NotNull PsiClass mapperClass) {
        VirtualFile mapperFile = mapperClass.getContainingFile().getVirtualFile();
        Module module = ModuleUtilCore.findModuleForPsiElement(mapperClass);
        if (module != null) {
            VirtualFile fallback = null;
            for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
                for (SourceFolder folder : entry.getSourceFolders()) {
                    VirtualFile file = folder.getFile();
                    if (file == null) {
                        continue;
                    }
                    if ("resources".equals(file.getName())) {
                        return file;
                    }
                    if (fallback == null) {
                        fallback = file;
                    }
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        return mapperFile == null ? null : mapperFile.getParent();
    }

    @Override
    public @NotNull String getFamilyName() {
        return "MyBatis注解SQL迁移";
    }

    @Override
    public @NotNull String getText() {
        return "迁移注解SQL到Mapper XML";
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
