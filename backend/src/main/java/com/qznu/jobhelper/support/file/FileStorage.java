package com.qznu.jobhelper.support.file;

import com.qznu.jobhelper.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地简历文件存储。
 *
 * <p>数据库只保存相对路径，真实文件名使用 UUID，防止路径穿越和重名覆盖。</p>
 */
@Component
public class FileStorage {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private final Path root;

    public FileStorage(@Value("${file.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root);
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择简历文件");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw BizException.badRequest("简历文件不能超过10MB");
        }

        String original = file.getOriginalFilename() == null ? "resume.txt" : file.getOriginalFilename();
        String extension = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BizException.badRequest("仅支持 PDF、DOCX、TXT 简历");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw BizException.badRequest("非法文件路径");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("保存简历文件失败", exception);
        }
        return new StoredFile(storedName, original, file.getSize(), extension);
    }

    public Path resolve(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw BizException.badRequest("非法文件路径");
        }
        return target;
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception ignored) {
            // 文件删除失败不阻断数据库主事务；清理任务会继续补偿。
        }
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String relativePath, String originalName, long size, String extension) {
    }
}
