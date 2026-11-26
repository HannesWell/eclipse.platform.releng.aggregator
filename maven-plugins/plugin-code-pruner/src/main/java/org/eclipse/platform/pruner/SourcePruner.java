/*
 * Copyright (c) 2026 Eclipse contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.platform.pruner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.jface.text.BadLocationException;

final class SourcePruner {
    record Change(String original, String replacement) {
    }

    record Plan(Map<Path, Change> changes, List<String> removedDeclarations, Charset encoding) {
        void apply() throws IOException {
            // Check the entire plan before writing, not just each individual file.
            for (var entry : changes.entrySet()) {
                if (!Files.readString(entry.getKey(), encoding).equals(entry.getValue().original())) {
                    throw new IOException("Source changed since analysis: " + entry.getKey());
                }
            }
            for (var entry : changes.entrySet()) {
                if (entry.getValue().replacement() == null) {
                    Files.delete(entry.getKey());
                } else {
                    Files.writeString(entry.getKey(), entry.getValue().replacement(), encoding);
                }
            }
        }
    }

    private static final class Declaration {
        final ASTNode node;
        final IBinding binding;
        final Path file;
        final Set<Declaration> dependencies = new LinkedHashSet<>();

        Declaration(ASTNode node, IBinding binding, Path file) {
            this.node = node;
            this.binding = binding;
            this.file = file;
        }

        String name() {
            if (binding instanceof ITypeBinding type) {
                return type.getQualifiedName();
            }
            if (binding instanceof IMethodBinding method) {
                return method.getDeclaringClass().getQualifiedName() + "#" + method.getName() + "("
                        + String.join(", ", Arrays.stream(method.getParameterTypes())
                                .map(ITypeBinding::getQualifiedName).toList()) + ")";
            }
            IVariableBinding field = (IVariableBinding) binding;
            return field.getDeclaringClass().getQualifiedName() + "#" + field.getName();
        }
    }

    private final List<Path> roots;
    private final Path target;
    private final List<Path> classpath;
    private final Charset encoding;
    private final Map<String, String> options;
    private final Set<String> keepTypes;
    private final Map<Path, String> sources = new LinkedHashMap<>();
    private final Map<Path, CompilationUnit> units = new LinkedHashMap<>();
    private final Map<ASTNode, Declaration> declarations = new IdentityHashMap<>();
    private final Map<String, Declaration> bindings = new LinkedHashMap<>();

    SourcePruner(List<Path> roots, Path target, List<Path> classpath, Charset encoding,
            String sourceLevel, List<String> keepTypes) throws IOException {
        this.roots = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isSymbolicLink(root)) {
                throw new IOException("Symbolic source roots are not supported: " + root);
            }
            if (!Files.isDirectory(root)) {
                throw new IOException("Source root does not exist: " + root);
            }
            Path real = root.toRealPath();
            if (!this.roots.contains(real)) {
                this.roots.add(real);
            }
        }
        for (Path root : this.roots) {
            if (this.roots.stream().anyMatch(other -> !other.equals(root) && root.startsWith(other))) {
                throw new IOException("Overlapping source roots are not supported: " + root);
            }
        }
        this.target = target.toRealPath();
        if (this.roots.stream().noneMatch(this.target::startsWith)) {
            throw new IOException("Selected folder is not within a Tycho source root: " + target);
        }
        this.classpath = List.copyOf(classpath);
        this.encoding = encoding;
        this.keepTypes = Set.copyOf(keepTypes);
        options = new HashMap<>();
        if (!JavaCore.getAllVersions().contains(sourceLevel)) {
            throw new IllegalArgumentException("JDT does not support Java source level " + sourceLevel
                    + "; configure pruner.sourceLevel explicitly.");
        }
        JavaCore.setComplianceOptions(sourceLevel, options);
        options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.DISABLED);
    }

    Plan plan() throws IOException {
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted().toList()) {
                    if (Files.isSymbolicLink(file)) {
                        throw new IOException("Symbolic links in source roots are not supported: " + file);
                    }
                    if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                        sources.put(file, Files.readString(file, encoding));
                    }
                }
            }
        }
        parse(roots, sources.keySet().stream().toList(), units::put);
        indexDeclarations();
        Set<Declaration> live = reachableDeclarations();
        Map<Path, Change> changes = new LinkedHashMap<>();
        List<String> removed = new ArrayList<>();
        for (var entry : units.entrySet()) {
            Path file = entry.getKey();
            if (!file.startsWith(target)) {
                continue;
            }
            CompilationUnit unit = entry.getValue();
            ASTRewrite rewrite = ASTRewrite.create(unit.getAST());
            Set<ASTNode> dead = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            declarations.values().stream().filter(d -> d.file.equals(file) && !live.contains(d))
                    .forEach(d -> dead.add(d.node));
            for (Declaration declaration : bindings.values()) {
                if (dead.contains(declaration.node)) {
                    removed.add(declaration.name());
                    if (!hasDeadAncestor(declaration.node, dead)) {
                        remove(rewrite, declaration.node, dead);
                    }
                }
            }
            boolean removedImport = false;
            for (Object value : unit.imports()) {
                ImportDeclaration imported = (ImportDeclaration) value;
                Declaration declaration = find(imported.resolveBinding());
                if (declaration != null && !live.contains(declaration)
                        && !hasLiveOverload(imported.resolveBinding(), live)) {
                    rewrite.remove(imported, null);
                    removedImport = true;
                }
            }
            if (dead.isEmpty() && !removedImport) {
                continue;
            }
            boolean hasTypes = unit.types().stream().anyMatch(type -> !dead.contains(type));
            String replacement = null;
            if (hasTypes || unit.types().isEmpty()) {
                Document document = new Document(sources.get(file));
                try {
                    rewrite.rewriteAST(document, options).apply(document);
                } catch (MalformedTreeException | BadLocationException e) {
                    throw new IOException("Cannot rewrite " + file, e);
                }
                replacement = document.get();
            }
            changes.put(file, new Change(sources.get(file), replacement));
        }
        validate(changes);
        return new Plan(java.util.Collections.unmodifiableMap(changes), removed.stream().sorted().toList(), encoding);
    }

    private void parse(List<Path> sourceRoots, List<Path> files,
            java.util.function.BiConsumer<Path, CompilationUnit> consumer) throws IOException {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(false);
        parser.setStatementsRecovery(false);
        parser.setCompilerOptions(options);
        parser.setEnvironment(classpath.stream().map(Path::toString).toArray(String[]::new),
                sourceRoots.stream().map(Path::toString).toArray(String[]::new),
                sourceRoots.stream().map(root -> encoding.name()).toArray(String[]::new), true);
        List<String> errors = new ArrayList<>();
        parser.createASTs(files.stream().map(Path::toString).toArray(String[]::new),
                files.stream().map(file -> encoding.name()).toArray(String[]::new),
                new String[0], new FileASTRequestor() {
                    @Override
                    public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                        for (IProblem problem : ast.getProblems()) {
                            if (problem.isError()) {
                                errors.add(sourceFilePath + ":" + problem.getSourceLineNumber() + ": "
                                        + problem.getMessage());
                            }
                        }
                        consumer.accept(Path.of(sourceFilePath), ast);
                    }
                }, null);
        if (!errors.isEmpty()) {
            throw new IOException("Source analysis failed; no sources were modified:\n" + String.join("\n", errors));
        }
    }

    private void indexDeclarations() throws IOException {
        for (var entry : units.entrySet()) {
            entry.getValue().accept(new ASTVisitor() {
                @Override
                public boolean preVisit2(ASTNode node) {
                    if (node instanceof AbstractTypeDeclaration type && isMember(type)) {
                        add(type, type.resolveBinding(), entry.getKey());
                    } else if (node instanceof MethodDeclaration method && isMember(method)) {
                        add(method, method.resolveBinding(), entry.getKey());
                    } else if (node instanceof AnnotationTypeMemberDeclaration member) {
                        add(member, member.resolveBinding(), entry.getKey());
                    } else if (node instanceof VariableDeclarationFragment field
                            && field.getParent() instanceof FieldDeclaration && isMember(field.getParent())) {
                        add(field, field.resolveBinding(), entry.getKey());
                    } else if (node instanceof EnumConstantDeclaration constant) {
                        add(constant, constant.resolveVariable(), entry.getKey());
                    }
                    return true;
                }
            });
        }
        if (declarations.values().stream().anyMatch(d -> d.binding == null || d.binding.isRecovered())) {
            throw new IOException("Unresolved declaration bindings; no sources were modified.");
        }
        Set<String> found = new HashSet<>();
        for (Declaration declaration : bindings.values()) {
            if (declaration.binding instanceof ITypeBinding type) {
                found.add(type.getQualifiedName());
            }
        }
        if (!found.containsAll(keepTypes)) {
            Set<String> missing = new LinkedHashSet<>(keepTypes);
            missing.removeAll(found);
            throw new IOException("keepTypes did not match plugin types: " + missing);
        }
    }

    private static boolean isMember(ASTNode node) {
        if (node.getParent() instanceof CompilationUnit) {
            return true;
        }
        return node.getParent() instanceof AbstractTypeDeclaration && isMember(node.getParent());
    }

    private void add(ASTNode node, IBinding binding, Path file) {
        Declaration declaration = new Declaration(node, binding, file);
        declarations.put(node, declaration);
        if (binding != null && !binding.isRecovered()) {
            bindings.put(key(binding), declaration);
        }
    }

    private Set<Declaration> reachableDeclarations() {
        Set<Declaration> seeds = new LinkedHashSet<>();
        for (Declaration declaration : bindings.values()) {
            Declaration owner = owner(declaration.node);
            if (owner != null) {
                declaration.dependencies.add(owner);
            }
            scan(declaration, declaration.node);
            if (declaration.node instanceof VariableDeclarationFragment field) {
                FieldDeclaration parent = (FieldDeclaration) field.getParent();
                scan(declaration, parent.getType());
                for (Object modifier : parent.modifiers()) {
                    scan(declaration, (ASTNode) modifier);
                }
                if (field.getInitializer() != null && owner != null
                        && field.getInitializer().resolveConstantExpressionValue() == null
                        && !(field.getInitializer() instanceof NullLiteral)) {
                    owner.dependencies.add(declaration);
                }
            }
            if (owner != null && (declaration.node instanceof EnumConstantDeclaration
                    || declaration.node instanceof AnnotationTypeMemberDeclaration)) {
                owner.dependencies.add(declaration);
            }
            if (declaration.node instanceof MethodDeclaration method && owner != null) {
                IMethodBinding binding = (IMethodBinding) declaration.binding;
                List<IMethodBinding> overridden = overriddenMethods(binding);
                overridden.stream().map(this::find).filter(java.util.Objects::nonNull)
                        .forEach(declaration.dependencies::add);
                if (method.isConstructor() || Modifier.isAbstract(binding.getModifiers()) || !overridden.isEmpty()) {
                    owner.dependencies.add(declaration);
                }
            }
            if (!declaration.file.startsWith(target) || isKept(declaration)) {
                seeds.add(declaration);
            }
        }
        // package-info annotations are executable metadata even though they do not declare a type.
        for (CompilationUnit unit : units.values()) {
            if (unit.types().isEmpty()) {
                unit.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(SimpleName name) {
                        Declaration dependency = find(name.resolveBinding());
                        if (dependency != null) {
                            seeds.add(dependency);
                        }
                        return true;
                    }
                });
            }
        }
        for (var entry : units.entrySet()) {
            if (!entry.getKey().startsWith(target)) {
                for (Object value : entry.getValue().imports()) {
                    Declaration dependency = find(((ImportDeclaration) value).resolveBinding());
                    if (dependency != null) {
                        seeds.add(dependency);
                    }
                }
            }
        }
        Set<Declaration> live = new LinkedHashSet<>();
        ArrayDeque<Declaration> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            Declaration next = queue.removeFirst();
            if (live.add(next)) {
                queue.addAll(next.dependencies);
            }
        }
        return live;
    }

    private boolean isKept(Declaration declaration) {
        for (Declaration current = declaration; current != null; current = owner(current.node)) {
            if (current.binding instanceof ITypeBinding type && keepTypes.contains(type.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private Declaration owner(ASTNode node) {
        for (ASTNode parent = node.getParent(); parent != null; parent = parent.getParent()) {
            Declaration declaration = declarations.get(parent);
            if (declaration != null) {
                return declaration;
            }
        }
        return null;
    }

    private void scan(Declaration declaration, ASTNode subtree) {
        subtree.accept(new ASTVisitor() {
            @Override
            public boolean preVisit2(ASTNode node) {
                return node == declaration.node
                        || (!declarations.containsKey(node)
                                && !(node instanceof FieldDeclaration && isMember(node)));
            }

            @Override
            public boolean visit(Javadoc node) {
                return false;
            }

            @Override
            public boolean visit(SimpleName node) {
                if (!node.isDeclaration()) {
                    reference(node.resolveBinding());
                }
                return true;
            }

            @Override
            public boolean visit(ClassInstanceCreation node) {
                reference(node.resolveConstructorBinding());
                return true;
            }

            @Override
            public boolean visit(ConstructorInvocation node) {
                reference(node.resolveConstructorBinding());
                return true;
            }

            @Override
            public boolean visit(SuperConstructorInvocation node) {
                reference(node.resolveConstructorBinding());
                return true;
            }

            @Override
            public boolean visit(CreationReference node) {
                reference(node.resolveMethodBinding());
                return true;
            }

            private void reference(IBinding binding) {
                Declaration dependency = find(binding);
                if (dependency != null) {
                    declaration.dependencies.add(dependency);
                }
            }
        });
    }

    private static List<IMethodBinding> overriddenMethods(IMethodBinding method) {
        List<IMethodBinding> result = new ArrayList<>();
        ITypeBinding type = method.getDeclaringClass();
        ArrayDeque<ITypeBinding> pending = new ArrayDeque<>();
        if (type.getSuperclass() != null) {
            pending.add(type.getSuperclass());
        }
        pending.addAll(List.of(type.getInterfaces()));
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            ITypeBinding parent = pending.removeFirst();
            if (!seen.add(parent.getKey())) {
                continue;
            }
            for (IMethodBinding inherited : parent.getDeclaredMethods()) {
                if (method.overrides(inherited)) {
                    result.add(inherited);
                }
            }
            if (parent.getSuperclass() != null) {
                pending.add(parent.getSuperclass());
            }
            pending.addAll(List.of(parent.getInterfaces()));
        }
        return result;
    }

    private boolean hasLiveOverload(IBinding binding, Set<Declaration> live) {
        if (!(binding instanceof IMethodBinding method)) {
            return false;
        }
        return live.stream().anyMatch(declaration -> declaration.binding instanceof IMethodBinding candidate
                && candidate.getName().equals(method.getName())
                && candidate.getDeclaringClass().isEqualTo(method.getDeclaringClass()));
    }

    private Declaration find(IBinding binding) {
        if (binding == null) {
            return null;
        }
        Declaration declaration = bindings.get(key(binding));
        if (declaration == null && binding instanceof IMethodBinding method) {
            // Implicit constructors and record accessors have no source declaration of their own.
            declaration = bindings.get(key(method.getDeclaringClass()));
        }
        return declaration;
    }

    private static String key(IBinding binding) {
        if (binding instanceof ITypeBinding type) {
            return type.getTypeDeclaration().getKey();
        }
        if (binding instanceof IMethodBinding method) {
            return method.getMethodDeclaration().getKey();
        }
        if (binding instanceof IVariableBinding field) {
            return field.getVariableDeclaration().getKey();
        }
        return binding.getKey();
    }

    private static boolean hasDeadAncestor(ASTNode node, Set<ASTNode> dead) {
        for (ASTNode parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (dead.contains(parent)) {
                return true;
            }
        }
        return false;
    }

    private static void remove(ASTRewrite rewrite, ASTNode node, Set<ASTNode> dead) {
        if (node instanceof VariableDeclarationFragment && node.getParent() instanceof FieldDeclaration field) {
            if (field.fragments().stream().allMatch(dead::contains)) {
                if (node == field.fragments().get(0)) {
                    rewrite.remove(field, null);
                }
            } else {
                rewrite.remove(node, null);
            }
        } else {
            rewrite.remove(node, null);
        }
    }

    private void validate(Map<Path, Change> changes) throws IOException {
        if (changes.isEmpty()) {
            return;
        }
        Path temporary = Files.createTempDirectory("plugin-code-pruner-");
        try {
            List<Path> stagedRoots = new ArrayList<>();
            List<Path> stagedFiles = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++) {
                Path root = roots.get(i);
                Path staged = Files.createDirectory(temporary.resolve(Integer.toString(i)));
                stagedRoots.add(staged);
                for (var source : sources.entrySet()) {
                    if (!source.getKey().startsWith(root)) {
                        continue;
                    }
                    Change change = changes.get(source.getKey());
                    String text = change == null ? source.getValue() : change.replacement();
                    if (text != null) {
                        Path file = staged.resolve(root.relativize(source.getKey()));
                        Files.createDirectories(file.getParent());
                        Files.writeString(file, text, encoding);
                        stagedFiles.add(file);
                    }
                }
            }
            parse(stagedRoots, stagedFiles, (file, ast) -> {});
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }
}
