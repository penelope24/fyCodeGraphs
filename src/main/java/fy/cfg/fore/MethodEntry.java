package fy.cfg.fore;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import fy.FileUtils.DirTraveler;
import fy.cfg.parse.TypeSolver;
import fy.cfg.visitor.GlobalVarVisitor;
import fy.cfg.visitor.MethodVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MethodEntry {

    public static void gen(String project, MethodDeclaration target, String output) throws FileNotFoundException {
        List<String> allJavaFiles = new ArrayList<>();
        List<String> allJars = new ArrayList<>();

        new DirTraveler((level, path, file) -> path.endsWith(".java"), (level, path, file) -> {
            allJavaFiles.add(file.getAbsolutePath());
        }).explore(new File(project));

        new DirTraveler((level, path, file) -> path.endsWith(".jar"), (level, path, file) -> {
            allJars.add(file.getAbsolutePath());
        }).explore(new File(project));

        TypeSolver typeSolver = new TypeSolver();
        typeSolver.collect(allJavaFiles);

        Node root = target.findRootNode();
        boolean is_cu = root.getClass().equals(CompilationUnit.class);
        CompilationUnit cu = is_cu ? (CompilationUnit) root : null;
        assert is_cu;
        GlobalVarVisitor globalVarVisitor = new GlobalVarVisitor(typeSolver.getPackage2types(), cu);
        globalVarVisitor.analyseFieldTypes();

        File outputDir = new File(output);
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }

        Properties graphProps = Config.loadProperties();

        // core
        MethodVisitor methodVisitor = new MethodVisitor(globalVarVisitor.getCurrentPackageAllTypes(),
                globalVarVisitor.getAllImports(), globalVarVisitor.getAllFields(), outputDir.getAbsolutePath());
        assert graphProps != null;
        methodVisitor.visit(target, graphProps);
    }
}
