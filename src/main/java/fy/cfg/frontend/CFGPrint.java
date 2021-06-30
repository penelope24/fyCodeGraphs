package fy.cfg.frontend;

import fy.structures.AstNode;
import fy.structures.EdgeTypes;
import fy.structures.GraphEdge;
import fy.structures.GraphNode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

/**
 * 纯cfg+ast+dfg+ncs
 */
public class CFGPrint {

    private String path; //打印文件到哪个目录下
    private StringBuilder str;
    private int index = 0; //表示节点的编号开始值
    private List<String> leafNodes;
    private Set<GraphEdge> allDFGEdgesList;
    private Properties prop;


    public CFGPrint(String path, Set<GraphEdge> allDFGEdgesList, Properties graphProps) {
        this.path = path;
        str = new StringBuilder("digraph {");
        leafNodes = new ArrayList<>();
        this.allDFGEdgesList = allDFGEdgesList;
        this.prop = graphProps;
    }

    /**
     * cfg 生成主要的方法
     * 广度优先遍历 提取所有的节点和边的信息
     *
     * @return 用于dot文件展示的数据
     */
    private void BFS(GraphNode root,boolean cfgFlag,boolean astFlag,boolean dfgFlag,boolean ncsFlag) {
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
                str.append(System.lineSeparator() + par.getDotNum() + " [label=\"" + DotPrintFilter.filterQuotation(par.getOriginalCodeStr()) + "\" , line=" + par.getCodeLineNum() + "];");
                //创立cfg节点同时 创建ast节点
                if(astFlag) {
                    ASTRecurive(par.getAstRootNode(), par.getDotNum());
                }
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
                    str.append(System.lineSeparator() + child.getDotNum() + " [label=\"" + DotPrintFilter.filterQuotation(child.getOriginalCodeStr()) + "\" , line=" + child.getCodeLineNum() +"];");
                    if(astFlag) {
                        ASTRecurive(child.getAstRootNode(), child.getDotNum());
                    }
                }
            }
            if(cfgFlag) {
                //建立边结构
                for (GraphEdge edge : par.getEdgs()) {
                    str.append(System.lineSeparator() + edge.getOriginalNode().getDotNum() + " -> " + edge.getAimNode().getDotNum() + "[color=" + edge.getType().getColor() + "];");
                }
            }
        }
        if(dfgFlag){
            //数据流创建
            for(GraphEdge edge:this.allDFGEdgesList){
                str.append(System.lineSeparator()+edge.getOriginalNode().getDotNum()+" -> "+edge.getAimNode().getDotNum()+"[color="+edge.getType().getColor()+"];");
            }
        }
        if(ncsFlag && astFlag) { //必须有ast才能构建
            NCS(leafNodes);
        }
        str.append(System.lineSeparator() + "}");
    }

    /**
     * ast 生成的主要方法
     * 按照ast Node进行递归生成节点
     */
    private void ASTRecurive(AstNode node, String parentNodeName) {
        if (node != null) {
            List<String> attributes = node.getAttributes();
            List<AstNode> subNodes = node.getSubNodes();
            List<String> subLists = node.getSubLists();
            List<List<AstNode>> subListNodes = node.getSubListNodes();

            String ndName = nextNodeName();

            if (!node.toString().equals("")) {
                str.append(System.lineSeparator() + ndName + " [label=\"" + DotPrintFilter.AstNodeFilter(node.getTypeName()) + "\", ast_node=\"true\"];");
//                if(DotPrintFilter.AstNodeFilter(node.getTypeName()).contains("identifier") || DotPrintFilter.AstNodeFilter(node.getTypeName()).contains("value") ){
//                    leafNodes.add(ndName);
//                }
            }
            if (parentNodeName != null) {
                str.append(System.lineSeparator() + parentNodeName + " -> " + ndName +"[color="+ EdgeTypes.AST.getColor()+"];");
            }

            for (String a : attributes) {
                String attrName = nextNodeName();
                str.append(System.lineSeparator() + attrName + " [label=\"" + DotPrintFilter.AstNodeFilter(a) + "\", ast_node=\"true\"];");
                str.append(System.lineSeparator() + ndName + " -> " + attrName +"[color="+ EdgeTypes.AST.getColor()+"];");
                if(DotPrintFilter.AstNodeFilter(a).contains("identifier") || DotPrintFilter.AstNodeFilter(a).contains("value") ){
                    leafNodes.add(attrName);
                }
            }

            for (int i = 0; i < subNodes.size(); i++) {
                ASTRecurive(subNodes.get(i), ndName);
            }

            for (int i = 0; i < subLists.size(); i++) {
                String ndLstName = nextNodeName();
                str.append(System.lineSeparator() + ndLstName + " [label=\"" + DotPrintFilter.AstNodeFilter(subLists.get(i)) + "\", ast_node=\"true\"];");
                str.append(System.lineSeparator() + ndName + " -> " + ndLstName +"[color="+ EdgeTypes.AST.getColor()+"];");
//                if(DotPrintFilter.AstNodeFilter(subLists.get(i)).contains("identifier") || DotPrintFilter.AstNodeFilter(subLists.get(i)).contains("value") ){
//                    leafNodes.add(ndName);
//                }
                for (int j = 0; j < subListNodes.get(i).size(); j++) {
                    ASTRecurive(subListNodes.get(i).get(j), ndLstName);
                }
            }
        }
    }

    private String nextNodeName() {
        return "n" + (index++);
    }

    /**
     * natural code sequence 自然语言代码序列
     * 按照叶子节点是 identifier 和value 进行连接
     * @parm allLeafNodes 所有的 identifier 和value 的叶子节点 dot文件的编号
     */
    public void NCS(List<String> allLeafNodes){
        if(allLeafNodes.size()>1) {
            /**
             * 所有叶子节点相连
             */
            for (int i=1;i<allLeafNodes.size();i++) {
               str.append(System.lineSeparator() + allLeafNodes.get(i-1) + " -> " + allLeafNodes.get(i) +"[color="+ EdgeTypes.NCS.getColor()+"];");
            }
        }
    }
    /**
     * 打印暴露的方法
     *
     * @param root 打印方法的图节点
     * @parm methodName 输出文件方法名字
     * @parm methodParms 方法参数名字
     * @parm ncs 表示是否输出ncs边的信息
     */
    public void print(GraphNode root, String methodName, String methodParms) {
        boolean cfgFlag = Boolean.parseBoolean(prop.getProperty("node.cfg"));
        boolean astFlag = Boolean.parseBoolean(prop.getProperty("node.ast"));
        boolean dfgFlag = Boolean.parseBoolean(prop.getProperty("edge.dataflow"));
        boolean ncsFlag = Boolean.parseBoolean(prop.getProperty("edge.ncs"));
        BFS(root,cfgFlag,astFlag,dfgFlag,ncsFlag);
        if (!new File(path).exists() && new File(path).isDirectory()) {
            new File(path).mkdir();
        }

        // 写入到文件中
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(
                    new File(path + File.separator + methodName + "." + methodParms + ".dot")));
            bufferedWriter.write(str.toString());
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch (Exception e) {
            System.out.println("数据写入ast文件发送异常！");
            System.out.println(e);
        }
    }
}
