package de.melb00m.tr4o.helper;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExclusionAwareFileCollector extends SimpleFileVisitor<Path> {

    private final Set<Path> exclusions;
    private final boolean includeDirectories;
    private final List<Path> collectedFiles = new ArrayList<>();

    public ExclusionAwareFileCollector(final Set<Path> exclusions, final boolean includeDirectories) {
        this.exclusions = exclusions;
        this.includeDirectories = includeDirectories;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (exclusions.contains(dir)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        if (includeDirectories) {
            collectedFiles.add(dir);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (!exclusions.contains(file)) {
            collectedFiles.add(file);
        }
        return FileVisitResult.CONTINUE;
    }

    public List<Path> getCollectedFiles() {
        return collectedFiles;
    }

}
