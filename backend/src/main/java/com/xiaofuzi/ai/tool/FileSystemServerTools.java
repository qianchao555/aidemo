package com.xiaofuzi.ai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FileSystemServerTools {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemServerTools.class);

    private static final Path WORK_DIR = Paths.get(System.getProperty("user.dir"), "workspace");

    private Path resolvePath(String relativePath) {
        Path resolved = WORK_DIR.resolve(relativePath).normalize();
        if (!resolved.startsWith(WORK_DIR)) {
            throw new SecurityException("Path traversal denied: " + relativePath);
        }
        return resolved;
    }

    private void ensureWorkDir() throws IOException {
        if (!Files.exists(WORK_DIR)) {
            Files.createDirectories(WORK_DIR);
            logger.info("Workspace directory created: {}", WORK_DIR.toAbsolutePath());
        }
    }

    private void ensureParentDir(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    @Tool(name = "write_file", description = "将文本内容写入 workspace 下的文件。自动创建父目录，支持 txt/md/json/xml/csv/html 等格式。写入同名文件会覆盖旧内容")
    public Map<String, Object> writeFile(
            @ToolParam(description = "File path relative to workspace, e.g. 'notes/summary.md' or 'data.json'", required = false) String path,
            @ToolParam(description = "Text content to write", required = false) String content) {

        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("开始准备写入文件");
            ensureWorkDir();
            Path filePath = resolvePath(path);
            ensureParentDir(filePath);

            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            long fileSize = Files.size(filePath);
            result.put("success", true);
            result.put("path", path);
            result.put("absolutePath", filePath.toAbsolutePath().toString());
            result.put("size", fileSize);
            result.put("sizeHuman", formatSize(fileSize));
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            logger.info("File written: {} ({})", filePath.toAbsolutePath(), formatSize(fileSize));
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.warn("Security check failed: {}", e.getMessage());
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Write failed: " + e.getMessage());
            logger.error("Write failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Tool(name = "read_file", description = "Read the full content of a text file")
    public Map<String, Object> readFile(
            @ToolParam(description = "File path relative to workspace, e.g. 'notes/summary.md'", required = true) String path) {

        Map<String, Object> result = new HashMap<>();
        try {
            Path filePath = resolvePath(path);

            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "File not found: " + path);
                return result;
            }

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            long fileSize = Files.size(filePath);

            result.put("success", true);
            result.put("path", path);
            result.put("absolutePath", filePath.toAbsolutePath().toString());
            result.put("content", content);
            result.put("size", fileSize);
            result.put("sizeHuman", formatSize(fileSize));

            logger.info("File read: {}", filePath.toAbsolutePath());
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.warn("Security check failed: {}", e.getMessage());
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Read failed: " + e.getMessage());
            logger.error("Read failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Tool(name = "list_directory", description = "List files and directories under a given path in the workspace. Supports recursive listing up to a specified depth (depth: recursion depth, starting from 1)")
    public Map<String, Object> listDirectory(
            @ToolParam(description = "Directory path relative to workspace, use '.' or empty for root", required = false) String path,
            @ToolParam(description = "Recursion depth (1 = current dir only, 2 = one level down, etc.), default 1", required = false) Integer depth) {

        Map<String, Object> result = new HashMap<>();
        try {
            ensureWorkDir();

            String dirPath = (path == null || path.isBlank()) ? "" : path;
            Path targetDir = resolvePath(dirPath);

            if (!Files.exists(targetDir)) {
                result.put("success", false);
                result.put("error", "Directory not found: " + dirPath);
                return result;
            }
            if (!Files.isDirectory(targetDir)) {
                result.put("success", false);
                result.put("error", "Not a directory: " + dirPath);
                return result;
            }

            int maxDepth = (depth != null && depth > 0) ? depth : 1;

            var entries = Files.walk(targetDir, maxDepth)
                    .filter(p -> !p.equals(targetDir))
                    .map(p -> {
                        Map<String, Object> entry = new HashMap<>();
                        String relative = WORK_DIR.relativize(p).toString().replace("\\", "/");
                        entry.put("path", relative);
                        entry.put("type", Files.isDirectory(p) ? "directory" : "file");
                        try {
                            if (Files.isRegularFile(p)) {
                                long size = Files.size(p);
                                entry.put("size", size);
                                entry.put("sizeHuman", formatSize(size));
                            }
                            entry.put("lastModified", Files.getLastModifiedTime(p).toString());
                        } catch (IOException ignored) {
                        }
                        return entry;
                    })
                    .collect(Collectors.toList());

            result.put("success", true);
            result.put("directory", dirPath.isBlank() ? "/" : dirPath);
            result.put("absolutePath", targetDir.toAbsolutePath().toString());
            result.put("count", entries.size());
            result.put("entries", entries);

            logger.info("Directory listed: {} ({} entries, depth={})", targetDir.toAbsolutePath(), entries.size(), maxDepth);
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.warn("Security check failed: {}", e.getMessage());
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "List failed: " + e.getMessage());
            logger.error("List failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Tool(name = "delete_file", description = "Delete a file from the workspace")
    public Map<String, Object> deleteFile(
            @ToolParam(description = "File path relative to workspace", required = true) String path) {

        Map<String, Object> result = new HashMap<>();
        try {
            Path filePath = resolvePath(path);

            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "File not found: " + path);
                return result;
            }
            if (Files.isDirectory(filePath)) {
                result.put("success", false);
                result.put("error", "Path is a directory, not a file: " + path);
                return result;
            }

            Files.delete(filePath);
            result.put("success", true);
            result.put("path", path);
            result.put("message", "File deleted: " + path);

            logger.info("File deleted: {}", filePath.toAbsolutePath());
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.warn("Security check failed: {}", e.getMessage());
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Delete failed: " + e.getMessage());
            logger.error("Delete failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Tool(name = "create_directory", description = "Create a directory (and any missing parent directories) in the workspace")
    public Map<String, Object> createDirectory(
            @ToolParam(description = "Directory path relative to workspace, e.g. 'notes/2026' or 'output/reports'", required = true) String path) {

        Map<String, Object> result = new HashMap<>();
        try {
            ensureWorkDir();
            Path dirPath = resolvePath(path);

            if (Files.exists(dirPath)) {
                result.put("success", true);
                result.put("path", path);
                result.put("absolutePath", dirPath.toAbsolutePath().toString());
                result.put("existed", true);
                result.put("message", "Directory already exists: " + path);
                return result;
            }

            Files.createDirectories(dirPath);
            result.put("success", true);
            result.put("path", path);
            result.put("absolutePath", dirPath.toAbsolutePath().toString());
            result.put("existed", false);
            result.put("message", "Directory created: " + path);

            logger.info("Directory created: {}", dirPath.toAbsolutePath());
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.warn("Security check failed: {}", e.getMessage());
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Create directory failed: " + e.getMessage());
            logger.error("Create directory failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Tool(name = "append_file", description = "Append text content to the end of an existing file")
    public Map<String, Object> appendFile(
            @ToolParam(description = "File path relative to workspace", required = true) String path,
            @ToolParam(description = "Text content to append", required = true) String content) {

        Map<String, Object> result = new HashMap<>();
        try {
            Path filePath = resolvePath(path);

            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "File not found: " + path + ". Use write_file to create it first.");
                return result;
            }

            Files.writeString(filePath, "\n" + content, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);

            long fileSize = Files.size(filePath);
            result.put("success", true);
            result.put("path", path);
            result.put("absolutePath", filePath.toAbsolutePath().toString());
            result.put("size", fileSize);
            result.put("sizeHuman", formatSize(fileSize));

            logger.info("Content appended to: {}", filePath.toAbsolutePath());
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.warn("Security check failed: {}", e.getMessage());
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Append failed: " + e.getMessage());
            logger.error("Append failed: {}", e.getMessage(), e);
        }
        return result;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}