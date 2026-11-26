/*
 * Copyright (c) 2026 Eclipse contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.platform.pruner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.tycho.core.BundleProject;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.osgitools.project.BuildOutputJar;
import org.eclipse.tycho.core.osgitools.project.EclipsePluginProject;

/**
 * Prunes declarations not reachable from Java sources outside the selected folder.
 */
@Mojo(name = "prune", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class PruneMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component(role = TychoProject.class, hint = "eclipse-plugin")
    private TychoProject tychoProject;

    /** Folder to modify, relative to the project directory. */
    @Parameter(property = "pruner.sourceFolder", required = true)
    private File sourceFolder;

    /** Report proposed changes without modifying source files. */
    @Parameter(property = "pruner.dryRun", defaultValue = "true")
    private boolean dryRun;

    /** Fully qualified types whose complete declarations must be retained. */
    @Parameter
    private List<String> keepTypes = new ArrayList<>();

    /** Java language level; defaults to Tycho's javacSource or the running JDK. */
    @Parameter(property = "pruner.sourceLevel")
    private String sourceLevel;

    /** Source encoding used for parsing and rewriting. */
    @Parameter(property = "pruner.encoding", defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!"eclipse-plugin".equals(project.getPackaging())) {
            throw new MojoFailureException("The prune goal requires eclipse-plugin packaging.");
        }
        if (!(tychoProject instanceof BundleProject bundleProject)) {
            throw new MojoExecutionException("Tycho's eclipse-plugin project component is unavailable.");
        }
        try {
            var reactorProject = DefaultReactorProject.adapt(project);
            EclipsePluginProject pluginProject = bundleProject.getEclipsePluginProject(reactorProject);
            List<Path> roots = pluginProject.getOutputJars().stream()
                    .flatMap(jar -> jar.getSourceFolders().stream()).map(File::toPath).distinct().toList();
            // Excluded files need their own root policy; do not accidentally treat them as plugin code.
            if (!pluginProject.getBuildProperties().getSourceExcludes().isEmpty()
                    || pluginProject.getOutputJars().stream().anyMatch(jar -> !jar.getFilesToExclude().isEmpty())) {
                throw new MojoFailureException("Source exclusions are not supported; refusing to prune.");
            }
            List<Path> outputs = pluginProject.getOutputJars().stream()
                    .map(BuildOutputJar::getOutputDirectory).map(File::toPath)
                    .map(path -> path.toAbsolutePath().normalize()).toList();
            List<Path> classpath = bundleProject.getClasspath(reactorProject).stream()
                    .filter(entry -> entry.getMavenProject() == null
                            || !entry.getMavenProject().sameProject(reactorProject))
                    .flatMap(entry -> entry.getLocations().stream()).map(File::toPath)
                    .filter(path -> !outputs.contains(path.toAbsolutePath().normalize())).distinct().toList();
            String level = sourceLevel != null ? sourceLevel : pluginProject.getBuildProperties().getJavacSource();
            if (level == null || level.isBlank()) {
                level = Integer.toString(Runtime.version().feature());
            }
            Charset charset = encoding == null || encoding.isBlank()
                    ? java.nio.charset.StandardCharsets.UTF_8 : Charset.forName(encoding);
            Path base = project.getBasedir().toPath().toRealPath();
            Path target = sourceFolder.isAbsolute() ? sourceFolder.toPath() : base.resolve(sourceFolder.toPath());
            target = target.toRealPath();
            if (!target.startsWith(base) || target.equals(base)) {
                throw new MojoFailureException("sourceFolder must be a source directory inside the plugin project.");
            }
            getLog().warn("Only Java references are considered. Manifest, extension, service and reflection "
                    + "entry points must be listed in keepTypes.");
            SourcePruner pruner = new SourcePruner(roots, target, classpath, charset, level, keepTypes);
            SourcePruner.Plan plan = pruner.plan();
            for (String declaration : plan.removedDeclarations()) {
                getLog().info((dryRun ? "Would remove " : "Removing ") + declaration);
            }
            if (!dryRun) {
                plan.apply();
            }
            getLog().info((dryRun ? "Dry run: " : "Pruned: ") + plan.removedDeclarations().size()
                    + " declarations in " + plan.changes().size() + " files.");
        } catch (IOException | IllegalArgumentException e) {
            throw new MojoExecutionException("Cannot prune plugin sources: " + e.getMessage(), e);
        }
    }
}
