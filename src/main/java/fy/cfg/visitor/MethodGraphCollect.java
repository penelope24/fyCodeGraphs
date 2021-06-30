package fy.cfg.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import fy.cfg.frontend.CFGPrint;
import fy.cfg.parse.ASTCreater;
import fy.cfg.parse.CFGCreator;
import fy.cfg.parse.CFGNodeSimplifier;
import fy.cfg.parse.DFGCreater;
import fy.structures.DFVarNode;
import fy.structures.GraphNode;
import org.jgrapht.graph.DirectedMultigraph;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class MethodGraphCollect extends VoidVisitorAdapter<DirectedMultigraph> {
    private final Set<String> package2types;
    private final List<String> imports;
    private final Set<DFVarNode> fields;
    private final String dot_file_path;
    private Properties prop;

    public MethodGraphCollect(VarVisitor varVisitor, String dot_file_path, Properties prop) {
        this.package2types = varVisitor.getCurrentPackageAllTypes();
        this.imports = varVisitor.getAllImports();
        this.fields = varVisitor.getAllFields();
        this.dot_file_path = dot_file_path;
        this.prop = prop;
    }

    @Override
    public void visit(MethodDeclaration n, DirectedMultigraph g) {
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
                        CFGPrint printer = new CFGPrint(dot_file_path, dfgCreater.getAllDFGEdgesList(), prop);
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
