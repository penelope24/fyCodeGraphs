package fy.cfg.frontend;

import fy.structures.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.*;

public class CFGBuild {
    private int index = 0; //表示节点的编号开始值
    private List<String> leafNodes;
    private Set<GraphEdge> allDFGEdgesList;
    private Properties prop;
    private DirectedMultigraph<StmtVertex, StmtEdge> graph = new DirectedMultigraph(StmtEdge.class);

    public CFGBuild(Set<GraphEdge> allDFGEdgesList, Properties prop) {
        this.leafNodes = new ArrayList<>();
        this.allDFGEdgesList = allDFGEdgesList;
        this.prop = prop;
    }

    private void BFS(GraphNode root, boolean cfgFlag, boolean astFlag, boolean dfgFlag, boolean ncsFlag) {
        Queue<GraphNode> dealingNodes = new LinkedList<>();
        dealingNodes.add(root);
        while (!dealingNodes.isEmpty()) {
            GraphNode par = dealingNodes.poll();
            String parIndexNum = "";
            if (par.getDotNum() != null) {
                //已经创立
                parIndexNum = par.getDotNum();
            } else {
                parIndexNum = "n" + (index++);
                par.setDotNum(parIndexNum);
                String label = DotPrintFilter.filterQuotation(par.getOriginalCodeStr());
                int line = par.getCodeLineNum();
                NodeAttrs nodeAttrs = new NodeAttrs(label, line, parIndexNum);
                StmtVertex vertex = new StmtVertex(nodeAttrs);
                graph.addVertex(vertex);
                //创立cfg节点同时 创建ast节点
//                if(astFlag) {
//                    ASTRecurive(par.getAstRootNode(), par.getDotNum());
//                }
            }
            //然后就是添加子节点
            List<GraphNode> adjacentPoints = par.getAdjacentPoints();
            //先把没有在dot文件中建立点的点都建立好，才能把边连起来！
            for (GraphNode child : adjacentPoints) {
                if (child.getDotNum() == null) {
                    //没有处理过，就需要入队列
                    dealingNodes.add(child);
                    child.setDotNum("n" + (index));
                    index++;
                    String label = DotPrintFilter.filterQuotation(child.getOriginalCodeStr());
                    int line = par.getCodeLineNum();
                    NodeAttrs nodeAttrs = new NodeAttrs(label, line, child.getDotNum());
                    StmtVertex vertex = new StmtVertex(nodeAttrs);
                    graph.addVertex(vertex);
//                    if(astFlag) {
//                        ASTRecurive(child.getAstRootNode(), child.getDotNum());
//                    }
                }
            }
            if(cfgFlag) {
                //建立边结构
                for (GraphEdge edge : par.getEdgs()) {
                    EdgeTypes type = edge.getType();
                    StmtVertex src = graph.vertexSet().stream().filter(stmtVertex -> stmtVertex.getNodeAttrs().keyString().equals(edge.getOriginalNode().getDotNum())).findAny().get();
                    StmtVertex tgt = graph.vertexSet().stream().filter(stmtVertex -> stmtVertex.getNodeAttrs().keyString().equals(edge.getAimNode().getDotNum())).findAny().get();
                    StmtEdge stmtEdge = new StmtEdge(type, src, tgt);
                    graph.addEdge(src, tgt, stmtEdge);
                }
            }
        }
        if(dfgFlag){
            //数据流创建
            for(GraphEdge edge:this.allDFGEdgesList){
                EdgeTypes type = edge.getType();
                StmtVertex src = graph.vertexSet().stream().filter(stmtVertex -> stmtVertex.getNodeAttrs().keyString().equals(edge.getOriginalNode().getDotNum())).findAny().get();
                StmtVertex tgt = graph.vertexSet().stream().filter(stmtVertex -> stmtVertex.getNodeAttrs().keyString().equals(edge.getAimNode().getDotNum())).findAny().get();
                StmtEdge stmtEdge = new StmtEdge(type, src, tgt);
                graph.addEdge(src, tgt, stmtEdge);
            }
        }
//        if(ncsFlag && astFlag) { //必须有ast才能构建
//            NCS(leafNodes);
//        }
    }

    public DirectedMultigraph<StmtVertex, StmtEdge> buildGraph(GraphNode root) {
        boolean cfgFlag = Boolean.parseBoolean(prop.getProperty("node.cfg"));
        boolean astFlag = Boolean.parseBoolean(prop.getProperty("node.ast"));
        boolean dfgFlag = Boolean.parseBoolean(prop.getProperty("edge.dataflow"));
        boolean ncsFlag = Boolean.parseBoolean(prop.getProperty("edge.ncs"));
        BFS(root,cfgFlag,astFlag,dfgFlag,ncsFlag);
        return graph;
    }
}