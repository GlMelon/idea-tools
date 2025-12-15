package io.github.easy.tools.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.github.easy.tools.service.mybatis.MapperXmlCacheService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper Xml Startup Activity
 *
 * @author haijun
 * @date 2025-12-12 14:10:48
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class MapperXmlStartupActivity implements StartupActivity.DumbAware {

    /**
     * 项目启动后执行
     *
     * @param project 当前项目
     * @since 1.0.0
     */
    @Override
    public void runActivity(@NotNull Project project) {
        log.info("项目启动，开始扫描MyBatis Mapper XML文件: {}", project.getName());

        // 异步执行扫描操作，避免阻塞项目启动
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            MapperXmlCacheService cacheService = MapperXmlCacheService.getInstance();
            cacheService.scanAndCacheMapperXmlFiles(project);
        });

        // 注册XML文件变化监听器
        MapperXmlChangeListener changeListener = new MapperXmlChangeListener(project);
        project.getMessageBus()
                .connect(project)
                .subscribe(VirtualFileManager.VFS_CHANGES, changeListener);

        log.info("MyBatis Mapper XML文件监听器已注册: {}", project.getName());
    }
}
