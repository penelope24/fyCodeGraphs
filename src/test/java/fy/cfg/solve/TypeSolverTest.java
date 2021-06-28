package fy.cfg.solve;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import fy.FileUtils.DirTraveler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;


class TypeSolverTest {
    String project = "D:\\my_github_projects\\MyCodeDiffAnalysis";
    List<String> allJavaFiles = new ArrayList<>();
    String p = "D:\\my_github_projects\\MyCodeDiffAnalysis\\src\\test\\java\\fore.java";

    @BeforeEach
    void init() {
        new DirTraveler((level, path, file) -> path.endsWith(".java"), (level, path, file) -> {
            allJavaFiles.add(file.getAbsolutePath());
        }).explore(new File(project));
    }

    @Test
    void analyse() {
//        allJavaFiles.forEach(System.out::println);
        TypeSolver solver = new TypeSolver();
        solver.collect(allJavaFiles);
    }

    @Test
    void debug() throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(p));
        cu.getTypes().forEach(typeDeclaration -> {
            System.out.println(typeDeclaration.getNameAsString());
        });
        String pkgInfo = null;
        if (cu.findFirst(PackageDeclaration.class).isPresent()) {
            pkgInfo = cu.findFirst(PackageDeclaration.class).get().getNameAsString();
        }
        System.out.println(pkgInfo);
    }
}