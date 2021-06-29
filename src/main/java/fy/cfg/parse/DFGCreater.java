package fy.cfg.parse;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import fy.cfg.structure.DFVarNode;
import fy.cfg.structure.EdgeTypes;
import fy.cfg.structure.GraphEdge;
import fy.cfg.structure.GraphNode;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 利用CFG 构造数据流 采取的策略定义--赋值--使用 大范围定义变量进入小范围变量定义（小范围替换大范围）
 */
public class DFGCreater {
    /**
     * allCFGNodesMap 存储的是产生控制流时候创建的所有node信息
     */
    private HashMap<String, GraphNode> allCFGNodesMap;

    /**
     * 存储整个方法体所有数据流边的信息
     * @param allCFGNodesMap
     */
    private Set<GraphEdge> allDFGEdgesList;

    public DFGCreater(HashMap<String, GraphNode> allCFGNodesMap) {
        this.allCFGNodesMap = allCFGNodesMap;
        this.allDFGEdgesList = new HashSet<>();
    }


    /**
     * 构建数据流 需要依赖于cfg 也就是需要依赖于
     * @param node
     */
    public void buildMethodDFG(Node node) {
        if (node instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = ((MethodDeclaration) node).asMethodDeclaration();
            if(methodDeclaration.getParentNode().isPresent()) {
                if (!(methodDeclaration.getParentNode().get() instanceof TypeDeclaration)) {
                    return; //专门针对于匿名对象 匿名对象的方法不处理
                }
            }
//            System.out.println("********************************************");
//            System.out.println("当前正在CFG基础上生成DFG方法的名字：" +methodDeclaration.getDeclarationAsString(false,false,true));
//            System.out.println("********************************************");
            String label = methodDeclaration.getDeclarationAsString(false, true, true);
            int lineNum = methodDeclaration.getBegin().isPresent() ? methodDeclaration.getBegin().get().line : -1;
            //先拿到cfg的方法声明的节点 因为需要扩充cfg节点的细节信息
            GraphNode methodNode = allCFGNodesMap.get(label + ":" + lineNum);

            //整个方法为一个大范围 所以这个范围定义的变量在子范围都有效
            Set<DFVarNode> currentDefVarMap = new HashSet<>();

            // 先把方法的形参 都添加到定义的节点中
            NodeList<Parameter> parameters = methodDeclaration.getParameters();
            for(Parameter parameter:parameters){
                currentDefVarMap.add(new DFVarNode(parameter.getNameAsString(),methodNode));
            }

            Optional<BlockStmt> body = methodDeclaration.getBody();
            if (body.isPresent()) {
                NodeList<Statement> statements = body.get().getStatements();
                for (Statement statement : statements) {
                    //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                    currentDefVarMap = buildDFG(statement,currentDefVarMap);
                }
            }
        }
    }

