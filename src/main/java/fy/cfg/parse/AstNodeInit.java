package fy.cfg.parse;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.metamodel.NodeMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;
import fy.structures.AstNode;

import java.util.ArrayList;
import java.util.List;

import static com.github.javaparser.utils.Utils.assertNotNull;
import static java.util.stream.Collectors.toList;

/**
 * AstNode初始化，用来根据原有的node来初始化这个AstNode，然后我们修改Astnode来实现改造ast
 */
public class AstNodeInit {

    private final boolean outputNodeType;
    private AstNode astNode;

    public AstNodeInit(boolean outputNodeType, AstNode astNode) {
        this.outputNodeType = outputNodeType;
        this.astNode = astNode;
    }

    public void Init(Node node) {
        astNode.setRootPrimary(node);
        output(node, "root", astNode);
    }

    private void output(Node node, String name, AstNode astNode) {
        assertNotNull(node);
        NodeMetaModel metaModel = node.getMetaModel();
        List<PropertyMetaModel> allPropertyMetaModels = metaModel.getAllPropertyMetaModels();
        List<PropertyMetaModel> attributes = allPropertyMetaModels.stream().filter(PropertyMetaModel::isAttribute).filter(PropertyMetaModel::isSingular).collect(toList());
        List<PropertyMetaModel> subNodes = allPropertyMetaModels.stream().filter(PropertyMetaModel::isNode).filter(PropertyMetaModel::isSingular).collect(toList());
        List<PropertyMetaModel> subLists = allPropertyMetaModels.stream().filter(PropertyMetaModel::isNodeList).collect(toList());

        // ast 中只有这种加括号的才是一整段代码，所以cfg只能加他上面，叶子节点不可能加线
        if (outputNodeType) {
            astNode.setTypeName(name + " (" + metaModel.getTypeName() + ")"); // 节点赋值
        }
        else {
            astNode.setTypeName(name);
        }

        // 加入所有的叶子节点
        for (PropertyMetaModel a : attributes) {
            astNode.getAttributes().add(a.getName() + "='" + a.getValue(node).toString() + "'");
        }

        for (PropertyMetaModel sn : subNodes) {
            Node nd = (Node) sn.getValue(node);
            //也就是block模块的子节点都不要
            if (nd != null && !nd.toString().equals("") && !(nd instanceof BlockStmt) && !(nd instanceof CatchClause)) {
                AstNode subAstNode = new AstNode();
                astNode.getSubNodes().add(subAstNode); // 和当前的ast关联起来
                astNode.setName(sn.getName());
                astNode.getSubNodesPrimary().add(nd);
                output(nd, sn.getName(), subAstNode);
            }
        }

        for (PropertyMetaModel sl : subLists) {
            @SuppressWarnings("unchecked")
            NodeList<? extends Node> nl = (NodeList<? extends Node>) sl.getValue(node);
            if (nl != null && nl.isNonEmpty()) {
                astNode.getSubLists().add(sl.getName());
                String slName = sl.getName().substring(0, sl.getName().length() - 1);
                astNode.getSubLists_name().add(slName);
                List<AstNode> astNodes = new ArrayList<AstNode>();
                List<Node> primaryNodes = new ArrayList<Node>();
                astNode.getSubListNodes().add(astNodes);
                astNode.getSubListNodesPrimary().add(primaryNodes);
                for (Node nd : nl) {
                    if (!nd.toString().equals("") && !(nd instanceof BlockStmt) && !(nd instanceof CatchClause)) {
                        primaryNodes.add(nd);
                        AstNode subAstNode = new AstNode();
                        astNodes.add(subAstNode);
                        output(nd, slName, subAstNode);
                    }
                }
            }
        }
    }
}
