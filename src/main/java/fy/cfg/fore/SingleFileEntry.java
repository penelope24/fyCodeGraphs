package fy.cfg.fore;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import fy.FileUtils.DirTraveler;
import fy.cfg.solve.TypeSolver;
import fy.cfg.visitor.GlobalVarVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class SingleFileEntry {

    public static void gen(String project, String target, String output) throws FileNotFoundException {
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

        CompilationUnit cu = StaticJavaParser.parse(new File(target));
        GlobalVarVisitor globalVarVisitor = new GlobalVarVisitor(typeSolver.getPackage2types(), cu);
        globalVarVisitor.analyseFieldTypes();

        File outputDir = new File(output);
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }

        // core
    }
}