    /**
     *
     * @param node 按照cfg的流程遍历整个方法体
     * @param parentDefVarMap 父范围内定义的变量 <变量名字,GraphNode信息>
     */
    private Set<DFVarNode> buildDFG(Node node,Set<DFVarNode> parentDefVarMap){
        /**
         * 先处理方法块内 无法继续递归的模块  无法递归说明就没有块结构 没有块结构那么就没有局部作用域
         */
        if (node instanceof ExpressionStmt) {
            ExpressionStmt expressionStmt = ((ExpressionStmt) node).asExpressionStmt();
            Expression expression = expressionStmt.getExpression();
            String label = expression.toString();
            int lineNum = expression.getBegin().isPresent() ? expression.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);

            //这个表达式的 定义边和使用边的信息
            return dealSingleRoadStmtDFG(parentDefVarMap,expression,cfgNode);

        }else if(node instanceof ReturnStmt){
            /*
            单线路
             */
            ReturnStmt returnStmt = ((ReturnStmt) node).asReturnStmt();
            String label = returnStmt.getExpression().isPresent() ? "return " + returnStmt.getExpression().get().toString() : "return";
            int lineNum = returnStmt.getBegin().isPresent() ? returnStmt.getBegin().get().line : -1;
            if (returnStmt.getExpression().isPresent()) {
                GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
                //处理 expression
                parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,returnStmt.getExpression().get(),cfgNode);
            }
            return parentDefVarMap;
        }else if(node instanceof AssertStmt){
            /*
            单线路
             */
            AssertStmt assertStmt = ((AssertStmt) node).asAssertStmt();

            String label = assertStmt.getMessage().isPresent() ? "assert" + assertStmt.getCheck().toString() + ";" + assertStmt.getMessage().get().toString() : "assert" + assertStmt.getCheck().toString();
            int lineNum = assertStmt.getBegin().isPresent() ? assertStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            return  dealSingleRoadStmtDFG(parentDefVarMap,assertStmt.getCheck(),cfgNode);

        }else if(node instanceof ThrowStmt){
            ThrowStmt throwStmt = ((ThrowStmt) node).asThrowStmt();

            String label = "throw " + throwStmt.getExpression();
            int lineNum = throwStmt.getBegin().isPresent() ? throwStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            return  dealSingleRoadStmtDFG(parentDefVarMap,throwStmt.getExpression(),cfgNode);
        }else if(node instanceof IfStmt){
            /**
             * 从这里开始块单元
             */
            IfStmt tempIfStmt = ((IfStmt) node).asIfStmt(); //最开始的if节点
            Set<DFVarNode> copy = this.copy(parentDefVarMap);
            while (tempIfStmt != null) {
                String ifLabel = "if (" + tempIfStmt.getCondition().toString() + ")";
                int ifLineNum = tempIfStmt.getBegin().isPresent() ? tempIfStmt.getBegin().get().line : -1;
                GraphNode ifCfgNode = allCFGNodesMap.get(ifLabel + ":" + ifLineNum);

                Set<DFVarNode> sonBlockDefVarSet = this.copy(copy);
                //if 条件语句中的修改 一般只有使用 没有定义 所以不用担心
                sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,tempIfStmt.getCondition(),ifCfgNode);

                if(!tempIfStmt.getThenStmt().isBlockStmt()){
                    //不是blockstmt块
                    sonBlockDefVarSet = buildDFG(tempIfStmt.getThenStmt(), sonBlockDefVarSet);
                }else {
                    //先处理这个if节点和最后跳出的节点向量
                    BlockStmt thenBlockStmt = tempIfStmt.getThenStmt().asBlockStmt();
                    NodeList<Statement> statements = thenBlockStmt.getStatements();
                    //到了子模块来了 就复制一份定义集合，这样所有新的定义变量除了这个范围不影响父范围的节点
                    for (Statement statement : statements) {
                        sonBlockDefVarSet = buildDFG(statement, sonBlockDefVarSet);
                    }
                }
                //父节点先合并一个分支
                parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet);

                if (tempIfStmt.getElseStmt().isPresent()) {
                    if (tempIfStmt.getElseStmt().get().isIfStmt()) {
                        tempIfStmt = tempIfStmt.getElseStmt().get().asIfStmt();
                    } else { //就是blockstmt
                        //else 分支的合并
                        Set<DFVarNode> sonBlockDefVarSet2 = this.copy(copy);
                        if(!tempIfStmt.getElseStmt().get().isBlockStmt()){
                            sonBlockDefVarSet2 = buildDFG(tempIfStmt.getElseStmt().get(),sonBlockDefVarSet2);
                        }else {
                            BlockStmt elseBlockStmt = tempIfStmt.getElseStmt().get().asBlockStmt();
                            NodeList<Statement> statements1 = elseBlockStmt.getStatements();
                            for (Statement statement : statements1) {
                                sonBlockDefVarSet2 = buildDFG(statement, sonBlockDefVarSet2);
                            }
                        }
                        //父节点先合并一个分支
                        parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet2);

                        tempIfStmt = null;
                    }
                } else {
                    tempIfStmt = null;
                }
            }
            return parentDefVarMap;
        } else if (node instanceof WhileStmt) {
            WhileStmt whileStmt = ((WhileStmt) node).asWhileStmt();
            String label = "while (" + whileStmt.getCondition().toString() + ")";
            int lineNum = whileStmt.getBegin().isPresent() ? whileStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理ast
            parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,whileStmt.getCondition(),cfgNode);

            //有新的block 就有新的局域范围
            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);
            if(!whileStmt.getBody().isBlockStmt()){
                sonBlockDefVarSet = buildDFG(whileStmt.getBody(), sonBlockDefVarSet);
            }else {
                NodeList<Statement> statements = whileStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return parentDefVarMap;
                }
                for (Statement statement : statements) {
                    sonBlockDefVarSet = buildDFG(statement, sonBlockDefVarSet);
                }
            }
            //合并分支
            parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet);
            return parentDefVarMap;
        }else if(node instanceof ForStmt){
            List<String> forValues = new ArrayList<>();

            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            ForStmt forStmt = ((ForStmt) node).asForStmt();
            forValues.add(StringUtils.join(forStmt.getInitialization(), ","));
            if (forStmt.getCompare().isPresent()) {
                forValues.add(forStmt.getCompare().get().toString());
            }
            forValues.add(StringUtils.join(forStmt.getUpdate(), ","));
            String label = "for(" + StringUtils.join(forValues, ';') + ")";
            int lineNum = forStmt.getBegin().isPresent() ? forStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            /**
             * for循环需要处理三种 expression  只是分析初始化 因为可能block用到i
              */
            NodeList<Expression> initialization = forStmt.getInitialization();
            for(Expression e:initialization){
                sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,e,cfgNode);
            }
            if(!forStmt.getBody().isBlockStmt()){
                sonBlockDefVarSet = buildDFG(forStmt.getBody(), sonBlockDefVarSet);
            }else {
                NodeList<Statement> statements = forStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return parentDefVarMap;
                }
                for (Statement statement : statements) {
                    sonBlockDefVarSet = buildDFG(statement, sonBlockDefVarSet);
                }
            }
            parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet);
            return parentDefVarMap;
        }else if(node instanceof ForEachStmt){
            ForEachStmt foreachStmt = ((ForEachStmt) node).asForEachStmt();
            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            String label = "for(" + foreachStmt.getVariable() + ":" + foreachStmt.getIterable() + ")";
            int lineNum = foreachStmt.getBegin().isPresent() ? foreachStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,foreachStmt.getVariable(),cfgNode);

            sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,foreachStmt.getIterable(),cfgNode);

            if(!foreachStmt.getBody().isBlockStmt()){
                sonBlockDefVarSet = buildDFG(foreachStmt.getBody(), sonBlockDefVarSet);
            }else {
                NodeList<Statement> statements = foreachStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return parentDefVarMap;
                }
                for (Statement statement : statements) {
                    sonBlockDefVarSet = buildDFG(statement, sonBlockDefVarSet);
                }
            }
            parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet);
            return parentDefVarMap;
        }else if(node instanceof SwitchStmt){

            SwitchStmt switchStmt = ((SwitchStmt) node).asSwitchStmt();

            String label = "switch(" + switchStmt.getSelector().toString() + ")";
            int lineNum = switchStmt.getBegin().isPresent() ? switchStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,switchStmt.getSelector(),cfgNode);


            NodeList<SwitchEntry> caseEntries = switchStmt.getEntries(); //case 入口
            if (caseEntries.size() == 0) {
                //表示如果while是空的话 直接返回当前节点
                return parentDefVarMap;
            }
            Set<DFVarNode> copy = this.copy(parentDefVarMap);
            for (int i = 0; i < caseEntries.size(); i++) {
                Set<DFVarNode> sonBlockDefVarSet = this.copy(copy);
                NodeList<Statement> statements = caseEntries.get(i).getStatements(); //一个case下面的所有语句
                /*
                case节点 没有进行数据流分析
                 */
                for (Statement statement : statements) {
                    sonBlockDefVarSet = buildDFG(statement,sonBlockDefVarSet);
                }
                //合并分支
                parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet);
            }
            return parentDefVarMap;
        }else if(node instanceof DoStmt){
            DoStmt doStmt = ((DoStmt) node).asDoStmt();

            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            NodeList<Statement> statements = doStmt.getBody().asBlockStmt().getStatements();
            for (Statement statement : statements) {
                sonBlockDefVarSet = buildDFG(statement, sonBlockDefVarSet);
            }

            String label = "while (" + doStmt.getCondition().toString() + ")";
            int lineNum = doStmt.getCondition().getBegin().isPresent() ? doStmt.getCondition().getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);

            sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,doStmt.getCondition(),cfgNode);

            //合并分支
            parentDefVarMap = this.merge(parentDefVarMap,sonBlockDefVarSet);
            return parentDefVarMap;
        }else if(node instanceof LabeledStmt){
            LabeledStmt labeledStmt = ((LabeledStmt) node).asLabeledStmt();
            buildDFG(labeledStmt.getStatement(),parentDefVarMap);
        }else if(node instanceof SynchronizedStmt){
            SynchronizedStmt synchronizedStmt = ((SynchronizedStmt) node).asSynchronizedStmt();

            String label = "synchronized (" + synchronizedStmt.getExpression() + ")";
            int lineNum = synchronizedStmt.getBegin().isPresent() ? synchronizedStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            // 处理 expression
            parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,synchronizedStmt.getExpression(),cfgNode);

            BlockStmt body = synchronizedStmt.getBody();
            NodeList<Statement> statements = body.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                parentDefVarMap = buildDFG(statement, parentDefVarMap);
            }
            return parentDefVarMap;
        }else if(node instanceof BlockStmt){
            BlockStmt blockStmt = ((BlockStmt) node).asBlockStmt();
            NodeList<Statement> statements = blockStmt.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                parentDefVarMap = buildDFG(statement, parentDefVarMap);
            }
            return parentDefVarMap;
        }else if(node instanceof TryStmt){
            TryStmt tryStmt = ((TryStmt) node).asTryStmt();
            String label = tryStmt.getResources().size() == 0 ? "try" : "try(" + StringUtils.join(tryStmt.getResources(), ";") + ")";
            int lineNum = tryStmt.getBegin().isPresent() ? tryStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理ast
            /**
             * 处理try中的表达式
             */
            NodeList<Expression> resources = tryStmt.getResources();
            for(Expression e:resources){
               parentDefVarMap =  dealSingleRoadStmtDFG(parentDefVarMap,e,cfgNode);
            }

            BlockStmt tryBlock = tryStmt.getTryBlock();
            NodeList<Statement> statements = tryBlock.getStatements();
            for (Statement statement : statements) {
                parentDefVarMap = buildDFG(statement, parentDefVarMap);
            }
            Optional<BlockStmt> finallyBlock = tryStmt.getFinallyBlock();
            if (finallyBlock.isPresent()) {
                //开始finllay 模块
                NodeList<Statement> finaBodyStas = finallyBlock.get().getStatements();
                for (Statement statement : finaBodyStas) {
                    //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                    parentDefVarMap = buildDFG(statement, parentDefVarMap);
                }
            }
            return parentDefVarMap;
        }else if(node instanceof LocalClassDeclarationStmt){
            LocalClassDeclarationStmt localClassDeclarationStmt = ((LocalClassDeclarationStmt) node).asLocalClassDeclarationStmt();
            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = localClassDeclarationStmt.getClassDeclaration();

            String label = classOrInterfaceDeclaration.getNameAsString();
            int lineNum = classOrInterfaceDeclaration.getBegin().isPresent()?classOrInterfaceDeclaration.getBegin().get().line:-1;

            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression

//            List<MethodDeclaration> allmethods = classOrInterfaceDeclaration.findAll(MethodDeclaration.class);
//            for (MethodDeclaration methodDeclaration : allmethods) {
//                buildMethodDFG(methodDeclaration);
//            }

        }
        return parentDefVarMap;
    }

    /**
     * 分析数据流的时候 就不是在statement级别上了，就需要在expression级别上了
     * @param expression
     */
    private void expressionAnalysis(Expression expression){
        if(expression instanceof ArrayAccessExpr){
            /*
             ArrayAccessExpr 就是获取数组值表达式 比如a = 12; datas[a]
             */
            ArrayAccessExpr arrayAccessExpr = expression.asArrayAccessExpr();
            System.out.println(arrayAccessExpr+"当前："+ArrayAccessExpr.class);
            Expression name = arrayAccessExpr.getName();
            Expression index = arrayAccessExpr.getIndex();
            expressionAnalysis(name);

        }else if(expression instanceof ClassExpr){
            /*
            ClassExpr Object.class 一个类获取class对象
             */
            ClassExpr classExpr = expression.asClassExpr();
            System.out.println(classExpr+"当前："+ClassExpr.class);
        }else if(expression instanceof ArrayCreationExpr){
            /*
            ArrayCreationExpr new int[5] 5 可能变成其他变量 所以可能改变数据流
             */
            ArrayCreationExpr arrayCreationExpr = expression.asArrayCreationExpr();
            System.out.println(arrayCreationExpr+"当前："+ArrayCreationExpr.class);

        }else if(expression instanceof LambdaExpr){
            /*
            lambda (a, b) -> a+b 这是定义函数的方式 所以对于数据流没有任何帮助
             */
            LambdaExpr lambdaExpr = expression.asLambdaExpr();
            System.out.println(lambdaExpr+"当前："+LambdaExpr.class);

        }else if(expression instanceof  ConditionalExpr){
            /*
             条件表达式 比如 if(a) 也就是里面有用
             */
            ConditionalExpr conditionalExpr = expression.asConditionalExpr();
            System.out.println(conditionalExpr+"当前："+ConditionalExpr.class);
        }else if(expression instanceof MethodCallExpr){
            /*
            MethodCallExpr System.out.println("true");
             */
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
            System.out.println(methodCallExpr+"当前："+MethodCallExpr.class);

        }else if(expression instanceof  AnnotationExpr){
            /*
            对数据流没有任何影响 这是方法的注解
             */
            AnnotationExpr annotationExpr = expression.asAnnotationExpr();
            System.out.println(annotationExpr+"当前："+AnnotationExpr.class);

        }else if(expression instanceof AssignExpr){
            /*
            赋值表达式   datas[0] = 1;
             */
            AssignExpr assignExpr = expression.asAssignExpr();
            System.out.println(assignExpr+"当前："+AssignExpr.class);
            expressionAnalysis(assignExpr.getTarget());

        }else if(expression instanceof InstanceOfExpr){
            /*
            instance of 对于数据流没有任何影响
             */
            InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
            System.out.println(instanceOfExpr+"当前："+InstanceOfExpr.class);

        }else if(expression instanceof CastExpr){
            /*
            caseExpr  对于数据流没有任何影响 (long)15 long数字
             */
            CastExpr castExpr = expression.asCastExpr();
            System.out.println(castExpr+"当前："+CastExpr.class);

        }else if(expression instanceof NameExpr){
            /*
            变量的名字  switch(a) 里面的a
             */
            NameExpr nameExpr = expression.asNameExpr();
            System.out.println(nameExpr+"当前："+NameExpr.class);

        }else if(expression instanceof ThisExpr){
            /*
            this 字符 对于数据流没有任何影响
             */
            ThisExpr thisExpr = expression.asThisExpr();
            System.out.println(thisExpr+"当前："+ThisExpr.class);

        }else if(expression instanceof EnclosedExpr){
            /*
              括号内的表达式 (1+1)
             */
            EnclosedExpr enclosedExpr = expression.asEnclosedExpr();
            System.out.println(enclosedExpr+"当前："+EnclosedExpr.class);

        }else if(expression instanceof MethodReferenceExpr){
            /*
             方法引用 左边是对象 System.out::println 的println
             */
            MethodReferenceExpr methodReferenceExpr = expression.asMethodReferenceExpr();
            System.out.println(methodReferenceExpr+"当前："+MethodReferenceExpr.class);

        }else if(expression instanceof VariableDeclarationExpr){
            /*
            VariableDeclarator 是 VariableDeclarationExpr 更细的粒度
            int[] datas = { 1, 2, 3, 4 } int a = 10
             */
            VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
            System.out.println(variableDeclarationExpr+"当前："+VariableDeclarationExpr.class);
            NodeList<VariableDeclarator> variables = variableDeclarationExpr.getVariables();
            for(VariableDeclarator var:variables){


            }


        }else if(expression instanceof LiteralExpr){
            /*
            文字表达式 也就是null true 等数值 对于数据流毫无影响
             */
            LiteralExpr literalExpr = expression.asLiteralExpr();
            System.out.println(literalExpr+"当前："+LiteralExpr.class);

        }else if(expression instanceof ObjectCreationExpr){
            /*
            new Exception("yichang") 声明变量的后一半
             */
            ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
            System.out.println(objectCreationExpr+"当前："+ObjectCreationExpr.class);

        }else if(expression instanceof UnaryExpr){
            /*
            一元运算符 i++
             */
            UnaryExpr unaryExpr = expression.asUnaryExpr();
            System.out.println(unaryExpr+"当前："+UnaryExpr.class);
        }else if(expression instanceof SuperExpr){
            /*
               SuperExpr  super 这个字符 对于数据流影响
             */
            SuperExpr superExpr = expression.asSuperExpr();
            System.out.println(superExpr+"当前："+SuperExpr.class);

        }else if(expression instanceof BinaryExpr){
            /*
            二元操作符表达式 比如if 条件中 a==10
             */
            BinaryExpr binaryExpr = expression.asBinaryExpr();
            System.out.println(binaryExpr+"当前："+BinaryExpr.class);

        }else if(expression instanceof TypeExpr){
            /*
            方法引用 World::greet 的world 就是类型 类名字
             */
            TypeExpr typeExpr = expression.asTypeExpr();
            System.out.println(typeExpr+"当前："+TypeExpr.class);

        }else if(expression instanceof ArrayInitializerExpr){
            /*
            new int[][] {{1, 1}, {2, 2}}
             */
            ArrayInitializerExpr arrayInitializerExpr = expression.asArrayInitializerExpr();
            System.out.println(arrayInitializerExpr+"当前："+ArrayInitializerExpr.class);

        }else if(expression instanceof FieldAccessExpr){
            /*
            对象获取属性 FieldAccessExpr person.name
             */
            FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
            System.out.println(fieldAccessExpr+"当前："+FieldAccessExpr.class);
        }
    }

    /**
     * 解析表达式得到这个表达式所使用的变量，定义的变量直接通过创建来的 List<HashMap<String,GraphNode>> parentDefVarMap
     * 存储起来，所以只需要返回这个expression 所用过的变量的信息
     * @return list[0] 存储使用的变量 ，list[1] 存储定义的变量  因为更新定义的变量必须在使用的变量边创建之后才行
     */
    private List<Set<String>> analysisExprForVar(Expression expression){
        if(expression instanceof ArrayAccessExpr){
            /*
             ArrayAccessExpr 就是获取数组值表达式 比如datas[a]
             */
            ArrayAccessExpr arrayAccessExpr = expression.asArrayAccessExpr();
            Expression name = arrayAccessExpr.getName();
            Expression index = arrayAccessExpr.getIndex();
            List<Set<String>> sets = analysisExprForVar(name);
            List<Set<String>> sets1 = analysisExprForVar(index);
            // 将使用过的变量和定义的变量都存储起来
            sets.get(0).addAll(sets1.get(0));
            sets.get(1).addAll(sets1.get(1));
            return sets;
        }else if(expression instanceof ClassExpr){
            /*
            ClassExpr Object.class 一个类获取class对象
             */
            ClassExpr classExpr = expression.asClassExpr();
        }else if(expression instanceof ArrayCreationExpr){
            /*
            ArrayCreationExpr new int[5] 5 可能变成其他变量 所以可能改变数据流
             */
            ArrayCreationExpr arrayCreationExpr = expression.asArrayCreationExpr();
            NodeList<ArrayCreationLevel> levels = arrayCreationExpr.getLevels();
            List<Set<String>> result = new ArrayList<>();
            Set<String> s0 = new HashSet<>();
            Set<String> s1 = new HashSet<>();
            result.add(s0);
            result.add(s1);
            for(ArrayCreationLevel a:levels){
                //把数组创建中维度是变量的节点信息记住！
                if(a.getDimension().isPresent()){
                    List<Set<String>> sets = analysisExprForVar(a.getDimension().get());
                    result.get(0).addAll(sets.get(0));
                    result.get(1).addAll(sets.get(1));
                }
            }
            if(arrayCreationExpr.getInitializer().isPresent()){
                ArrayInitializerExpr arrayInitializerExpr = arrayCreationExpr.getInitializer().get();
                NodeList<Expression> values = arrayInitializerExpr.getValues();
                for(Expression expression1:values){
                    List<Set<String>> sets = analysisExprForVar(expression1);
                    result.get(0).addAll(sets.get(0));
                    result.get(1).addAll(sets.get(1));
                }
            }
            return result;

        }else if(expression instanceof LambdaExpr){
            /*
            lambda (a, b) -> a+b 这是定义函数的方式 所以对于数据流没有任何帮助
             */
            LambdaExpr lambdaExpr = expression.asLambdaExpr();
        }else if(expression instanceof  ConditionalExpr){
            /*
             条件表达式 比如 if(a) 也就是里面有用 ifstmt中的if 包含在这个expr里面
             */
            ConditionalExpr conditionalExpr = expression.asConditionalExpr();
            List<Set<String>> sets = analysisExprForVar(conditionalExpr.getCondition());
            List<Set<String>> sets1 = analysisExprForVar(conditionalExpr.getThenExpr());
            List<Set<String>> sets2 = analysisExprForVar(conditionalExpr.getElseExpr());
            sets.get(0).addAll(sets1.get(0));
            sets.get(1).addAll(sets1.get(1));
            sets.get(0).addAll(sets2.get(0));
            sets.get(1).addAll(sets2.get(1));
            return sets;
        }else if(expression instanceof MethodCallExpr){
            /*
            MethodCallExpr System.out.println("true");
             */
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
            List<Set<String>> result = new ArrayList<>();
            Set<String> s0 = new HashSet<>();
            Set<String> s1 = new HashSet<>();
            result.add(s0);
            result.add(s1);
            //这个是得到方法的调用变量
            if(methodCallExpr.getScope().isPresent()){
                List<Set<String>> sets = analysisExprForVar(methodCallExpr.getScope().get());
                result.get(0).addAll(sets.get(0));
                result.get(1).addAll(sets.get(1));
            }
            //继续拿到方法的参数变量名字
            NodeList<Expression> arguments = methodCallExpr.getArguments();
            for(Expression expression1:arguments){
                List<Set<String>> sets = analysisExprForVar(expression1);
                result.get(0).addAll(sets.get(0));
                result.get(1).addAll(sets.get(1));
            }
            return result;
        }else if(expression instanceof  AnnotationExpr){
            /*
            对数据流没有任何影响 这是方法的注解
             */
            AnnotationExpr annotationExpr = expression.asAnnotationExpr();

        }else if(expression instanceof AssignExpr){
            /*
            赋值表达式   datas[0] = 1;
             */
            AssignExpr assignExpr = expression.asAssignExpr();
            List<Set<String>> sets = analysisExprForVar(assignExpr.getTarget());
            sets.get(1).addAll(sets.get(0));
            //注意上面是赋值操作的左边 所以这些变量 1.是赋值变量 2.是使用变量 依赖于operator
//            if(!assignExpr.getOperator().equals(AssignExpr.Operator.ASSIGN)){
//                //左边的变量是使用变量的同时也是定义变量 默认的name 里面都是使用变量
//                sets.get(1).addAll(sets.get(0));
//            }else{
//                //assign 就是定义变量
//                for(String s:sets.get(0)){
//                    sets.get(1).add(s);
//                }
//                //清空定义变量
//                sets.get(0).clear();
//            }

            // 赋值语句右边就是使用变量
            List<Set<String>> sets1 = analysisExprForVar(assignExpr.getValue());

            sets.get(0).addAll(sets1.get(0));
            sets.get(1).addAll(sets1.get(1));

            return sets;

        }else if(expression instanceof InstanceOfExpr){
            /*
            instance of 对于数据流 有影响就是用数据流
             */
            InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
            return analysisExprForVar(instanceOfExpr.getExpression());
        }else if(expression instanceof CastExpr){
            /*
            caseExpr  对于数据流没有任何影响 (long)15 long数字
             */
            CastExpr castExpr = expression.asCastExpr();
            return  analysisExprForVar(castExpr.getExpression());
        }else if(expression instanceof NameExpr){
            /*
            变量的名字  switch(a) 里面的a
             */
            NameExpr nameExpr = expression.asNameExpr();
            List<Set<String>> result = new ArrayList<>();
            Set<String> s0 = new HashSet<>();
            Set<String> s1 = new HashSet<>();
            //默认解析到NameExpr的时候 都是来源于使用的，对于Assign单独处理
            s0.add(nameExpr.getName().getIdentifier());
            result.add(s0);
            result.add(s1);
            return result;

        }else if(expression instanceof ThisExpr){
            /*
            this 字符 对于数据流没有任何影响
             */
            ThisExpr thisExpr = expression.asThisExpr();
            if(thisExpr.getTypeName().isPresent()){
                return analysisExprForVar(thisExpr.asClassExpr());
            }
        }else if(expression instanceof EnclosedExpr){
            /*
              括号内的表达式 (1+1)
             */
            EnclosedExpr enclosedExpr = expression.asEnclosedExpr();
            List<Set<String>> sets = analysisExprForVar(enclosedExpr.getInner());
            return  sets;

        }else if(expression instanceof MethodReferenceExpr){
            /*
             方法引用 左边是对象 System.out::println 的println
             */
            MethodReferenceExpr methodReferenceExpr = expression.asMethodReferenceExpr();
            return analysisExprForVar(methodReferenceExpr.getScope());

        }else if(expression instanceof VariableDeclarationExpr){
            /*
            VariableDeclarator 是 VariableDeclarationExpr 更细的粒度
            int[] datas = { 1, 2, 3, 4 } int a = 10
             */
            //只有这个节点才是变量定义节点 所以需要记录当前节点的变量定义信息！
            VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
            NodeList<VariableDeclarator> variables = variableDeclarationExpr.getVariables();
            List<Set<String>> result = new ArrayList<>();
            Set<String> s0 = new HashSet<>();
            Set<String> s1 = new HashSet<>();
            result.add(s0);
            result.add(s1);
            for(VariableDeclarator var:variables){
                s1.add(var.getNameAsString());
                if(var.getInitializer().isPresent()){
                    List<Set<String>> sets = analysisExprForVar(var.getInitializer().get());
                    result.get(0).addAll(sets.get(0));
                    result.get(1).addAll(sets.get(1));
                }
            }
            return result;
        }else if(expression instanceof LiteralExpr){
            /*
            文字表达式 也就是null true 等数值 对于数据流毫无影响
             */
            LiteralExpr literalExpr = expression.asLiteralExpr();

        }else if(expression instanceof ObjectCreationExpr){
            /*
            new Exception("yichang") 声明变量的后一半
             */
            ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();

            List<Set<String>> result = new ArrayList<>();
            Set<String> s0 = new HashSet<>();
            Set<String> s1 = new HashSet<>();
            result.add(s0);
            result.add(s1);
            NodeList<Expression> arguments = objectCreationExpr.getArguments();
            for(Expression expression1:arguments){
                List<Set<String>> sets = analysisExprForVar(expression1);
                result.get(0).addAll(sets.get(0));
                result.get(1).addAll(sets.get(1));
            }
            return result;
        }else if(expression instanceof UnaryExpr){
            /*
            一元运算符 i++
             */
            UnaryExpr unaryExpr = expression.asUnaryExpr();
            List<Set<String>> sets = analysisExprForVar(unaryExpr.getExpression());
            //assign 就是定义变量
            UnaryExpr.Operator operator = unaryExpr.getOperator();
            if(operator.equals(UnaryExpr.Operator.PREFIX_DECREMENT)|| operator.equals(UnaryExpr.Operator.PREFIX_INCREMENT)||operator.equals(UnaryExpr.Operator.POSTFIX_DECREMENT)|| operator.equals(UnaryExpr.Operator.POSTFIX_INCREMENT) ){
                //这四种符号的变量 既充当使用的变量 也充当赋值的变量
                sets.get(1).addAll(sets.get(0));
            }
            return sets;
        }else if(expression instanceof SuperExpr){
            /*
               SuperExpr  super 这个字符 对于数据流影响
             */
            SuperExpr superExpr = expression.asSuperExpr();
            if(superExpr.getTypeName().isPresent()){
                return analysisExprForVar(superExpr.asClassExpr());
            }
        }else if(expression instanceof BinaryExpr){
            /*
            二元操作符表达式 比如if 条件中 a==10
             */
            BinaryExpr binaryExpr = expression.asBinaryExpr();
            List<Set<String>> sets = analysisExprForVar(binaryExpr.getLeft());
            List<Set<String>> sets1 = analysisExprForVar(binaryExpr.getRight());
            sets.get(0).addAll(sets1.get(0));
            sets.get(1).addAll(sets1.get(1));
            return sets;
        }else if(expression instanceof TypeExpr){
            /*
            方法引用 World::greet 的world 就是类型 类名字
             */
            TypeExpr typeExpr = expression.asTypeExpr();

        }else if(expression instanceof ArrayInitializerExpr){
            /*
            new int[][] {{1, 1}, {2, 2}}
             */
            ArrayInitializerExpr arrayInitializerExpr = expression.asArrayInitializerExpr();
            List<Set<String>> result = new ArrayList<>();
            Set<String> s0 = new HashSet<>();
            Set<String> s1 = new HashSet<>();
            result.add(s0);
            result.add(s1);
            NodeList<Expression> values = arrayInitializerExpr.getValues();
            for(Expression expression1:values){
                List<Set<String>> sets = analysisExprForVar(expression1);
                result.get(0).addAll(sets.get(0));
                result.get(1).addAll(sets.get(1));
            }
            return result;
        }else if(expression instanceof FieldAccessExpr){
            /*
            对象获取属性 FieldAccessExpr person.name
             */
            FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
            List<Set<String>> sets = analysisExprForVar(fieldAccessExpr.getScope());
            return sets;
        }
        Set<String> s1 = new HashSet<>();
        Set<String> s2 = new HashSet<>();
        List<Set<String>> result = new ArrayList<>();
        result.add(s1);
        result.add(s2);
        return result;
    }

    /**
     * 代码中很多单线路的stmt 这些stmt的更新方式就先先把expression的引用的变量连上边，然后就是更新定义变量的信息
     * @param expression 需要分析的源码表达式
     * @param node 被分析源码所在的node的信息
     * @return 更新之后的定义的变量信息
     */
    private Set<DFVarNode> dealSingleRoadStmtDFG(Set<DFVarNode> parentDefVarMap,Expression expression,GraphNode node){
        //处理 expression
        List<Set<String>> sets = analysisExprForVar(expression);
        //先解决 变量使用的边
        for(String usedVarName:sets.get(0)){
            for(DFVarNode dfVarNode:parentDefVarMap){
                if(dfVarNode.getVarName().equals(usedVarName)){
                    // 添加数据流的边
                    this.allDFGEdgesList.add(new GraphEdge(EdgeTypes.DFG, dfVarNode.getNode(), node));
                }
            }
        }
        //更新定义的边  单线路就是求交集
        for(String defVarName:sets.get(1)){
            parentDefVarMap = this.intersection(parentDefVarMap, defVarName, node);
        }
        return parentDefVarMap;
    }

    /**
     *单线路的节点 数据流就是取交集 保留最新节点信息
     * @param in 表示到达节点之前的变量定义的信息
     * @param varName 变量的名字信息
     * @return  返回去除了除了当前这个变量之前的所有该变量，因为是单线路
     */
    private Set<DFVarNode> intersection(Set<DFVarNode> in,String varName,GraphNode node){
        Iterator<DFVarNode> iterator = in.iterator();
        while(iterator.hasNext()){
            DFVarNode next = iterator.next();
            if(next.getVarName().equals(varName)){
                //只要名字相同的变量 都要被删除 用这个替代
                iterator.remove();
            }
        }
        in.add(new DFVarNode(varName,node));
        return in;
    }

    /**
     *多线路的节点 数据流就是取并集 保留多条路径信息
     * @param in1 第一个分支出来的变量定义的信息
     * @param in2 第二个分支出来的变量定义的信息
     * @return  返回所有分支并起来的数据信息
     */
    private Set<DFVarNode> merge( Set<DFVarNode> in1,Set<DFVarNode> in2){
        //因为已经实现了var和node必须一样才算一致，所以对于分支改变了父区域的变量，都会合并到一起，每个分支的node都不同
        //合并的只是多个分支修改的父类方法的变量，而不是合并分支里面定义的变量
        Set<DFVarNode> copy = this.copy(in1);
        for(DFVarNode dfVarNode:in1){
            for(DFVarNode dfVarNode1:in2){
                // 也就是分支修改了父类定义过的变量才能合并
                if(dfVarNode.getVarName().equals(dfVarNode1.getVarName())){
                    copy.add(new DFVarNode(dfVarNode1.getVarName(),dfVarNode1.getNode()));
                }
            }
        }
        return copy;
    }

    /**
     * 每一个局部作用域 都有自己的作用范围 超过这个范围，在该范围内定义的变量都会被消除
     * @param in 父范围定义的变量信息
     * @return  返回只是copy这个父范围的变量信息的一样的数据结构对象
     */
    private Set<DFVarNode> copy( Set<DFVarNode> in){
        Set<DFVarNode> result = new HashSet<>();
        result.addAll(in);
        return result;
    }

    public Set<GraphEdge> getAllDFGEdgesList() {
        return allDFGEdgesList;
    }

    public void setAllDFGEdgesList(Set<GraphEdge> allDFGEdgesList) {
        this.allDFGEdgesList = allDFGEdgesList;
    }
}
