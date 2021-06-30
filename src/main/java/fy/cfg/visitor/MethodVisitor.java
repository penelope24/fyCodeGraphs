package fy.cfg.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import fy.cfg.parse.ASTCreater;
import fy.cfg.parse.CFGCreator;
import fy.cfg.parse.CFGNodeSimplifier;
import fy.cfg.parse.DFGCreater;
import fy.cfg.frontend.CodePropertyGraphPrinter;
import fy.cfg.structure.DFVarNode;
import fy.cfg.structure.GraphNode;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class MethodVisitor extends VoidVisitorAdapter<Properties> {
    private Set<String> package2types;
    private List<String> imports;
    private Set<DFVarNode> fields;
    private String dot_file_path;

    public MethodVisitor(Set<String> package2types, List<String> imports, Set<DFVarNode> fields, String dot_file_path) {
        this.package2types = package2types;
        this.imports = imports;
        this.fields = fields;
        this.dot_file_path = dot_file_path;
    }

    @Override
    public void visit(MethodDeclaration n, Properties prop) {
        // parse properties
        // node.cfg & edge.cfg are always true
        // edge.ncs is only used when printing
        boolean node_simplify = Boolean.parseBoolean(prop.getProperty("node.simplify"));
        boolean node_ast = Boolean.parseBoolean(prop.getProperty("node.ast"));
        boolean edge_dataflow = Boolean.parseBoolean(prop.getProperty("edge.dataflow"));


        // filter out constructors
        if (n.getType() != null) {
            if (n.getParentNode().isPresent()) {
                // filter out anonymous methods
                if (!(n.getParentNode().get() instanceof TypeDeclaration)) {
                    return;
                }
                System.out.println("parsing " + n.getNameAsString());
                // build cfg
                CFGCreator cfgCreator = new CFGCreator();
                List<GraphNode> graphNodes = cfgCreator.buildMethodCFG(n);
                if (node_ast) {
                    // build ast
                    ASTCreater astCreater = new ASTCreater(cfgCreator.getAllNodesMap());
                    astCreater.buildMethodAST(n);
                }
                DFGCreater dfgCreater = new DFGCreater(cfgCreator.getAllNodesMap());
                if (edge_dataflow) {
                    // analyse data flow
                    dfgCreater.buildMethodDFG(n);
                }
                if (node_simplify) {
                    // simplify node
                    CFGNodeSimplifier simplifier = new CFGNodeSimplifier(cfgCreator.getAllNodesMap(), package2types, imports, fields);
                    simplifier.simplifyCFGNodeStr(n);
                }

                for (GraphNode node : graphNodes) {
                    try {
                        CodePropertyGraphPrinter printer = new CodePropertyGraphPrinter(dot_file_path, dfgCreater.getAllDFGEdgesList(), prop);
                        printer.print(node, n.getNameAsString(), n.getParameters().size() +
                                UUID.randomUUID().toString().substring(0, 4));
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("输出方法文件失败："+n.getNameAsString());
                    }
                }
            }
        }
    }
}
