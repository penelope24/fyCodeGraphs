package fy.cfg.fore;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import fy.FileUtils.DirTraveler;
import fy.cfg.parse.TypeSolver;
import fy.cfg.visitor.GlobalVarVisitor;
import fy.cfg.visitor.MethodVisitor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

        Properties graphProps = Config.loadProperties();

        // core
        MethodVisitor methodVisitor = new MethodVisitor(globalVarVisitor.getCurrentPackageAllTypes(),
                globalVarVisitor.getAllImports(), globalVarVisitor.getAllFields(), outputDir.getAbsolutePath(), graphProps);
        methodVisitor.visit(cu, null);
    }

    public static void main(String[] args) throws FileNotFoundException {
        String project = "D:\\data\\fastjson";
        String target = "D:\\data\\fastjson\\src\\main\\java\\com\\alibaba\\fastjson\\parser\\ParserConfig.java";
        String output = "D:\\data\\test\\test";
        SingleFileEntry.gen(project, target, output);
    }
}
