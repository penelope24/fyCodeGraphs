package fy.cfg.entry;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import fy.FileUtils.DirTraveler;
import fy.cfg.parse.TypeSolver;
import fy.cfg.visitor.VarVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class SolverEntry {

    public static HashMap<String, Set<String>> solve_pkg2types(List<String> projects) {
        List<String> allJavaFiles = new ArrayList<>();
        projects.forEach(project -> {
            // find java files
            new DirTraveler((level, path, file) -> path.endsWith(".java"), (level, path, file) -> {
                allJavaFiles.add(file.getAbsolutePath());
            }).explore(new File(project));
        });
        TypeSolver typeSolver = new TypeSolver();
        typeSolver.collect(allJavaFiles);
        return typeSolver.getPackage2types();
    }

    public static HashMap<String, Set<String>> solve_pkg2types(String project) {
        List<String> allJavaFiles = new ArrayList<>();
        // find java files
        new DirTraveler((level, path, file) -> path.endsWith(".java"), (level, path, file) -> {
            allJavaFiles.add(file.getAbsolutePath());
        }).explore(new File(project));
        TypeSolver typeSolver = new TypeSolver();
        typeSolver.collect(allJavaFiles);
        return typeSolver.getPackage2types();
    }

    public static VarVisitor solveVarTypesInFile(String javaFile, HashMap<String, Set<String>> pkg2types)
            throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(javaFile));
        VarVisitor varVisitor = new VarVisitor(pkg2types, cu);
        varVisitor.analyseFieldTypes();
        return varVisitor;
    }

}
